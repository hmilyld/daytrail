package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint

/**
 * 一天的采样点怎么变成地图上的线。
 *
 * 入库和画线用两套精度门槛：
 * - **入库宁多勿少**：只给「大致位置」的用户也必须有点，否则整天一片空白
 * - **画线宁缺勿滥**：2 公里误差的点画上去就是一条横穿城市的乱线
 *
 * 如果一整天都没有足够精确的点，退回用粗点画——至少能看出「今天去了哪几个区」，
 * 比什么都不显示有用。
 */
object TrackDisplay {
    /** 参与画线的精度上限（米）。超过这个值的点只入库、不上图。 */
    const val MAX_LINE_ACCURACY_METERS = 120f

    /** 地图上画停留标记的最短时长。 */
    const val MIN_STAY_MARKER_MS = 15 * 60_000L

    /**
     * 生成可直接交给地图的分段折线。
     *
     * 顺序很重要：先按精度筛，再平滑，最后才按「时间 + 位移」切段。
     * 反过来先切段的话，被筛掉的点会让段边界整体偏移。
     */
    fun segments(points: List<TrackPointSample>): List<List<GeoPoint>> {
        if (points.isEmpty()) return emptyList()

        val precise = points.filter { it.accuracyMeters <= MAX_LINE_ACCURACY_METERS }
        val usable = if (precise.size >= 2) precise else points

        val smoothed = TrackSmoother.smooth(usable)
        val placed =
            usable.mapIndexed { index, sample ->
                sample.copy(point = smoothed[index])
            }

        return TrackSegmenter.segment(placed)
    }
}
