package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps.AMap
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.domain.TrackVertex
import com.hmilyld.daytrail.ui.UiText
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.EmptyState
import com.hmilyld.daytrail.ui.components.MapAccessibilityOverlay
import com.hmilyld.daytrail.ui.components.Metric
import com.hmilyld.daytrail.ui.components.MetricRow
import com.hmilyld.daytrail.ui.components.TimelineRow
import com.hmilyld.daytrail.ui.components.TimelineTone
import com.hmilyld.daytrail.ui.components.immersiveSafePadding
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapControlButton
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.TrackEditOverlay
import com.hmilyld.daytrail.ui.components.map.TrackOverlay
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import com.hmilyld.daytrail.ui.resolve
import com.hmilyld.daytrail.ui.resolveJoined
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 单日详情：地图 + 时间轴。
 *
 * 时间轴有四类节点：出发 / 停留 / 数据缺失 / 结束。
 * 「数据缺失」必须显式画出——高铁过隧道会频繁出现，
 * 不标出来用户会以为轨迹画错了。
 *
 * 窄屏上下分区（地图 60%），600dp 以上左右分栏；
 * 全屏查看时只留地图，标题栏与时间轴一起让开。
 *
 * 手动微调是**第三种全屏**：进入编辑后地图铺满、底部换成编辑工具条。
 * 它改的只是画出来的线，里程时长与导出都不变（见 `TrackDisplay` 的 edit 分支）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    dateKey: String,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DayDetailViewModel = viewModel(factory = appViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()

    LaunchedEffect(dateKey) { viewModel.load(dateKey) }

    DayDetailContent(
        state = state,
        editState = editState,
        onBack = onBack,
        onCorrectRoute = viewModel::correctRoute,
        onRestoreRoute = viewModel::restoreRoute,
        onBeginEdit = viewModel::beginEdit,
        onRestoreEdit = viewModel::restoreEdit,
        onSelectVertex = viewModel::selectVertex,
        onMoveVertex = viewModel::moveVertex,
        onInsertPoint = viewModel::insertPoint,
        onUndoEdit = viewModel::undo,
        onDeleteVertex = viewModel::deleteSelected,
        onCancelEdit = viewModel::cancelEdit,
        onSaveEdit = viewModel::saveEdit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailContent(
    state: DayDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    editState: TrackEditUiState = TrackEditUiState(),
    onCorrectRoute: () -> Unit = {},
    onRestoreRoute: () -> Unit = {},
    onBeginEdit: () -> Unit = {},
    onRestoreEdit: () -> Unit = {},
    onSelectVertex: (TrackVertex) -> Unit = {},
    onMoveVertex: (TrackVertex, GeoPoint) -> Unit = { _, _ -> },
    onInsertPoint: (GeoPoint, Double) -> Unit = { _, _ -> },
    onUndoEdit: () -> Unit = {},
    onDeleteVertex: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onSaveEdit: () -> Unit = {},
) {
    val mapAvailable = rememberAmapAvailable()
    var amap by remember { mutableStateOf<AMap?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val polylineColor = MaterialTheme.colorScheme.primary.toArgb()

    val editing = editState.active
    // 编辑永远是全屏的：顶点在 60% 高的地图卡里根本点不中
    val fullscreen = expanded || editing

    // 返回键的优先级：先问「放弃修改」，再退全屏，最后才退出页面。
    BackHandler(enabled = fullscreen) {
        if (editing) confirmDiscard = true else expanded = false
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onCancelEdit()
                    },
                ) {
                    Text(stringResource(R.string.edit_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.edit_keep_editing))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 全屏时不给内容加安全区：地图要铺到状态栏与手势条下面，
        // 浮层用 immersiveSafePadding 自己避让。
        contentWindowInsets =
            if (fullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.title.ifBlank { state.dateKey },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (state.hasTrack) {
                                val stops =
                                    stringResource(R.string.day_detail_stops, state.stopCount)
                                Text(
                                    text =
                                        listOfNotNull(
                                            state.timeRange.takeIf { it.isNotBlank() },
                                            stops,
                                        ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val wideLayout = maxWidth >= TrailSpacing.wideBreakpoint

            when {
                editing ->
                    // 工具条排在布局流里（地图下方），不叠在地图上：
                    // 整屏宽的浮层会盖住高德左下角的 logo 与版权，
                    // SDK 条款不允许遮挡（见 MapAccessibilityOverlay 的同一条注释）。
                    Column(modifier = Modifier.fillMaxSize()) {
                        TrackMapPane(
                            state = state,
                            amap = amap,
                            onAmapReady = { map -> amap = map },
                            polylineColor = polylineColor,
                            mapAvailable = mapAvailable,
                            expanded = true,
                            onToggleFullscreen = {},
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            editState = editState,
                            onSelectVertex = onSelectVertex,
                            onMoveVertex = onMoveVertex,
                            onInsertPoint = onInsertPoint,
                        )
                        TrackEditBar(
                            state = editState,
                            onUndo = onUndoEdit,
                            onDelete = onDeleteVertex,
                            onCancel = { confirmDiscard = true },
                            onSave = onSaveEdit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                wideLayout && !expanded ->
                    Row(modifier = Modifier.fillMaxSize()) {
                        TrackMapPane(
                            state = state,
                            amap = amap,
                            onAmapReady = { map -> amap = map },
                            polylineColor = polylineColor,
                            mapAvailable = mapAvailable,
                            expanded = false,
                            onToggleFullscreen = { expanded = true },
                            modifier =
                                Modifier
                                    .weight(MAP_WEIGHT)
                                    .fillMaxHeight(),
                        )
                        VerticalDivider()
                        TimelinePane(
                            state = state,
                            canEditTrack = mapAvailable && state.canEditTrack,
                            onCorrectRoute = onCorrectRoute,
                            onRestoreRoute = onRestoreRoute,
                            onBeginEdit = onBeginEdit,
                            onRestoreEdit = onRestoreEdit,
                            modifier =
                                Modifier
                                    .weight(1f - MAP_WEIGHT)
                                    .fillMaxHeight(),
                        )
                    }

                else ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        TrackMapPane(
                            state = state,
                            amap = amap,
                            onAmapReady = { map -> amap = map },
                            polylineColor = polylineColor,
                            mapAvailable = mapAvailable,
                            expanded = expanded,
                            onToggleFullscreen = { expanded = !expanded },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (expanded) {
                                            Modifier.weight(1f)
                                        } else {
                                            Modifier.weight(MAP_WEIGHT)
                                        },
                                    ),
                        )
                        if (!expanded) {
                            TimelinePane(
                                state = state,
                                canEditTrack = mapAvailable && state.canEditTrack,
                                onCorrectRoute = onCorrectRoute,
                                onRestoreRoute = onRestoreRoute,
                                onBeginEdit = onBeginEdit,
                                onRestoreEdit = onRestoreEdit,
                                modifier =
                                    Modifier
                                        .weight(1f - MAP_WEIGHT)
                                        .fillMaxWidth(),
                            )
                        }
                    }
            }
        }
    }
}

/** 地图区。全屏时铺满整个内容区；编辑态换成可拖动的顶点。 */
@Composable
private fun TrackMapPane(
    state: DayDetailUiState,
    amap: AMap?,
    onAmapReady: (AMap) -> Unit,
    polylineColor: Int,
    mapAvailable: Boolean,
    expanded: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    editState: TrackEditUiState = TrackEditUiState(),
    onSelectVertex: (TrackVertex) -> Unit = {},
    onMoveVertex: (TrackVertex, GeoPoint) -> Unit = { _, _ -> },
    onInsertPoint: (GeoPoint, Double) -> Unit = { _, _ -> },
) {
    val editing = editState.active

    Box(modifier = modifier) {
        if (mapAvailable) {
            AmapView(
                modifier = Modifier.fillMaxSize(),
                onMapCreated = onAmapReady,
            )
            if (editing) {
                // 编辑态没有停留/起终点标记，也没有采样点：
                // 手臂和别的标记叠在一起时点不中。
                TrackEditOverlay(
                    amap = amap,
                    segments = editState.segments,
                    selected = editState.selected,
                    polylineColor = polylineColor,
                    onSelect = onSelectVertex,
                    onMove = onMoveVertex,
                    onInsert = onInsertPoint,
                    fitKey = state.dateKey,
                )
            } else {
                TrackOverlay(
                    amap = amap,
                    segments = state.trackSegments,
                    stays = state.stayMarkers,
                    stayCentroids = state.stayCentroids,
                    polylineColor = polylineColor,
                    // 按日期缩一次；进入全屏再缩一次看全整条轨迹。
                    fitKey = if (expanded) "${state.dateKey}-full" else state.dateKey,
                    // 纠偏后的折线是**路网顶点**，不是采样点，画出来会误导。
                    // 手动微调过的线同理：原始采样点已经不在这条线上了。
                    sampleDots = state.routeOverride == null && !state.hasManualEdit,
                    // 用户画的线不能再被抽稀，否则刚拖出来的顶点会被删掉。
                    simplify = !state.hasManualEdit,
                )
            }
            // 编辑态底部是工具条，无障碍浮层会和它叠在一起；
            // 编辑说明本身由工具条承担。
            if (!editing) {
                MapAccessibilityOverlay(
                    text = state.accessibilitySummary.resolveJoined(R.string.list_separator),
                    modifier =
                        Modifier
                            // 高德 logo 与版权固定在左下角，浮层压上去会违反
                            // SDK 条款（不得遮挡 logo）。右下角在本项目未启用
                            // 比例尺/缩放控件，不会与 SDK 元素重叠。
                            .align(Alignment.BottomEnd)
                            .immersiveSafePadding(expanded)
                            .padding(TrailSpacing.md),
                )
            }

            // 全屏时标题栏被隐藏，把日期压在图上，否则不知道在看哪一天。
            // 编辑态同样铺满，所以复用这个角标。
            if (expanded || editing) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .immersiveSafePadding(expanded || editing)
                            .padding(TrailSpacing.md),
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = TrailSpacing.md, vertical = 6.dp),
                    )
                }
            }
        } else {
            MapUnavailablePlaceholder(modifier = Modifier.fillMaxSize())
        }

        // 编辑态已经全屏，不需要再放「全屏」按钮；退出靠底部工具条。
        if (!editing) {
            MapControlButton(
                icon = if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                contentDescription =
                    stringResource(
                        if (expanded) R.string.map_exit_fullscreen else R.string.map_fullscreen,
                    ),
                onClick = onToggleFullscreen,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .immersiveSafePadding(expanded)
                        .padding(TrailSpacing.md),
            )
        }
    }
}

/** 时间轴区。地图之外的第二层信息：这一天是怎么发生的。 */
@Composable
private fun TimelinePane(
    state: DayDetailUiState,
    canEditTrack: Boolean,
    onCorrectRoute: () -> Unit,
    onRestoreRoute: () -> Unit,
    onBeginEdit: () -> Unit,
    onRestoreEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        if (state.entries.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Outlined.Route,
                title = stringResource(R.string.day_detail_empty),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = TrailSpacing.gutter,
                        end = TrailSpacing.gutter,
                        top = TrailSpacing.md,
                        bottom = TrailSpacing.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(TrailSpacing.md),
            ) {
                if (state.hasTrack) {
                    item {
                        MetricRow(
                            metrics =
                                listOf(
                                    Metric(
                                        value = state.distanceKm,
                                        label = stringResource(R.string.today_stat_distance),
                                        unit = stringResource(R.string.unit_km),
                                    ),
                                    Metric(
                                        value = state.duration,
                                        label = stringResource(R.string.today_stat_duration),
                                    ),
                                    Metric(
                                        value = state.stopCount.toString(),
                                        label = stringResource(R.string.today_stat_stops),
                                    ),
                                ),
                        )
                    }
                    item {
                        if (state.canCorrectRoute ||
                            state.routeOverride != null ||
                            state.correctingRoute ||
                            canEditTrack ||
                            state.hasManualEdit
                        ) {
                            // 补全路线只覆盖驾车段，手动微调覆盖整天，
                            // 所以两件事并排列在这里、互不干扰。
                            CorrectionBar(
                                state = state,
                                canEditTrack = canEditTrack,
                                onCorrect = onCorrectRoute,
                                onRestore = onRestoreRoute,
                                onBeginEdit = onBeginEdit,
                                onRestoreEdit = onRestoreEdit,
                            )
                        }
                    }
                }
                itemsIndexed(items = state.entries) { index, entry ->
                    TimelineRow(
                        time = entry.time,
                        title = entry.title.resolve(),
                        detail = entry.detail?.resolve().orEmpty(),
                        tone = entry.kind.toTone(),
                        showConnector = index != state.entries.lastIndex,
                    )
                }
            }
        }
    }
}

