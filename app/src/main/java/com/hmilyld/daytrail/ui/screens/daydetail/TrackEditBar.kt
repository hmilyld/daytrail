package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.ui.resolve
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 手动微调的工具条。
 *
 * 说明当前该做什么、撤销/删除、放弃与保存。
 *
 * **它必须排在布局流里（地图下方），不能叠在地图上**：高德 logo 与版权固定在
 * 地图左下角，整屏宽的浮层压上去会违反 SDK 条款（不得遮挡 logo），
 * 这也是 `MapAccessibilityOverlay` 一律放到右下角的原因。
 *
 * 编辑态外层 Scaffold 不消费 inset，所以底部与左右的安全区要自己避让。
 */
@Composable
fun TrackEditBar(
    state: TrackEditUiState,
    onUndo: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint =
        when {
            state.notice != null -> state.notice.resolve()
            state.recording -> stringResource(R.string.edit_recording_notice)
            else -> stringResource(R.string.edit_hint)
        }
    val hintColor =
        if (state.notice != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        modifier =
            modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                ).padding(TrailSpacing.md)
                .fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = hintColor,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
            ) {
                TextButton(
                    onClick = onUndo,
                    enabled = state.undoDepth > 0 && !state.saving,
                    modifier = Modifier.heightIn(min = TOUCH_MIN),
                ) {
                    Text(stringResource(R.string.edit_action_undo))
                }
                // 「删除」只在选中顶点时出现：常驻会让工具条一直挤着，
                // 而且没有选中时它本来就是无效操作。
                if (state.hasSelection) {
                    TextButton(
                        onClick = onDelete,
                        enabled = state.canDelete && !state.saving,
                        modifier = Modifier.heightIn(min = TOUCH_MIN),
                    ) {
                        Text(
                            text = stringResource(R.string.edit_action_delete),
                            color =
                                if (state.canDelete) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onCancel,
                    enabled = !state.saving,
                    modifier = Modifier.heightIn(min = TOUCH_MIN),
                ) {
                    Text(stringResource(R.string.edit_action_discard))
                }
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier.heightIn(min = TOUCH_MIN),
                ) {
                    Text(
                        stringResource(
                            if (state.saving) {
                                R.string.edit_action_saving
                            } else {
                                R.string.edit_action_save
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** 工具条按钮的触达高度。低于 48dp 不符合无障碍要求。 */
private val TOUCH_MIN = 48.dp
