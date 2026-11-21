package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/**
 * 停留 → 地点的归属判定（纯逻辑，可单测）。
 *
 * 规则只有一条：**落在候选地点自己的半径内就归给它，否则就是新地点**。
 *
 * 为什么用固定半径而不是聚类算法：个人轨迹里地点数量是个位数（家、公司、常去的几家店），
 * 增量归并比 DBSCAN 更好解释，也不需要每次重算全量历史。
 *
 * 为什么锚点一旦确定就不再更新：停留质心每天会差十几米，如果按新观察做滑动平均，
 * 「家」在地图上会缓慢挪动；而锚点固定之后，同一个地方每天落在同一个像素上，
 * 命名也能稳定复用。单次质心本身已经是多点的均值，误差在半径面前可以忽略。
 *
 * **半径是每个地点自带的**（`place.radius_m`），不是全局常量。
 * 同一个小区、同一个园区里两次停车的位置可能差两三百米，用固定的 120 米会
 * 把同一个地方判成两个地点（实测「富田太阳城」与「富田太阳城3期」相距 260 米）。
 * 用户手动合并两个地点时会把目标地点的半径撑大（见 [PlaceMerge]），
 * 之后的重算就能继续把停留归到目标上，合并不会在 60 秒后被打回去。
 */
object PlaceResolver {
    /** 新地点、以及还不该扩张的地点的默认归属半径（米）。 */
    const val MATCH_RADIUS_METERS = 120.0

    /** 参与匹配的地点。 */
    data class Candidate(
        val id: Long,
        val point: GeoPoint,
        /** 这个地点自己的归属半径。 */
        val radiusMeters: Double = MATCH_RADIUS_METERS,
    )

    /** 最近的匹配地点；都超出各自半径时返回 null。 */
    fun nearest(
        candidates: List<Candidate>,
        point: GeoPoint,
    ): Candidate? =
        candidates
            .map { it to distanceMeters(it.point, point) }
            .filter { (candidate, distance) -> distance <= candidate.radiusMeters }
            .minByOrNull { it.second }
            ?.first
}
