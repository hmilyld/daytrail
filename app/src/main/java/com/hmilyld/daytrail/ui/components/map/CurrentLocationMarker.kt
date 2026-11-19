package com.hmilyld.daytrail.ui.components.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
 * 这里改成自己画，并且**只有一条定位链路**：
 * - 记录中：直接跟随 `RecordingService` 的采样点，自己不请求定位。
 *   早期实现每 15 秒单独请求一次高精度定位，既费电，又会出现
 *   「圆点在这一头、轨迹末端在另一头」的错位。
 * - 未记录：地图可见时取一次，之后每 60 秒刷新；切到后台立即停。
 */
@Composable
fun CurrentLocationMarker(
    amap: AMap?,
    /** 是否正在记录。记录中跟随服务，不再自己请求定位。 */
    recording: Boolean = false,
    /** 服务最近一次采样点（GCJ-02）。 */
    lastKnownFix: GeoPoint? = null,
    /** 首次取到位置时是否自动把镜头移过去。 */
    autoCenterOnFirstFix: Boolean = true,
    /** 位置变化回调，供「回到当前位置」按钮复用同一个坐标。 */
    onLocationChanged: ((GeoPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val currentAmap by rememberUpdatedState(amap)
    val currentOnLocation by rememberUpdatedState(onLocationChanged)

    var marker by remember { mutableStateOf<Marker?>(null) }
    val dotColor = MaterialTheme.colorScheme.primary.toArgb()
    val icon = remember(dotColor) { createLocationIcon(dotColor) }

    // 是否已经自动居中过。只在首次定位时移动镜头，
    // 之后不再干预——否则用户拖动地图会被强行拽回。
    var centered by remember { mutableStateOf(false) }

    // 清理标记
    DisposableEffect(Unit) {
        onDispose {
            runCatching { marker?.remove() }
            marker = null
        }
    }

    fun place(
        map: AMap,
        point: GeoPoint,
        center: Boolean = true,
    ) {
        val position = LatLng(point.latitude, point.longitude)
        val existing = marker
        if (existing == null) {
            marker =
                runCatching {
                    map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .icon(icon)
                            // 图钉的尖角对准坐标点，不是图片中心
                            .anchor(0.5f, LOCATION_ICON_ANCHOR_Y)
                            // 不参与点击，避免遮挡停留点
                            .zIndex(20f),
                    )
                }.getOrNull()
        } else {
            runCatching { existing.position = position }
        }

        if (center && autoCenterOnFirstFix && !centered) {
            centered = true
            runCatching {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(position, INITIAL_ZOOM),
                )
            }
        }
        currentOnLocation?.invoke(point)
    }

    // 未记录：自己取一次，之后低频刷新。
    //
    // 必须挂在 RESUMED 上：Compose 的组合在 Activity 停止后依然存在，
    // LaunchedEffect 不会自己停。实测把应用切到桌面后，这个循环仍然每
    // 60 秒定位一次，75 秒里有 24 秒处于定位活跃状态——纯浪费。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(currentAmap, recording, lifecycleOwner) {
        val map = currentAmap ?: return@LaunchedEffect
        if (recording) return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val source = SystemLocationSource(context)

            // 先用缓存位置把圆点画出来，不必等一次真实定位。
            // 不居中：镜头交给下面拿到的真实位置，避免先跳到旧位置再跳回来。
            if (marker == null) {
                val cached = source.lastKnownFix()
                val ageMs =
                    cached?.let { System.currentTimeMillis() - it.timestampMs } ?: Long.MAX_VALUE
                if (cached != null && cached.errorCode == 0 && ageMs <= CACHED_SEED_MAX_AGE_MS) {
                    place(map, cached.toGcj02(), center = false)
                }
            }

            var hasFix = false
            while (isActive) {
                val fix = source.requestOnce(highAccuracy = true, timeoutMs = 8_000L)
                if (fix != null && fix.errorCode == 0) {
                    hasFix = true
                    place(map, fix.toGcj02())
                }
                // 还没取到位置时尽快重试，让图标早点出现
                delay(if (hasFix) IDLE_REFRESH_MS else RETRY_INTERVAL_MS)
            }
        }
    }

    // 记录中：只跟随服务的采样点，不额外请求定位
    LaunchedEffect(currentAmap, recording, lastKnownFix) {
        val map = currentAmap ?: return@LaunchedEffect
        if (!recording) return@LaunchedEffect
        val point = lastKnownFix ?: return@LaunchedEffect
        place(map, point)
    }
}

/** 尚未取到位置时的重试间隔。 */
private const val RETRY_INTERVAL_MS = 5_000L

/** 缓存位置最多允许多旧——太旧的点画出来只会误导。 */
private const val CACHED_SEED_MAX_AGE_MS = 6 * 60 * 60 * 1000L

/**
 * 未记录时的刷新间隔。
 *
 * 从 15 秒放宽到 60 秒：这个界面的屏幕是亮的，屏幕耗电远大于一次定位，
 * 而记录中根本不需要这里的定位。
 */
private const val IDLE_REFRESH_MS = 60_000L

/** 首次定位的缩放级别。17 约等于能看清街区。 */
private const val INITIAL_ZOOM = 17f

/**
 * 用代码绘制定位图标：水滴形图钉 + 白色描边 + 白色中心点。
 *
 * 早期画的是实心圆点，蓝色圆点在地图上和「停留点」「采样点」太像，
 * 用户很难一眼找到自己。水滴图钉是各家地图的通用语义：
 * 尖角所在的位置就是「我现在在这里」。
 *
 * 停留点、起终点仍然是圆点（`MapIcons.dot`），所以两者不会混。
 *
 * 颜色由调用方从 `colorScheme` 传进来，不在页面里写十六进制。
 */
private fun createLocationIcon(color: Int): BitmapDescriptor {
    val width = 64
    val height = 80
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val centerX = width / 2f
    val centerY = 28f
    val radius = 18f
    val tipY = 76f

    // 圆头 + 收拢的尾巴，构成一个完整的水滴
    val path =
        Path().apply {
            addCircle(centerX, centerY, radius, Path.Direction.CW)
            moveTo(centerX, tipY)
            lineTo(centerX - radius * 0.62f, centerY + radius * 0.82f)
            lineTo(centerX + radius * 0.62f, centerY + radius * 0.82f)
            close()
        }

    // 白色描边：深色底图上也能看清轮廓
    val outlinePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 6f
            strokeJoin = Paint.Join.ROUND
        }
    canvas.drawPath(path, outlinePaint)

    val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
    canvas.drawPath(path, fillPaint)

    // 中心白点：指示精确位置
    val dotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }
    canvas.drawCircle(centerX, centerY - 1f, 6.5f, dotPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * 图钉尖角在图片里的纵向位置（76/80）。
 * 锚点取 1.0 会把尖角下方的 4px 留白也算进去，坐标看起来会偏北。
 */
private const val LOCATION_ICON_ANCHOR_Y = 0.95f
