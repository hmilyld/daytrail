package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/**
 * 轨迹分段。
 *
 * 地图上的折线必须按「数据缺失」切开，否则会把隧道、关机、信号丢失
 * 期间的两端连成一条穿越建筑和河流的直线。
 *
 * 但切分条件不能只看距离：
 * - 省电档采样间隔是分钟级，正常驾驶时相邻点相距 2–5 公里很常见
 * - 若按「距离 > 2km 就切」，会把一条完整的行程切成十几截，
 *   地图上看起来就是断断续续的虚线
 *
 * 因此与 [GapDetector] 用同一套判据：**时间超过阈值 且 位移超过阈值** 才算缺失。
 * 时间足够长说明中间本该有采样点却没有，这才是真正的数据缺失。
 */
object TrackSegmenter {
    /** 相邻点时间差超过它才可能是缺失。导出、手动微调的接续判断共用。 */
    const val GAP_MIN_MS = 10 * 60_000L

    /** 同时位移也要超过它。省电档正常驾驶时相邻点相距 2–5 公里，只看距离会切碎。 */
    const val GAP_MIN_DISPLACEMENT_METERS = 2_000.0

    /**
     * 把带时间戳的点切成若干连续段。
     *
     * @param minGapMs 相邻点时间差超过该值才可能是缺失
     * @param minDisplacementMeters 同时位移也要超过该值
     */
    fun segment(
        samples: List<TrackPointSample>,
        minGapMs: Long = GAP_MIN_MS,
        minDisplacementMeters: Double = GAP_MIN_DISPLACEMENT_METERS,
    ): List<List<GeoPoint>> {
        if (samples.isEmpty()) return emptyList()

        val segments = mutableListOf<List<GeoPoint>>()
        var current = mutableListOf(samples.first().point)

        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val point = samples[index]

            val elapsed = point.timestampMs - previous.timestampMs
            val moved = distanceMeters(previous.point, point.point)
            val isGap = elapsed > minGapMs && moved > minDisplacementMeters

            if (isGap) {
                if (current.isNotEmpty()) segments += current
                current = mutableListOf(point.point)
            } else {
                current += point.point
            }
        }
        if (current.isNotEmpty()) segments += current

        return segments
    }

    /** 所有段的点，按原顺序拼接。用于计算覆盖范围等。 */
    fun flatten(segments: List<List<GeoPoint>>): List<GeoPoint> = segments.flatten()
}
