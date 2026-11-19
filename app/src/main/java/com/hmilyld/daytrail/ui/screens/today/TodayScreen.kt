package com.hmilyld.daytrail.ui.screens.today

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps.AMap
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.location.LocationFailureReason
import com.hmilyld.daytrail.location.RecordingService
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.CalloutBanner
import com.hmilyld.daytrail.ui.components.CalloutTone
import com.hmilyld.daytrail.ui.components.MapAccessibilityOverlay
import com.hmilyld.daytrail.ui.components.Metric
import com.hmilyld.daytrail.ui.components.MetricRow
import com.hmilyld.daytrail.ui.components.RecordingStatusChip
import com.hmilyld.daytrail.ui.components.SectionCard
import com.hmilyld.daytrail.ui.components.TimelineRow
import com.hmilyld.daytrail.ui.components.TimelineTone
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.CurrentLocationMarker
import com.hmilyld.daytrail.ui.components.map.LocateButton
import com.hmilyld.daytrail.ui.components.map.MapControlButton
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.TrackOverlay
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import com.hmilyld.daytrail.ui.components.trailSegmentedColors
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 今日页。
 *
 * 结构（自上而下）：标题与状态 → 提示条 → 地图卡 → 可滚动的统计 / 行程 / 采样
 * → 底部控制条。
 *
 * 地图固定在上方而不跟随滚动：这是地图类应用，用户滚动查看统计时
 * 不该把「我在哪、今天走了哪」滚出屏幕。
 *
 * 全屏查看时整块地图铺满可视区域，并把底部导航栏一起让开
 * （`ChromeState`），否则「全屏」只是把地图拉高一点。
 */
