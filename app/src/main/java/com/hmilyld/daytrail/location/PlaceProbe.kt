package com.hmilyld.daytrail.location

/** 网络切换探针之后的动作。 */
enum class ProbeAction {
    /** 已离开原地点，按出发处理。 */
    WAKE,

    /** 还在原地点（或判断不了），只开守候窗口，不唤醒。 */
    WATCH,
}

/**
 * 网络切换探针的判定（纯逻辑，可单测）。
 *
 * 探针只回答一个问题：这个点还在原地点半径内吗？
 * - 在内 → 只是下楼、电梯口，进守候窗口；
 * - 在外 → 真的走了，唤醒；
 * - 没有任何已知地点（新装、地点被删）→ 无法证明「还在」，按离开处理。
 */
object PlaceProbe {
    /**
     * 判定半径。比地点归并的 120 米宽，因为高层下楼、小区里走两步
     * 很容易到 120–180 米，用 120 米会被误判成「离开了」。
     */
    const val RADIUS_METERS = 200.0

    fun decide(
        nearestMeters: Double?,
        radiusMeters: Double = RADIUS_METERS,
    ): ProbeAction =
        if (nearestMeters != null && nearestMeters <= radiusMeters) {
            ProbeAction.WATCH
        } else {
            ProbeAction.WAKE
        }
}
