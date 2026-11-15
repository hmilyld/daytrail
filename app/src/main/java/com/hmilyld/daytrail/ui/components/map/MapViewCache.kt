package com.hmilyld.daytrail.ui.components.map

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.amap.api.maps.TextureMapView
import java.lang.ref.WeakReference

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
 * 注意：用 application context 创建，避免持有 Activity 造成泄漏。
 * 高德 MapView 在 application context 下工作正常，因为它不依赖 Activity 主题。
 */
object MapViewCache {
    private const val TAG = "MapViewCache"

    /** 缓存的 MapView。用弱引用让系统在内存紧张时可以回收。 */
    private var cachedView: WeakReference<TextureMapView>? = null

    /** 是否已经调用过 onCreate。重复调用会破坏内部状态。 */
    private var created = false

    /**
     * 取得（或创建）地图视图。
     *
     * 返回的 View 可能仍挂在旧的父容器上，调用方必须先从父容器移除。
     */
    fun obtain(context: Context): TextureMapView {
        cachedView?.get()?.let { existing ->
            // 已存在：从旧父容器摘下，交给新的 Compose 容器
            (existing.parent as? android.view.ViewGroup)?.removeView(existing)
            return existing
        }

        val view = TextureMapView(context.applicationContext)
        if (!created) {
            view.onCreate(Bundle())
            created = true
        }
        cachedView = WeakReference(view)
        Log.d(TAG, "创建地图实例")
        return view
    }

    /** 页面可见时调用。 */
    fun resume() {
        runCatching { cachedView?.get()?.onResume() }
    }

    /** 页面不可见时调用。只暂停渲染，不销毁。 */
    fun pause() {
        runCatching { cachedView?.get()?.onPause() }
    }

    /**
     * 应用真正退出时销毁。
     *
     * 只在 `Application.onTerminate` 或进程结束时发生，
     * 不在页面切换时调用——那是本类要解决的核心问题。
     */
    fun destroy() {
        cachedView?.get()?.let { view ->
            runCatching { view.onDestroy() }
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        cachedView = null
        created = false
    }

    /** 地图实例是否已经创建。用于判断是否需要恢复相机位置。 */
    val isCreated: Boolean get() = cachedView?.get() != null
}
