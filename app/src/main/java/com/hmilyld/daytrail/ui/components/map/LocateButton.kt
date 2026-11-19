package com.hmilyld.daytrail.ui.components.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.location.SystemLocationSource
import com.hmilyld.daytrail.location.toGcj02
import kotlinx.coroutines.launch

/**
 * 回到当前位置按钮。
 *
 * 用系统定位而不是高德 SDK：系统定位在任何架构上都可用，
 * 作为按钮的即时反馈更可靠。取到坐标后转换到 GCJ-02 再移动镜头。
 */
@Composable
fun LocateButton(
    amap: AMap?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }

    MapControlButton(
        icon = Icons.Outlined.MyLocation,
        contentDescription = stringResource(R.string.map_locate_current),
        onClick = {
            val map = amap ?: return@MapControlButton
            if (locating) return@MapControlButton
            locating = true
            scope.launch {
                val source = SystemLocationSource(context)
                val fix = source.requestOnce(highAccuracy = true, timeoutMs = 10_000L)
                locating = false
                if (fix != null && fix.errorCode == 0) {
                    val point = fix.toGcj02()
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(point.latitude, point.longitude),
                            LOCATE_ZOOM,
                        ),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

/** 定位按钮的缩放级别。17 约等于能看清街区。 */
private const val LOCATE_ZOOM = 17f
