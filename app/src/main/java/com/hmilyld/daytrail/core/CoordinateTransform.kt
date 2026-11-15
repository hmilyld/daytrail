package com.hmilyld.daytrail.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系转换。
 *
 * 为什么必须有这一层：
 * - 高德定位 SDK 返回 **GCJ-02**（国测局加密坐标，俗称火星坐标）
 * - Android 系统 `LocationManager` 返回 **WGS-84**（GPS 原始坐标）
 * - 两者在国内相差 50–500 米，混用会让轨迹明显偏移、里程偏大
 *
 * 本应用**统一以 GCJ-02 入库**，因为高德地图底图就是 GCJ-02。
 * 任何 WGS-84 来源的坐标都必须在入库前转换。
 *
 * 转换算法是公开的标准实现（WGS-84 → GCJ-02 正向偏移）。
 * 不调用高德的 `CoordinateConverter`：它依赖 ARM 原生库，
 * 在 x86_64 上不可用，而这个转换是纯数学，没必要依赖 SDK。
 */
object CoordinateTransform {
    /** 克拉索夫斯基椭球参数。 */
    private const val A = 6378245.0

    private const val EE = 0.00669342162296594323

    /** 中国境外不做偏移（GCJ-02 只在国内生效）。 */
    private fun outOfChina(
        latitude: Double,
        longitude: Double,
    ): Boolean =
        longitude < 72.004 ||
            longitude > 137.8347 ||
            latitude < 0.8293 ||
            latitude > 55.8271

    /**
     * WGS-84 → GCJ-02。
     *
     * 境外坐标原样返回：GCJ-02 的偏移只在中国境内生效，
     * 境外强行加偏移反而会引入误差。
     */
    fun wgs84ToGcj02(point: GeoPoint): GeoPoint {
        if (!point.isValid) return point
        if (outOfChina(point.latitude, point.longitude)) return point

        var dLat = transformLat(point.longitude - 105.0, point.latitude - 35.0)
        var dLon = transformLon(point.longitude - 105.0, point.latitude - 35.0)

        val radLat = point.latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)

        return GeoPoint(
            latitude = point.latitude + dLat,
            longitude = point.longitude + dLon,
        )
    }

    /**
     * GCJ-02 → WGS-84（近似逆变换）。
     *
     * 用迭代逼近：正向转换后与目标值比较，误差超过 [tolerance] 就继续修正。
     * 3 次迭代能把误差压到 1 米内，足够本应用使用。
     *
     * 只在导出数据给使用 WGS-84 的工具（如 GPX 导入 Google Earth）时才需要。
     */
    fun gcj02ToWgs84(
        point: GeoPoint,
        tolerance: Double = 1e-7,
    ): GeoPoint {
        if (!point.isValid) return point
        if (outOfChina(point.latitude, point.longitude)) return point

        var candidate = point
        repeat(3) {
            val forward = wgs84ToGcj02(candidate)
            val dLat = forward.latitude - point.latitude
            val dLon = forward.longitude - point.longitude
            if (abs(dLat) < tolerance && abs(dLon) < tolerance) return candidate
            candidate =
                GeoPoint(
                    latitude = candidate.latitude - dLat,
                    longitude = candidate.longitude - dLon,
                )
        }
        return candidate
    }

    private fun transformLat(
        x: Double,
        y: Double,
    ): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(
        x: Double,
        y: Double,
    ): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private const val PI = 3.1415926535897932384626
}
