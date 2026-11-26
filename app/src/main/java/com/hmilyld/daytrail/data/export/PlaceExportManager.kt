package com.hmilyld.daytrail.data.export

import android.content.Context
import android.net.Uri
import com.hmilyld.daytrail.BuildConfig
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 地点导出结果。文案由界面层按字符串资源组装。 */
sealed interface PlaceExportResult {
    data class Success(
        val placeCount: Int,
    ) : PlaceExportResult

    data object Empty : PlaceExportResult

    data class Failure(
        val message: String,
    ) : PlaceExportResult
}

/**
 * 地点导出的 Android 胶水层：SAF 写文件、文案注入、数据收集。
 *
 * 纯序列化在 [PlaceExport]，这里只负责上下文相关的部分。
 */
class PlaceExportManager(
    private val context: Context,
    private val repository: TrackRepository,
) {
    private val texts: PlaceExportTexts
        get() =
            PlaceExportTexts(
                unnamed = context.getString(R.string.places_unnamed),
                coordinateLine = context.getString(R.string.place_share_coordinates),
                radiusAndVisits = context.getString(R.string.place_export_radius_visits),
            )

    /** 单个地点的分享文本。未命名地点用 strings.xml 里的占位名。 */
    fun shareText(summary: PlaceSummary): String =
        PlaceExport.shareText(summary.toExportPlace(), texts)

    /** 打开文件选择器之前就要定好名字，不能先加载全部地点。 */
    suspend fun suggestedName(format: ExportFormat): String = PlaceExport.suggestedFileName(format)

    suspend fun export(
        uri: Uri,
        format: ExportFormat,
    ): PlaceExportResult =
        withContext(Dispatchers.IO) {
            try {
                val places =
                    repository
                        .observePlaces()
                        .first()
                        .filter { !it.place.suppressed }
                        .map { it.toExportPlace() }
                if (places.isEmpty()) return@withContext PlaceExportResult.Empty

                val content =
                    when (format) {
                        ExportFormat.GEOJSON ->
                            PlaceExport.toGeoJson(
                                places = places,
                                texts = texts,
                                generator = "DayTrail ${BuildConfig.VERSION_NAME}",
                            )
                        ExportFormat.GPX -> PlaceExport.toGpx(places, texts)
                    }

                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
                    ?: return@withContext PlaceExportResult.Failure(
                        context.getString(R.string.place_export_open_failed),
                    )

                PlaceExportResult.Success(places.size)
            } catch (error: Exception) {
                PlaceExportResult.Failure(
                    error.message ?: context.getString(R.string.place_export_write_failed),
                )
            }
        }
}

private fun PlaceSummary.toExportPlace(): ExportPlace =
    ExportPlace(
        label = place.label,
        point = GeoPoint(place.latitude, place.longitude),
        radiusMeters = place.radiusMeters,
        visitDays = visitDays,
        firstSeenMs = place.firstSeenMs,
        lastSeenMs = place.lastSeenMs,
        suppressed = place.suppressed,
    )
