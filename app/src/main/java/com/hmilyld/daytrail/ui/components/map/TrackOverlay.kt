package com.hmilyld.daytrail.ui.components.map

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.domain.TrackSimplifier

/**
 * 轨迹覆盖层。
 *
 * 负责把一天的轨迹画到地图上：
 * - 折线按断点分段（断点两侧不连直线）
 * - 起点绿、终点蓝、停留琥珀，都是圆点而不是水滴图钉
 * - 可选画出每个采样点，让「每隔几分钟一个点」这件事一眼可见
 * - 自动缩放到轨迹范围
 *
 * 高德地图是 View 体系，覆盖物只能通过 `AMap.addPolyline` / `addMarker` 添加，
 * 这里用一个 Composable 管理这些覆盖物的生命周期。
 */
@Composable
fun TrackOverlay(
    amap: AMap?,
    /**
     * 已按「数据缺失」切好的段。
     *
     * 分段必须由 ViewModel 用 `TrackDisplay` 计算（精度筛 → 毛刺修复 → 切段），
     * 不能在地图层用「距离 > 2km」简单判断——省电档采样间隔是分钟级，
     * 正常驾驶时相邻点相距 2–5 公里很常见，按距离切会把完整行程切成十几截。
     */
    segments: List<List<GeoPoint>>,
    /** 停留点。只放足够长的停留，避免满屏图钉。 */
    stays: List<GeoPoint>,
    polylineColor: Int,
    /**
     * 是否自动缩放镜头以看全轨迹。
     *
     * 录制中必须传 false：轨迹每分钟更新一次，每次自动缩放会把用户
     * 拖动的地图反复拽回。录制结束后再缩一次。
     */
    autoFit: Boolean = true,
    /** 自动缩放的去重键。换天或点数变化时才重新缩放。 */
    fitKey: Any? = null,
    /** 是否画出每个采样点。单日详情打开，今日页关闭（录制中会频繁重建）。 */
    sampleDots: Boolean = false,
) {
    val currentAmap by rememberUpdatedState(amap)
    val density = LocalDensity.current

    val startColor = MaterialTheme.colorScheme.secondary.toArgb()
    val endColor = MaterialTheme.colorScheme.primary.toArgb()
    val stayColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val sampleColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val startIcon =
        remember(startColor, density) { MapIcons.dot(startColor, dpToPx(13.dp, density)) }
    val endIcon = remember(endColor, density) { MapIcons.dot(endColor, dpToPx(13.dp, density)) }
    val stayIcon = remember(stayColor, density) { MapIcons.dot(stayColor, dpToPx(11.dp, density)) }
    val sampleIcon =
        remember(sampleColor, density) {
            MapIcons.dot(sampleColor, dpToPx(4.dp, density))
        }

    // 覆盖物持有者：每次重建时统一清理，避免残留
    val overlay = remember { OverlayHandle() }

    DisposableEffect(overlay) {
        onDispose { overlay.clear() }
    }

    // 已经缩放过的键。避免轨迹更新时反复抢镜头。
    var fittedKey by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(
        currentAmap,
        segments,
        stays,
        startIcon,
        endIcon,
        stayIcon,
        sampleIcon,
        sampleDots,
    ) {
        val map = currentAmap ?: return@LaunchedEffect
        overlay.clear()

        val allPoints = segments.flatten()
        if (allPoints.isEmpty()) return@LaunchedEffect

        segments.forEach { segment ->
            if (segment.size < 2) return@forEach
            val simplified = TrackSimplifier.simplify(segment)
            val line: Polyline =
                map.addPolyline(
                    PolylineOptions()
                        .addAll(simplified.map { LatLng(it.latitude, it.longitude) })
                        .color(polylineColor)
                        .width(12f)
                        .setUseTexture(false),
                )
            overlay.polylines += line
        }

        // 采样点：只画「线为什么是这个形状」这件事的答案。
        // 点太多就跳过——给一整天的 400+ 个点逐个建 Marker 会明显卡顿。
        if (sampleDots && allPoints.size <= MAX_SAMPLE_DOTS) {
            segments.forEach { segment ->
                segment.forEach { point ->
                    overlay.markers +=
                        map.addMarker(
                            MarkerOptions()
                                .position(point.toLatLng())
                                .icon(sampleIcon)
                                .anchor(0.5f, 0.5f)
                                .zIndex(SAMPLE_DOT_Z_INDEX),
                        )
                }
            }
        }

        // 起点
        overlay.markers +=
            map.addMarker(
                MarkerOptions()
                    .position(allPoints.first().toLatLng())
                    .icon(startIcon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(POINT_Z_INDEX),
            )

        // 终点
        overlay.markers +=
            map.addMarker(
                MarkerOptions()
                    .position(allPoints.last().toLatLng())
                    .icon(endIcon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(POINT_Z_INDEX),
            )

        // 停留点
        stays.forEach { stay ->
            overlay.markers +=
                map.addMarker(
                    MarkerOptions()
                        .position(stay.toLatLng())
                        .icon(stayIcon)
                        .anchor(0.5f, 0.5f)
                        .zIndex(POINT_Z_INDEX),
                )
        }

        // 缩放到轨迹范围。
        // 只在一个「键」第一次出现时缩一次：录制中传 autoFit=false 完全不缩，
        // 单日详情按日期键缩一次，之后用户拖动地图不会被拽回。
        if (autoFit && fittedKey != fitKey) {
            fitBounds(map, allPoints)
            fittedKey = fitKey
        }
    }
}

/** 覆盖物持有者。集中清理，避免忘记移除导致重复绘制。 */
private class OverlayHandle {
    val polylines = mutableListOf<Polyline>()
    val markers = mutableListOf<Marker>()

    fun clear() {
        polylines.forEach { runCatching { it.remove() } }
        markers.forEach { runCatching { it.remove() } }
        polylines.clear()
        markers.clear()
    }
}

private fun dpToPx(
    dp: Dp,
    density: Density,
): Int = with(density) { dp.roundToPx() }

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

/** 采样点数量上限。超过就不再画点，只画线。 */
private const val MAX_SAMPLE_DOTS = 300

/** 采样点的层级，压在起点/终点/停留之下。 */
private const val SAMPLE_DOT_Z_INDEX = 1f

/** 起点/终点/停留的层级。 */
private const val POINT_Z_INDEX = 10f

/** 把镜头缩放到能看全整条轨迹。单个点或极近的点用固定缩放级别。 */
private fun fitBounds(
    map: AMap,
    points: List<GeoPoint>,
) {
    if (points.isEmpty()) return

    if (points.size == 1) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first().toLatLng(), 15f))
        return
    }

    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(it.toLatLng()) }

    runCatching {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80))
    }.onFailure {
        // 所有点几乎重合时 newLatLngBounds 会失败，退化为固定缩放
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first().toLatLng(), 15f))
    }
}
