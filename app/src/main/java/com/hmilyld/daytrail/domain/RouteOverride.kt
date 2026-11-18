package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint

/**
 * 一天的纠偏结果（派生数据）。
 *
 * 只覆盖驾车那段的时间窗；渲染时窗口外仍按原始点画，窗口内用 [points]。
 * 放在 domain 是因为渲染管线要用它，而渲染不应该依赖 data 层。
 */
data class RouteOverride(
    val startMs: Long,
    val endMs: Long,
    /** 已按「过长的直连段」切开，中间留白比画一条错的线诚实。 */
    val segments: List<List<GeoPoint>>,
    /** 路网里程。只用于展示，**不写回** `track_day.distance`。 */
    val distanceMeters: Double,
    val createdAtMs: Long,
)
