package com.hmilyld.daytrail.ui.components.map

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import com.amap.api.maps.TextureMapView

/**
 * 地图实例缓存。
 *
 * 为什么需要它：
 * 高德 `TextureMapView` 的创建成本很高——要建立 GL 上下文、初始化渲染管线、
 * 拉取首批瓦片。如果每次切换 Tab 都重建，用户会看到明显的白屏与瓦片重新下载，
 * 而且反复创建/销毁容易触发 GL 资源泄漏。
 *
 * 做法：进程内只保留一个 MapView 实例，页面离开时只把它从视图树摘下，
 * 不调用 `onDestroy()`。这样瓦片缓存、相机位置、GL 上下文全部保留。
 *
 * 生命周期由 `DayTrailApplication` 负责：应用退出时才真正销毁。
 *
 * **为什么用强引用而不是 WeakReference**：
 * 早期版本用 `WeakReference` 缓存，另用一个 `Boolean` 标记是否已 `onCreate`。
 * 一旦实例被 GC 回收，新建的 View 会因标志位仍为 true 而跳过 `onCreate()`，
 * 得到一个未初始化的地图（白屏且无报错），极难排查。
 * View 用 application context 创建，不会泄漏 Activity，强引用是安全的。
 */
object MapViewCache {
    private const val TAG = "MapViewCache"

    /** 缓存的 MapView。用 application context 创建，强引用不会泄漏 Activity。 */
    private var cachedView: TextureMapView? = null

    /**
     * 取得（或创建）地图视图。
     *
     * 返回的 View 可能仍挂在旧的父容器上，这里先摘下再交给新的 Compose 容器。
     */
    fun obtain(context: Context): TextureMapView {
        cachedView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            Log.d(TAG, "复用已有地图实例")
            return existing
        }

        val view = TextureMapView(context.applicationContext)
        // onCreate 与实例绑定：只要走到这里就是新实例，必须初始化
        view.onCreate(Bundle())
        cachedView = view
        Log.d(TAG, "创建地图实例")
        return view
    }

    /** 页面可见时调用。 */
    fun resume() {
        runCatching { cachedView?.onResume() }
    }

    /** 页面不可见时调用。只暂停渲染，不销毁。 */
    fun pause() {
        runCatching { cachedView?.onPause() }
    }

    /**
     * 应用真正退出时销毁。
     *
     * 只在 `Application.onTerminate` 或进程结束时发生，
     * 不在页面切换时调用——那是本类要解决的核心问题。
     */
    fun destroy() {
        cachedView?.let { view ->
            runCatching { view.onDestroy() }
            (view.parent as? ViewGroup)?.removeView(view)
        }
        cachedView = null
    }

    /** 地图实例是否已经创建。 */
    val isCreated: Boolean get() = cachedView != null
}
