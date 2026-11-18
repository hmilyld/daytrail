package com.hmilyld.daytrail.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.hmilyld.daytrail.core.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    /**
     * 按间隔持续订阅定位。
     *
     * GPS 优先：只在 GPS 迟迟没有回调时才补注册网络定位。
     * 室外不注册网络 provider，就不会白白拉起基站与 WiFi 扫描——
     * 那是「明明在户外，耗电还是不低」的常见来源。
     *
     * 拿不到位置时不结束流：室内、地下都会连续几分钟没有回调，
     * 这属于正常情况，由调用方决定要不要提示用户。
     */
    @SuppressLint("MissingPermission")
    override fun updates(intervalMs: Long): Flow<LocationFix> =
        callbackFlow {
            val lm = manager ?: throw IllegalStateException("设备没有 LocationManager")

            val gpsEnabled =
                runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }
                    .getOrDefault(false)
            val networkEnabled =
                runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
                    .getOrDefault(false)

            val initial =
                when {
                    gpsEnabled -> listOf(LocationManager.GPS_PROVIDER)
                    networkEnabled -> listOf(LocationManager.NETWORK_PROVIDER)
                    else -> throw IllegalStateException("没有启用的定位 provider")
                }

            val registered = mutableListOf<String>()
            var lastDeliveryAtMs = System.currentTimeMillis()

            val listener =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lastDeliveryAtMs = System.currentTimeMillis()
                        trySend(location.toFix())
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

            fun register(providers: List<String>) {
                providers.forEach { provider ->
                    if (provider in registered) return@forEach
                    runCatching {
                        lm.requestLocationUpdates(
                            provider,
                            intervalMs,
                            0f,
                            listener,
                            Looper.getMainLooper(),
                        )
                    }.onSuccess {
                        registered += provider
                        Log.d(TAG, "订阅 $provider，间隔 ${intervalMs / 1000}s")
                    }.onFailure {
                        Log.d(TAG, "订阅 $provider 失败：${it.message}")
                    }
                }
            }

            register(initial)
            if (registered.isEmpty()) {
                throw IllegalStateException("定位 provider 注册失败")
            }

            if (registered == listOf(LocationManager.GPS_PROVIDER) && networkEnabled) {
                // 室内 GPS 可能永远不回调，而且**中途进室内**也会发生：
                // 早上在户外开始记录，下午到了公司就再也拿不到点。
                // 所以这不是「开局判一次」，而是持续盯着——连续两个间隔都没有
                // 回调，就补注册网络定位；补上之后本次订阅不再重复注册。
                launch {
                    val fallbackAfter = maxOf(intervalMs * 2, MIN_FALLBACK_AFTER_MS)
                    while (isActive) {
                        delay(FALLBACK_CHECK_MS)
                        val silentMs = System.currentTimeMillis() - lastDeliveryAtMs
                        if (silentMs < fallbackAfter) continue
                        if (LocationManager.NETWORK_PROVIDER in registered) break
                        Log.d(TAG, "GPS 已 ${silentMs / 1000}s 没有回调，补注册网络定位")
                        register(listOf(LocationManager.NETWORK_PROVIDER))
                        break
                    }
                }
            }

            // 注册时先给一次缓存位置：重新订阅（档位变化）之后不必再等一个间隔。
            // 只有比一个间隔还新的缓存才用，避免写入陈旧坐标。
            newestCached(lm, registered)
                ?.takeIf { System.currentTimeMillis() - it.time <= intervalMs }
                ?.let { trySend(it.toFix()) }

            awaitClose { runCatching { lm.removeUpdates(listener) } }
        }.conflate()

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

                    // 逐个 provider 独立注册，单个失败不影响其他。
                    // 早期用一个 runCatching 包住整个 forEach：没有精确权限时
                    // GPS 注册抛异常，循环直接中断，网络定位永远不会被注册，
                    // 于是「只有大致位置」的用户一个点都采不到。
                    var registered = false
                    providers.forEach { provider ->
                        runCatching {
                            lm.requestLocationUpdates(
                                provider,
                                0L,
                                0f,
                                currentListener,
                                Looper.getMainLooper(),
                            )
                        }.onSuccess { registered = true }
                            .onFailure { Log.d(TAG, "注册 $provider 失败：${it.message}") }
                    }

                    if (!registered && !delivered) {
                        delivered = true
                        continuation.resumeWith(Result.success(null))
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

        // 两种 provider 都注册，先回调的胜出。
        //
        // 早期实现「高精度时只请求 GPS」，结果室内 GPS 永远定不到，
        // 整段轨迹为空、通知一直停在「正在获取定位」。
        // 网络定位精度差一些，但至少能给出位置，比什么都没有好。
        return buildList {
            if (highAccuracy) {
                if (gpsEnabled) add(LocationManager.GPS_PROVIDER)
                if (networkEnabled) add(LocationManager.NETWORK_PROVIDER)
            } else {
                if (networkEnabled) add(LocationManager.NETWORK_PROVIDER)
                if (gpsEnabled) add(LocationManager.GPS_PROVIDER)
            }
        }
    }

    /** 系统是否至少有一个定位 provider 可用。没有说明定位开关被关掉了。 */
    fun hasAnyProvider(): Boolean {
        val lm = manager ?: return false
        return enabledProviders(lm, highAccuracy = true).isNotEmpty()
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
        const val TAG = "SystemLocationSource"

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

        /** 补注册网络定位前的等待下限。驾车档间隔只有 1 分钟，不能等太久。 */
        const val MIN_FALLBACK_AFTER_MS = 4 * 60_000L

        /** 检查「GPS 是否已经不再回调」的频率。 */
        const val FALLBACK_CHECK_MS = 60_000L
    }
}
