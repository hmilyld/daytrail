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
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import com.hmilyld.daytrail.domain.TrackEditOps
import com.hmilyld.daytrail.domain.TrackVertex
import kotlin.math.hypot

/**
 * 手动微调的编辑覆盖层。
 *
 * 三件事：
 * 1. 把编辑中的折线画出来（不做抽稀，否则刚拖出来的顶点会被当共线点删掉）；
 * 2. 每个顶点放一个可拖动的手柄，拖动时实时更新折线；
 * 3. 点地图就在最近的一段线上插一个点——「在轨迹线上加一个点」。
 *
 * **只显示当前视野内的手柄**：一天可能有几百个顶点，全部画出来既看不清
 * 也点不中。视野判断放在相机变化之后，拖动过程中不重建手柄。
 *
 * 覆盖物只通过 [EditOverlayHandle] 持有并自行移除，**绝不调用 `map.clear()`**：
 * 地图实例是全局单例（见 `MapViewCache`），clear 会把别的图层一起清掉。
 */
@Composable
fun TrackEditOverlay(
    amap: AMap?,
    segments: List<List<GeoPoint>>,
    selected: TrackVertex?,
    polylineColor: Int,
    onSelect: (TrackVertex) -> Unit,
    onMove: (TrackVertex, GeoPoint) -> Unit,
    onInsert: (GeoPoint, Double) -> Unit,
    /** 进入编辑时缩放一次的键。换天或重新进入时换一个值。 */
    fitKey: Any? = null,
) {
    val density = LocalDensity.current
    val handleColor = MaterialTheme.colorScheme.outline.toArgb()
    val selectedColor = MaterialTheme.colorScheme.primary.toArgb()

    val handleIcon =
        remember(handleColor, density) { MapIcons.dot(handleColor, dpToPx(HANDLE_SIZE, density)) }
    val selectedIcon =
        remember(selectedColor, density) {
            MapIcons.dot(selectedColor, dpToPx(SELECTED_SIZE, density))
        }

    // 监听器只装一次，回调通过 rememberUpdatedState 读最新值：
    // 回调里捕获 Compose 参数会一直用第一次组合时的旧值。
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnInsert by rememberUpdatedState(onInsert)

    val holder = remember { EditOverlayHandle() }
    var fittedKey by remember { mutableStateOf<Any?>(null) }

    DisposableEffect(holder) {
        onDispose { holder.clear() }
    }

    DisposableEffect(amap) {
        val map = amap
        if (map == null) {
            onDispose { }
        } else {
            val dragListener =
                object : AMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) = Unit

                    override fun onMarkerDrag(marker: Marker) {
                        val vertex = holder.vertexOf[marker.id] ?: return
                        holder.segments =
                            TrackEditOps.move(holder.segments, vertex, marker.position.toGeoPoint())
                        holder.redrawSegment(vertex.segmentIndex)
                    }

                    override fun onMarkerDragEnd(marker: Marker) {
                        val vertex = holder.vertexOf[marker.id] ?: return
                        currentOnMove(vertex, marker.position.toGeoPoint())
                    }
                }

            val markerClickListener =
                AMap.OnMarkerClickListener { marker ->
                    val vertex = holder.vertexOf[marker.id]
                    if (vertex == null) {
                        false
                    } else {
                        currentOnSelect(vertex)
                        true
                    }
                }

            val mapClickListener =
                AMap.OnMapClickListener { latLng ->
                    currentOnInsert(
                        GeoPoint(latLng.latitude, latLng.longitude),
                        insertToleranceMeters(map, density),
                    )
                }

            // 接口有两个方法，不能用 SAM 转换，必须写成 object
            val cameraListener =
                object : AMap.OnCameraChangeListener {
                    override fun onCameraChange(position: CameraPosition) = Unit

                    override fun onCameraChangeFinish(position: CameraPosition) {
                        runCatching { holder.applyVisibility(map) }
                    }
                }

            runCatching {
                map.setOnMarkerDragListener(dragListener)
                map.setOnMarkerClickListener(markerClickListener)
                map.setOnMapClickListener(mapClickListener)
                map.setOnCameraChangeListener(cameraListener)
                // 拖动顶点时地图一转，手指离开的位置和落点就对不上了
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
            }

            onDispose {
                // 地图是全局复用的实例，不摘掉监听器的话，下一个页面点地图也会走到这里
                runCatching {
                    map.setOnMarkerDragListener(null)
                    map.setOnMarkerClickListener(null)
                    map.setOnMapClickListener(null)
                    map.setOnCameraChangeListener(null)
                    map.uiSettings.isRotateGesturesEnabled = true
                }
            }
        }
    }

    LaunchedEffect(amap, segments, selected, handleIcon, selectedIcon, polylineColor) {
        val map = amap ?: return@LaunchedEffect
        holder.clear()
        holder.segments = segments

        segments.forEach { segment ->
            if (segment.size < 2) return@forEach
            val line: Polyline =
                map.addPolyline(
                    PolylineOptions()
                        .addAll(segment.map { LatLng(it.latitude, it.longitude) })
                        .color(polylineColor)
                        .width(12f)
                        .setUseTexture(false),
                )
            holder.polylines += line
        }

        segments.forEachIndexed { segmentIndex, segment ->
            segment.forEachIndexed { vertexIndex, point ->
                val vertex = TrackVertex(segmentIndex, vertexIndex)
                val marker: Marker =
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(point.latitude, point.longitude))
                            .icon(if (selected == vertex) selectedIcon else handleIcon)
                            .anchor(0.5f, 0.5f)
                            .draggable(true)
                            .zIndex(HANDLE_Z_INDEX),
                    )
                holder.markers += marker
                holder.vertexOf[marker.id] = vertex
            }
        }

        runCatching { holder.applyVisibility(map) }
    }

    LaunchedEffect(amap, fitKey, segments.isNotEmpty()) {
        val map = amap ?: return@LaunchedEffect
        if (fitKey == null || fittedKey == fitKey) return@LaunchedEffect
        val points = segments.flatten()
        if (points.isEmpty()) return@LaunchedEffect
        fitBounds(map, points)
        fittedKey = fitKey
    }
}

