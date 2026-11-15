package com.hmilyld.daytrail.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一天的轨迹汇总。
 *
 * 历史页与月历只读这张表，不扫原始点。
 * 每次批量写入原始点后由 [DayAggregator] 重算当天这一行。
 */
@Entity(tableName = "track_day")
data class TrackDayEntity(
    @PrimaryKey
    @ColumnInfo(name = "date_key")
    val dateKey: String,
    @ColumnInfo(name = "distance_m")
    val distanceMeters: Double = 0.0,
    @ColumnInfo(name = "moving_ms")
    val movingMillis: Long = 0L,
    @ColumnInfo(name = "stopped_ms")
    val stoppedMillis: Long = 0L,
    @ColumnInfo(name = "point_count")
    val pointCount: Int = 0,
    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,
    /** 归属的行程，未归入行程时为 null。M3 填充。 */
    @ColumnInfo(name = "trip_id")
    val tripId: Long? = null,
)

/**
 * 原始采样点。只追加，不修改。
 *
 * 索引 `(day_key, ts)` 服务于两种查询：按天取轨迹、按天取时间范围。
 * `sync_state` 与 `device_id` 是云端接缝，二期接入同步时无需迁移表结构。
 */
@Entity(
    tableName = "track_point",
    indices = [Index(value = ["day_key", "ts"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "ts")
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_m")
    val accuracyMeters: Float,
    @ColumnInfo(name = "speed_mps")
    val speedMps: Float?,
    val bearing: Float?,
    @ColumnInfo(name = "day_key")
    val dayKey: String,
    @ColumnInfo(name = "is_mock")
    val isMock: Boolean = false,
    /** 0 = 未同步。二期用。 */
    @ColumnInfo(name = "sync_state")
    val syncState: Int = 0,
    /** 设备标识。二期用，现在留空。 */
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
)

/**
 * 停留段。由 [com.hmilyld.daytrail.domain.StayDetector] 识别。
 *
 * `place_id` 指向聚类出的地点；`label_source` 区分名称来源，
 * 决定界面是否显示「未命名」。
 */
@Entity(
    tableName = "stay_segment",
    indices = [Index(value = ["day_key"])],
)
data class StaySegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "day_key")
    val dayKey: String,
    @ColumnInfo(name = "start_ts")
    val startMs: Long,
    @ColumnInfo(name = "end_ts")
    val endMs: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "radius_m")
    val radiusMeters: Double,
    @ColumnInfo(name = "place_id")
    val placeId: Long? = null,
    @ColumnInfo(name = "label_source")
    val labelSource: String = LabelSource.UNNAMED,
)

/** 停留点名称的来源。 */
object LabelSource {
    /** 用户手动命名。 */
    const val MANUAL = "manual"

    /** 同一位置复用上次的名称。 */
    const val REUSED = "reused"

    /** 还没有名称。 */
    const val UNNAMED = "unnamed"
}

/**
 * 数据缺失区间。
 *
 * 高铁过隧道、手机关机、权限被回收都会产生。时间轴必须显式画出来，
 * 否则用户会以为轨迹画错了。
 */
@Entity(
    tableName = "day_gap",
    indices = [Index(value = ["day_key"])],
)
data class DayGapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "day_key")
    val dayKey: String,
    @ColumnInfo(name = "start_ts")
    val startMs: Long,
    @ColumnInfo(name = "end_ts")
    val endMs: Long,
    val reason: String,
)
