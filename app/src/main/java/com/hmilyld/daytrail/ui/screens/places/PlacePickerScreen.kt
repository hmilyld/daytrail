package com.hmilyld.daytrail.ui.screens.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import kotlinx.coroutines.launch

/**
 * 在地图上手动新增一个地点。
 *
 * 交互是「点哪算哪」：地图上轻点一下，标记跟过去，底部按钮确认。
 * 初始位置优先用最近一次定位——比让用户从全国地图里找自己友好得多。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlacesViewModel = viewModel(factory = appViewModelFactory(container))
    val lastFix by container.recordingState.lastFix.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val mapAvailable = rememberAmapAvailable()

    var amap by remember { mutableStateOf<AMap?>(null) }
    var picked by remember { mutableStateOf<GeoPoint?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var naming by remember { mutableStateOf(false) }
    var centered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 地图是全局复用的实例，监听器必须在离开时摘掉，否则下一个页面点地图也会走到这里
    DisposableEffect(amap) {
        val map = amap
        val listener =
            AMap.OnMapClickListener { latLng ->
                picked = GeoPoint(latLng.latitude, latLng.longitude)
            }
        map?.setOnMapClickListener(listener)
        onDispose { map?.setOnMapClickListener(null) }
    }

    // 首次进入：当前位置 → 已有的第一个地点 → 地图当前中心。
    // 只做一次，之后用户拖动地图不会被拽回来。
    LaunchedEffect(amap, lastFix, places) {
        val map = amap ?: return@LaunchedEffect
        if (centered) return@LaunchedEffect

        val target =
            lastFix?.let { LatLng(it.latitude, it.longitude) }
                ?: places.firstOrNull()?.place?.let { LatLng(it.latitude, it.longitude) }
                ?: map.cameraPosition.target

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, PICKER_ZOOM))
        picked = GeoPoint(target.latitude, target.longitude)
        centered = true
    }

    LaunchedEffect(amap, picked) {
        val map = amap ?: return@LaunchedEffect
        val target = picked ?: return@LaunchedEffect
        val position = LatLng(target.latitude, target.longitude)
        marker?.remove()
        marker = map.addMarker(MarkerOptions().position(position))
    }

    DisposableEffect(Unit) {
        onDispose { marker?.remove() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_add)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.places_add_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { naming = true },
                        enabled = mapAvailable && picked != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.places_add_confirm))
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (mapAvailable) {
                AmapView(
                    modifier = Modifier.fillMaxSize(),
                    onMapReady = { map -> amap = map },
                )
            } else {
                MapUnavailablePlaceholder()
            }
        }
    }

    if (naming) {
        val point = picked
        PlaceNameDialog(
            title = stringResource(R.string.places_name_title),
            initial = "",
            onDismiss = { naming = false },
            onConfirm = { label ->
                naming = false
                val target = point
                if (target != null) {
                    // 等写库完成再返回：直接 onBack 会清掉 ViewModel，写入可能被取消
                    scope.launch {
                        viewModel.add(target, label)
                        onBack()
                    }
                }
            },
        )
    }
}

private const val PICKER_ZOOM = 16f
