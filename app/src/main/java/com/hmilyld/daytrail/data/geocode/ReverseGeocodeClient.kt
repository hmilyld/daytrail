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
                    IllegalStateException("没有配置高德 Web 服务 Key"),
                )
            }
            if (!point.isValid) {
                return@withContext Result.failure(IllegalArgumentException("坐标无效"))
            }

            runCatching {
                val location = "${point.longitude.format()},${point.latitude.format()}"
                val connection =
                    (
                        URL("$url?key=$apiKey&location=$location&extensions=all&radius=200")
                            .openConnection() as HttpURLConnection
                    ).apply {
                        requestMethod = "GET"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                    }

                try {
                    val code = connection.responseCode
                    val text =
                        (if (code in 200..299) connection.inputStream else connection.errorStream)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()

                    if (code !in 200..299) {
                        error("逆地理编码返回 HTTP $code")
                    }
                    parseName(text) ?: error("这个位置查不到地名")
                } finally {
                    connection.disconnect()
                }
            }
        }

    /**
     * 解析返回报文。
     *
     * 高德在字段缺失时会返回空数组 `[]` 而不是 `null`，所以不能直接按 String 反序列化，
     * 这里逐个字段取 [JsonElement] 再判断是不是字符串。
     *
     * internal 是为了单测：这段解析逻辑最容易在真实报文上翻车。
     */
    internal fun parseName(text: String): String? {
        val root =
            runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                ?: return null
        if (root["status"].text() != "1") return null
        val regeocode = root["regeocode"] as? JsonObject ?: return null
        return nameFrom(regeocode)
    }

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
