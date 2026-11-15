package com.hmilyld.daytrail.ui.components.map

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.location.SystemLocationSource
import kotlinx.coroutines.launch

/**
 * 定位蓝点 + 回到当前位置按钮。
 *
 * 蓝点用高德的 `MyLocationStyle`：它内部走系统定位，
 * 会持续更新位置，且不额外耗电（与我们的采样共用同一份位置源）。
 *
 * 「回到当前位置」用 [SystemLocationSource] 单次取点：
 * 不走高德定位 SDK，因为定位 SDK 依赖 ARM 原生库，
 * 而系统定位在任何设备上都能用，作为按钮的即时反馈更可靠。
 */
@Composable
fun LocateButton(
    amap: AMap?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }

    FilledTonalIconButton(
        onClick = {
            val map = amap ?: return@FilledTonalIconButton
            if (locating) return@FilledTonalIconButton
            locating = true
            scope.launch {
                val source = SystemLocationSource(context)
                val fix = source.requestOnce(highAccuracy = true, timeoutMs = 10_000L)
                locating = false
                if (fix != null && fix.errorCode == 0) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(fix.point.latitude, fix.point.longitude),
                            LOCATE_ZOOM,
                        ),
                    )
                }
            }
        },
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.MyLocation,
            contentDescription = stringResource(R.string.map_locate_current),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 打开地图的定位蓝点。 */
fun AMap.enableMyLocationDot() {
    val style =
        MyLocationStyle()
            // 连续定位，蓝点跟随
            .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            // 蓝点外圈精度圆的颜色
            .radiusFillColor(0x222F6BFF)
            .strokeColor(0x662F6BFF)
            .strokeWidth(1f)
            .interval(3_000L)
            .showMyLocation(true)

    runCatching {
        myLocationStyle = style
        isMyLocationEnabled = true
    }
}

/** 关闭定位蓝点，释放系统定位监听。 */
fun AMap.disableMyLocationDot() {
    runCatching {
        isMyLocationEnabled = false
    }
}

/** 定位按钮的缩放级别。17 约等于能看清街区。 */
private const val LOCATE_ZOOM = 17f

/** 供需要自定义图标时使用。 */
internal val defaultLocationIcon = BitmapDescriptorFactory.HUE_AZURE
