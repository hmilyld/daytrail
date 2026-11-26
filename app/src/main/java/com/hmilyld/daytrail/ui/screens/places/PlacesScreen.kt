package com.hmilyld.daytrail.ui.screens.places

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.export.ExportFormat
import com.hmilyld.daytrail.data.export.PlaceExportResult
import com.hmilyld.daytrail.data.geocode.NamingFailure
import com.hmilyld.daytrail.domain.PlaceNeighbors
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.EmptyState
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 地点列表。
 *
 * 只展示「名字 + 去过多久 + 最近一次」，不再显示坐标——坐标对用户没有意义，
 * 想知道在哪就点进去看地图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    container: AppContainer,
    onOpenPlace: (Long) -> Unit,
    onAddPlace: () -> Unit,
    modifier: Modifier = Modifier,
    /** 一级入口进入时为 null：没有「上一页」可回。 */
    onBack: (() -> Unit)? = null,
) {
    val viewModel: PlacesViewModel = viewModel(factory = appViewModelFactory(container))
    val places by viewModel.places.collectAsStateWithLifecycle()
    val naming by viewModel.naming.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()

    val unnamedCount = places.count { it.place.label.isNullOrBlank() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showOverflow by rememberSaveable { mutableStateOf(false) }
    val exportSuccess = stringResource(R.string.places_export_success)
    val exportEmpty = stringResource(R.string.places_export_empty)
    val exportFailed = stringResource(R.string.places_export_failed)

    fun runExport(
        uri: Uri,
        format: ExportFormat,
    ) {
        scope.launch {
            val message =
                when (val result = container.placeExportManager.export(uri, format)) {
                    is PlaceExportResult.Success ->
                        String.format(exportSuccess, result.placeCount)
                    PlaceExportResult.Empty -> exportEmpty
                    is PlaceExportResult.Failure -> String.format(exportFailed, result.message)
                }
            snackbar.showSnackbar(message)
        }
    }

    // 两种格式各一个 launcher：CreateDocument 的 MIME 类型在创建时固定
    val gpxLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gpx+xml"),
        ) { uri -> uri?.let { runExport(it, ExportFormat.GPX) } }
    val geojsonLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/geo+json"),
        ) { uri -> uri?.let { runExport(it, ExportFormat.GEOJSON) } }

    val namingLabel =
        when {
            naming -> R.string.places_autoname_running
            !viewModel.canBackfill -> R.string.places_autoname_no_key
            else -> R.string.places_autoname
        }

    // 相距 400 米内的地点互相标「疑似重复」：同一个小区里两次停车能差两三百米，
    // 归并半径只有 120 米，于是会建出两个地点。合不合并由用户点进详情决定。
    val suspects =
        remember(places) {
            PlaceNeighbors.suspects(
                places.map { summary ->
                    PlaceNeighbors.Entry(
                        id = summary.place.id,
                        point = GeoPoint(summary.place.latitude, summary.place.longitude),
                        radiusMeters = summary.place.radiusMeters,
                    )
                },
            )
        }

    // 文案必须在组合期用 stringResource 取：用 LocalContext.getString 拿到的是
    // 配置变更前的旧值（lint: LocalContextGetResourceValueCall）。
    val result = outcome
    val message =
        when {
            result == null -> null
            result.named > 0 -> stringResource(R.string.places_autoname_done, result.named)
            result.failure != null -> stringResource(failureMessage(result.failure))
            else -> stringResource(R.string.places_autoname_failed)
        }

    // 补全结果只展示一次，展示完就清掉，避免旋转屏幕后又弹一遍
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeOutcome()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    // 只在还有未命名地点时出现：没有可补的就别占位
                    if (unnamedCount > 0) {
                        TextButton(
                            onClick = { viewModel.backfillNames() },
                            enabled = !naming && viewModel.canBackfill,
                        ) {
                            Text(text = stringResource(namingLabel))
                        }
                    }
                    // 有「补全名称」按钮时把导出收进溢出菜单，窄屏不会挤成两行。
                    Box {
                        IconButton(
                            onClick = {
                                if (unnamedCount >
                                    0
                                ) {
                                    showOverflow = true
                                } else {
                                    showExportDialog = true
                                }
                            },
                            enabled = places.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector =
                                    if (unnamedCount > 0) {
                                        Icons.Outlined.MoreVert
                                    } else {
                                        Icons.Outlined.FileDownload
                                    },
                                contentDescription = stringResource(R.string.places_export),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.places_export)) },
                                onClick = {
                                    showOverflow = false
                                    showExportDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPlace,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.places_add)) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = if (places.isEmpty()) Alignment.Center else Alignment.TopCenter,
        ) {
            if (places.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Place,
                    title = stringResource(R.string.places_empty_title),
                    body = stringResource(R.string.places_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            // 底部留出 FAB 的高度，否则最后一个地点会被按钮压住
                            bottom = 96.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.widthIn(max = 840.dp),
                ) {
                    // 没配 Web Key 时按钮是灰的，必须在页面上看得见原因，
                    // 否则用户只会以为「补全名称」坏了。文案与「补全路线」的 Key 检查对齐。
                    if (unnamedCount > 0 && !viewModel.canBackfill) {
                        item(key = "naming_no_key_hint") {
                            Card(
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.places_autoname_no_key_msg),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(TrailSpacing.lg),
                                )
                            }
                        }
                    }
                    items(places, key = { it.place.id }) { summary ->
                        PlaceCard(
                            summary = summary,
                            suspect = summary.place.id in suspects,
                            onClick = { onOpenPlace(summary.place.id) },
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.places_export_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                val name =
                                    container.placeExportManager.suggestedName(ExportFormat.GPX)
                                gpxLauncher.launch(name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.places_export_gpx),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                val name =
                                    container.placeExportManager.suggestedName(
                                        ExportFormat.GEOJSON,
                                    )
                                geojsonLauncher.launch(name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.places_export_geojson),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaceCard(
    summary: PlaceSummary,
    suspect: Boolean,
    onClick: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val lastSeen = Instant.ofEpochMilli(summary.place.lastSeenMs).atZone(zone).toLocalDate()
    val supporting =
        if (summary.visitDays > 0) {
            stringResource(R.string.places_visit_days, summary.visitDays) +
                " · " +
                stringResource(R.string.places_last_seen, lastSeen.monthValue, lastSeen.dayOfMonth)
        } else {
            stringResource(R.string.places_never_visited)
        }

    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TrailSpacing.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
            ) {
                Text(
                    text = summary.place.label ?: stringResource(R.string.places_unnamed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (suspect) {
                    // 只做提示，不自动合并：相距 400 米也可能是两家相邻的店
                    Text(
                        text = stringResource(R.string.places_suspect),
                        // 不能用 tertiary 琥珀色：它在浅色背景上只有 2.0:1，
                        // 作为文字远低于 WCAG AA。这是提示而非告警，用中性色即可。
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 失败原因 → 文案资源。中文只放在 strings.xml，Compose 侧只认资源 id。 */
@StringRes
private fun failureMessage(failure: NamingFailure): Int =
    when (failure) {
        NamingFailure.NO_KEY -> R.string.places_autoname_no_key_msg
        NamingFailure.API_REJECTED -> R.string.places_autoname_key_rejected
        NamingFailure.NETWORK -> R.string.places_autoname_network_failed
        NamingFailure.INVALID_POINT -> R.string.places_autoname_invalid_point
        NamingFailure.NO_NAME -> R.string.places_autoname_no_name_found
    }
