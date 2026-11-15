package com.hmilyld.daytrail.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 地球平均半径（米）。haversine 公式使用。 */
private const val EARTH_RADIUS_M = 6_371_008.8

/** 一个地理坐标点。纬经度单位均为十进制度。 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    /** 坐标是否落在合法范围内。用于过滤高德返回的异常值（0,0 等）。 */
    val isValid: Boolean
        get() =
            latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                !(abs(latitude) < 0.0001 && abs(longitude) < 0.0001)

    /** 高德返回 GCJ-02 坐标，判断是否在国内大致范围内（用于决定要不要纠偏）。 */
    val isLikelyInChina: Boolean
        get() = latitude in 3.0..54.0 && longitude in 73.0..136.0
}

/**
 * 两点间大圆距离，单位米。
 *
 * 用 haversine 而非平面近似：跨城市距离上平面近似的误差可达百分之几，
 * 而本应用的核心指标就是里程。
 */
fun distanceMeters(
    from: GeoPoint,
    to: GeoPoint,
): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)

    val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_M * c
}

/** 从 a 到 b 的方位角，单位度，范围 [0, 360)。 */
fun bearingDegrees(
    from: GeoPoint,
    to: GeoPoint,
): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val degrees = Math.toDegrees(atan2(y, x))
    return (degrees + 360.0) % 360.0
}

/** 路径总长度，单位米。输入点需已过滤。 */
fun polylineLengthMeters(points: List<GeoPoint>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (index in 1 until points.size) {
        total += distanceMeters(points[index - 1], points[index])
    }
    return total
}