@Composable
fun TodayScreen(
    container: AppContainer,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TodayViewModel = viewModel(factory = appViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    TodayContent(
        state = state,
        onStart = { RecordingService.start(context) },
        onPause = { RecordingService.pause(context) },
        onStop = { RecordingService.stop(context) },
        onRetry = { RecordingService.retry(context) },
        onOpenDay = { onOpenDay(state.dayKey.value) },
        onSamplingTier = viewModel::setSamplingTier,
        onImmersiveChange = container.chrome::setImmersive,
        modifier = modifier,
    )
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOpenDay: () -> Unit,
    onSamplingTier: (SamplingTier) -> Unit,
    modifier: Modifier = Modifier,
    onImmersiveChange: (Boolean) -> Unit = {},
) {
    val mapAvailable = rememberAmapAvailable()
    var amap by remember { mutableStateOf<AMap?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var tierExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val polylineColor = MaterialTheme.colorScheme.primary.toArgb()
    val recording = state.recordingState == RecordingState.RECORDING

    // 档位卡在指标下面，展开不会把自己顶出视野，所以不需要额外滚动。

    // 全屏期间让外壳把导航栏收起来；离开页面必须复位，
    // 否则返回列表页后底部导航会一直消失。
    LaunchedEffect(expanded) { onImmersiveChange(expanded) }
    DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }
    BackHandler(enabled = expanded) { expanded = false }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val mapHeight: Dp =
            (maxHeight * MAP_HEIGHT_FRACTION)
                .coerceIn(MAP_MIN_HEIGHT, MAP_MAX_HEIGHT)

        Column(modifier = Modifier.fillMaxSize()) {
            if (!expanded) {
                TodayHeader(state = state)
                RecordingNotices(state = state, onRetry = onRetry)
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (expanded) {
                                Modifier.weight(1f)
                            } else {
                                Modifier
                                    .padding(horizontal = TrailSpacing.gutter)
                                    .height(mapHeight)
                                    .clip(MaterialTheme.shapes.medium)
                            },
                        ),
            ) {
                if (mapAvailable) {
                    AmapView(
                        modifier = Modifier.fillMaxSize(),
                        onMapCreated = { map -> amap = map },
                    )
                    CurrentLocationMarker(
                        amap = amap,
                        recording = recording,
                        lastKnownFix = state.lastFix,
                    )
                    TrackOverlay(
                        amap = amap,
                        segments = state.trackSegments,
                        stays = state.stayMarkers,
                        polylineColor = polylineColor,
                        // 录制中不自动缩放（轨迹每分钟更新，会把用户拖动的地图反复拽回）；
                        // 进入全屏时缩一次，让整条轨迹落进更大的画布。
                        autoFit = !recording || expanded,
                        fitKey = if (expanded) FULLSCREEN_FIT_KEY else state.pointCount,
                    )
                } else {
                    MapUnavailablePlaceholder(modifier = Modifier.fillMaxSize())
                }

                // 右上角：全屏 / 回到当前位置。两个都是 48dp 触达目标。
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(TrailSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
                ) {
                    MapControlButton(
                        icon =
                            if (expanded) {
                                Icons.Outlined.FullscreenExit
                            } else {
                                Icons.Outlined.Fullscreen
                            },
                        contentDescription =
                            stringResource(
                                if (expanded) {
                                    R.string.map_exit_fullscreen
                                } else {
                                    R.string.map_fullscreen
                                },
                            ),
                        onClick = { expanded = !expanded },
                    )
                    if (mapAvailable) {
                        LocateButton(amap = amap)
                    }
                }

                // 全屏时状态与提示必须留在图上：标题栏和提示条都被隐藏了。
                if (expanded) {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(TrailSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
                    ) {
                        RecordingStatusChip(
                            state = state.recordingState,
                            duration =
                                if (state.recordingState == RecordingState.IDLE) {
                                    null
                                } else {
                                    state.duration
                                },
                        )
                        if (state.sleeping && recording) {
                            CalloutBanner(
                                text = stringResource(R.string.today_sleeping_hint),
                                tone = CalloutTone.INFO,
                                icon = Icons.Outlined.Bedtime,
                            )
                        } else if (state.fixStale && recording) {
                            CalloutBanner(
                                text = stringResource(R.string.today_fix_stale),
                                tone = CalloutTone.WARNING,
                                icon = Icons.Outlined.LocationOff,
                            )
                        }
                    }
                }

                // 地图对读屏不可用，必须给等价文字
                MapAccessibilityOverlay(
                    text = state.accessibilitySummary,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(TrailSpacing.md),
                )

                // 空状态：未开始且没有任何点时给一句话，避免一片空地。
                // 地图不可用时不再叠加：占位说明本身已经写清了原因。
                if (mapAvailable &&
                    !expanded &&
                    state.pointCount == 0 &&
                    state.recordingState == RecordingState.IDLE
                ) {
                    EmptyMapHint(
                        title = stringResource(R.string.today_map_empty_title),
                        body = stringResource(R.string.today_map_empty_body),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            if (!expanded) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = TrailSpacing.contentMaxWidth)
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(
                                    PaddingValues(
                                        start = TrailSpacing.gutter,
                                        end = TrailSpacing.gutter,
                                        top = TrailSpacing.md,
                                        bottom = TrailSpacing.lg,
                                    ),
                                ),
                        verticalArrangement = Arrangement.spacedBy(TrailSpacing.md),
                    ) {
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

                        // 档位放在行程上方：用户抱怨「今天点怎么这么少」时，
                        // 紧挨着就能看到当前间隔，直接改，不用跳去设置。
                        SamplingCard(
                            tier = state.samplingTier,
                            expanded = tierExpanded,
                            onToggle = { tierExpanded = !tierExpanded },
                            onSelect = onSamplingTier,
                        )

                        TodayTimelineCard(state = state, onOpenDay = onOpenDay)
                    }
                }

                RecordingControlBar(
                    state = state.recordingState,
                    onStart = onStart,
                    onPause = onPause,
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
private fun TodayHeader(state: TodayUiState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TrailSpacing.gutter, vertical = TrailSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.dateLabel.isNotBlank()) {
                Text(
                    text = state.dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        RecordingStatusChip(
            state = state.recordingState,
            duration =
                if (state.recordingState == RecordingState.IDLE) {
                    null
                } else {
                    state.duration
                },
        )
    }
}

/**
 * 提示条。
 *
 * 三种状态互斥，按严重程度只显示一条：定位异常 > 休眠 > 暂无新定位。
 * 同时出现时只把最需要处理的那条摆出来，避免一屏三条警告。
 */
@Composable
private fun RecordingNotices(
    state: TodayUiState,
    onRetry: () -> Unit,
) {
    val recording = state.recordingState == RecordingState.RECORDING
    val issue = state.locationIssue
    val showSleeping = state.sleeping && recording
    val showStale = state.fixStale && recording

    // 没有任何提示时不要留一个空盒子：那会在地图和标题之间塞进一段
    // 看不见的间距，看起来像标题和地图没对齐。
    if (issue == null && !showSleeping && !showStale) return

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = TrailSpacing.gutter,
                    end = TrailSpacing.gutter,
                    bottom = TrailSpacing.md,
                ),
    ) {
        when {
            issue != null ->
                CalloutBanner(
                    text =
                        stringResource(
                            when (issue) {
                                LocationFailureReason.PERMISSION_DENIED ->
                                    R.string.location_issue_permission
                                LocationFailureReason.LOCATION_DISABLED ->
                                    R.string.location_issue_disabled
                            },
                        ),
                    tone = CalloutTone.ERROR,
                    icon = Icons.Outlined.LocationOff,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )

            showSleeping ->
                CalloutBanner(
                    text = stringResource(R.string.today_sleeping_hint),
                    tone = CalloutTone.INFO,
                    icon = Icons.Outlined.Bedtime,
                )

            else ->
                CalloutBanner(
                    text = stringResource(R.string.today_fix_stale),
                    tone = CalloutTone.WARNING,
                    icon = Icons.Outlined.LocationOff,
                )
        }
    }
}

/** 今日行程卡。只展示最近三条，完整时间轴在单日详情里。 */
@Composable
private fun TodayTimelineCard(
    state: TodayUiState,
    onOpenDay: () -> Unit,
) {
    val stayWord = stringResource(R.string.timeline_stay)
    val stayMinutes = stringResource(R.string.timeline_stay_minutes)
    val startWord = stringResource(R.string.timeline_start)

    val rows =
        buildList {
            state.startedAtLabel?.let { time ->
                add(TodayRow(time, startWord, "", TimelineTone.START))
            }
            state.stays.forEach { stay ->
                add(
                    TodayRow(
                        time = stay.startTimeLabel,
                        title = stay.label ?: stayWord,
                        detail = String.format(stayMinutes, stay.minutes),
                        tone = TimelineTone.STAY,
                    ),
                )
            }
        }.takeLast(TIMELINE_PREVIEW_COUNT)

    SectionCard(
        title = stringResource(R.string.today_timeline_title),
        actionLabel = stringResource(R.string.action_all),
        onAction = onOpenDay,
    ) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.timeline_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rows.forEachIndexed { index, row ->
                TimelineRow(
                    time = row.time,
                    title = row.title,
                    detail = row.detail,
                    tone = row.tone,
                    showConnector = index != rows.lastIndex,
                )
            }
        }
        if (state.pointCount > 0) {
            Text(
                text = stringResource(R.string.today_samples, state.pointCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TrailSpacing.sm),
            )
        }
    }
}

