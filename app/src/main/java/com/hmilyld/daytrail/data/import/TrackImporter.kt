package com.hmilyld.daytrail.data.import

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.DayImportBatch
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * 导入编排：解析 → 校验 → **坐标系转换** → 去重 → 事务写入。
 *
 * 三段式设计：
 * 1. [preview] 只读不写，把所有可能失败的事情做完（解析、校验、与库内冲突）；
 * 2. 用户看到 preview 后选择冲突策略；
 * 3. [commit] 一个事务写完，失败整体回滚。
 *
 * **坐标系**：文件是 WGS-84（导出时由 GCJ-02 转过），入库前必须转回 GCJ-02。
 * 不转的话地图上整体偏 500 米，而文件本身和轨迹形状都完全正常，极难发现。
 */
class TrackImporter(
    private val repository: TrackRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun preview(
        fileName: String,
        raw: String,
    ): PreviewResult {
        if (raw.isBlank()) return PreviewResult.Failure("文件是空的")
        if (raw.length > MAX_CHARS) {
            return PreviewResult.Failure("文件超过 ${MAX_CHARS / 1024 / 1024} MB，请先按时间范围拆分")
        }

        val format =
            when (raw.firstOrNull { !it.isWhitespace() }) {
                '{' -> ImportFormat.GEOJSON
                '<' -> ImportFormat.GPX
                else -> return PreviewResult.Failure("无法识别文件格式，只支持导出的 GeoJSON 与 GPX")
            }

        val parsed =
            try {
                when (format) {
                    ImportFormat.GEOJSON -> GeoJsonImport.parse(raw)
                    ImportFormat.GPX -> GpxImport.parse(raw, zone)
                }
            } catch (error: ImportException) {
                return PreviewResult.Failure(error.message)
            } catch (error: Exception) {
                return PreviewResult.Failure("解析失败：${error.message ?: "文件可能已损坏"}")
            }

        if (parsed.days.sumOf { it.points.size } > MAX_POINTS) {
            return PreviewResult.Failure("文件包含超过 $MAX_POINTS 个点，请按时间范围拆分后再导入")
        }

        var dropped = parsed.droppedPoints
        val batches = mutableListOf<DayImportBatch>()

        parsed.days.forEach { day ->
            val dayKey = DayKeys.parse(day.dateKey)
            if (dayKey == null) {
                dropped += day.points.size
                return@forEach
            }
            val cleaned = clean(day.points)
            dropped += cleaned.dropped
            if (cleaned.points.size < MIN_DAY_POINTS) {
                dropped += cleaned.points.size
                return@forEach
            }
            batches +=
                DayImportBatch(
                    dayKey = dayKey,
                    points = cleaned.points.map { point -> point.toEntity(dayKey) },
                )
        }

        if (batches.isEmpty()) {
            return PreviewResult.Failure("文件里没有可导入的轨迹（需要每天至少 $MIN_DAY_POINTS 个带时间的点）")
        }

        val existing = batches.filter { hasData(it.dayKey) }.map { it.dayKey.value }
        val dates = batches.map { it.dayKey.value }.sorted()

        return PreviewResult.Ready(
            ImportPreview(
                fileName = fileName,
                format = format,
                batches = batches.sortedBy { it.dayKey.value },
                totalPoints = batches.sumOf { it.points.size },
                firstDate = dates.first(),
                lastDate = dates.last(),
                newDays = dates.filterNot { it in existing },
                existingDays = existing.sorted(),
                droppedPoints = dropped,
            ),
        )
    }

    suspend fun commit(
        preview: ImportPreview,
        strategy: ConflictStrategy,
    ): ImportOutcome {
        val existing = preview.existingDays.toSet()
        val replace = strategy == ConflictStrategy.REPLACE

        val targets =
            when (strategy) {
                ConflictStrategy.REPLACE -> preview.batches
                ConflictStrategy.SKIP -> preview.batches.filterNot { it.dayKey.value in existing }
                ConflictStrategy.MERGE ->
                    preview.batches.mapNotNull { batch ->
                        if (batch.dayKey.value !in existing) return@mapNotNull batch
                        val taken = timestampSet(batch.dayKey)
                        val merged = batch.points.filterNot { it.timestampMs in taken }
                        if (merged.isEmpty()) null else batch.copy(points = merged)
                    }
            }

        if (targets.isEmpty()) {
            return ImportOutcome(
                importedDays = 0,
                importedPoints = 0,
                skippedDays = preview.batches.size,
                droppedPoints = preview.droppedPoints,
            )
        }

        val inserted = repository.importDays(targets, replaceExisting = replace)
        return ImportOutcome(
            importedDays = targets.size,
            importedPoints = inserted,
            skippedDays = preview.batches.size - targets.size,
            droppedPoints = preview.droppedPoints,
        )
    }

    private suspend fun hasData(dayKey: DayKey): Boolean =
        repository.observeDay(dayKey).first() != null

    private suspend fun timestampSet(dayKey: DayKey): Set<Long> =
        repository.pointsOfDay(dayKey).mapTo(HashSet()) { it.timestampMs }

    /**
     * 校验 + 坐标转换 + 去重。
     *
     * 顺序有意为之：先按时间排序，再做「时间窗 + 距离」双重去重——
     * 同一轮采样的重复投递在导出文件里也可能出现（4.23 的同一现象）。
     *
     * 同时返回丢弃数量：预览页的「已丢弃 N 个无效点」要包含这里过滤掉的
     * 坐标非法点与重复点，否则用户会以为文件很干净。
     */
    private fun clean(points: List<RawPoint>): Cleaned {
        val limit = now() + 24L * 60 * 60 * 1000
        val valid = points.filter { it.isValid(limit) }
        val invalid = points.size - valid.size

        val result = mutableListOf<RawPoint>()
        var duplicates = 0
        valid.sortedBy { it.timestampMs }.forEach { point ->
            val last = result.lastOrNull()
            val duplicate =
                last != null &&
                    point.timestampMs - last.timestampMs <= DEDUPE_WINDOW_MS &&
                    distanceMeters(
                        GeoPoint(last.latitude, last.longitude),
                        GeoPoint(point.latitude, point.longitude),
                    ) < DEDUPE_METERS
            if (duplicate) duplicates++ else result += point
        }
        return Cleaned(result, invalid + duplicates)
    }

    private data class Cleaned(
        val points: List<RawPoint>,
        val dropped: Int,
    )

    private fun RawPoint.isValid(limitMs: Long): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0) &&
            timestampMs > 0L &&
            timestampMs <= limitMs

    /**
     * 落库实体。
     *
     * 这里做 WGS-84 → GCJ-02。精度缺失时按 50 米处理：导入的点本来就没有精度，
     * 记 0 会让 TrackDisplay 把它们当成完美点全部画出来，反而失真。
     */
    private fun RawPoint.toEntity(dayKey: DayKey): TrackPointEntity {
        val gcj = CoordinateTransform.wgs84ToGcj02(GeoPoint(latitude, longitude))
        return TrackPointEntity(
            timestampMs = timestampMs,
            latitude = gcj.latitude,
            longitude = gcj.longitude,
            accuracyMeters = (accuracyMeters ?: DEFAULT_ACCURACY).coerceIn(1f, MAX_ACCURACY),
            speedMps = null,
            bearing = null,
            dayKey = dayKey.value,
        )
    }

    private companion object {
        const val MAX_CHARS = 50 * 1024 * 1024
        const val MAX_POINTS = 200_000

        /** 一天少于 2 个点画不出线，也没有统计意义。 */
        const val MIN_DAY_POINTS = 2

        const val DEFAULT_ACCURACY = 50f

        /** 与 PointFilter 的入库门槛一致。 */
        const val MAX_ACCURACY = 5_000f

        const val DEDUPE_WINDOW_MS = 1_000L
        const val DEDUPE_METERS = 5.0
    }
}
