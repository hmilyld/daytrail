package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.GeoPoint

/**
 * 坐标所属的坐标系。
 *
 * - 高德地图底图使用 GCJ-02
 * - Android 系统定位返回 WGS-84
 *
 * 入库前必须统一转成 GCJ-02，否则轨迹偏移 50–550 米。
 */
enum class CoordinateSystem {
    /** 国测局加密坐标，高德地图底图使用。 */
    GCJ02,

    /** GPS 原始坐标，Android 系统定位返回。 */
    WGS84,
}

/** 一次定位结果。 */
data class LocationFix(
    val timestampMs: Long,
    val point: GeoPoint,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val isMock: Boolean,
    val errorCode: Int = 0,
    /** 该坐标的坐标系。入库前必须转成 GCJ-02。 */
    val coordinateSystem: CoordinateSystem = CoordinateSystem.GCJ02,
)

/**
 * 定位来源的统一接口。
 *
 * 目前只有 [SystemLocationSource] 一个实现。
 *
 * **为什么不用高德定位 SDK**：
 * 它需要 `com.amap.api.location.APSService` 在 Manifest 中声明，
 * 漏声明会永远返回 error 7（定位失败），而地图上的位置圆点
 * （走系统定位）却正常显示，用户会看到「有圆点但提示定位失败」。
 * 两套定位并存还会互相抢资源、各自维护一套失败计数。
 *
 * 系统定位同样能拿到 GPS 与网络定位，对「记录大概位置」足够，
 * 且不依赖厂商 SDK 的额外服务。
 *
 * 实现必须如实标注 [LocationFix.coordinateSystem]，
 * 由 [TrackRecorder] 统一转成 GCJ-02 入库。
 */
interface LocationSource {
    /** 请求一次定位。实现必须保证回调只触发一次。 */
    suspend fun requestOnce(
        highAccuracy: Boolean,
        timeoutMs: Long,
    ): LocationFix?

    /** 释放资源。 */
    fun release()
}

/**
 * 把定位结果统一转成 GCJ-02。
 *
 * 地图底图是 GCJ-02，而系统定位返回 WGS-84。
 * 任何要在地图上绘制的坐标都必须先过这一步，否则偏移 50–550 米。
 */
fun LocationFix.toGcj02(): GeoPoint =
    if (coordinateSystem == CoordinateSystem.WGS84) {
        CoordinateTransform.wgs84ToGcj02(point)
    } else {
        point
    }
