package com.hmilyld.daytrail.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.hmilyld.daytrail.core.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 基于 Android 系统定位的采集实现。
 *
 * 为什么需要它：
 * 高德定位 SDK 依赖 `libAMapSDK_MAP_*.so`，该库只提供 arm64-v8a / armeabi-v7a。
 * 在 x86_64 模拟器上高德定位必然失败（错误码 7），整个采集链路无法验证。
 *
 * 系统 `LocationManager` 不依赖高德原生库，任何架构都能工作。
 * 真机上它同样有价值：高德定位异常时兜底，避免整天采不到点。
 */
class SystemLocationSource(
    private val context: Context,
) : LocationSource {
    private val manager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /** 系统定位是否可用（至少一个 provider 启用）。 */
    val isAvailable: Boolean
        get() {
            val lm = manager ?: return false
            return runCatching {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        }

    @SuppressLint("MissingPermission")
    override suspend fun requestOnce(
        highAccuracy: Boolean,
        timeoutMs: Long,
    ): LocationFix? {
        val lm = manager ?: return null

        val providers = enabledProviders(lm, highAccuracy)
        if (providers.isEmpty()) return null

        // 先看缓存位置：模拟器与静止场景下主动请求可能一直等不到新事件，
        // 但最近一次位置仍然有效。这是最可靠的一手。
        val cached = newestCached(lm, providers)
        if (cached != null && System.currentTimeMillis() - cached.time <= FRESH_ENOUGH_MS) {
            return cached.toFix()
        }

        val fresh = awaitUpdate(lm, providers, timeoutMs)
        val best = fresh ?: cached ?: return null

        // 只有缓存、且已过期时放弃，避免写入陈旧坐标
        if (fresh == null && System.currentTimeMillis() - best.time > STALE_LIMIT_MS) {
            return null
        }

        return best.toFix()
    }

    /** 取所有 provider 里最新的缓存位置。 */
    @SuppressLint("MissingPermission")
    private fun newestCached(
        lm: LocationManager,
        providers: List<String>,
    ): Location? =
        providers
            .mapNotNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }

    override fun release() = Unit

    /**
     * 等待一次位置更新。
     *
     * 用 try/finally 保证监听器一定被移除——无论成功、超时还是被取消。
     *
     * 早期实现只在 `invokeOnCancellation` 里移除，成功返回时不移除，
     * 导致每取一次位置就残留一个监听器。地图页每 15 秒取一次，
     * 几分钟后系统 LocationManager 被几十个监听器拖垮，后续请求拿不到回调，
     * 表现为「开始记录后没有任何采样数据」。
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitUpdate(
        lm: LocationManager,
        providers: List<String>,
        timeoutMs: Long,
    ): Location? {
        var listener: LocationListener? = null

        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    var delivered = false

                    val currentListener =
                        object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (delivered) return
                                delivered = true
                                continuation.resumeWith(Result.success(location))
                            }

                            @Deprecated("Deprecated in API 30")
                            override fun onStatusChanged(
                                provider: String?,
                                status: Int,
                                extras: android.os.Bundle?,
                            ) = Unit

                            override fun onProviderEnabled(provider: String) = Unit

                            override fun onProviderDisabled(provider: String) = Unit
                        }
                    listener = currentListener

                    runCatching {
                        providers.forEach { provider ->
                            lm.requestLocationUpdates(
                                provider,
                                0L,
                                0f,
                                currentListener,
                                Looper.getMainLooper(),
                            )
                        }
                    }.onFailure {
                        if (!delivered) {
                            delivered = true
                            continuation.resumeWith(Result.success(null))
                        }
                    }
                }
            }
        } finally {
            // 关键：任何路径都要移除，否则监听器会持续累积
            listener?.let { runCatching { lm.removeUpdates(it) } }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enabledProviders(
        lm: LocationManager,
        highAccuracy: Boolean,
    ): List<String> {
        val gpsEnabled =
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val networkEnabled =
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
                .getOrDefault(false)

        return when {
            highAccuracy && gpsEnabled -> listOf(LocationManager.GPS_PROVIDER)
            networkEnabled && gpsEnabled ->
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            networkEnabled -> listOf(LocationManager.NETWORK_PROVIDER)
            gpsEnabled -> listOf(LocationManager.GPS_PROVIDER)
            else -> emptyList()
        }
    }

    private fun Location.toFix(): LocationFix =
        LocationFix(
            timestampMs = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            point = GeoPoint(latitude, longitude),
            accuracyMeters = accuracy,
            speedMps = speed.takeIf { it > 0f },
            bearingDegrees = bearing.takeIf { it >= 0f },
            isMock =
                runCatching {
                    @Suppress("DEPRECATION")
                    isFromMockProvider
                }.getOrDefault(false),
            // 系统 LocationManager 返回的是 GPS 原始坐标
            coordinateSystem = CoordinateSystem.WGS84,
        )

    private companion object {
        /** 缓存位置的过期上限。超过就不再用，宁可这一轮不采。 */
        const val STALE_LIMIT_MS = 10 * 60_000L

        /**
         * 缓存位置「足够新」的阈值。
         *
         * 在这个窗口内直接用缓存，不发起主动请求：
         * 主动请求在静止时可能等到超时，而缓存已经是我们需要的最新坐标。
         * 省电档下采样间隔是 10 分钟，因此 2 分钟的缓存窗口不会造成明显滞后。
         */
        const val FRESH_ENOUGH_MS = 2 * 60_000L
    }
}
