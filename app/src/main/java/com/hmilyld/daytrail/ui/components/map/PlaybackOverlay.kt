package com.hmilyld.daytrail.ui.components.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.domain.PlaybackFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 回放指针的覆盖层：只管理自己的那一个 Marker，绝不 `map.clear()`（地图是全局单例）。 */
@Composable
fun PlaybackOverlay(
    amap: AMap?,
    /** 由界面层写的当前帧。按 State 读，不让每帧重组重建整层覆盖物。 */
    frame: State<PlaybackFrame?>,
    active: Boolean,
    follow: Boolean,
    onMapDragged: () -> Unit,
) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.primary.toArgb()
    // 比采样点（4dp）和起终点（13dp）都大，一眼能认出「这是回放指针」。
    val icon = remember(color, density) { MapIcons.dot(color, dpToPx(20.dp, density)) }
    val currentFollow by rememberUpdatedState(follow)
    val currentOnDragged by rememberUpdatedState(onMapDragged)
    val suppressUntil = remember { mutableLongStateOf(0L) }
    val programmaticCamera = remember { mutableStateOf(false) }
    var marker by remember { mutableStateOf<Marker?>(null) }

    // 回放刚进入时 TrackOverlay 会因为 fitKey 变化做一次 autoFit，
    // 那次程序 moveCamera 不能算「用户拖地图」，否则跟随第一帧就被关掉。
    DisposableEffect(amap, active) {
        val map = amap
        if (map != null && active) {
            suppressUntil.longValue = System.currentTimeMillis() + STARTUP_SUPPRESS_MS
            programmaticCamera.value = false
            val listener =
                object : AMap.OnCameraChangeListener {
                    override fun onCameraChange(position: CameraPosition) {
                        if (programmaticCamera.value) return
                        if (System.currentTimeMillis() < suppressUntil.longValue) return
                        currentOnDragged()
                    }

                    override fun onCameraChangeFinish(position: CameraPosition) {
                        // 程序触发的 moveCamera 到这里才算结束；用户手势的 finish
                        // 已经在 onCameraChange 里报过一次，不再重复。
                        if (programmaticCamera.value) programmaticCamera.value = false
                    }
                }
            runCatching { map.setOnCameraChangeListener(listener) }
            onDispose { runCatching { map.setOnCameraChangeListener(null) } }
        } else {
            onDispose {}
        }
    }

    // 用户重新打开跟随时，立刻把镜头拉回指针，不等下一个 5Hz tick。
    LaunchedEffect(amap, active, follow) {
        if (!active || !follow) return@LaunchedEffect
        val map = amap ?: return@LaunchedEffect
        val current = frame.value ?: return@LaunchedEffect
        followCamera(map, current.point, programmaticCamera, suppressUntil)
    }

    LaunchedEffect(amap, active, icon) {
        val map = amap ?: return@LaunchedEffect
        if (!active) {
            marker?.remove()
            marker = null
            return@LaunchedEffect
        }

        var tick = 0
        while (isActive) {
            val current = frame.value
            if (current != null) {
                val position = LatLng(current.point.latitude, current.point.longitude)
                if (marker == null) {
                    marker =
                        map.addMarker(
                            MarkerOptions().position(position).icon(icon).anchor(0.5f, 0.5f),
                        )
                    // 第一帧：打开回放就自动把镜头切到指针位置。
                    followCamera(map, current.point, programmaticCamera, suppressUntil)
                } else {
                    marker?.position = position
                }

                // 相机按 5Hz 降频；每帧 animateCamera 会排队，明显掉帧。
                if (currentFollow && tick > 0 && tick % CAMERA_EVERY_TICKS == 0) {
                    followCamera(map, current.point, programmaticCamera, suppressUntil)
                }
            }
            tick++
            delay(FRAME_DELAY_MS)
        }
    }

    DisposableEffect(Unit) {
        onDispose { marker?.remove() }
    }
}

private fun followCamera(
    map: AMap,
    point: GeoPoint,
    programmatic: MutableState<Boolean>,
    suppressUntil: MutableState<Long>,
) {
    programmatic.value = true
    suppressUntil.value = System.currentTimeMillis() + CAMERA_SUPPRESS_MS
    map.moveCamera(
        CameraUpdateFactory.newLatLng(LatLng(point.latitude, point.longitude)),
    )
}

private const val STARTUP_SUPPRESS_MS = 800L
private const val FRAME_DELAY_MS = 16L
private const val CAMERA_EVERY_TICKS = 12
private const val CAMERA_SUPPRESS_MS = 250L

private fun dpToPx(
    dp: Dp,
    density: Density,
): Int = with(density) { dp.roundToPx() }
