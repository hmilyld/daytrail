package com.hmilyld.daytrail.data.import

import com.hmilyld.daytrail.core.DayKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime

/**
 * GeoJSON 导入，对应 [com.hmilyld.daytrail.data.export.TrackExport.toGeoJson]。
 *
 * 只认两种 Feature：`kind=track`（LineString，带 times）与 `kind=stay`。
 * stay 是派生物，**不导入**——导入后由 `recomputeDay` 重新识别，
 * 否则会带进一个与现有地点体系无关的孤立坐标，反而制造重复地点。
 *
 * 坐标系：文件里是 WGS-84（导出时转过），转换在 [TrackImporter] 统一完成。
 */
internal object GeoJsonImport {
    /** 本项目能读的最高 schema。更高的版本直接拒绝，不猜。 */
    const val SUPPORTED_SCHEMA = 2

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): ParsedFile {
        val schema = JsonScan.intValueOf(raw, "schema")
        if (schema != null && schema > SUPPORTED_SCHEMA) {
            throw ImportException("这个文件来自更新的版本（schema=$schema），请先升级应用")
        }

        val features =
            JsonScan.elementsOfArray(raw, "features")
                ?: throw ImportException("这不是有效的 GeoJSON：找不到 features 数组")

        val days = LinkedHashMap<String, MutableList<RawPoint>>()
        var dropped = 0

        features.forEach { featureText ->
            val feature =
                runCatching { json.parseToJsonElement(featureText) as? JsonObject }
                    .getOrNull()
                    ?: throw ImportException("GeoJSON 里有一个 Feature 无法解析")

            val properties = feature["properties"] as? JsonObject ?: return@forEach
            if (properties.string("kind") != "track") return@forEach

            val dateKey =
                properties.string("date")?.takeIf { it.isNotBlank() }
                    ?: return@forEach

            val coordinates =
                (feature["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray
                    ?: return@forEach
            val times =
                properties["times"] as? JsonArray
                    ?: throw ImportException("$dateKey 缺少 times，无法确定采样时间")
            val accuracies = properties["accuracies"] as? JsonArray

            val bucket = days.getOrPut(dateKey) { mutableListOf() }
            coordinates.forEachIndexed { index, element ->
                val pair = element as? JsonArray
                if (pair == null || pair.size < 2) {
                    dropped++
                    return@forEachIndexed
                }
                val longitude = pair[0].doubleOrNull()
                val latitude = pair[1].doubleOrNull()
                val timeMs = times.getOrNull(index)?.text()?.let(::parseTime)
                if (longitude == null || latitude == null || timeMs == null) {
                    dropped++
                    return@forEachIndexed
                }
                bucket +=
                    RawPoint(
                        timestampMs = timeMs,
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = accuracies?.getOrNull(index)?.doubleOrNull()?.toFloat(),
                    )
            }
        }

        return ParsedFile(
            format = ImportFormat.GEOJSON,
            days =
                days.entries.map { entry ->
                    ParsedDay(dateKey = entry.key, points = entry.value)
                },
            droppedPoints = dropped,
        )
    }

    /**
     * 解析带偏移的 ISO-8601。导出写的是 `2026-11-18T08:12:01+08:00`；
     * 别人给的文件可能是 `Z`，[OffsetDateTime.parse] 两种都接受。
     */
    private fun parseTime(raw: String): Long? =
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun kotlinx.serialization.json.JsonElement.text(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun kotlinx.serialization.json.JsonElement.doubleOrNull(): Double? =
        (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    /** 供单测构造日期用：与 [DayKeys] 的本地时区规则保持一致。 */
    internal fun dayKeyOf(timestampMs: Long): String = DayKeys.of(timestampMs).value
}
