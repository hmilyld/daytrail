package com.hmilyld.daytrail.data.geocode

import com.hmilyld.daytrail.BuildConfig
import com.hmilyld.daytrail.core.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToLong

/**
 * 高德「逆地理编码」Web 服务客户端：坐标 → 地名。
 *
 * 为什么走 Web 服务而不是 SDK 的 `GeocodeSearch`：
 * `3dmap` 的 jar 里没有 `com.amap.api.services.*`，要用就得再加一个 `com.amap.api:search`
 * 依赖；而项目里已经有 Web 服务 Key（`AMAP_WEB_KEY`，纠偏用的那个），
 * 直接发一次 GET 就够，不引入新依赖。
 *
 * 传进去的必须是 **GCJ-02**——库内统一按 GCJ-02 存（见 AGENTS.md 4.14），
 * 高德底图也是 GCJ-02，正好对得上，不要再转 WGS-84。
 *
 * 取名按「从细到粗」退让：最近 POI → 街道+门牌 → 乡镇/街道 → 区县 → 城市。
 * 用户说「粗略的也可以」，所以退到区县也是可接受的结果；全都没有就返回失败，
 * 调用方保持「未命名」即可。
 */
class ReverseGeocodeClient(
    private val url: String = ENDPOINT,
    private val apiKey: String = BuildConfig.AMAP_WEB_KEY,
) {
    /** 没配置 Key 时调用方应直接跳过，而不是请求出去再失败。 */
    val configured: Boolean get() = apiKey.isNotBlank()

    suspend fun lookup(point: GeoPoint): Result<String> =
        withContext(Dispatchers.IO) {
            if (!configured) {
                return@withContext Result.failure(
                    ReverseGeocodeException(NamingFailure.NO_KEY, "AMAP_WEB_KEY not configured"),
                )
            }
            if (!point.isValid) {
                return@withContext Result.failure(
                    ReverseGeocodeException(NamingFailure.INVALID_POINT, "invalid coordinate"),
                )
            }

            runCatching {
                var connection: HttpURLConnection? = null
                try {
                    // 坐标放请求体、只有 Key 留在查询串：
                    // GET 会把「Key + 精确坐标」整条写进 URL，任何系统代理日志、
                    // 抓包工具、企业 MDM 都能原样看到。高德 Web 服务支持 POST。
                    val body =
                        "location=${point.longitude.format()},${point.latitude.format()}" +
                            "&extensions=all&radius=200"
                    val conn =
                        (URL("$url?key=$apiKey").openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            doOutput = true
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            setRequestProperty(
                                "Content-Type",
                                "application/x-www-form-urlencoded; charset=utf-8",
                            )
                        }
                    connection = conn

                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw ReverseGeocodeException(NamingFailure.NETWORK, "HTTP $code")
                    }
                    val text =
                        conn.inputStream
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()

                    when (val parsed = parse(text)) {
                        is RegeoParse.Named -> parsed.name
                        is RegeoParse.Rejected ->
                            throw ReverseGeocodeException(
                                NamingFailure.API_REJECTED,
                                "regeo rejected: ${parsed.info}",
                            )
                        RegeoParse.NoName ->
                            throw ReverseGeocodeException(NamingFailure.NO_NAME, "no place name")
                    }
                } catch (e: ReverseGeocodeException) {
                    throw e
                } catch (e: Exception) {
                    // 断网、超时、代理返回非 JSON 等都归到网络类。
                    throw ReverseGeocodeException(
                        NamingFailure.NETWORK,
                        e.message ?: "network error",
                    )
                } finally {
                    connection?.disconnect()
                }
            }
        }

    /**
     * 解析返回报文，并区分「高德拒绝」和「查不到名字」。
     *
     * 高德在字段缺失时会返回空数组 `[]` 而不是 `null`，所以不能直接按 String 反序列化，
     * 这里逐个字段取 [JsonElement] 再判断是不是字符串。
     *
     * internal 是为了单测：这段解析逻辑最容易在真实报文上翻车。
     */
    internal fun parse(text: String): RegeoParse {
        val root =
            json.parseToJsonElement(text) as? JsonObject
                ?: return RegeoParse.Rejected(null)
        if (root["status"].text() != "1") {
            return RegeoParse.Rejected(root["info"].text())
        }
        val regeocode = root["regeocode"] as? JsonObject ?: return RegeoParse.NoName
        return nameFrom(regeocode)?.let { RegeoParse.Named(it) } ?: RegeoParse.NoName
    }

    /** 保留给既有单测与「只想要名字」的调用方：拿到名字或 null。 */
    internal fun parseName(text: String): String? =
        (runCatching { parse(text) }.getOrNull() as? RegeoParse.Named)?.name

    private fun nameFrom(regeocode: JsonObject): String? {
        val nearestPoi = (regeocode["pois"] as? JsonArray)?.firstOrNull() as? JsonObject
        nearestPoi?.get("name").text()?.let { if (it.isNotBlank()) return it }

        val component = regeocode["addressComponent"] as? JsonObject
        val streetNumber = component?.get("streetNumber") as? JsonObject
        val street = streetNumber?.get("street").text()
        if (!street.isNullOrBlank()) {
            val number = streetNumber?.get("number").text()
            return if (number.isNullOrBlank()) street else "$street$number"
        }

        for (key in FALLBACK_KEYS) {
            val value = component?.get(key).text()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /** 缺字段时高德给的是 `[]`，只有真正的字符串才算数。 */
    private fun JsonElement?.text(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }

    private fun Double.format(): String {
        val scaled = (this * 1_000_000).roundToLong()
        return (scaled / 1_000_000.0).toString()
    }

    private companion object {
        const val ENDPOINT = "https://restapi.amap.com/v3/geocode/regeo"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000

        /** 退让顺序：乡镇/街道 → 区县 → 城市 → 省。 */
        val FALLBACK_KEYS = listOf("township", "district", "city", "province")

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** 报文解析的三种结局。internal 单测要断言这三种，不能只看名字。 */
internal sealed interface RegeoParse {
    data class Named(
        val name: String,
    ) : RegeoParse

    /** status != 1，[info] 是高德给的原因（如 INVALID_USER_KEY）。 */
    data class Rejected(
        val info: String?,
    ) : RegeoParse

    /** 请求成功但取不到名字。 */
    data object NoName : RegeoParse
}

/** 把失败原因带出 [ReverseGeocodeClient.lookup] 的 [Result]。 */
class ReverseGeocodeException(
    val failure: NamingFailure,
    message: String,
) : Exception(message)
