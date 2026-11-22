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

    /** 高德逆地理编码给的粗略地名。用户改名后会变成 [MANUAL]。 */
    const val AUTO = "auto"

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

/**
 * 地点。由多天的停留聚类而来，是「家」「公司」这类地方的稳定坐标。
 *
 * 为什么需要它：停留质心每天都会差十几米，直接用当天质心的话，
 * 地图上的「家」每天都在挪位置，命名也无法复用。
 *
 * `suppressed` 用于「删除」：行还留着参与匹配，只是不再显示。
 * 直接删行的话，下一次重算会立刻把同一个地点重新建出来。
 */
@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    /** 归属半径。落在圈内的停留都算同一个地点。 */
    @ColumnInfo(name = "radius_m")
    val radiusMeters: Double = 120.0,
    /** 用户起的名字。未命名为 null。 */
    val label: String? = null,
    @ColumnInfo(name = "label_source")
    val labelSource: String = LabelSource.UNNAMED,
    @ColumnInfo(name = "first_seen_ms")
    val firstSeenMs: Long,
    @ColumnInfo(name = "last_seen_ms")
    val lastSeenMs: Long,
    /** 用户删除过的地点。保留参与匹配，避免被重算重新创建。 */
    val suppressed: Boolean = false,
)

/**
 * 手动微调后的折线（派生数据，只影响显示）。
 *
 * 和 [RouteOverrideEntity] 同一条原则：原始点一个都不改，`track_day` 里的
 * 里程时长也不动。区别是纠偏结果只覆盖驾车时间窗，手动微调覆盖**整天**，
 * 并且带一个截断点，让录制中保存之后的新点还能接着画。
 */
@Entity(tableName = "track_edit")
data class TrackEditEntity(
    @PrimaryKey
    @ColumnInfo(name = "day_key")
    val dayKey: String,
    /** 折线坐标，JSON 数组 `[[[lon,lat],...],...]`，按断点分段。 */
    val segments: String,
    /** 进入编辑时最后一个原始点的时间戳。更大的点算「编辑之后新采到的」。 */
    @ColumnInfo(name = "cutoff_ts")
    val cutoffMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtMs: Long,
)

/**
 * 纠偏后的路线（派生数据，不参与统计）。
 *
 * 只存「用户点了补全路线」之后的结果。原始点一个都不改，
 * `track_day.distance` 也不动——纠偏返回的是路网里程，通常比原始折线长，
 * 混进统计会让历史口径不一致。
 */
@Entity(tableName = "route_override")
data class RouteOverrideEntity(
    @PrimaryKey
    @ColumnInfo(name = "day_key")
    val dayKey: String,
    @ColumnInfo(name = "start_ts")
    val startMs: Long,
    @ColumnInfo(name = "end_ts")
    val endMs: Long,
    /** 折线坐标，JSON 数组 `[[lon,lat],...]`。 */
    val points: String,
    @ColumnInfo(name = "distance_m")
    val distanceMeters: Double,
    @ColumnInfo(name = "created_at")
    val createdAtMs: Long,
)