/** 编辑覆盖层的覆盖物持有者。集中清理，避免残留。 */
private class EditOverlayHandle {
    val polylines = mutableListOf<Polyline>()
    val markers = mutableListOf<Marker>()

    /** 手柄 → 顶点。用 `Marker.id` 做键：拖动回调只能拿到 Marker。 */
    val vertexOf = mutableMapOf<String, TrackVertex>()

    /** 当前折线。拖动过程中在内存里推进，保存时才交给 ViewModel。 */
    var segments: List<List<GeoPoint>> = emptyList()

    /** 拖动时只更新被拖那一段，不重建整层覆盖物。 */
    fun redrawSegment(index: Int) {
        val line = polylines.getOrNull(index) ?: return
        val points = segments.getOrNull(index) ?: return
        runCatching { line.setPoints(points.map { LatLng(it.latitude, it.longitude) }) }
    }

    /** 只显示视野内的手柄。视野外的手柄仍然存在，只是不可见。 */
    fun applyVisibility(map: AMap) {
        val bounds = map.projection.visibleRegion.latLngBounds
        markers.forEach { marker ->
            val vertex = vertexOf[marker.id] ?: return@forEach
            val point = segments.getOrNull(vertex.segmentIndex)?.getOrNull(vertex.vertexIndex)
            marker.isVisible = point != null && bounds.contains(point.toLatLng())
        }
    }

    fun clear() {
        polylines.forEach { runCatching { it.remove() } }
        markers.forEach { runCatching { it.remove() } }
        polylines.clear()
        markers.clear()
        vertexOf.clear()
    }
}

/** 手柄尺寸。20dp 便于点中，选中后放大到 28dp。 */
private val HANDLE_SIZE = 20.dp
private val SELECTED_SIZE = 28.dp

/** 手柄层级：压在折线之上。 */
private const val HANDLE_Z_INDEX = 20f

/** 点线插点的容差（dp）。缩得越小越精确，换算成米时取两者较大值。 */
private val INSERT_TOLERANCE_DP = 24.dp

/** 测「一个屏幕像素对应多少米」时用的纬度探针，约 55 米。 */
private const val PROBE_DEGREES = 0.0005

/**
 * 点线插点的容差，单位米。
 *
 * 固定米数在不同缩放下体感差别很大：缩到城市级时 50 米还不到一个像素，
 * 放大到街道级时又宽得离谱。这里按当前比例尺换算，并保底 50 米
 * （见 [TrackEditOps.MATCH_RADIUS_METERS]）。
 */
private fun insertToleranceMeters(
    map: AMap,
    density: Density,
): Double {
    val pixels = with(density) { INSERT_TOLERANCE_DP.toPx().toDouble() }
    val perPixel =
        runCatching {
            val center = map.cameraPosition.target
            val origin = map.projection.toScreenLocation(center)
            val north =
                map.projection.toScreenLocation(
                    LatLng(center.latitude + PROBE_DEGREES, center.longitude),
                )
            val distance =
                hypot(
                    (north.x - origin.x).toDouble(),
                    (north.y - origin.y).toDouble(),
                )
            if (distance < 1.0) {
                0.0
            } else {
                distanceMeters(
                    GeoPoint(center.latitude, center.longitude),
                    GeoPoint(center.latitude + PROBE_DEGREES, center.longitude),
                ) / distance
            }
        }.getOrDefault(0.0)

    return maxOf(TrackEditOps.MATCH_RADIUS_METERS, perPixel * pixels)
}

private fun dpToPx(
    dp: Dp,
    density: Density,
): Int = with(density) { dp.roundToPx() }

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
