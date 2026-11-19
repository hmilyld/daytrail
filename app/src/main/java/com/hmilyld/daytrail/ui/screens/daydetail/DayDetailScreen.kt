package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.TrackOverlay
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 单日详情：上方地图轨迹，下方时间轴。
 *
 * 时间轴有四类节点：出发 / 停留 / 数据缺失 / 结束。
 * 「数据缺失」必须显式画出——高铁过隧道会频繁出现，
 * 不标出来用户会以为轨迹画错了。
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
    val polylineColor = MaterialTheme.colorScheme.primary.toArgb()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.title.ifBlank { state.dateKey },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.hasTrack) {
                            Text(
                                text =
                                    buildString {
                                        if (state.timeRange.isNotBlank()) {
                                            append(state.timeRange)
                                            append(" · ")
                                        }
                                        append("${state.distanceKm} km")
                                        append(" · ${state.duration}")
                                        append(" · ${state.stopCount} 处停留")
                                    },
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
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // 日详情的主角是「今天走了哪」，地图必须占到一半以上。
            // 早期写死 260dp，地图只占屏幕三分之一，时间轴挤在一起，两头都不好用。
            val wideLayout = maxWidth >= WIDE_BREAKPOINT

            if (wideLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    TrackMapPane(
                        state = state,
                        amap = amap,
                        onAmapReady = { map -> amap = map },
                        polylineColor = polylineColor,
                        mapAvailable = mapAvailable,
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
                        modifier =
                            Modifier
                                .weight(MAP_WEIGHT)
                                .fillMaxWidth(),
                    )
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

/**
 * 地图区。整页的主角：轨迹、停留点、文字版行程都在这里。
 */
@Composable
private fun TrackMapPane(
    state: DayDetailUiState,
    amap: AMap?,
    onAmapReady: (AMap) -> Unit,
    polylineColor: Int,
    mapAvailable: Boolean,
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
                polylineColor = polylineColor,
                // 单日详情：按日期缩一次，看全整条轨迹
                fitKey = state.dateKey,
                // 画出每个采样点，让「每隔几分钟一个点」一眼可见。
                // 纠偏后的折线是**路网顶点**，不是采样点，画出来会误导。
                sampleDots = state.routeOverride == null,
            )
            // 文字版行程：地图对读屏不可用，必须提供等价信息
            MapAccessibilitySummary(
                text = state.accessibilitySummary,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
            )
        } else {
            MapUnavailablePlaceholder(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * 时间轴区。
 *
 * 独立成一个 Surface：窄屏时它在地图下方，宽屏时在右侧，
 * 有自己的底色才能在两种布局里都和地图分开。
 */
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (state.hasTrack) {
                    item {
                        CorrectionBar(
                            state = state,
                            onCorrect = onCorrectRoute,
                            onRestore = onRestoreRoute,
                        )
                    }
                }
                items(items = state.entries) { entry ->
                    TimelineRow(entry = entry)
                }
            }
        }
    }
}

/**
 * 「补全路线」入口。
 *
 * 只在用户点的时候联网一次；结果存成派生数据，之后每次打开都直接用，不会重复请求。
 * 按钮不可用（没配 Key、没有驾车段）时整行不显示，避免给一个点不动的按钮。
 */
@Composable
private fun CorrectionBar(
    state: DayDetailUiState,
    onCorrect: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.canCorrectRoute && state.routeOverride == null && !state.correctingRoute) return

    when {
        state.correctingRoute -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier.padding(bottom = 12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.day_detail_correcting),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        state.routeOverride != null -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(bottom = 12.dp),
            ) {
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
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                )
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.day_detail_route_restore))
                }
            }
        }

        else -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(bottom = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.day_detail_correct_route),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text =
                            state.routeError
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
 * 地图的文字版行程。
 *
 * 折线本身对 TalkBack 完全不可用，这里给出「起点 → 终点 + 里程」的等价描述。
 * 用 `clearAndSetSemantics` 合并成一句，避免读屏念一堆坐标。
 */
@Composable
private fun MapAccessibilitySummary(
    text: String,
    modifier: Modifier = Modifier,
) {
    MapAccessibilityOverlay(text = text, modifier = modifier)
}

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    val dotColor =
        when (entry.kind) {
            TimelineKind.START -> MaterialTheme.colorScheme.secondary
            TimelineKind.STOP -> MaterialTheme.colorScheme.onSurfaceVariant
            TimelineKind.GAP -> MaterialTheme.colorScheme.tertiary
            TimelineKind.END -> MaterialTheme.colorScheme.primary
        }

    val spoken = "${entry.time}，${entry.title}，${entry.detail}"

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = spoken }
                .padding(vertical = 6.dp),
    ) {
        Text(
            text = entry.time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(color = dotColor, shape = CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 地图在页面里的占比。地图是这一页的主角（用户来这就是为了看走了哪），
 * 时间轴排在它下面；60% 在手机上大约是一屏的三分之二。
 */
private const val MAP_WEIGHT = 0.6f

/** 600dp 以上改成左右分栏：宽屏上下堆叠会把地图压成一条横带。 */
private val WIDE_BREAKPOINT = 600.dp

@Preview(showBackground = true, name = "单日详情")
@Composable
private fun DayDetailPreview() {
    DayTrailTheme {
        DayDetailContent(
            state =
                DayDetailUiState(
                    dateKey = "2026-11-14",
                    title = "11月14日 周六",
                    distanceKm = "86.2",
                    duration = "4h06m",
                    stopCount = 3,
                    entries =
                        listOf(
                            TimelineEntry("08:12", "开始记录", "", TimelineKind.START),
                            TimelineEntry("09:41", "停留", "停留 18 分钟", TimelineKind.STOP),
                            TimelineEntry("10:20", "数据缺失", "约 40 分钟无数据", TimelineKind.GAP),
                            TimelineEntry("18:02", "结束记录", "", TimelineKind.END),
                        ),
                    loading = false,
                ),
            onBack = {},
        )
    }
}
