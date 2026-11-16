package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/** 一个带时间戳的轨迹点，供日汇总与断点检测使用。 */
data class TrackPointSample(
    val timestampMs: Long,
    val point: GeoPoint,
    /**
     * 定位精度（米）。用于判断位移是否可信。
     *
     * 默认 0 表示「未提供」：视为精确点，不做噪声过滤，
     * 这样既有测试与调用方不需要改。
     */
    val accuracyMeters: Float = 0f,
)

/** 一天的统计结果。 */
data class DayStats(
    val distanceMeters: Double,
    val movingMillis: Long,
    val stoppedMillis: Long,
    val pointCount: Int,
    val startedAt: Long?,
    val endedAt: Long?,
)

/**
 * 日汇总（纯逻辑，可单测）。
 *
 * 里程用 haversine 逐点累加，不用高德 SDK 的算路距离：
 * 后者会联网，且给的是路网距离而不是实际轨迹距离。
 */
object DayAggregator {
    /** 相邻两点时间差超过该值，视为数据缺失，不计入移动时长。 */
    private const val GAP_THRESHOLD_MS = 10 * 60_000L

    /** 速度低于该值视为静止（约 1.8 km/h），用于拆分移动/静止时长。 */
    private const val STILL_SPEED_MPS = 0.5

    /**
     * 计算里程时的最小可信位移（米）。
     *
     * 即使两个点都标称 5 米精度，5 米以内的抖动也不该累加，
     * 否则静止一分钟就能凭空多出几十米。
     */
    private const val MIN_DISTANCE_ACCURACY_METERS = 15f

    fun aggregate(points: List<TrackPointSample>): DayStats {
        if (points.isEmpty()) {
            return DayStats(
                distanceMeters = 0.0,
                movingMillis = 0L,
                stoppedMillis = 0L,
                pointCount = 0,
                startedAt = null,
                endedAt = null,
            )
        }

        if (points.size == 1) {
            return DayStats(
                distanceMeters = 0.0,
                movingMillis = 0L,
                stoppedMillis = 0L,
                pointCount = 1,
                startedAt = points.first().timestampMs,
                endedAt = points.first().timestampMs,
            )
        }

        var distance = 0.0
        var movingMs = 0L
        var stoppedMs = 0L

        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val segmentDistance = distanceMeters(previous.point, current.point)
            val elapsed = current.timestampMs - previous.timestampMs

            // 只有位移明显超过定位误差时才算真实移动。
            // 用 2 倍误差作为阈值：静止时相邻两次抖动本身就能差出一个误差距离，
            // 单倍阈值挡不住，坐着不动也能凭空累积出几公里。
            val accuracy = maxOf(previous.accuracyMeters, current.accuracyMeters)
            val threshold =
                maxOf(MIN_DISTANCE_ACCURACY_METERS, 2f * accuracy).toDouble()

            if (segmentDistance > threshold) {
                distance += segmentDistance
            }

            // 时长只在合理间隔内统计：长间隔无法判断移动还是静止，计入任一边都会失真
            if (elapsed <= 0 || elapsed > GAP_THRESHOLD_MS) continue

            val speed = segmentDistance / (elapsed / 1000.0)
            if (speed >= STILL_SPEED_MPS) {
                movingMs += elapsed
            } else {
                stoppedMs += elapsed
            }
        }

        return DayStats(
            distanceMeters = distance,
            movingMillis = movingMs,
            stoppedMillis = stoppedMs,
            pointCount = points.size,
            startedAt = points.first().timestampMs,
            endedAt = points.last().timestampMs,
        )
    }
}
