package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint

/** 坐标所属的坐标系。 */
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
 * 有两个实现：
 * - [LocationEngine]：高德定位，返回 GCJ-02，多源融合、精度更好，依赖 ARM 原生库
 * - [SystemLocationSource]：Android 系统定位，返回 WGS-84，任何架构都能用
 *
 * 实现必须如实标注 [LocationFix.coordinateSystem]，
 * 由 [com.hmilyld.daytrail.location.TrackRecorder] 统一转成 GCJ-02 入库。
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
