package com.hmilyld.daytrail.ui.components.map

import android.os.Bundle
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
import com.amap.api.maps.MapView

/**
 * 高德 MapView 的 Compose 封装。
 *
 * MapView 是 View 而不是 Composable，直接放进 AndroidView 会漏掉生命周期：
 * 必须在宿主 Activity 的 onResume / onPause / onDestroy 上转发，
 * 否则地图会持续持有传感器与渲染资源，造成内存泄漏和耗电。
 *
 * 这里用 LocalLifecycleOwner 桥接，比在 Activity 里手动转发更可靠，
 * 也让地图可以安全地放在任意页面里。
 */
@Composable
fun AmapView(
    modifier: Modifier = Modifier,
    onMapReady: (AMap) -> Unit = {},
    onMapReleased: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapView 的创建成本高，必须只创建一次，不能随重组重建
    val mapView =
        remember {
            MapView(context).apply {
                onCreate(Bundle())
                // 关闭地图内置的手势缩放按钮与缩放控件，交给外层 UI 控制
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
