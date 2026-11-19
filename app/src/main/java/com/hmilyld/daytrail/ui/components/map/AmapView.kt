package com.hmilyld.daytrail.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap

/**
 * 高德地图的 Compose 封装。
 *
 * 三个关键设计：
 *
 * 1. **用 TextureMapView 而不是 MapView**
 *    MapView 内部是 SurfaceView，绘制在独立 Surface 层，不参与 View 层级的
 *    alpha / transform 动画。页面切换时 Compose 的淡出对 SurfaceView 无效，
 *    地图会「粘」在屏幕上直到整页被移除。TextureMapView 基于 TextureView，
 *    跟随正常 View 层级绘制。
 *
 * 2. **实例跨页面复用，不在 onDispose 里销毁**
 *    地图创建成本高（GL 上下文、渲染管线、首批瓦片）。如果每次切 Tab 都重建，
 *    会看到明显白屏与瓦片重新下载。这里只做 acquire/release 计数，
 *    最后一个页面离开时才暂停，真正的销毁交给 [MapViewCache.destroy]。
 *
 * 3. **在包装层统一关掉 SDK 自带的缩放按钮**
 *    它按屏幕密度绘制，在高 DPI 手机上又大又糊，而且和「双指缩放」重复。
 *    手势能力在这里显式打开：不依赖 SDK 默认值，否则换个版本就可能整体失灵。
 */
@Composable
fun AmapView(
    modifier: Modifier = Modifier,
    onMapReady: (AMap) -> Unit = {},
    onMapReleased: () -> Unit = {},
    onMapCreated: (AMap) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从缓存取实例：已存在则复用，不重建
    val mapView = remember { MapViewCache.obtain(context) }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> MapViewCache.resume()
                    Lifecycle.Event.ON_PAUSE -> MapViewCache.pause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        // 进入页面时占位 +1：转场期间两个页面同时存在，谁都不能把地图暂停掉
        MapViewCache.acquire()

        configure(mapView.map)
        onMapReady(mapView.map)
        onMapCreated(mapView.map)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onMapReleased()
            // 计数归零才暂停：实例留给下次复用
            MapViewCache.release()
        }
    }

    AndroidView(
        factory = {
            mapView.also { view ->
                // 挂上窗口之后再 resume：DisposableEffect 里的 acquire() 发生在
                // AndroidView 把 View 交给容器之前，此时调用 onResume 是空操作，
                // 地图会停在暂停状态——表现就是「画着最后一帧，但点击拖动全都没反应」。
                view.doOnAttach { MapViewCache.resume() }
            }
        },
        modifier = modifier,
    )
}

/**
 * 地图交互设置。
 *
 * - 关掉 SDK 自带的 +/− 缩放按钮：它们按密度放大，在高 DPI 屏幕上非常突兀
 * - 双指缩放、拖动、旋转显式打开；倾斜关掉（看轨迹不需要三维视角，
 *   误触倾斜后整张图会变形，很难调回来）
 */
private fun configure(amap: AMap) {
    runCatching {
        amap.uiSettings.apply {
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = false
            isCompassEnabled = true
        }
    }
}