private data class TodayRow(
    val time: String,
    val title: String,
    val detail: String,
    val tone: TimelineTone,
)

/**
 * 采样档位卡。
 *
 * 默认收起成一行，点开才露出三档：档位不是高频操作，
 * 但「今天怎么这么少点」时用户要能当场调，而不是跳去设置里翻。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SamplingCard(
    tier: SamplingTier,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (SamplingTier) -> Unit,
) {
    SectionCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_sampling_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(tier.shortLabelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Icon(
                imageVector =
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            Column {
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = TrailSpacing.md),
                ) {
                    val options = SamplingTier.entries
                    options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == tier,
                            onClick = { onSelect(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            colors = trailSegmentedColors(),
                        ) {
                            Text(text = stringResource(option.nameRes()))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.today_sampling_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = TrailSpacing.sm),
                )
            }
        }
    }
}

/** 底部记录控制条。常驻，不随内容滚动。 */
@Composable
private fun RecordingControlBar(
    state: RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = TrailSpacing.contentMaxWidth)
                        .fillMaxWidth()
                        .padding(
                            horizontal = TrailSpacing.gutter,
                            vertical = TrailSpacing.md,
                        ),
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
            ) {
                when (state) {
                    RecordingState.IDLE ->
                        Button(
                            onClick = onStart,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = CONTROL_HEIGHT),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.today_action_start))
                        }

                    RecordingState.RECORDING -> {
                        FilledTonalButton(
                            onClick = onPause,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = CONTROL_HEIGHT),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.today_action_pause))
                        }
                        Button(
                            onClick = onStop,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = CONTROL_HEIGHT),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.today_action_stop))
                        }
                    }

                    RecordingState.PAUSED -> {
                        Button(
                            onClick = onStart,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = CONTROL_HEIGHT),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.today_action_start))
                        }
                        FilledTonalButton(
                            onClick = onStop,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = CONTROL_HEIGHT),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.today_action_stop))
                        }
                    }
                }
            }
        }
    }
}

