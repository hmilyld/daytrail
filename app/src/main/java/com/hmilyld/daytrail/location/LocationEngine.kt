package com.hmilyld.daytrail.location

import android.annotation.SuppressLint
import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.hmilyld.daytrail.core.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 高德定位的封装。
 *
 * 只负责「请求一次定位 / 停止」，不做过滤和状态机判断——
 * 那些是 [PointFilter] 与 [SamplingPolicy] 的职责，保持纯逻辑可单测。
 *
 * 高德定位 SDK 已随 `3dmap` 一起引入，无需额外依赖。
 *
 * **架构限制**：高德只提供 arm64-v8a / armeabi-v7a 的原生库，
 * x86_64 设备（典型是模拟器）上定位必然失败并返回错误码 7。
 * 服务会在连续失败后自动切到 [SystemLocationSource]。
 */
class LocationEngine(
    private val context: Context,
) : LocationSource {
    private var client: AMapLocationClient? = null

    @SuppressLint("MissingPermission")
    override suspend fun requestOnce(
        highAccuracy: Boolean,
        timeoutMs: Long,
    ): LocationFix? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                // 先释放上一次的实例，避免监听器叠加
                release()

                val locationClient = AMapLocationClient(context.applicationContext)
                client = locationClient

                var delivered = false

                continuation.invokeOnCancellation {
                    runCatching {
                        locationClient.stopLocation()
                        locationClient.onDestroy()
                    }
                }

                locationClient.setLocationListener { location ->
                    if (delivered) return@setLocationListener
                    delivered = true

                    val fix =
                        when {
                            location == null -> failureFix()
                            location.errorCode != 0 -> failureFix(location.errorCode)
                            else ->
                                LocationFix(
                                    timestampMs =
                                        location.time.takeIf { it > 0 }
                                            ?: System.currentTimeMillis(),
                                    point = GeoPoint(location.latitude, location.longitude),
                                    accuracyMeters = location.accuracy,
                                    // 高德无速度数据时返回 0，转 null 让上层用位移推算
                                    speedMps = location.speed.takeIf { it > 0f },
                                    bearingDegrees = location.bearing.takeIf { it >= 0f },
                                    isMock = location.isMock,
                                )
                        }
                    continuation.resumeWith(Result.success(fix))
                }

                val option =
                    AMapLocationClientOption().apply {
                        locationMode =
                            if (highAccuracy) {
                                AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                            } else {
                                // 省电优先：静止与步行用基站/WiFi 粗定位
                                AMapLocationClientOption.AMapLocationMode.Battery_Saving
                            }
                        isOnceLocation = true
                        isOnceLocationLatest = false
                        isNeedAddress = false
                        isLocationCacheEnable = false
                        isMockEnable = true
                        // 单次定位超时，避免卡住采样循环
                        httpTimeOut = 8_000
                    }

                locationClient.setLocationOption(option)
                locationClient.startLocation()
            }
        }

    override fun release() {
        client?.run {
            runCatching {
                stopLocation()
                onDestroy()
            }
        }
        client = null
    }

    private fun failureFix(errorCode: Int = -1): LocationFix =
        LocationFix(
            timestampMs = System.currentTimeMillis(),
            point = GeoPoint(0.0, 0.0),
            accuracyMeters = Float.MAX_VALUE,
            speedMps = null,
            bearingDegrees = null,
            isMock = false,
            errorCode = errorCode,
        )
}
