package com.hmilyld.daytrail.data.import

import com.hmilyld.daytrail.data.repo.DayImportBatch

/** 导入文件格式。与导出保持一致，不额外扩展 KML/CSV。 */
enum class ImportFormat {
    GEOJSON,
    GPX,
}

/**
 * 冲突策略。
 *
 * 默认 [SKIP]：最安全。用户导入另一台设备的数据时，
 * 覆盖会连带删掉本机已有的同一天记录。
 */
enum class ConflictStrategy {
    /** 该天已有数据就整天空过。 */
    SKIP,

    /** 只补时间戳不重复的点。 */
    MERGE,

    /** 删除该天旧数据后写入。破坏性，需要二次确认。 */
    REPLACE,
}

/**
 * 文件里的一个点。坐标**仍是文件原始的 WGS-84**，
 * 转换发生在 [TrackImporter] 的预览阶段。
 */
internal data class RawPoint(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

internal data class ParsedDay(
    val dateKey: String,
    val points: List<RawPoint>,
)

internal data class ParsedFile(
    val format: ImportFormat,
    val days: List<ParsedDay>,
    val droppedPoints: Int,
)

/**
 * 预览结果：坐标已经转成 GCJ-02、可以直接写库。
 *
 * 全部信息在预览阶段算出，用户点「开始导入」之后不再做解析，
 * 避免「确认了才发现文件后半段是坏的」。
 */
data class ImportPreview(
    val fileName: String,
    val format: ImportFormat,
    val batches: List<DayImportBatch>,
    val totalPoints: Int,
    val firstDate: String,
    val lastDate: String,
    val newDays: List<String>,
    val existingDays: List<String>,
    val droppedPoints: Int,
)

sealed interface PreviewResult {
    data class Ready(
        val preview: ImportPreview,
    ) : PreviewResult

    data class Failure(
        val message: String,
    ) : PreviewResult
}

/** 导入结果，用于结果提示。 */
data class ImportOutcome(
    val importedDays: Int,
    val importedPoints: Int,
    val skippedDays: Int,
    val droppedPoints: Int,
)

/** 解析或校验失败。消息直接展示给用户，所以写成人话。 */
internal class ImportException(
    override val message: String,
) : Exception(message)
