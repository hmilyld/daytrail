package com.hmilyld.daytrail.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap

/**
 * 高德地图的 Compose 封装。
 *
 * 两个关键设计：
 *
 * 1. **用 TextureMapView 而不是 MapView**
 *    MapView 内部是 SurfaceView，绘制在独立 Surface 层，不参与 View 层级的
 *    alpha / transform 动画。页面切换时 Compose 的淡出对 SurfaceView 无效，
 *    地图会「粘」在屏幕上直到整页被移除。TextureMapView 基于 TextureView，
 *    跟随正常 View 层级绘制。
 *
 * 2. **实例跨页面复用，不在 onDispose 里销毁**
 *    地图创建成本高（GL 上下文、渲染管线、首批瓦片）。如果每次切 Tab 都重建，
 *    会看到明显白屏与瓦片重新下载。这里只转发 resume/pause，
 *    真正的销毁交给 [MapViewCache.destroy]，只在应用退出时发生。
 *
 * 代价：地图会常驻一份内存（约 30–60MB）。对轨迹类应用是值得的取舍——
 * 用户在地图和列表之间来回切换是高频操作。
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

        // 进入页面时确保处于 resume 状态
        MapViewCache.resume()

        onMapReady(mapView.map)
        onMapCreated(mapView.map)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onMapReleased()
            // 只暂停，不销毁：实例留给下次复用
            MapViewCache.pause()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
