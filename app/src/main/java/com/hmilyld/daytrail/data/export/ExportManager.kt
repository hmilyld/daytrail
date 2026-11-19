package com.hmilyld.daytrail.data.export

import android.content.Context
import android.net.Uri
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 导出格式。 */
enum class ExportFormat(
    val extension: String,
    val mimeType: String,
) {
    GEOJSON("geojson", "application/geo+json"),
    GPX("gpx", "application/gpx+xml"),
}

/**
 * 导出范围。两个字段都为 null 表示全部。
 *
 * 用日期字符串而不是 DayKey，方便 UI 层直接拼装。
 */
data class ExportRange(
    val from: String? = null,
    val to: String? = null,
) {
    val isAll: Boolean get() = from == null && to == null
}

/** 导出结果。 */
sealed interface ExportResult {
    data class Success(
        val dayCount: Int,
        val pointCount: Int,
    ) : ExportResult

    /** 没有可导出的数据。 */
    data object Empty : ExportResult

    data class Failure(
        val message: String,
    ) : ExportResult
}

/**
 * 数据导出。
 *
 * 为什么用 SAF（`ACTION_CREATE_DOCUMENT`）而不是直接写外部存储：
 * Android 10+ 分区存储下，直接写 `Downloads` 需要额外权限且各 ROM 行为不一。
 * SAF 让用户自己选位置，系统负责授权，不需要任何存储权限。
 */
class ExportManager(
    private val context: Context,
    private val repository: TrackRepository,
) {
    /** 范围内有数据的日期，升序。 */
    private suspend fun dayKeys(range: ExportRange): List<DayKey> {
        val all = repository.observeDaysWithDataFirst()
        return when {
            range.from == null || range.to == null -> all
            else -> all.filter { it.value in range.from..range.to }
        }
    }

    /** 收集要导出的数据。默认导出全部。 */
    private suspend fun collect(range: ExportRange = ExportRange()): List<ExportDay> {
        val places = repository.observePlaces().first()
        return dayKeys(range)
            .mapNotNull { key ->
                val points = repository.pointsOfDay(key)
                if (points.isEmpty()) return@mapNotNull null

                val stays = repository.staysOfDay(key)
                ExportDay(
                    dateKey = key.value,
                    points =
                        points.map { entity ->
                            ExportPoint(
                                point = GeoPoint(entity.latitude, entity.longitude),
                                timestampMs = entity.timestampMs,
                                accuracyMeters = entity.accuracyMeters,
                            )
                        },
                    stays =
                        stays.map { stay ->
                            val place = places.firstOrNull { it.place.id == stay.placeId }
                            ExportStay(
                                point = GeoPoint(stay.latitude, stay.longitude),
                                startMs = stay.startMs,
                                endMs = stay.endMs,
                                // 命名过的地点要带上名字，否则导出后又只剩「停留」
                                label = place?.place?.label,
                            )
                        },
                )
            }.sortedBy { it.dateKey }
    }

    /** 导出建议的文件名。只查日期，不加载轨迹点。 */
    suspend fun suggestedName(
        format: ExportFormat,
        range: ExportRange = ExportRange(),
    ): String =
        TrackExport.suggestedFileNameFromDates(
            dates = dayKeys(range).map { it.value },
            extension = format.extension,
        )

    /**
     * 写入用户选定的文件。
     *
     * 先收集数据再写：写文件是 IO，放到 IO 调度器；
     * 收集本身是数据库查询，也是 IO。
     */
    suspend fun export(
        uri: Uri,
        format: ExportFormat,
        range: ExportRange = ExportRange(),
    ): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                val days = collect(range)
                if (days.isEmpty()) return@withContext ExportResult.Empty

                val content =
                    when (format) {
                        ExportFormat.GEOJSON -> TrackExport.toGeoJson(days)
                        ExportFormat.GPX -> TrackExport.toGpx(days)
                    }

                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: return@withContext ExportResult.Failure("无法打开目标文件")

                ExportResult.Success(
                    dayCount = days.size,
                    pointCount = days.sumOf { it.points.size },
                )
            } catch (error: Exception) {
                ExportResult.Failure(error.message ?: "写入失败")
            }
        }
}

/**
 * 一次性读取有数据的日期列表。
 *
 * 仓库层提供的是 Flow（用于界面订阅），导出只需要一次快照，
 * 这里用 `first()` 取首值即可。
 */
private suspend fun TrackRepository.observeDaysWithDataFirst(): List<DayKey> =
    observeDaysWithData()
        .first()
        .mapNotNull { entity -> DayKeys.parse(entity.dateKey) }
