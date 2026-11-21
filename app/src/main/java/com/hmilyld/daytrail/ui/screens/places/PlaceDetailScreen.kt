package com.hmilyld.daytrail.ui.screens.places

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.domain.PlaceNeighbors
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 地点详情：把地点放回地图上。
 *
 * 列表里只有名字和次数是不够的——「这个未命名地点到底在哪」必须看一眼地图。
 * 这里还能改名和删除，列表就只负责浏览。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    placeId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlaceDetailViewModel = viewModel(factory = appViewModelFactory(container))
    val place by viewModel.place.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()

    LaunchedEffect(placeId) { viewModel.load(placeId) }

    var renaming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<PlaceMergeTarget?>(null) }
    val scope = rememberCoroutineScope()
    val mapAvailable = rememberAmapAvailable()

    val point =
        place?.place?.let { entity ->
            GeoPoint(entity.latitude, entity.longitude)
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(place?.place?.label ?: stringResource(R.string.places_unnamed))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }, enabled = place != null) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.places_rename),
                        )
                    }
                    IconButton(onClick = { removing = true }, enabled = place != null) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.places_delete),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (mapAvailable) {
                PlaceMap(point = point, modifier = Modifier.fillMaxSize())
            } else {
                MapUnavailablePlaceholder()
            }

            place?.let { summary ->
                PlaceInfoCard(
                    summary = summary,
                    suspect = targets.firstOrNull()?.takeIf { it.isSuspect() },
                    canMerge = targets.isNotEmpty(),
                    onMergeClick = { pickingTarget = true },
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                )
            }
        }
    }

    if (renaming) {
        val current = place
        PlaceNameDialog(
            title = stringResource(R.string.places_rename_title),
            initial = current?.place?.label.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = { label ->
                viewModel.rename(placeId, label)
                renaming = false
            },
        )
    }

    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.places_delete_title)) },
            text = { Text(stringResource(R.string.places_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = false
                        // 等写库完成再返回，避免弹栈取消写入
                        scope.launch {
                            viewModel.remove(placeId)
                            onBack()
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (pickingTarget) {
        AlertDialog(
            onDismissRequest = { pickingTarget = false },
            title = { Text(stringResource(R.string.places_merge_title)) },
            text = {
                if (targets.isEmpty()) {
                    Text(stringResource(R.string.places_merge_empty))
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(R.string.places_merge_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        targets.forEach { target ->
                            TextButton(
                                onClick = {
                                    pickingTarget = false
                                    pendingTarget = target
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.places_merge_target,
                                            target.displayName(),
                                            DistanceFormat.roundedMeters(target.distanceMeters),
                                        ),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingTarget = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingTarget = null },
            title = {
                Text(
                    stringResource(R.string.places_merge_confirm_title, target.displayName()),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.places_merge_confirm_body,
                        target.displayName(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTarget = null
                        // 等写库完成再返回，避免弹栈取消写入。
                        // 合并被拒（源/目标已不存在，或距离超出半径上限）时留在本页，
                        // 别让用户以为合并成功了。
                        scope.launch {
                            if (viewModel.merge(placeId, target.id)) onBack()
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 距得够近才提示「疑似同一个地方」。 */
private fun PlaceMergeTarget.isSuspect(): Boolean =
    distanceMeters <= PlaceNeighbors.SUSPECT_RADIUS_METERS

/** 候选的显示名。未命名的地点在界面上不能显示空白。 */
@Composable
private fun PlaceMergeTarget.displayName(): String =
    label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.places_unnamed)

/**
 * 只画一个标记。
 *
 * 不调用 `map.clear()`：地图实例是全局复用的，清空会把别的图层的覆盖物一起清掉。
 */
@Composable
private fun PlaceMap(
    point: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    var amap by remember { mutableStateOf<AMap?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }

    AmapView(
        modifier = modifier,
        onMapReady = { map -> amap = map },
    )

    LaunchedEffect(amap, point) {
        val map = amap ?: return@LaunchedEffect
        val target = point ?: return@LaunchedEffect
        val position = LatLng(target.latitude, target.longitude)

        marker?.remove()
        marker = map.addMarker(MarkerOptions().position(position))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, PLACE_ZOOM))
    }

    DisposableEffect(Unit) {
        onDispose { marker?.remove() }
    }
}

@Composable
private fun PlaceInfoCard(
    summary: com.hmilyld.daytrail.data.db.dao.PlaceSummary,
    suspect: PlaceMergeTarget?,
    canMerge: Boolean,
    onMergeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val lastSeen = Instant.ofEpochMilli(summary.place.lastSeenMs).atZone(zone).toLocalDate()
    val text =
        if (summary.visitDays > 0) {
            stringResource(R.string.places_visit_days, summary.visitDays) +
                " · " +
                stringResource(R.string.places_last_seen, lastSeen.monthValue, lastSeen.dayOfMonth)
        } else {
            stringResource(R.string.places_never_visited)
        }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
            if (suspect != null) {
                Text(
                    text =
                        stringResource(
                            R.string.places_merge_hint,
                            suspect.displayName(),
                            stringResource(
                                R.string.places_merge_distance,
                                DistanceFormat.roundedMeters(suspect.distanceMeters),
                            ),
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (canMerge) {
                TextButton(
                    onClick = onMergeClick,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.places_merge))
                }
            }
        }
    }
}

private const val PLACE_ZOOM = 16f
