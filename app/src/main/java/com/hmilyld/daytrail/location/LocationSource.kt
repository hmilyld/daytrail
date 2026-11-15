package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint

/** 一次定位结果。 */
data class LocationFix(
    val timestampMs: Long,
    val point: GeoPoint,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val isMock: Boolean,
    val errorCode: Int = 0,
)

/**
 * 定位来源的统一接口。
 *
 * 有两个实现：
 * - [LocationEngine]：高德定位，多源融合、精度更好，依赖 ARM 原生库
 * - [SystemLocationSource]：Android 系统定位，任何架构都能用，精度略差
 *
 * 服务启动时优先高德，探测失败后切换到系统定位。
 * 这样真机上用的是最优路径，模拟器上也能完整验证采集逻辑。
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
