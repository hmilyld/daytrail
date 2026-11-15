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
import com.hmilyld.daytrail.location.RecordingService
import com.hmilyld.daytrail.ui.appViewModelFactory
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
        modifier = modifier,
    )
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
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
            // 始终跟随：用户打开应用就想知道自己在哪，与是否在记录无关。
            CurrentLocationMarker(amap = amap)
            TrackOverlay(
                amap = amap,
                points = state.trackPoints,
                stays = state.stayMarkers,
                polylineColor = polylineColor,
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
        )
    }
}
