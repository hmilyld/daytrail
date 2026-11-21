package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.EmptyState
import com.hmilyld.daytrail.ui.components.MapAccessibilityOverlay
import com.hmilyld.daytrail.ui.components.Metric
import com.hmilyld.daytrail.ui.components.MetricRow
import com.hmilyld.daytrail.ui.components.TimelineRow
import com.hmilyld.daytrail.ui.components.TimelineTone
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapControlButton
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.TrackOverlay
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
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

    LaunchedEffect(dateKey) { viewModel.load(dateKey) }

    DayDetailContent(
        state = state,
        onBack = onBack,
        onCorrectRoute = viewModel::correctRoute,
        onRestoreRoute = viewModel::restoreRoute,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailContent(
    state: DayDetailUiState,
    onBack: () -> Unit,
    onCorrectRoute: () -> Unit = {},
    onRestoreRoute: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mapAvailable = rememberAmapAvailable()
    var amap by remember { mutableStateOf<AMap?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val polylineColor = MaterialTheme.colorScheme.primary.toArgb()

    BackHandler(enabled = expanded) { expanded = false }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (!expanded) {
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

            if (wideLayout && !expanded) {
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
                        onCorrectRoute = onCorrectRoute,
                        onRestoreRoute = onRestoreRoute,
                        modifier =
                            Modifier
                                .weight(1f - MAP_WEIGHT)
                                .fillMaxHeight(),
                    )
                }
            } else {
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
                            onCorrectRoute = onCorrectRoute,
                            onRestoreRoute = onRestoreRoute,
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

/** 地图区。全屏时铺满整个内容区。 */
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
) {
    Box(modifier = modifier) {
        if (mapAvailable) {
            AmapView(
                modifier = Modifier.fillMaxSize(),
                onMapCreated = onAmapReady,
            )
            TrackOverlay(
                amap = amap,
                segments = state.trackSegments,
                stays = state.stayMarkers,
                stayCentroids = state.stayCentroids,
                polylineColor = polylineColor,
                // 按日期缩一次；进入全屏再缩一次看全整条轨迹。
                fitKey = if (expanded) "${state.dateKey}-full" else state.dateKey,
                // 纠偏后的折线是**路网顶点**，不是采样点，画出来会误导。
                sampleDots = state.routeOverride == null,
            )
            MapAccessibilityOverlay(
                text = state.accessibilitySummary,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(TrailSpacing.md),
            )

            // 全屏时标题栏被隐藏，把日期压在图上，否则不知道在看哪一天
            if (expanded) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(TrailSpacing.md),
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            MapUnavailablePlaceholder(modifier = Modifier.fillMaxSize())
        }

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
                    .padding(TrailSpacing.md),
        )
    }
}

/** 时间轴区。地图之外的第二层信息：这一天是怎么发生的。 */
@Composable
private fun TimelinePane(
    state: DayDetailUiState,
    onCorrectRoute: () -> Unit,
    onRestoreRoute: () -> Unit,
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
                            state.correctingRoute
                        ) {
                            CorrectionBar(
                                state = state,
                                onCorrect = onCorrectRoute,
                                onRestore = onRestoreRoute,
                            )
                        }
                    }
                }
                itemsIndexed(items = state.entries) { index, entry ->
                    TimelineRow(
                        time = entry.time,
                        title = entry.title,
                        detail = entry.detail,
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
 * 「补全路线」入口。
 *
 * 只在用户点的时候联网一次；结果存成派生数据，之后每次打开都直接用，
 * 不会重复请求。按钮不可用（没配 Key、没有驾车段）时整行不显示。
 */
@Composable
private fun CorrectionBar(
    state: DayDetailUiState,
    onCorrect: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.canCorrectRoute && state.routeOverride == null && !state.correctingRoute) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TrailSpacing.md,
                        end = TrailSpacing.sm,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
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
                            text = stringResource(R.string.day_detail_correct_route_hint),
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
                            TimelineEntry("08:12", "开始记录", "", TimelineKind.START),
                            TimelineEntry("09:41", "公司", "停留 18 分钟", TimelineKind.STOP),
                            TimelineEntry("13:04", "数据缺失", "约 40 分钟无采样", TimelineKind.GAP),
                            TimelineEntry("18:02", "结束记录", "", TimelineKind.END),
                        ),
                    loading = false,
                ),
            onBack = {},
        )
    }
}
