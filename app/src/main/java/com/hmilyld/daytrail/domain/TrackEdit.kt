package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint

/**
 * 手动微调后的折线（派生数据）。
 *
 * 和 [RouteOverride] 是同一类东西：**不覆盖原始点，也不参与任何统计**。
 * 库里存的原始采样点一个都不改，`track_day.distance`、停留段、断点、地点
 * 全部照旧由原始点算出；只有地图上画出来的线被这份数据替换。
 *
 * 放在 domain 是因为渲染管线要用它，而渲染不应该依赖 data 层。
 */
data class TrackEdit(
    /** 已编辑的折线，已按「数据缺失」分段。每段至少 2 个点。 */
    val segments: List<List<GeoPoint>>,
    /**
     * 截断点：进入编辑时基线快照里最后一个原始点的时间戳。
     *
     * 时间戳**大于**它的点视为「编辑之后才采到的」，渲染时接在编辑线后面继续画。
     * 用快照时间而不是保存时间：编辑期间新到的点不会被烘进编辑结果，
     * 保存后仍然走增量，不会和编辑线重复。
     */
    val cutoffMs: Long,
    val updatedAtMs: Long,
)
