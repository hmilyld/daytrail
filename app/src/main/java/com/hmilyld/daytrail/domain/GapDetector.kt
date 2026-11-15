package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.distanceMeters

/** 数据缺失区间。 */
data class DayGap(
    val startMs: Long,
    val endMs: Long,
    val reason: String,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * 断点检测（纯逻辑，可单测）。
 *
 * 判据：相邻点时间差超过阈值，**且**距离超过位移阈值。
 *
 * 为什么两个条件都要：
 * - 只看时间差：手机放桌上静止一晚上，会产出一堆假断点
 * - 只看距离：坐高铁过隧道时相邻点可能只差 1 分钟但已跑出 3km，
 *   这种是真实缺失，必须标出来
 *
 * 产品决策的省电档下，静止采样间隔是 10 分钟，所以时间阈值取 10 分钟
 * 不会误报正常采样。
 */
class GapDetector(
    private val minGapMs: Long = 10 * 60_000L,
    private val minDisplacementMeters: Double = 2_000.0,
) {
    fun detect(points: List<TrackPointSample>): List<DayGap> {
        if (points.size < 2) return emptyList()

        val gaps = mutableListOf<DayGap>()
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val elapsed = current.timestampMs - previous.timestampMs
            if (elapsed <= minGapMs) continue

            val displacement = distanceMeters(previous.point, current.point)
            if (displacement > minDisplacementMeters) {
                gaps +=
                    DayGap(
                        startMs = previous.timestampMs,
                        endMs = current.timestampMs,
                        reason = REASON_NO_DATA,
                    )
            }
        }
        return gaps
    }

    companion object {
        /** 采集中断，可能是隧道、关机、权限被回收。 */
        const val REASON_NO_DATA = "no_data"
    }
}
