package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint

/**
 * 毛刺修复（纯逻辑，可单测）。
 *
 * 只处理一种点：**精度明显比前后两个点都差**的点。这种点一个就够把线
 * 甩出去几十上百米，而它自己的精度恰好说明它不可信。按 1/精度 加权，
 * 把它拉向三点的加权中心。
 *
 * 为什么不对所有点做平滑：精度相同的情况下，三点加权会把每个拐角
 * 抹掉三分之一——用户看到的是「路被抄了近道」，比毛刺更难解释。
 */
object TrackSmoother {
    /** 精度权重的下限，避免 accuracy = 0（未提供）时权重发散。 */
    private const val MIN_ACCURACY_METERS = 10f

    /**
     * @param points 按时间升序，精度为米
     * @return 与输入等长的坐标列表，首尾点不动
     */
    fun smooth(points: List<TrackPointSample>): List<GeoPoint> {
        if (points.size < 3) return points.map { it.point }

        val result = points.map { it.point }.toMutableList()
        for (index in 1 until points.lastIndex) {
            val previous = points[index - 1]
            val current = points[index]
            val next = points[index + 1]

            val wPrevious = weight(previous.accuracyMeters)
            val wCurrent = weight(current.accuracyMeters)
            val wNext = weight(next.accuracyMeters)

            // 只修「明显比前后都差」的点。精度不差就不动，
            // 否则会把真实拐角磨圆。
            if (wCurrent * 2.0 >= minOf(wPrevious, wNext)) continue

            val total = wPrevious + wCurrent + wNext
            result[index] =
                GeoPoint(
                    latitude =
                        (
                            previous.point.latitude * wPrevious +
                                current.point.latitude * wCurrent +
                                next.point.latitude * wNext
                        ) / total,
                    longitude =
                        (
                            previous.point.longitude * wPrevious +
                                current.point.longitude * wCurrent +
                                next.point.longitude * wNext
                        ) / total,
                )
        }
        return result
    }

    private fun weight(accuracyMeters: Float): Double =
        1.0 / maxOf(accuracyMeters, MIN_ACCURACY_METERS).toDouble()
}
