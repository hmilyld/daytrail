package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import kotlin.math.cos

/**
 * 顶点引用：第几段的第几个点。
 *
 * 不用坐标做身份标识：拖动过程中坐标一直在变，而「第 3 段的第 7 个点」
 * 在整个编辑会话里是稳定的。
 */
data class TrackVertex(
    val segmentIndex: Int,
    val vertexIndex: Int,
)

/**
 * 「在线段上加一个点」的落点。
 *
 * @param insertIndex 新点在段内的下标，落在 [point] 之前
 * @param point 投影到折线上的坐标（不是点击位置），保证新点落在线上
 */
data class TrackInsertion(
    val segmentIndex: Int,
    val insertIndex: Int,
    val point: GeoPoint,
    val distanceMeters: Double,
)

/**
 * 手动微调的纯几何操作。
 *
 * 全部是不可变操作：传入旧折线，返回新折线，方便做撤销栈，也好写单测。
 * 不依赖 Android，也不依赖地图 SDK。
 */
object TrackEditOps {
    /**
     * 点击位置到折线的最大距离。
     *
     * 取 50 米：手指落在线上通常只差几个像素，而线本身有 12 像素宽；
     * 50 米足够容错，又不至于把地图另一头的点吸进来。
     */
    const val MATCH_RADIUS_METERS = 50.0

    /** 编辑基线：抽稀到和地图上看到的拐点一致，做到「所见即所拖」。 */
    fun baseline(segments: List<List<GeoPoint>>): List<List<GeoPoint>> =
        segments.map { TrackSimplifier.simplify(it) }.filter { it.size >= 2 }

    /** 移动一个顶点。越界时原样返回，避免 UI 传错下标把数据写坏。 */
    fun move(
        segments: List<List<GeoPoint>>,
        vertex: TrackVertex,
        to: GeoPoint,
    ): List<List<GeoPoint>> {
        val segment = segments.getOrNull(vertex.segmentIndex) ?: return segments
        if (vertex.vertexIndex !in segment.indices) return segments

        return segments.toMutableList().apply {
            this[vertex.segmentIndex] =
                segment.toMutableList().apply { this[vertex.vertexIndex] = to }
        }
    }

    /** 插入一个点。新点由 [TrackInsertion.point] 决定，不采用原始点击位置。 */
    fun insert(
        segments: List<List<GeoPoint>>,
        insertion: TrackInsertion,
    ): List<List<GeoPoint>> {
        val segment = segments.getOrNull(insertion.segmentIndex) ?: return segments
        if (insertion.insertIndex !in 1..segment.size) return segments

        return segments.toMutableList().apply {
            this[insertion.segmentIndex] =
                segment.toMutableList().apply { add(insertion.insertIndex, insertion.point) }
        }
    }

    /**
     * 删除一个点。
     *
     * 每段至少保留 2 个点：只剩 1 个点的「折线」画不出来，
     * 而整段消失会让用户以为数据丢了。UI 应该在这里返回原值时禁用删除按钮。
     */
    fun delete(
        segments: List<List<GeoPoint>>,
        vertex: TrackVertex,
    ): List<List<GeoPoint>> {
        val segment = segments.getOrNull(vertex.segmentIndex) ?: return segments
        if (segment.size <= MIN_SEGMENT_POINTS) return segments
        if (vertex.vertexIndex !in segment.indices) return segments

        return segments.toMutableList().apply {
            this[vertex.segmentIndex] =
                segment.toMutableList().apply { removeAt(vertex.vertexIndex) }
        }
    }

    /** 当前点是否可删。UI 用它决定按钮是否可用。 */
    fun canDelete(
        segments: List<List<GeoPoint>>,
        vertex: TrackVertex,
    ): Boolean = (segments.getOrNull(vertex.segmentIndex)?.size ?: 0) > MIN_SEGMENT_POINTS

    /** 是否还有任何可编辑的顶点。 */
    fun isEditable(segments: List<List<GeoPoint>>): Boolean = segments.any { it.size >= 2 }

    /**
     * 找离点击位置最近的折线段，并把点击位置投影到线上。
     *
     * 两条约束：
     * - 超过 [maxDistanceMeters] 视为「没点在线附近」，返回 null，由 UI 给提示；
     * - 投影参数钳在 `[0,1]`，所以点在线段延长线外侧时落到端点上，
     *   新点不会跑到线外去。
     */
    fun nearest(
        segments: List<List<GeoPoint>>,
        tap: GeoPoint,
        maxDistanceMeters: Double = MATCH_RADIUS_METERS,
    ): TrackInsertion? {
        var best: TrackInsertion? = null

        segments.forEachIndexed { segmentIndex, segment ->
            for (index in 1 until segment.size) {
                val start = segment[index - 1]
                val end = segment[index]
                val projected = project(tap, start, end)
                if (projected.distance > maxDistanceMeters) continue
                // 投影落到已经存在的顶点上时不插点：那会得到一个和邻居完全重合的
                // 顶点，两个手柄叠在一起，谁拖谁都不清楚。
                if (distanceMeters(projected.point, start) < MIN_VERTEX_OFFSET_METERS) continue
                if (distanceMeters(projected.point, end) < MIN_VERTEX_OFFSET_METERS) continue
                if (best == null || projected.distance < best.distanceMeters) {
                    best =
                        TrackInsertion(
                            segmentIndex = segmentIndex,
                            insertIndex = index,
                            point = projected.point,
                            distanceMeters = projected.distance,
                        )
                }
            }
        }

        return best
    }

    private class Projected(
        val point: GeoPoint,
        val distance: Double,
    )

    /**
     * 把 [tap] 投影到 [a]-[b] 线段上。
     *
     * 以 [a] 为原点做局部平面近似（和抽稀用的是同一套），几公里的局部范围内
     * 误差可忽略；这里只需要「离哪一段最近、落在哪个位置」。
     */
    private fun project(
        tap: GeoPoint,
        a: GeoPoint,
        b: GeoPoint,
    ): Projected {
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = 111_320.0 * cos(Math.toRadians(a.latitude))

        val px = (tap.longitude - a.longitude) * metersPerDegreeLon
        val py = (tap.latitude - a.latitude) * metersPerDegreeLat
        val ex = (b.longitude - a.longitude) * metersPerDegreeLon
        val ey = (b.latitude - a.latitude) * metersPerDegreeLat

        val lengthSquared = ex * ex + ey * ey
        val t =
            if (lengthSquared == 0.0) {
                0.0
            } else {
                ((px * ex + py * ey) / lengthSquared).coerceIn(0.0, 1.0)
            }

        val point =
            GeoPoint(
                latitude = a.latitude + (b.latitude - a.latitude) * t,
                longitude = a.longitude + (b.longitude - a.longitude) * t,
            )
        return Projected(point = point, distance = distanceMeters(tap, point))
    }

    /** 一段折线至少要两个点才画得出来。 */
    private const val MIN_SEGMENT_POINTS = 2

    /** 新插入的点与已有顶点的最小间距。低于它就等于插了一个重复点。 */
    private const val MIN_VERTEX_OFFSET_METERS = 2.0
}
