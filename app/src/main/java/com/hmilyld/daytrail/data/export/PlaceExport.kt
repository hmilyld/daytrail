package com.hmilyld.daytrail.data.export

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.GeoPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 地点导出所需的最小数据，不依赖 Room，便于单测。坐标是 GCJ-02。 */
data class ExportPlace(
    val label: String?,
    val point: GeoPoint,
    val radiusMeters: Double,
    val visitDays: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val suppressed: Boolean = false,
)

/**
 * 文案由调用方从 strings.xml 注入，纯逻辑里不写中文。
 *
 * - [coordinateLine]：模板，例如 "纬度 %1\$s  经度 %2\$s（WGS-84）"
 * - [radiusAndVisits]：模板，例如 "半径 %1\$d 米 · 去过 %2\$d 天"
 */
data class PlaceExportTexts(
    val unnamed: String,
    val coordinateLine: String,
    val radiusAndVisits: String,
)

/**
 * 地点分享与导出的序列化。
 *
 * **坐标系**：库内统一存 GCJ-02，但 GPX / GeoJSON / `geo:` 链接的国际标准是 WGS-84。
 * 所有对外坐标一律经过 [CoordinateTransform.gcj02ToWgs84]，否则整体偏移约 500 米。
 * 这一点和 [TrackExport] 是同一条铁律。
 *
 * 全部函数都是纯 JVM 逻辑，不依赖 Android，可以直接单测。
 */
object PlaceExport {
    /** 地点导出的 schema 版本。字段名变化时才递增。 */
    const val SCHEMA_VERSION = 1

    private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)

    /**
     * 单个地点的分享文本。
     *
     * 末行是可选的 `geo:` 链接，使用 WGS-84；不拼高德的 `androidamap://viewMap`，
     * 那条 URI 要 GCJ-02，混用会偏约 500 米。
     */
    fun shareText(
        place: ExportPlace,
        texts: PlaceExportTexts,
        includeGeoLink: Boolean = true,
    ): String {
        val wgs = toWgs84(place.point)
        val name = displayName(place, texts)
        val coordinate =
            String.format(Locale.US, texts.coordinateLine, num(wgs.latitude), num(wgs.longitude))
        val result =
            buildString {
                append(name).append('\n')
                append(coordinate).append('\n')
                append(radiusAndVisits(place, texts))
            }
        if (!includeGeoLink) return result

        val lat = num(wgs.latitude)
        val lon = num(wgs.longitude)
        return result + "\n" + "geo:$lat,$lon?q=$lat,$lon(${encodeUriPart(name)})"
    }

    /** GPX 1.1：每个地点一个 waypoint。 */
    fun toGpx(
        places: List<ExportPlace>,
        texts: PlaceExportTexts,
    ): String {
        val body = StringBuilder()
        active(places).forEach { place ->
            val wgs = toWgs84(place.point)
            body.append("  <wpt lat=\"${num(wgs.latitude)}\" lon=\"${num(wgs.longitude)}\">\n")
            body.append("    <name>${escapeXml(displayName(place, texts))}</name>\n")
            body.append("    <desc>${escapeXml(radiusAndVisits(place, texts))}</desc>\n")
            body.append("  </wpt>\n")
        }

        // 显式拼接而不是 trimIndent：插值会让缩进计算错位，部分 GPX 解析器很挑。
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"DayTrail\" ")
            append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append(body)
            append("</gpx>\n")
        }
    }

    /** GeoJSON：FeatureCollection，每个地点一个 Point Feature。 */
    fun toGeoJson(
        places: List<ExportPlace>,
        texts: PlaceExportTexts,
        generator: String = "",
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val features =
            active(places).joinToString(",") { place ->
                val wgs = toWgs84(place.point)
                val name = escapeJson(displayName(place, texts))
                """{"type":"Feature","properties":{"name":"$name",""" +
                    """"radiusMeters":${place.radiusMeters.roundToInt()},""" +
                    """"visitDays":${place.visitDays},""" +
                    """"firstSeen":"${iso(place.firstSeenMs, zone)}",""" +
                    """"lastSeen":"${iso(place.lastSeenMs, zone)}"},""" +
                    """"geometry":{"type":"Point","coordinates":""" +
                    """[${num(wgs.longitude)},${num(wgs.latitude)}]}}"""
            }
        val generatorText = generator.ifBlank { "DayTrail" }
        return """{"type":"FeatureCollection","daytrail.schemaVersion":$SCHEMA_VERSION,""" +
            """"generator":"${escapeJson(generatorText)}","name":"DayTrail",""" +
            """"crs":{"type":"name","properties":""" +
            """{"name":"urn:ogc:def:crs:OGC:1.3:CRS84"}},""" +
            """"features":[$features]}"""
    }

    /** 导出建议文件名：`daytrail-places-YYYYMMDD.<ext>`。 */
    fun suggestedFileName(
        format: ExportFormat,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val date = FILE_DATE.format(Instant.now().atZone(zone))
        return "daytrail-places-$date.${format.extension}"
    }

    /** 被删除的地点不导出；行还在库里参与匹配，但不应出现在用户分享的文件里。 */
    private fun active(places: List<ExportPlace>): List<ExportPlace> =
        places.filter { !it.suppressed }

    private fun displayName(
        place: ExportPlace,
        texts: PlaceExportTexts,
    ): String = place.label?.takeIf { it.isNotBlank() } ?: texts.unnamed

    private fun radiusAndVisits(
        place: ExportPlace,
        texts: PlaceExportTexts,
    ): String =
        String.format(
            Locale.US,
            texts.radiusAndVisits,
            place.radiusMeters.roundToInt(),
            place.visitDays,
        )

    /** GCJ-02 → WGS-84。导出格式的国际标准是 WGS-84。 */
    private fun toWgs84(point: GeoPoint): GeoPoint = CoordinateTransform.gcj02ToWgs84(point)

    /** 6 位小数，约 0.1 米精度，足够且与分享文案一致。 */
    private fun num(value: Double): String = String.format(Locale.US, "%.6f", value)

    /** 带偏移的本地时间，与 [TrackExport] 的口径一致。 */
    private fun iso(
        timestampMs: Long,
        zone: ZoneId,
    ): String = ISO_OFFSET.format(Instant.ofEpochMilli(timestampMs).atZone(zone))

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    /** `geo:` 链接里的地点名必须做百分号编码，中文不能直接塞进 URI。 */
    private fun encodeUriPart(value: String): String =
        buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val code = byte.toInt() and 0xFF
                val unreserved =
                    code in 'A'.code..'Z'.code ||
                        code in 'a'.code..'z'.code ||
                        code in '0'.code..'9'.code ||
                        code in setOf('-'.code, '_'.code, '.'.code, '~'.code)
                if (unreserved) {
                    append(code.toChar())
                } else {
                    append('%').append(String.format(Locale.US, "%02X", code))
                }
            }
        }
}
