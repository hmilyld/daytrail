package com.hmilyld.daytrail.location

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** 当前默认网络的传输方式。 */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    OTHER,
    NONE,
}

/**
 * 默认网络变化监听。
 *
 * 只监听连接状态，**不扫 WiFi**：`registerDefaultNetworkCallback` 是纯被动的，
 * 系统本来就在维护连接，我们只是收个回调，几乎不耗电。而 `startScan()` 既耗电
 * 又被 Android 10+ 限制，绝不能用。
 *
 * 用途：离开一栋楼时 WiFi 会掉、改用蜂窝，这是「可能出发了」的免费线索。
 */
class NetworkWatcher(
    context: Context,
) {
    private val manager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    /** 当前默认网络的传输方式。 */
    fun current(): NetworkTransport {
        val cm = manager ?: return NetworkTransport.NONE
        val network = cm.activeNetwork ?: return NetworkTransport.NONE
        val capabilities = cm.getNetworkCapabilities(network) ?: return NetworkTransport.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                NetworkTransport.CELLULAR
            }
            else -> NetworkTransport.OTHER
        }
    }

    /** 默认网络变化。会先去重，相邻的同值事件只发一次。 */
    fun events(): Flow<NetworkTransport> =
        callbackFlow {
            val cm = manager
            if (cm == null) {
                close()
                return@callbackFlow
            }

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(current())
                    }

                    override fun onLost(network: Network) {
                        trySend(current())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        trySend(current())
                    }
                }

            runCatching { cm.registerDefaultNetworkCallback(callback) }
                .onFailure {
                    close()
                    return@callbackFlow
                }

            trySend(current())
            awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
        }.distinctUntilChanged()
}
