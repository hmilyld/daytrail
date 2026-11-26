package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.domain.TrackPlayback
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/** 回放状态。只活在界面层；不写进 DataStore，也不进 ViewModel 的数据状态。 */
data class PlaybackUiState(
    val active: Boolean = false,
    val playing: Boolean = false,
    /** 已播放的墙上时间（毫秒），倍速已经折算进时间轴。 */
    val elapsedMs: Long = 0L,
    val speed: Double = 1.0,
    /** true 时 speed 由总时长自动算出，界面只显示「自动」。 */
    val auto: Boolean = true,
    val follow: Boolean = true,
)

/**
 * 回放条。
 *
 * **排在布局流里（时间轴区、地图下方），不能叠在地图上**：高德 logo 与版权固定在
 * 左下角，整屏宽的浮层压上去会违反 SDK 条款（同 `TrackEditBar`）。
 */
@Composable
fun PlaybackBar(
    state: PlaybackUiState,
    currentTime: String,
    durationMs: Long,
    inGap: Boolean,
    inStay: Boolean,
    recording: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onAuto: () -> Unit,
    onSpeed: (Double) -> Unit,
    onFollow: (Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint =
        when {
            inGap -> stringResource(R.string.playback_gap)
            inStay -> stringResource(R.string.playback_stay)
            recording -> stringResource(R.string.playback_live)
            else -> stringResource(R.string.playback_hint)
        }
    val playLabel =
        stringResource(if (state.playing) R.string.playback_pause else R.string.playback_play)
    val speedLabel =
        stringResource(
            R.string.playback_speed_current,
            if (state.auto) {
                stringResource(R.string.playback_auto)
            } else {
                "${state.speed.toInt()}×"
            },
        )
    var speedMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TrailSpacing.md, vertical = TrailSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector =
                            if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = playLabel,
                    )
                }
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = 48.dp),
                )
                Slider(
                    value = state.elapsedMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                )
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.StopCircle,
                        contentDescription = stringResource(R.string.playback_stop),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
            ) {
                Box {
                    TextButton(
                        onClick = { speedMenu = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(speedLabel)
                    }
                    DropdownMenu(
                        expanded = speedMenu,
                        onDismissRequest = { speedMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playback_auto_option)) },
                            trailingIcon = {
                                if (state.auto) Icon(Icons.Outlined.Check, null)
                            },
                            onClick = {
                                onAuto()
                                speedMenu = false
                            },
                        )
                        TrackPlayback.MANUAL_SPEEDS.forEach { option ->
                            val selected = !state.auto && state.speed == option
                            DropdownMenuItem(
                                text = { Text("${option.toInt()}×") },
                                trailingIcon = {
                                    if (selected) Icon(Icons.Outlined.Check, null)
                                },
                                onClick = {
                                    onSpeed(option)
                                    speedMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.playback_follow),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = state.follow,
                        onCheckedChange = onFollow,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}
