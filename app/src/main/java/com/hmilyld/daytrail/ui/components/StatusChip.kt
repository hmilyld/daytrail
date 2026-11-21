package com.hmilyld.daytrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 记录状态胶囊。
 *
 * 三件事必须成立：
 * - 状态用**文字**写明，颜色只是辅助（色觉障碍用户也要能分辨记录中与已暂停）
 * - 深色 / 浅色主题下都从 `colorScheme` 取容器色，不写死绿底
 * - 时长与状态在同一行，省掉一次视线移动
 */
@Composable
fun RecordingStatusChip(
    state: RecordingState,
    modifier: Modifier = Modifier,
    duration: String? = null,
) {
    val label =
        stringResource(
            when (state) {
                RecordingState.RECORDING -> R.string.today_recording
                RecordingState.PAUSED -> R.string.today_paused
                RecordingState.IDLE -> R.string.today_idle
            },
        )

    val container: Color
    val content: Color
    val dot: Color
    when (state) {
        RecordingState.RECORDING -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
            dot = MaterialTheme.colorScheme.secondary
        }

        RecordingState.PAUSED -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
            dot = MaterialTheme.colorScheme.tertiary
        }

        RecordingState.IDLE -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh
            content = MaterialTheme.colorScheme.onSurfaceVariant
            dot = MaterialTheme.colorScheme.outline
        }
    }

    val spoken = if (duration.isNullOrBlank()) label else "$label，$duration"

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
            modifier = Modifier.padding(horizontal = TrailSpacing.md, vertical = 6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(color = dot, shape = CircleShape),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            if (!duration.isNullOrBlank()) {
                Text(text = duration, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
