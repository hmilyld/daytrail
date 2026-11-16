package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint

/**
 * 轨迹抽稀（Douglas-Peucker）。
 *
 * 为什么渲染时要抽稀：
 * 省电档一天约 150–500 个点，看起来不多；但驾车档 1 分钟一点、
 * 高精度档 15 秒一点，长途一天可能上千点。地图 SDK 对折线顶点数敏感，
 * 点数过多会明显拖慢渲染。
 *
 * 库里存**原始点**，只在渲染时抽稀。这样换算法不需要重算历史数据。
 *
 * 阈值 5 米：小于 GPS 自身精度（通常 5–10 米），
 * 抽稀后肉眼无法分辨与原轨迹的差别。
 */
object TrackSimplifier {
    /**
     * @param points 按时间升序的原始点
     * @param toleranceMeters 允许的最大偏移。越大点越少，轨迹越粗
     */
    fun simplify(
        points: List<GeoPoint>,
        toleranceMeters: Double = 5.0,
    ): List<GeoPoint> {
        if (points.size <= 2) return points

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        simplifySection(points, 0, points.size - 1, toleranceMeters, keep)

        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun simplifySection(
        points: List<GeoPoint>,
        startIndex: Int,
        endIndex: Int,
        toleranceMeters: Double,
        keep: BooleanArray,
    ) {
        if (endIndex <= startIndex + 1) return

        var maxDistance = 0.0
        var maxIndex = startIndex

        for (index in startIndex + 1 until endIndex) {
            val distance =
                perpendicularDistanceMeters(
                    points[index],
                    points[startIndex],
                    points[endIndex],
                )
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = index
            }
        }

        if (maxDistance > toleranceMeters) {
            keep[maxIndex] = true
            simplifySection(points, startIndex, maxIndex, toleranceMeters, keep)
            simplifySection(points, maxIndex, endIndex, toleranceMeters, keep)
        }
    }

    /**
     * 点到线段的最短距离。
     *
     * 用平面近似而不是球面计算：在几公里的局部范围内误差可忽略，
     * 而抽稀只需要相对大小关系，不需要绝对精确。
     */
    private fun perpendicularDistanceMeters(
        point: GeoPoint,
        lineStart: GeoPoint,
        lineEnd: GeoPoint,
    ): Double {
        // 以 lineStart 为原点做局部平面投影，经度按纬度收缩
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = 111_320.0 * kotlin.math.cos(Math.toRadians(lineStart.latitude))

        val px = (point.longitude - lineStart.longitude) * metersPerDegreeLon
        val py = (point.latitude - lineStart.latitude) * metersPerDegreeLat
        val ex = (lineEnd.longitude - lineStart.longitude) * metersPerDegreeLon
        val ey = (lineEnd.latitude - lineStart.latitude) * metersPerDegreeLat

        val segmentLengthSquared = ex * ex + ey * ey
        if (segmentLengthSquared == 0.0) {
            return kotlin.math.sqrt(px * px + py * py)
        }

        // 投影参数限制在 [0,1]，退化为到端点的距离
        val t = ((px * ex + py * ey) / segmentLengthSquared).coerceIn(0.0, 1.0)
        val dx = px - t * ex
        val dy = py - t * ey
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