/** 地图中央的空状态提示。 */
@Composable
private fun EmptyMapHint(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 2.dp,
        modifier = modifier.padding(horizontal = TrailSpacing.xl),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 采样档位的短标签。收起状态显示的就是这一句。 */
private fun SamplingTier.shortLabelRes(): Int =
    when (this) {
        SamplingTier.POWER_SAVER -> R.string.tier_power_short
        SamplingTier.BALANCED -> R.string.tier_balanced_short
        SamplingTier.HIGH_ACCURACY -> R.string.tier_high_short
    }

/** 分段按钮上的名字，两个汉字，三档能在一行里放下。 */
private fun SamplingTier.nameRes(): Int =
    when (this) {
        SamplingTier.POWER_SAVER -> R.string.tier_power
        SamplingTier.BALANCED -> R.string.tier_balanced
        SamplingTier.HIGH_ACCURACY -> R.string.tier_high
    }

/** 地图占可视高度的比例，以及上下限。 */
private const val MAP_HEIGHT_FRACTION = 0.42f
private val MAP_MIN_HEIGHT = 200.dp
private val MAP_MAX_HEIGHT = 360.dp

/** 控制按钮高度。52dp 高于 Material 默认的 40dp，户外单手更好按。 */
private val CONTROL_HEIGHT = 52.dp

/** 今日行程卡最多显示几条。 */
private const val TIMELINE_PREVIEW_COUNT = 3

/** 进入全屏时换一个 fitKey，让轨迹重新缩放一次。 */
private const val FULLSCREEN_FIT_KEY = "today-fullscreen"

@Preview(showBackground = true, name = "今日 - 未开始")
@Composable
private fun TodayIdlePreview() {
    DayTrailTheme {
        TodayContent(
            state = TodayUiState(dayKey = DayKey("2026-11-19"), dateLabel = "11月19日 星期四"),
            onStart = {},
            onPause = {},
            onStop = {},
            onRetry = {},
            onOpenDay = {},
            onSamplingTier = {},
        )
    }
}

@Preview(showBackground = true, name = "今日 - 记录中")
@Composable
private fun TodayRecordingPreview() {
    DayTrailTheme {
        TodayContent(
            state =
                TodayUiState(
                    dayKey = DayKey("2026-11-19"),
                    dateLabel = "11月19日 星期四",
                    recordingState = RecordingState.RECORDING,
                    distanceKm = "12.4",
                    duration = "2:14",
                    stopCount = 8,
                    pointCount = 412,
                    startedAtLabel = "08:12",
                    stays =
                        listOf(
                            TodayStay("09:41", "公司", 18),
                            TodayStay("12:30", null, 32),
                        ),
                ),
            onStart = {},
            onPause = {},
            onStop = {},
            onRetry = {},
            onOpenDay = {},
            onSamplingTier = {},
        )
    }
}
