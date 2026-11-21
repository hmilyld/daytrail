package com.hmilyld.daytrail.data.import

import com.hmilyld.daytrail.core.DayKeys
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * GPX 导入，对应 [com.hmilyld.daytrail.data.export.TrackExport.toGpx]。
 *
 * **流式扫描而不是 XML 解析器**：`XmlPullParser` 在 Android 上有、在纯 JVM
 * 单测里没有（kxml2 不随 JDK 提供）。为了既能流式读大文件、又能用普通 JUnit
 * 覆盖，这里只做一件很小的事——顺序扫描 `<trkpt>` 标签，取 `lat`/`lon`
 * 属性与紧随其后的 `<time>` 文本，其余标签一律跳过。
 *
 * 不处理：`<wpt>`（停留是派生物，导入后由 recomputeDay 重新识别）、
 * `<extensions>`、命名空间前缀。
 *
 * 天数归属由时间戳按**本地时区**推出——GPX 没有 `date` 属性，
 * 这是与 GeoJSON 的唯一语义差异。
 */
internal object GpxImport {
    private val ATTRIBUTES = Regex("""([A-Za-z_:][\w:.\-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun parse(
        raw: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ParsedFile {
        val days = LinkedHashMap<String, MutableList<RawPoint>>()
        var dropped = 0
        var position = 0

        while (true) {
            val open = raw.indexOf('<', position)
            if (open < 0) break
            val close = raw.indexOf('>', open)
            if (close < 0) break

            val tag = raw.substring(open + 1, close)
            val selfClosing = tag.trimEnd().endsWith("/")
            val name = tag.trimEnd('/').trim().substringBefore(' ')
            position = close + 1

            if (name != "trkpt") continue

            val attributes =
                ATTRIBUTES.findAll(tag).associate { match ->
                    val key = match.groupValues[1]
                    val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
                    key to value
                }
            val latitude = attributes["lat"]?.toDoubleOrNull()
            val longitude = attributes["lon"]?.toDoubleOrNull()

            var timeMs: Long? = null
            if (!selfClosing) {
                val end = raw.indexOf("</trkpt>", position)
                if (end < 0) break
                timeMs = readTime(raw, position, end)
                position = end + "</trkpt>".length
            }

            if (latitude == null || longitude == null || timeMs == null) {
                // 没有时间就无法归到某一天，也没有采样间隔信息——直接丢弃并计数
                dropped++
                continue
            }

            val dateKey = DayKeys.of(timeMs, zone).value
            days.getOrPut(dateKey) { mutableListOf() } +=
                RawPoint(
                    timestampMs = timeMs,
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = null,
                )
        }

        return ParsedFile(
            format = ImportFormat.GPX,
            days = days.entries.map { ParsedDay(dateKey = it.key, points = it.value) },
            droppedPoints = dropped,
        )
    }

    /** 在 `<trkpt>` 与 `</trkpt>` 之间找 `<time>` 的文本。 */
    private fun readTime(
        raw: String,
        from: Int,
        to: Int,
    ): Long? {
        val open = raw.indexOf("<time>", from)
        if (open < 0 || open > to) return null
        val close = raw.indexOf("</time>", open)
        if (close < 0 || close > to) return null
        val text = raw.substring(open + "<time>".length, close).trim()
        return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
    }
}
