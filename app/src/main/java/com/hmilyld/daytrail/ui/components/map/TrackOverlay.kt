package com.hmilyld.daytrail.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    points: List<GeoPoint>,
    stays: List<MapStayMarker>,
    polylineColor: Int,
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

    LaunchedEffect(currentAmap, points, stays) {
        val map = currentAmap ?: return@LaunchedEffect
        overlay.clear()

        if (points.isEmpty()) return@LaunchedEffect

        // 按断点分段：超过 2km 的跳跃说明数据缺失，不能连成直线
        val segments = TrackSimplifier.splitByGaps(points)
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
                    .position(points.first().toLatLng())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .anchor(0.5f, 0.5f),
            )

        // 终点
        overlay.markers +=
            map.addMarker(
                MarkerOptions()
                    .position(points.last().toLatLng())
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

        // 缩放到轨迹范围
        fitBounds(map, points)
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
