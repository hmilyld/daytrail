package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint

/**
 * 地图上的停留标记：**每天每个地点一个点**，不是每段停留一个点。
 *
 * 为什么按地点聚合：同一天里「出门前在家」「回来后还在家」是两段停留
 * （时间轴必须分开，否则时长和起止时间都是错的），但它们是**同一个地点**。
 * 之前每段停留各画一个标记，同一个家里就出现两个点，看起来像「聚合失败」。
 *
 * 坐标取**地点锚点**而不是当天质心：锚点是稳定的，家的点不会每天挪几米，
 * 合并过的地点（半径被撑大）也能落回同一个像素。
 *
 * 更短的停留照旧不画（[TrackDisplay.MIN_STAY_MARKER_MS]），否则一天里路过
 * 便利店、等红灯都会留下标记。
 */
object StayMarkers {
    /**
     * 起点/终点与停留重合到这个距离内时，不再单独绘制。
     *
     * 否则「在家开始、回家结束」的一天，同一个家里会叠着起点、终点和停留三个点。
     */
    const val ENDPOINT_MERGE_METERS = 60.0

    /** 一段停留的展示信息。 */
    data class Stay(
        val placeId: Long?,
        val point: GeoPoint,
        val durationMs: Long,
        /** 地点锚点。没有归属地点时为 null，退回当天质心。 */
        val anchor: GeoPoint? = null,
    )

    /**
     * 一天的标记计划。
     *
     * 分成两份而不是一份：标记画在**地点锚点**上，而合并过的大地点锚点可能离
     * 当次停留的质心几百米。判断「起点/终点是不是已经在这个停留里」必须看
     * 当次的 [centroids]，只看锚点会让起终点在几百米外重新冒出来。
     */
    data class Plan(
        /** 要画的停留标记，每天每个地点一个。 */
        val markers: List<GeoPoint>,
        /** 有标记的停留各自的质心，按时间顺序。只用于判断起终点是否已被代表。 */
        val centroids: List<GeoPoint>,
    )

    /** 聚合后的标记坐标，按当天第一次出现的顺序。 */
    fun plan(
        stays: List<Stay>,
        minDurationMs: Long = TrackDisplay.MIN_STAY_MARKER_MS,
    ): Plan {
        val markers = LinkedHashMap<Any, GeoPoint>()
        val centroids = mutableListOf<GeoPoint>()
        stays.forEachIndexed { index, stay ->
            if (stay.durationMs < minDurationMs) return@forEachIndexed
            centroids += stay.point
            // 没有地点归属的停留（理论上只出现在首次重算之前）各自保留一个点
            val key: Any = stay.placeId ?: "stay-$index"
            markers.putIfAbsent(key, stay.anchor ?: stay.point)
        }
        return Plan(markers = markers.values.toList(), centroids = centroids)
    }
}
