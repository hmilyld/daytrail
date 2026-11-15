package com.hmilyld.daytrail.ui.components.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.TextureMapView

/**
 * 高德地图的 Compose 封装。
 *
 * 为什么用 TextureMapView 而不是 MapView：
 * MapView 内部是 SurfaceView，绘制在独立的 Surface 层上，不参与 View 层级的
 * alpha / transform 动画。页面切换时 Compose 做的淡出动画对 SurfaceView 无效，
 * 地图会「粘」在屏幕上直到整个页面被移除，看起来像卡住。
 * TextureMapView 基于 TextureView，跟随正常 View 层级绘制，能正确参与过渡动画。
 *
 * 代价是 TextureView 比 SurfaceView 多一次 GPU 纹理拷贝，但在本应用的地图
 * 负载下差异可忽略，视觉正确性更重要。
 *
 * 生命周期：MapView 系必须转发 onResume / onPause / onDestroy，
 * 否则会持续持有传感器与渲染资源。这里用 LocalLifecycleOwner 桥接。
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

    // 地图实例创建成本高，只创建一次，不能随重组重建
    val mapView =
        remember {
            TextureMapView(context).apply {
                onCreate(Bundle())
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isScaleControlsEnabled = false
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false
            }
        }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onMapReady(mapView.map)
        onMapCreated(mapView.map)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onMapReleased()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
