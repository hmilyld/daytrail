package com.hmilyld.daytrail.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.ui.components.StatBlock
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 今日页：地图优先布局。
 *
 * 结构（自上而下）：
 * 1. 全屏地图（占满剩余空间）
 * 2. 悬浮状态胶囊（左上角）
 * 3. 底部统计面板（里程 / 时长 / 停留）
 *
 * 状态胶囊不只靠颜色区分，必须带文字，满足无障碍要求。
 */
@Composable
fun TodayScreen(
    container: com.hmilyld.daytrail.AppContainer,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TodayContent(
        state = RecordingState.IDLE,
        distanceKm = "0.0",
        duration = "0h00m",
        stopCount = 0,
        modifier = modifier,
    )
}

@Composable
private fun TodayContent(
    state: RecordingState,
    distanceKm: String,
    duration: String,
    stopCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (rememberAmapAvailable()) {
            AmapView(
                modifier = Modifier.fillMaxSize(),
                onMapReady = { map -> map.uiSettings.isZoomControlsEnabled = false },
            )
        } else {
            MapUnavailablePlaceholder(modifier = Modifier.fillMaxSize())
        }

        RecordingPill(
            state = state,
            duration = duration,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
        )

        StatsSheet(
            state = state,
            distanceKm = distanceKm,
            duration = duration,
            stopCount = stopCount,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp),
        )
    }
}

/**
 * 记录状态胶囊。呼吸点用静态圆点代替动画：
 * 省电优先的应用不该为了装饰效果持续跑动画，也会干扰读屏。
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
    state: RecordingState,
    distanceKm: String,
    duration: String,
    stopCount: Int,
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
                    value = distanceKm,
                    unit = stringResource(R.string.today_stat_distance),
                    label = "",
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    value = duration,
                    unit = "",
                    label = stringResource(R.string.today_stat_duration),
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    value = stopCount.toString(),
                    unit = "",
                    label = stringResource(R.string.today_stat_stops),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            RecordingControls(state = state)
        }
    }
}

@Composable
private fun RecordingControls(state: RecordingState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            RecordingState.IDLE ->
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Box(modifier = Modifier.width(ButtonDefaults.IconSpacing)) {}
                    Text(text = stringResource(R.string.today_action_start))
                }

            RecordingState.RECORDING -> {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Box(modifier = Modifier.width(ButtonDefaults.IconSpacing)) {}
                    Text(text = stringResource(R.string.today_action_pause))
                }
                Button(
                    onClick = {},
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
                    Box(modifier = Modifier.width(ButtonDefaults.IconSpacing)) {}
                    Text(text = stringResource(R.string.today_action_stop))
                }
            }

            RecordingState.PAUSED -> {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Box(modifier = Modifier.width(ButtonDefaults.IconSpacing)) {}
                    Text(text = stringResource(R.string.today_action_start))
                }
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Box(modifier = Modifier.width(ButtonDefaults.IconSpacing)) {}
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
            state = RecordingState.IDLE,
            distanceKm = "0.0",
            duration = "0h00m",
            stopCount = 0,
        )
    }
}

@Preview(showBackground = true, name = "今日 - 记录中")
@Composable
private fun TodayRecordingPreview() {
    DayTrailTheme {
        TodayContent(
            state = RecordingState.RECORDING,
            distanceKm = "12.4",
            duration = "2h14m",
            stopCount = 8,
        )
    }
}
