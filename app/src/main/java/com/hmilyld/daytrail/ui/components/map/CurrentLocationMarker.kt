package com.hmilyld.daytrail.ui.components.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.location.SystemLocationSource
import com.hmilyld.daytrail.location.toGcj02
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 当前位置标记。
 *
 * 为什么不用高德内置的 `MyLocationStyle` 蓝点：
 * 它依赖高德定位 SDK 自身成功回调。一旦高德定位失败（权限不全、
 * ROM 限制、x86_64 架构缺少原生库），蓝点完全不显示，用户看不到自己在哪。
 *
 * 这里改成自己画：用系统定位（不依赖高德原生库）取坐标，
 * 再用 `addMarker` 画一个自定义图标。这样只要系统能给位置，图标就一定会出现。
 *
 * 图标用代码绘制而不是 drawable 资源：需要在中心留一个精确的定位点，
 * 且要能在深色地图上也清晰可见（白色描边）。
 */
@Composable
fun CurrentLocationMarker(
    amap: AMap?,
    /** 是否正在记录。记录中降低轮询频率，避免与采集服务抢定位资源。 */
    recording: Boolean = false,
    /** 首次取到位置时是否自动把镜头移过去。 */
    autoCenterOnFirstFix: Boolean = true,
    /** 位置变化回调，供「回到当前位置」按钮复用同一个坐标。 */
    onLocationChanged: ((GeoPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val currentAmap by rememberUpdatedState(amap)
    val currentOnLocation by rememberUpdatedState(onLocationChanged)

    var marker by remember { mutableStateOf<Marker?>(null) }
    val icon = remember { createLocationIcon() }

    // 清理标记
    DisposableEffect(Unit) {
        onDispose {
            runCatching { marker?.remove() }
            marker = null
        }
    }

    // 是否已经自动居中过。只在首次定位时移动镜头，
    // 之后不再干预——否则用户拖动地图会被强行拽回。
    var centered by remember { mutableStateOf(false) }

    LaunchedEffect(currentAmap, recording) {
        val map = currentAmap ?: return@LaunchedEffect

        val source = SystemLocationSource(context)
        var hasFix = false

        while (isActive) {
            val fix = source.requestOnce(highAccuracy = true, timeoutMs = 8_000L)
            if (fix != null && fix.errorCode == 0) {
                val point = fix.toGcj02()
                val position = LatLng(point.latitude, point.longitude)

                val existing = marker
                if (existing == null) {
                    marker =
                        runCatching {
                            map.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .icon(icon)
                                    .anchor(0.5f, 0.5f)
                                    // 不参与点击，避免遮挡停留点
                                    .zIndex(10f),
                            )
                        }.getOrNull()
                    hasFix = true
                } else {
                    runCatching { existing.position = position }
                }

                // 首次定位自动居中：打开应用就该看到自己在哪
                if (autoCenterOnFirstFix && !centered) {
                    centered = true
                    runCatching {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(position, INITIAL_ZOOM),
                        )
                    }
                }

                currentOnLocation?.invoke(point)
            }

            // 间隔策略：
            // - 未取到位置时 5 秒重试，让图标尽快出现
            // - 记录中 60 秒：采集服务才是主力，这里只做视觉参考
            // - 未记录时 15 秒：跟随够用，几乎不耗电
            val interval =
                when {
                    !hasFix -> RETRY_INTERVAL_MS
                    recording -> RECORDING_INTERVAL_MS
                    else -> IDLE_INTERVAL_MS
                }
            delay(interval)
        }
    }
}

/** 尚未取到位置时的重试间隔。稍短一点，让图标尽快出现。 */
private const val RETRY_INTERVAL_MS = 5_000L

/** 记录中的轮询间隔。采集服务是主力，这里只做视觉参考，不宜抢资源。 */
private const val RECORDING_INTERVAL_MS = 60_000L

/** 未记录时的跟随间隔。 */
private const val IDLE_INTERVAL_MS = 15_000L

/** 首次定位的缩放级别。17 约等于能看清街区。 */
private const val INITIAL_ZOOM = 17f

/**
 * 用代码绘制定位图标：蓝色实心圆 + 白色描边 + 白色中心点。
 *
 * 为什么不用高德的 `defaultMarker`：那是水滴形图钉，
 * 当前位置用圆点更符合地图惯例，也不会和停留点的图钉混淆。
 */
private fun createLocationIcon(): BitmapDescriptor {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    // 外圈白色描边：深色地图上保证可见
    val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    canvas.drawCircle(center, center - 2f, 20f, strokePaint)

    // 蓝色实心圆
    val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2F6BFF")
            style = Paint.Style.FILL
        }
    canvas.drawCircle(center, center - 2f, 15f, fillPaint)

    // 中心白点：指示精确位置
    val dotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    canvas.drawCircle(center, center - 2f, 5f, dotPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
