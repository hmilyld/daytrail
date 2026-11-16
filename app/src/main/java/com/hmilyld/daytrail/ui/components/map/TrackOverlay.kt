package com.hmilyld.daytrail.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.domain.TrackSimplifier

/** 地图上要画的停留点。 */
data class MapStayMarker(
    val point: GeoPoint,
    val label: String?,
    val durationMinutes: Long,
)

/**
 * 轨迹覆盖层。
 *
 * 负责把一天的轨迹画到地图上：
 * - 折线按断点分段（断点两侧不连直线）
 * - 起点绿、终点蓝、停留点橙
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
     * 分段必须由 ViewModel 用 `TrackSegmenter` 计算（判据是时间 + 位移），
     * 不能在地图层用「距离 > 2km」简单判断——省电档采样间隔是分钟级，
     * 正常驾驶时相邻点相距 2–5 公里很常见，按距离切会把完整行程切成十几截。
     */
    segments: List<List<GeoPoint>>,
    stays: List<MapStayMarker>,
    polylineColor: Int,
    /**
     * 是否自动缩放镜头以看全轨迹。
     *
     * 今日页必须传 false：录制中轨迹每 60 秒更新一次，
     * 若每次都缩放镜头，用户拖动地图会被反复拽回，体验很糟。
     * 单日详情传 true，并配合 [fitKey] 保证每天只缩一次。
     */
    autoFit: Boolean = true,
    /** 自动缩放的去重键。换天时传新的日期键才会重新缩放。 */
    fitKey: Any? = null,
) {
    val currentAmap by rememberUpdatedState(amap)

    // 覆盖物持有者：每次重建时统一清理，避免残留
    val overlay =
        remember {
            OverlayHandle()
        }

    DisposableEffect(overlay) {
        onDispose { overlay.clear() }
    }

    // 已经缩放过的键。避免轨迹更新时反复抢镜头。
    var fittedKey by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(currentAmap, segments, stays) {
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

        // 起点
        overlay.markers +=
            map.addMarker(
                MarkerOptions()
                    .position(allPoints.first().toLatLng())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .anchor(0.5f, 0.5f),
            )

        // 终点
        overlay.markers +=
            map.addMarker(
                MarkerOptions()
                    .position(allPoints.last().toLatLng())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    .anchor(0.5f, 0.5f),
            )

        // 停留点
        stays.forEach { stay ->
            overlay.markers +=
                map.addMarker(
                    MarkerOptions()
                        .position(stay.point.toLatLng())
                        .icon(
                            BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_ORANGE,
                            ),
                        ).title(stay.label ?: "停留 ${stay.durationMinutes} 分钟")
                        .anchor(0.5f, 0.5f),
                )
        }

        // 缩放到轨迹范围。
        // 只在一个「键」第一次出现时缩一次：今日页传 autoFit=false 完全不缩，
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

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

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
