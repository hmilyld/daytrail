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
 * **为什么需要 attach/release 计数**：
 * 页面转场时 `NavDisplay` 会短暂同时组合两个页面（进入单日详情的那 200ms），
 * 两个 `AmapView` 拿着同一个实例。后进来的把 View 抢过去，
 * 先离开的那个在 `onDispose` 里调用 `pause()`——于是**正在用的地图被暂停**，
 * 表现为白屏一次、之后点击/拖动/缩放全部没有反应，而且不会自己恢复。
 * 计数到 0 才暂停，才能真正只在「没有任何页面在用」时停下渲染。
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

    /** 当前有几个页面在用它。归零才允许暂停。 */
    private var holders: Int = 0

    /**
     * 宿主 Activity 是否在前台。
     *
     * **为什么不能听每个页面自己的 lifecycle**：Navigation 3 里每个 entry 有自己的
     * `LifecycleOwner`，而地图是进程内共享的一个实例。以前 `AmapView` 把 entry 的
     * `ON_PAUSE` 直接映射成全局 `pause()`，于是**离开任意一个页面都会暂停别人正在用的
     * 地图**；而 `resume()` 在 View 还没挂上窗口时是空操作（真正的恢复靠 `doOnAttach`），
     * 一旦「新页面 attach + resume」早于「旧 entry 的 ON_PAUSE」，最后落在暂停上，
     * 实例就停在暂停态——此后每一页都是白屏，直到进程重启。
     *
     * 现在只有一个真值来源：Activity 的 onResume/onPause（高德文档要求的用法）。
     */
    private var hostResumed: Boolean = false

    /**
     * 取得（或创建）地图视图。
     *
     * 返回的 View 可能仍挂在旧的父容器上，这里先摘下再交给新的 Compose 容器。
     */
    fun obtain(context: Context): TextureMapView {
        cachedView?.let { existing ->
            val oldParent = existing.parent as? ViewGroup
            (oldParent)?.removeView(existing)
            Log.d(
                TAG,
                "复用已有地图实例（原本挂在 ${oldParent?.javaClass?.simpleName ?: "无父容器"}）",
            )
            return existing
        }

        val view = TextureMapView(context.applicationContext)
        // onCreate 与实例绑定：只要走到这里就是新实例，必须初始化
        view.onCreate(Bundle())
        cachedView = view
        Log.d(TAG, "创建地图实例")
        return view
    }

    /** 页面进入组合：计数 +1。 */
    fun acquire() {
        holders++
        sync("acquire")
    }

    /** 页面离开组合：计数 -1。 */
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        sync("release")
    }

    /** 宿主进入前台。由 `MainActivity.onResume` 驱动。 */
    fun onHostResume() {
        hostResumed = true
        sync("host")
    }

    /** 宿主退到后台。由 `MainActivity.onPause` 驱动。 */
    fun onHostPause() {
        hostResumed = false
        sync("host")
    }

    /**
     * 唯一的开关：**宿主在前台**且**还有页面持有**才渲染。
     *
     * 两个条件缺一不可。只看来任何一个都会出错：只看持有数的话，切到后台后地图还在空转；
     * 只看宿主状态的话，页面已经离开、View 被摘下，`onResume` 也没有意义。
     */
    fun sync(reason: String = "direct") {
        if (hostResumed && holders > 0) {
            resume(reason)
        } else {
            pause(reason)
        }
    }

    /** 页面可见时调用。 */
    private fun resume(reason: String) {
        Log.d(TAG, "resume($reason) holders=$holders ${stateOf()}")
        runCatching { cachedView?.onResume() }
    }

    /** 页面不可见时调用。只暂停渲染，不销毁。 */
    private fun pause(reason: String) {
        Log.d(TAG, "pause($reason) holders=$holders ${stateOf()}")
        runCatching { cachedView?.onPause() }
    }

    /** 日志用：把「谁、挂着没挂着」一次性打出来，排查白屏全靠它。 */
    private fun stateOf(): String =
        cachedView?.let { "attached=${it.isAttachedToWindow} shown=${it.isShown}" } ?: "no-view"

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
        holders = 0
    }

    /** 地图实例是否已经创建。 */
    val isCreated: Boolean get() = cachedView != null
}
