package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/**
 * 「这两个地点是不是同一个地方」的提示（纯逻辑，可单测）。
 *
 * 归并半径是 120 米，而同一个小区/园区里两次停车的位置差两三百米很常见，
 * 于是一次新的停留会建出一个新地点。地理上没法自动判断「同一个小区」——
 * 硬把半径放大到 300 米会把隔壁真的一家店也吸进来。所以只做**提示**，
 * 合不合并由用户决定。
 *
 * 提示半径取 400 米：实测三组重复分别是 163 m、260 m（确实同一处）
 * 和 334 m（可能确实是两处）。再宽就会把相邻的不同地点也报成疑似。
 *
 * **已经被半径覆盖的组合不算疑似**：合并过的地点半径会被撑大（最多 500 米），
 * 落在它半径里的邻居按匹配规则本来就会归给它，再提示「疑似重复」只会诱导
 * 用户做一次没必要的合并。
 */
object PlaceNeighbors {
    /** 提示「疑似同一个地方」的半径（米）。 */
    const val SUSPECT_RADIUS_METERS = 400.0

    /**
     * 手动合并时可选的半径（米）。
     *
     * 必须不超过 [PlaceMerge.MAX_DISTANCE_METERS]：超过上限的合并在下一次重算时
     * 会被重建出来，等于白做（这是 0.3.2 审计发现的真实问题）。
     */
    const val PICK_RADIUS_METERS: Double = PlaceMerge.MAX_DISTANCE_METERS

    data class Entry(
        val id: Long,
        val point: GeoPoint,
        /** 这个地点自己的归属半径，见 `PlaceResolver`。 */
        val radiusMeters: Double = PlaceResolver.MATCH_RADIUS_METERS,
    )

    data class Neighbor(
        val id: Long,
        val distanceMeters: Double,
    )

    /** [fromId] 周围半径内的其他地点，按距离升序。 */
    fun around(
        entries: List<Entry>,
        fromId: Long,
        radiusMeters: Double = SUSPECT_RADIUS_METERS,
    ): List<Neighbor> {
        val from = entries.firstOrNull { it.id == fromId } ?: return emptyList()
        return entries
            .asSequence()
            .filter { it.id != fromId }
            .map { Neighbor(it.id, distanceMeters(from.point, it.point)) }
            .filter { it.distanceMeters <= radiusMeters }
            .sortedBy { it.distanceMeters }
            .toList()
    }

    /**
     * 至少有一个**未被覆盖**的邻居在半径内的地点 id 集合。列表页用来标「疑似重复」。
     *
     * 「被覆盖」指两个锚点的距离已经落在一方的归属半径内——那种情况下匹配引擎
     * 本来就会把它们当成同一个地点。
     */
    fun suspects(
        entries: List<Entry>,
        radiusMeters: Double = SUSPECT_RADIUS_METERS,
    ): Set<Long> {
        val result = mutableSetOf<Long>()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val distance = distanceMeters(entries[i].point, entries[j].point)
                if (distance > radiusMeters) continue
                val covered = maxOf(entries[i].radiusMeters, entries[j].radiusMeters)
                if (distance <= covered) continue
                result += entries[i].id
                result += entries[j].id
            }
        }
        return result
    }
}
