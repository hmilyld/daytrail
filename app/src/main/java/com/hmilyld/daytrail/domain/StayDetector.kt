package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/** 识别出的停留段。坐标为窗口内质心。 */
data class StaySegment(
    val startMs: Long,
    val endMs: Long,
    val point: GeoPoint,
    val radiusMeters: Double,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * 停留识别（纯逻辑，可单测）。
 *
 * 算法：滑动窗口。以窗口起点为中心，若后续点在 [radiusMeters] 内且持续
 * 时间达到 [minDurationMs]，则记一段停留，取窗口内**圈内点**的质心作为停留点。
 *
 * 为什么不用「连续两点距离小于阈值」的简单判定：
 * 等红灯时车不动但只是短暂停顿，简单判定会产出一堆几秒的假停留；
 * 反过来走路很慢时两点间距很小，又会被误判成停留。用时间窗口能同时解决两者。
 *
 * 阈值取产品决策：80m 半径、8 分钟。80m 覆盖 GPS 在建筑间的漂移，
 * 8 分钟能把「等红灯」「短暂停车」排除，又不会漏掉「开会」「吃饭」。
 *
 * **退出迟滞**（[maxConsecutiveOutside]）：窗口不因单个出圈点而结束。
 * 实测数据里，人在家或公司时会出现 50–80m 的慢漂移（0.1–0.6 m/s，
 * 与真实走动无法区分），旧实现遇到第一个出圈点就收尾，一个漂移点
 * 就能把 5 小时的停留切成三段。现在要求连续多个点都在圈外才判定离开。
 *
 * 注意：迟滞只影响「判定」，停留的 [StaySegment.endMs] 仍是**最后一个圈内点**，
 * 不是确认离开的那个点——否则时间轴会凭空多出一段。
 */
class StayDetector(
    private val radiusMeters: Double = 80.0,
    private val minDurationMs: Long = 8 * 60_000L,
    /**
     * 连续多少个点出圈才判定离开。1 = 旧行为（第一个出圈点就结束）。
     *
     * 取 2：能跨过单个漂移点，又不会把「出门后连续两个点都在外面」误判成停留。
     * 再大就会吞掉真实的短途（静止档 10 分钟一个点，3 个点就是半小时）。
     */
    private val maxConsecutiveOutside: Int = 2,
) {
    /** 一个带时间戳的点。 */
    data class TimedPoint(
        val timestampMs: Long,
        val point: GeoPoint,
    )

    /**
     * 从按时间升序的点序列里识别停留段。
     *
     * 相邻停留段不重叠：一段确认后，窗口从该段最后一个圈内点之后继续。
     */
    fun detect(points: List<TimedPoint>): List<StaySegment> {
        if (points.size < 2) return emptyList()

        val outsideLimit = maxConsecutiveOutside.coerceAtLeast(1)
        val result = mutableListOf<StaySegment>()
        var anchorIndex = 0

        while (anchorIndex < points.size - 1) {
            val anchor = points[anchorIndex]
            var lastInsideIndex = anchorIndex
            var latSum = 0.0
            var lonSum = 0.0
            var maxRadius = 0.0
            var count = 0
            var consecutiveOutside = 0

            var cursor = anchorIndex
            while (cursor < points.size) {
                val candidate = points[cursor]
                val distance = distanceMeters(anchor.point, candidate.point)
                if (distance > radiusMeters) {
                    // 先记一笔，连续出圈的个数到了才真的收尾。
                    // 单个漂移点被跳过，既不计入质心，也不算作离开。
                    consecutiveOutside++
                    if (consecutiveOutside >= outsideLimit) break
                    cursor++
                    continue
                }

                consecutiveOutside = 0
                latSum += candidate.point.latitude
                lonSum += candidate.point.longitude
                maxRadius = maxOf(maxRadius, distance)
                count++
                lastInsideIndex = cursor
                cursor++
            }

            val duration = points[lastInsideIndex].timestampMs - anchor.timestampMs
            if (count >= 2 && duration >= minDurationMs) {
                val centroid =
                    GeoPoint(
                        latitude = latSum / count,
                        longitude = lonSum / count,
                    )
                result +=
                    StaySegment(
                        startMs = anchor.timestampMs,
                        endMs = points[lastInsideIndex].timestampMs,
                        point = centroid,
                        radiusMeters = maxRadius,
                    )
                anchorIndex = lastInsideIndex + 1
            } else {
                anchorIndex++
            }
        }

        return result
    }
}
