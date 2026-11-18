package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailContent(
    state: DayDetailUiState,
    onBack: () -> Unit,
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(MAP_HEIGHT),
            ) {
                if (mapAvailable) {
                    AmapView(
                        modifier = Modifier.fillMaxSize(),
                        onMapCreated = { map -> amap = map },
                    )
                    TrackOverlay(
                        amap = amap,
                        segments = state.trackSegments,
                        stays = state.stayMarkers,
                        polylineColor = polylineColor,
                        // 单日详情：按日期缩一次，看全整条轨迹
                        fitKey = state.dateKey,
                        // 画出每个采样点，让「每隔几分钟一个点」一眼可见
                        sampleDots = true,
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

            if (state.entries.isEmpty() && !state.loading) {
                EmptyState(
                    icon = Icons.Outlined.Route,
                    title = stringResource(R.string.day_detail_empty),
                    body = stringResource(R.string.history_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    items(items = state.entries) { entry ->
                        TimelineRow(entry = entry)
                    }
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

/** 地图区域高度。固定值保证时间轴有足够空间，避免地图吃掉整屏。 */
private val MAP_HEIGHT = 260.dp

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
