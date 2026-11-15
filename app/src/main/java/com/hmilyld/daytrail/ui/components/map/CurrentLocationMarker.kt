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
    /** 是否持续跟随更新。记录中应开启，只看历史时应关闭以省电。 */
    follow: Boolean = true,
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

    LaunchedEffect(currentAmap, follow) {
        val map = currentAmap ?: return@LaunchedEffect
        if (!follow) return@LaunchedEffect

        val source = SystemLocationSource(context)
        var firstFixDone = false

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
                    firstFixDone = true
                } else {
                    runCatching { existing.position = position }
                }

                currentOnLocation?.invoke(point)
            }

            // 首次取到位置后缩短间隔，让图标跟随更跟手；
            // 未取到时保持较长间隔，避免频繁唤醒耗电
            delay(if (firstFixDone) FOLLOW_INTERVAL_MS else RETRY_INTERVAL_MS)
        }
    }
}

/** 已定位后的跟随间隔。15 秒对「看自己在哪」完全够用，且几乎不耗电。 */
private const val FOLLOW_INTERVAL_MS = 15_000L

/** 尚未定位时的重试间隔。稍短一点，让图标尽快出现。 */
private const val RETRY_INTERVAL_MS = 5_000L

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
