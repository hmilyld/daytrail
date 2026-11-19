package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/**
 * 停留 → 地点的归属判定（纯逻辑，可单测）。
 *
 * 规则只有一条：**落在最近地点半径内就归给它，否则就是新地点**。
 *
 * 为什么用固定半径而不是聚类算法：个人轨迹里地点数量是个位数（家、公司、常去的几家店），
 * 增量归并比 DBSCAN 更好解释，也不需要每次重算全量历史。
 *
 * 为什么锚点一旦确定就不再更新：停留质心每天会差十几米，如果按新观察做滑动平均，
 * 「家」在地图上会缓慢挪动；而锚点固定之后，同一个地方每天落在同一个像素上，
 * 命名也能稳定复用。单次质心本身已经是多点的均值，误差在半径面前可以忽略。
 */
object PlaceResolver {
    /** 归并半径（米）。停留半径是 80 m，留出两天的质心差异。 */
    const val MATCH_RADIUS_METERS = 120.0

    /** 参与匹配的地点。 */
    data class Candidate(
        val id: Long,
        val point: GeoPoint,
    )

    /** 最近的匹配地点；都超出半径时返回 null。 */
    fun nearest(
        candidates: List<Candidate>,
        point: GeoPoint,
        radiusMeters: Double = MATCH_RADIUS_METERS,
    ): Candidate? =
        candidates
            .map { it to distanceMeters(it.point, point) }
            .filter { it.second <= radiusMeters }
            .minByOrNull { it.second }
            ?.first
}
