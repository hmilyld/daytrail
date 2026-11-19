package com.hmilyld.daytrail.data.export

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.GeoPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 一个停留点，导出用。 */
data class ExportStay(
    val point: GeoPoint,
    val startMs: Long,
    val endMs: Long,
    val label: String?,
)

/** 一天的导出数据。 */
data class ExportDay(
    val dateKey: String,
    val points: List<ExportPoint>,
    val stays: List<ExportStay> = emptyList(),
)

/** 一个轨迹点，导出用。 */
data class ExportPoint(
    val point: GeoPoint,
    val timestampMs: Long,
    val accuracyMeters: Float,
)

/**
 * 轨迹序列化。
 *
 * **坐标系**：内部统一存 GCJ-02，但 GPX 与 GeoJSON 的国际标准是 WGS-84。
 * 导出时必须转回去，否则在 Google Earth、Garmin 等工具里会整体偏移 500 米。
 * 这一点很容易漏掉——文件能打开、轨迹形状也对，就是位置不对。
 *
 * **时间**：写成带偏移的本地时间（`2026-11-18T15:56:20+08:00`），而不是 UTC 的 `Z`。
 * 之前的写法让文件里出现「`date` 是本地日期、`times` 是 UTC」的混搭：
 * 本地凌晨 00:30 会写成前一天的 `16:30Z`，日期属性与时间对不上。
 * 带偏移的 ISO 8601 仍然是唯一的瞬时值，GPX 的 `xsd:dateTime` 也允许偏移。
 *
 * 两个格式都是纯字符串拼接，不依赖 Android，可以直接单测。
 */
object TrackExport {
    private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * GeoJSON FeatureCollection。
     *
     * 结构：每天一个 Feature（LineString），停留点各一个 Feature（Point）。
     * 断点不特殊处理——导入方看到一条直连线比丢失数据好，
     * 且 GeoJSON 本身没有「段」的概念。
     */
    fun toGeoJson(
        days: List<ExportDay>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val features = mutableListOf<String>()

        days.forEach { day ->
            if (day.points.size >= 2) {
                val coordinates =
                    day.points.joinToString(",") { exported ->
                        val wgs = toWgs84(exported.point)
                        "[${num(wgs.longitude)},${num(wgs.latitude)}]"
                    }
                // 附带每个点的时间戳：坐标本身无法反映采样是否连续，
                // 排查「轨迹为什么断了」时必须看时间。
                val times =
                    day.points.joinToString(",") { exported ->
                        "\"${iso(exported.timestampMs, zone)}\""
                    }
                features +=
                    """{"type":"Feature","properties":{"date":"${day.dateKey}",""" +
                    """"kind":"track","pointCount":${day.points.size},""" +
                    """"times":[$times]},""" +
                    """"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
            }

            day.stays.forEach { stay ->
                val wgs = toWgs84(stay.point)
                val label = escapeJson(stay.label ?: "停留")
                val minutes = (stay.endMs - stay.startMs) / 60_000
                features +=
                    """{"type":"Feature","properties":{"date":"${day.dateKey}",""" +
                    """"kind":"stay","label":"$label","start":"${iso(stay.startMs, zone)}",""" +
                    """"end":"${iso(stay.endMs, zone)}","minutes":$minutes},""" +
                    """"geometry":{"type":"Point","coordinates":""" +
                    """[${num(wgs.longitude)},${num(wgs.latitude)}]}}"""
            }
        }

        return """{"type":"FeatureCollection","name":"DayTrail",""" +
            """"crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:OGC:1.3:CRS84"}},""" +
            """"features":[${features.joinToString(",")}]}"""
    }

    /**
     * GPX 1.1。
     *
     * 每天一个 `<trk>`，`<trkseg>` 按断点切开：相邻点间隔超过 2 公里时不连成一段，
     * 否则导入方会画出穿越建筑、河流的直线。
     */
    fun toGpx(
        days: List<ExportDay>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val body = StringBuilder()

        days.forEach { day ->
            body.append("  <trk>\n")
            body.append("    <name>${escapeXml(day.dateKey)}</name>\n")
            body.append("    <desc>坐标已转换为 WGS-84</desc>\n")

            var previous: ExportPoint? = null
            var segmentOpen = false

            day.points.forEach { current ->
                val last = previous
                val needsNewSegment =
                    last == null || distanceMeters(last.point, current.point) > 2_000.0

                if (needsNewSegment) {
                    if (segmentOpen) body.append("    </trkseg>\n")
                    body.append("    <trkseg>\n")
                    segmentOpen = true
                }

                val wgs = toWgs84(current.point)
                body.append(
                    "      <trkpt lat=\"${num(wgs.latitude)}\" lon=\"${num(wgs.longitude)}\">",
                )
                body.append("<time>${iso(current.timestampMs, zone)}</time>")
                body.append("</trkpt>\n")
                previous = current
            }

            if (segmentOpen) body.append("    </trkseg>\n")
            body.append("  </trk>\n")

            day.stays.forEach { stay ->
                val wgs = toWgs84(stay.point)
                val label = escapeXml(stay.label ?: "停留")
                val minutes = (stay.endMs - stay.startMs) / 60_000
                body.append("  <wpt lat=\"${num(wgs.latitude)}\" lon=\"${num(wgs.longitude)}\">\n")
                body.append("    <name>$label</name>\n")
                body.append("    <desc>停留 $minutes 分钟</desc>\n")
                body.append("  </wpt>\n")
            }
        }

        // 显式拼接而不是 trimIndent：字符串插值会让 trimIndent 计算错缩进，
        // 导致输出带前导空格，部分 GPX 解析器会拒绝。
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"DayTrail\" ")
            append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append(body)
            append("</gpx>\n")
        }
    }

    /** GCJ-02 → WGS-84。导出格式的国际标准是 WGS-84。 */
    private fun toWgs84(point: GeoPoint): GeoPoint = CoordinateTransform.gcj02ToWgs84(point)

    /** 经纬度保留 7 位小数，约 1 厘米精度，足够且不冗长。 */
    private fun num(value: Double): String = String.format(Locale.US, "%.7f", value)

    /**
     * 带偏移的本地时间。仍然是唯一的瞬时值，但读文件的人不用自己加 8 小时。
     *
     * 用 `ISO_OFFSET_DATE_TIME` 而不是 `ISO_INSTANT`：后者只能输出 UTC 的 `Z`。
     */
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

    /** 仅供分段判断使用，避免引入 domain 层依赖。 */
    private fun distanceMeters(
        a: GeoPoint,
        b: GeoPoint,
    ): Double =
        com.hmilyld.daytrail.core
            .distanceMeters(a, b)

    /** 导出文件的默认名。带日期范围，便于区分多次导出。 */
    fun suggestedFileName(
        days: List<ExportDay>,
        extension: String,
    ): String {
        if (days.isEmpty()) return "daytrail.$extension"
        val first = days.first().dateKey
        val last = days.last().dateKey
        return if (first == last) {
            "daytrail-$first.$extension"
        } else {
            "daytrail-$first-$last.$extension"
        }
    }

    /** 导出时间用的时区。导出内容与文件名统一用本地日期。 */
    fun localDateKey(
        timestampMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String =
        com.hmilyld.daytrail.core.DayKeys
            .of(timestampMs, zone)
            .value
}