private fun TimelineKind.toTone(): TimelineTone =
    when (this) {
        TimelineKind.START -> TimelineTone.START
        TimelineKind.STOP -> TimelineTone.STAY
        TimelineKind.GAP -> TimelineTone.GAP
        TimelineKind.END -> TimelineTone.END
    }

/**
 * 「补全路线」与「微调显示」两行入口。
 *
 * 补全路线只在用户点的时候联网一次，结果存成派生数据；
 * 微调显示完全离线，改的只是画出来的线，两个结果都随时可还原。
 */
@Composable
private fun CorrectionBar(
    state: DayDetailUiState,
    canEditTrack: Boolean,
    onCorrect: () -> Unit,
    onRestore: () -> Unit,
    onBeginEdit: () -> Unit,
    onRestoreEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TrailSpacing.md,
                        end = TrailSpacing.sm,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
        ) {
            // 手动微调和纠偏是二选一：两者都写「这一天画哪条线」，
            // 微调的优先级更高。同时摆出来会让用户点了「补全路线」发现地图没变。
            val showCorrection =
                !state.hasManualEdit &&
                    (
                        state.canCorrectRoute ||
                            state.routeOverride != null ||
                            state.correctingRoute
                    )

            if (showCorrection) {
                RouteCorrectionRow(state = state, onCorrect = onCorrect, onRestore = onRestore)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = TrailSpacing.sm),
                )
            }
            ManualEditRow(
                state = state,
                canEditTrack = canEditTrack,
                onBeginEdit = onBeginEdit,
                onRestoreEdit = onRestoreEdit,
            )
        }
    }
}

