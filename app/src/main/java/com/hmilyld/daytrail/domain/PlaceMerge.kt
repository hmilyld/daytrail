package com.hmilyld.daytrail.domain

/**
 * 两个地点合并时的半径计算（纯逻辑，可单测）。
 *
 * 为什么合并要动半径：`recomputeDay` 是「按天全量重算」，每次都用距离重新判归属，
 * 不看停留当前挂在哪个地点上。如果只把 `stay_segment.place_id` 改到目标、
 * 把源地点删掉，下一次重算（录制期间每 60 秒一次）会因为目标半径不够而
 * **重新建出一个一模一样的地点**，合并等于白做。
 *
 * 所以合并 = 改判停留 + 把目标半径撑到能覆盖源地点，两者缺一不可。
 * 上限存在的意义：半径过大的地点会把旁边真的另一个地点吸进来，
 * 而「最近的先匹配」只能挡住一部分（见 [PlaceResolver.nearest]）。
 */
object PlaceMerge {
    /** 半径在「两锚点距离 + 源半径」之外再留的余量（米）。 */
    const val MARGIN_METERS = 50.0

    /** 合并后允许的最大归属半径（米）。 */
    const val MAX_RADIUS_METERS = 500.0

    /**
     * 显式合并允许的最大锚点距离（米）。
     *
     * 合并后的半径有 [MAX_RADIUS_METERS] 上限，而「合并能站住」要求
     * `min(距离 + 源半径 + 余量, 上限) >= 距离`——只要距离不超过上限就恒成立，
     * 超过就必然小于距离：源行删掉后下一次重算会在源坐标把地点重建出来，
     * 用户看到的是「合并成功后又冒出一个新地点」。
     *
     * 所以候选列表的半径（[PlaceNeighbors.PICK_RADIUS_METERS]）不能超过这个值。
     * 两者曾经不一致（1000 / 500），500 米以外的合并是注定会被打回的。
     */
    const val MAX_DISTANCE_METERS = MAX_RADIUS_METERS

    /**
     * 合并后的目标半径。
     *
     * @param toRadiusMeters 目标地点当前半径
     * @param distanceMeters 两个锚点之间的距离
     * @param fromRadiusMeters 源地点当前半径
     */
    fun mergedRadius(
        toRadiusMeters: Double,
        distanceMeters: Double,
        fromRadiusMeters: Double,
    ): Double {
        val needed = distanceMeters + fromRadiusMeters + MARGIN_METERS
        return maxOf(toRadiusMeters, needed.coerceAtMost(MAX_RADIUS_METERS))
    }

    /** 这个距离上的合并能否站住（半径上限覆盖得到源地点）。 */
    fun isCoverable(distanceMeters: Double): Boolean = distanceMeters <= MAX_DISTANCE_METERS
}
