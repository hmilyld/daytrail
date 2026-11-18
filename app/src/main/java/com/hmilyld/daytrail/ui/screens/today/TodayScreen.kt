package com.hmilyld.daytrail.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps.AMap
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.location.LocationFailureReason
import com.hmilyld.daytrail.location.RecordingService
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.MapAccessibilityOverlay
import com.hmilyld.daytrail.ui.components.StatBlock
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.CurrentLocationMarker
import com.hmilyld.daytrail.ui.components.map.LocateButton
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.TrackOverlay
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 今日页：地图优先布局。
 *
 * 结构（自上而下）：
 * 1. 全屏地图（占满剩余空间）
 * 2. 悬浮状态胶囊（左上角，避开状态栏）
 * 3. 底部统计面板（里程 / 时长 / 停留 + 控制按钮）
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
    modifier: Modifier = Modifier,
) {
    val mapAvailable = rememberAmapAvailable()
    var amap by remember { mutableStateOf<AMap?>(null) }
    val polylineColor = MaterialTheme.colorScheme.primary.toArgb()

    Box(modifier = modifier.fillMaxSize()) {
        if (mapAvailable) {
            AmapView(
                modifier = Modifier.fillMaxSize(),
                onMapCreated = { map -> amap = map },
            )
            // 当前位置标记：自己画，不依赖高德定位 SDK 的蓝点。
            // 首次定位自动居中，打开应用就能看到自己在哪。
            CurrentLocationMarker(
                amap = amap,
                recording = state.recordingState == RecordingState.RECORDING,
                lastKnownFix = state.lastFix,
            )
            TrackOverlay(
                amap = amap,
                segments = state.trackSegments,
                stays = state.stayMarkers,
                polylineColor = polylineColor,
                // 录制中不缩放：轨迹每分钟更新，每次缩放都会把用户拖动的地图
                // 反复拽回。停止记录后再缩一次，让整条轨迹落在屏幕里。
                autoFit = state.recordingState != RecordingState.RECORDING,
                fitKey = state.pointCount,
            )
        } else {
            MapUnavailablePlaceholder(modifier = Modifier.fillMaxSize())
        }

        RecordingPill(
            state = state.recordingState,
            duration = state.duration,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
        )

        // 定位按钮：地图右上角，与状态胶囊同一行
        if (mapAvailable) {
            LocateButton(
                amap = amap,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
            )
        }

        // 地图的文字版概要：折线对读屏不可用，必须给等价信息
        MapAccessibilityOverlay(
            text = state.accessibilitySummary,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 220.dp),
        )

        // 空状态：未开始记录且没有数据时给一句提示，避免一片空地
        if (state.pointCount == 0 && state.recordingState == RecordingState.IDLE) {
            EmptyHintCard(
                title = stringResource(R.string.today_empty_title),
                body = stringResource(R.string.today_empty_body),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 定位异常：采集已停止，明确告知原因并给一键重试
        state.locationIssue?.let { issue ->
            LocationIssueBanner(
                reason = issue,
                onRetry = onRetry,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 220.dp),
            )
        }

        StatsSheet(
            state = state,
            onStart = onStart,
            onPause = onPause,
            onStop = onStop,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp),
        )
    }
}

/**
 * 记录状态胶囊。
 *
 * 呼吸点用静态圆点代替动画：省电优先的应用不该为了装饰效果持续跑动画，
 * 也会干扰读屏。状态靠文字区分，不只靠颜色。
 */
@Composable
private fun RecordingPill(
    state: RecordingState,
    duration: String,
    modifier: Modifier = Modifier,
) {
    val (dotColor, label) =
        when (state) {
            RecordingState.RECORDING ->
                MaterialTheme.colorScheme.secondary to stringResource(R.string.today_recording)
            RecordingState.PAUSED ->
                MaterialTheme.colorScheme.tertiary to stringResource(R.string.today_paused)
            RecordingState.IDLE ->
                MaterialTheme.colorScheme.outline to stringResource(R.string.today_idle)
        }

    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .semantics { contentDescription = label },
            ) {
                Surface(
                    color = dotColor,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize(),
                ) {}
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            if (state != RecordingState.IDLE) {
                Text(text = "·", style = MaterialTheme.typography.labelLarge)
                Text(text = duration, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 定位异常提示条。
 *
 * 三种原因的修复方式不同，所以文案分开给：
 * 权限问题要去设置页、开关问题要去快捷面板、信号问题只需换个地方重试。
 */
@Composable
private fun LocationIssueBanner(
    reason: LocationFailureReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message =
        when (reason) {
            LocationFailureReason.PERMISSION_DENIED ->
                stringResource(R.string.location_issue_permission)
            LocationFailureReason.LOCATION_DISABLED ->
                stringResource(R.string.location_issue_disabled)
            LocationFailureReason.NO_FIX ->
                stringResource(R.string.location_issue_no_fix)
            LocationFailureReason.UNKNOWN ->
                stringResource(R.string.location_issue_unknown)
        }

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onRetry) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

/** 空状态提示卡片。悬浮在地图中央，不遮挡底部控制区。 */
@Composable
private fun EmptyHintCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 4.dp,
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

/** 底部统计面板，含开始 / 暂停 / 结束控制。 */
@Composable
private fun StatsSheet(
    state: TodayUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatBlock(
                    value = state.distanceKm,
                    unit = stringResource(R.string.today_stat_distance),
                    label = "",
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    value = state.duration,
                    unit = "",
                    label = stringResource(R.string.today_stat_duration),
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    value = state.stopCount.toString(),
                    unit = "",
                    label = stringResource(R.string.today_stat_stops),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            RecordingControls(
                state = state.recordingState,
                onStart = onStart,
                onPause = onPause,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun RecordingControls(
    state: RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            RecordingState.IDLE ->
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.today_action_start))
                }

            RecordingState.RECORDING -> {
                FilledTonalButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.today_action_pause))
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
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
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.today_action_stop))
                }
            }

            RecordingState.PAUSED -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.today_action_start))
                }
                FilledTonalButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.today_action_stop))
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "今日 - 未开始")
@Composable
private fun TodayIdlePreview() {
    DayTrailTheme {
        TodayContent(
            state =
                TodayUiState(
                    dayKey =
                        com.hmilyld.daytrail.core
                            .DayKey("2026-11-15"),
                ),
            onStart = {},
            onPause = {},
            onStop = {},
            onRetry = {},
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
                    dayKey =
                        com.hmilyld.daytrail.core
                            .DayKey("2026-11-15"),
                    recordingState = RecordingState.RECORDING,
                    distanceKm = "12.4",
                    duration = "2h14m",
                    stopCount = 8,
                    pointCount = 412,
                ),
            onStart = {},
            onPause = {},
            onStop = {},
            onRetry = {},
        )
    }
}
