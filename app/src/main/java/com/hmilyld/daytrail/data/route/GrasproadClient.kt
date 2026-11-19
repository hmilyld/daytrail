package com.hmilyld.daytrail.data.route

import com.hmilyld.daytrail.BuildConfig
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.domain.TrackCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** 纠偏结果：贴路后的折线与里程。 */
data class CorrectedRoute(
    /** 已按「过长的直连段」切开，每段都是可信的路网折线。 */
    val segments: List<List<GeoPoint>>,
    val distanceMeters: Double,
)

/**
 * 高德「轨迹纠偏」Web 服务客户端。
 *
 * 用 v4 的独立接口而不是猎鹰：猎鹰要先建服务、建终端、上传轨迹再查询，
 * 而这里只需要一个 POST。个人认证的配额是每天 1 万次，对我们够用。
 *
 * 只在**用户点按钮**时调用，录制期间不联网——这是「按需纠偏」的全部意义。
 */
class GrasproadClient(
    private val url: String = ENDPOINT,
    private val apiKey: String = BuildConfig.AMAP_WEB_KEY,
) {
    /** 没配置 Key 时界面应禁用按钮，而不是请求出去再失败。 */
    val configured: Boolean get() = apiKey.isNotBlank()

    /**
     * 纠偏一段轨迹。
     *
     * 一次最多 500 个点；调用方负责切块（见 [TrackCorrection.chunks]）。
     */
    suspend fun correct(points: List<TrackCorrection.RequestPoint>): Result<CorrectedRoute> =
        withContext(Dispatchers.IO) {
            if (!configured) {
                return@withContext Result.failure(
                    IllegalStateException("没有配置高德 Web 服务 Key"),
                )
            }
            if (points.size < 2) {
                return@withContext Result.failure(
                    IllegalArgumentException("至少需要 2 个点"),
                )
            }

            val body =
                json.encodeToString(
                    points.map { point ->
                        RequestPointDto(
                            x = point.longitude,
                            y = point.latitude,
                            ag = point.bearingDegrees,
                            sp = point.speedKmh,
                            tm = point.timeSeconds,
                        )
                    },
                )

            runCatching {
                val connection =
                    (URL("$url?key=$apiKey").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }

                try {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val text =
                        (if (code in 200..299) connection.inputStream else connection.errorStream)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()

                    if (code !in 200..299) {
                        error("纠偏服务返回 HTTP $code")
                    }
                    parse(text)
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun parse(text: String): CorrectedRoute {
        val response = json.decodeFromString<ResponseDto>(text)
        val points = response.data?.points.orEmpty()
        if (points.isEmpty()) {
            // 30001 是「抓路失败」：点太少或太稀疏。这是 60 秒采样最常见的结果，
            // 界面要提示「这段太稀疏」，而不是把 ENGINE_RESPONSE_DATA_ERROR 丢给用户。
            error(
                when (response.errcode) {
                    ERRCODE_ROAD_MATCH_FAILED -> "这段轨迹点太稀疏，没能匹配到路网"
                    else -> response.errmsg.ifBlank { "纠偏失败（errcode=${response.errcode}）" }
                },
            )
        }
        return CorrectedRoute(
            segments = TrackCorrection.splitAtLongJumps(points.map { GeoPoint(it.y, it.x) }),
            distanceMeters = response.data?.distance ?: 0.0,
        )
    }

    // ---- 报文 ----

    @Serializable
    private data class RequestPointDto(
        val x: Double,
        val y: Double,
        val ag: Int,
        val sp: Double,
        val tm: Long,
    )

    @Serializable
    private data class ResponseDto(
        val errcode: Int = 0,
        val errmsg: String = "",
        val data: ResponseDataDto? = null,
    )

    @Serializable
    private data class ResponseDataDto(
        val distance: Double = 0.0,
        val points: List<ResponsePointDto> = emptyList(),
    )

    @Serializable
    private data class ResponsePointDto(
        val x: Double = 0.0,
        val y: Double = 0.0,
    )

    private companion object {
        const val ENDPOINT = "https://restapi.amap.com/v4/grasproad/driving"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000

        /** 抓路失败。官方文档：传入点数较少或较稀疏时会返回。 */
        const val ERRCODE_ROAD_MATCH_FAILED = 30001

        val json = Json { ignoreUnknownKeys = true }
    }
}
