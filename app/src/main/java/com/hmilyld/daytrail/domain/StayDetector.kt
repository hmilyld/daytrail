package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/** 识别出的停留段。坐标为窗口内质心。 */
data class StaySegment(
    val startMs: Long,
    val endMs: Long,
    val point: GeoPoint,
    /**
     * 圈内点的最大偏移：重定心被采纳时是到最终质心，否则仍是到窗口首点。
     *
     * 目前只落库、不参与任何判定。正因为参照系不统一（0.3.1 引入重定心后
     * 才有了「到质心」这一支），拿它做阈值判断或跨版本比较之前先想清楚。
     */
    val radiusMeters: Double,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * 停留识别（纯逻辑，可单测）。
 *
 * 算法：滑动窗口。先以窗口起点为圆心长出一个候选段；确认后**再以候选段的质心
 * 重新长一遍**，最后取窗口内圈内点的质心作为停留点。
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
 * 就能把 5 小时的停留切成三段。
 *
 * **质心重定心**（[REFINE_PASSES]）：首点常常落在停留区域的边缘。
 * 实测办公室那次，首点离真实质心 55m，于是「离质心只有 36m」的一个点
 * 在「离首点」的判据下变成 83m，叠加前后一个 178m 的漂移点，刚好凑够
 * 连续两个出圈，上午那段 221 分钟的停留被切成 154 + 67 两段。
 * 以质心为准后它们并成一段 224 分钟。
 *
 * 注意别把当天 12:28 之后那段 101 分钟的停留算进来：中间 12:00–12:23
 * 人去了 1.5 km 外，那是**真实的往返**，新旧实现都会切成独立的停留。
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

    /** 一次生长的结果。 */
    private data class Window(
        val count: Int,
        val lastInside: Int,
        val latSum: Double,
        val lonSum: Double,
        val maxRadius: Double,
    ) {
        /** 圈内点的质心。计数为 0 时返回 0，调用方不会用到这个值。 */
        val centroid: GeoPoint
            get() =
                if (count == 0) {
                    GeoPoint(0.0, 0.0)
                } else {
                    GeoPoint(latSum / count, lonSum / count)
                }
    }

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
            val window = grow(points, anchorIndex, points[anchorIndex].point, outsideLimit)
            if (!confirmed(points, anchorIndex, window)) {
                anchorIndex++
                continue
            }

            val refined = refine(points, anchorIndex, window, outsideLimit)
            result +=
                StaySegment(
                    startMs = points[anchorIndex].timestampMs,
                    endMs = points[refined.lastInside].timestampMs,
                    point = refined.centroid,
                    radiusMeters = refined.maxRadius,
                )
            anchorIndex = refined.lastInside + 1
        }

        return result
    }

    /** 用候选段质心重新生长，只接受「更长」的结果。 */
    private fun refine(
        points: List<TimedPoint>,
        startIndex: Int,
        initial: Window,
        outsideLimit: Int,
    ): Window {
        var best = initial
        var center = initial.centroid

        repeat(REFINE_PASSES) {
            val next = grow(points, startIndex, center, outsideLimit)
            if (next.lastInside > best.lastInside) {
                best = next
                center = next.centroid
            }
        }

        return best
    }

    /** 从 [startIndex] 开始，以 [center] 为圆心生长窗口。 */
    private fun grow(
        points: List<TimedPoint>,
        startIndex: Int,
        center: GeoPoint,
        outsideLimit: Int,
    ): Window {
        var lastInside = startIndex
        var latSum = 0.0
        var lonSum = 0.0
        var maxRadius = 0.0
        var count = 0
        var consecutiveOutside = 0
        var cursor = startIndex

        while (cursor < points.size) {
            val distance = distanceMeters(center, points[cursor].point)
            if (distance > radiusMeters) {
                // 先记一笔，连续出圈的个数到了才真的收尾。
                // 单个漂移点被跳过，既不计入质心，也不算作离开。
                consecutiveOutside++
                if (consecutiveOutside >= outsideLimit) break
                cursor++
                continue
            }

            consecutiveOutside = 0
            latSum += points[cursor].point.latitude
            lonSum += points[cursor].point.longitude
            maxRadius = maxOf(maxRadius, distance)
            count++
            lastInside = cursor
            cursor++
        }

        return Window(count, lastInside, latSum, lonSum, maxRadius)
    }

    /** 一段停留是否成立：至少两个圈内点，且持续够久。 */
    private fun confirmed(
        points: List<TimedPoint>,
        startIndex: Int,
        window: Window,
    ): Boolean {
        if (window.count < 2) return false
        val duration = points[window.lastInside].timestampMs - points[startIndex].timestampMs
        return duration >= minDurationMs
    }

    private companion object {
        /** 重定心迭代次数。实测两遍就收敛，再多收益很小。 */
        const val REFINE_PASSES = 2
    }
}