@Composable
private fun RouteCorrectionRow(
    state: DayDetailUiState,
    onCorrect: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.canCorrectRoute && state.routeOverride == null && !state.correctingRoute) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
    ) {
        when {
            state.correctingRoute -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.day_detail_correcting),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.routeOverride != null -> {
                Icon(
                    imageVector = Icons.Outlined.Route,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.day_detail_route_done),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.day_detail_route_restore))
                }
            }

            else -> {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // 具体原因（点太稀疏 / 没有驾车段）比通用提示更有用
                        text =
                            state.routeError?.resolve()
                                ?: stringResource(R.string.day_detail_correct_route_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (state.routeError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                TextButton(onClick = onCorrect) {
                    Text(
                        stringResource(
                            if (state.routeError != null) {
                                R.string.day_detail_route_retry
                            } else {
                                R.string.day_detail_correct_route
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 手动微调入口。
 *
 * 文案刻意写成「微调显示」而不是「修正轨迹」：里程时长与导出都不跟着变，
 * 说成「修正」用户会以为数字也该变。
 */
@Composable
private fun ManualEditRow(
    state: DayDetailUiState,
    canEditTrack: Boolean,
    onBeginEdit: () -> Unit,
    onRestoreEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasManualEdit && !canEditTrack) return

    // 两行：说明占满一行，动作放下一行靠右。
    // 挤在一行时，长提示（「拖动顶点修正画出来的线，不影响统计与导出」）
    // 在 360dp 宽的机器上会折成两三行，把按钮压到只剩几个字。
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint =
                    if (state.hasManualEdit) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text =
                    if (state.hasManualEdit) {
                        stringResource(R.string.day_detail_edit_done)
                    } else {
                        stringResource(R.string.day_detail_edit_track_hint)
                    },
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (state.hasManualEdit) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (state.hasManualEdit) {
                TextButton(onClick = onRestoreEdit) {
                    Text(stringResource(R.string.day_detail_edit_restore))
                }
            }
            TextButton(onClick = onBeginEdit, enabled = canEditTrack) {
                Text(stringResource(R.string.day_detail_edit_track))
            }
        }
    }
}

/**
 * 地图在页面里的占比。地图是这一页的主角（用户来这就是为了看走了哪），
 * 时间轴排在它下面；60% 在手机上大约是一屏的三分之二。
 */
private const val MAP_WEIGHT = 0.6f

@Preview(showBackground = true, name = "单日详情")
@Composable
private fun DayDetailPreview() {
    DayTrailTheme {
        DayDetailContent(
            state =
                DayDetailUiState(
                    dateKey = "2026-11-18",
                    title = "11月18日 周二",
                    distanceKm = "86.2",
                    duration = "4h06m",
                    stopCount = 3,
                    timeRange = "08:12 – 18:02",
                    entries =
                        listOf(
                            TimelineEntry(
                                time = "08:12",
                                title = UiText.Res(R.string.timeline_start),
                                kind = TimelineKind.START,
                            ),
                            TimelineEntry(
                                time = "09:41",
                                title = UiText.Raw("公司"),
                                detail = UiText.Res(R.string.timeline_stay_minutes, listOf(18)),
                                kind = TimelineKind.STOP,
                            ),
                            TimelineEntry(
                                time = "13:04",
                                title = UiText.Res(R.string.timeline_gap_title),
                                detail = UiText.Res(R.string.timeline_gap_detail, listOf(40)),
                                kind = TimelineKind.GAP,
                            ),
                            TimelineEntry(
                                time = "18:02",
                                title = UiText.Res(R.string.timeline_end),
                                kind = TimelineKind.END,
                            ),
                        ),
                    loading = false,
                ),
            onBack = {},
        )
    }
}
