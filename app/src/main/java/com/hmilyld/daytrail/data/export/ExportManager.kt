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
    /** 收集要导出的数据。不指定日期时导出全部。 */
    private suspend fun collect(dayKey: DayKey? = null): List<ExportDay> {
        val keys =
            if (dayKey != null) {
                listOf(dayKey)
            } else {
                repository.observeDaysWithDataFirst()
            }

        return keys
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
                            ExportStay(
                                point = GeoPoint(stay.latitude, stay.longitude),
                                startMs = stay.startMs,
                                endMs = stay.endMs,
                                label = null,
                            )
                        },
                )
            }.sortedBy { it.dateKey }
    }

    /** 导出建议的文件名。 */
    suspend fun suggestedName(
        format: ExportFormat,
        dayKey: DayKey? = null,
    ): String {
        val days = collect(dayKey)
        return TrackExport.suggestedFileName(days, format.extension)
    }

    /**
     * 写入用户选定的文件。
     *
     * 先收集数据再写：写文件是 IO，放到 IO 调度器；
     * 收集本身是数据库查询，也是 IO。
     */
    suspend fun export(
        uri: Uri,
        format: ExportFormat,
        dayKey: DayKey? = null,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                val days = collect(dayKey)
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
