package com.hmilyld.daytrail.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.SectionHeader
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 设置页。
 *
 * 布局约束：
 * - 正文最宽 840dp 居中，避免平板上一行拉太长
 * - 可点击行最小高度 56dp（超过 48dp 的触达下限）
 * - 破坏性操作二次确认
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = appViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        state = state,
        onTierChange = viewModel::setSamplingTier,
        onLearningChange = viewModel::setLearningEnabled,
        onClearAll = viewModel::clearAllData,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onTierChange: (SamplingTier) -> Unit,
    onLearningChange: (Boolean) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp)) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )

            SectionHeader(text = stringResource(R.string.settings_section_recording))

            SamplingTierSelector(
                selected = state.samplingTier,
                onSelect = onTierChange,
            )

            SwitchRow(
                title = stringResource(R.string.settings_learning),
                subtitle = stringResource(R.string.settings_learning_desc),
                checked = state.learningEnabled,
                onCheckedChange = onLearningChange,
            )

            SectionHeader(text = stringResource(R.string.settings_section_data))

            SettingsRow(
                title = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_points, state.pointCount),
                showChevron = false,
                onClick = null,
            )

            SettingsRow(
                title = stringResource(R.string.settings_clear_all),
                subtitle = stringResource(R.string.settings_clear_confirm_body),
                showChevron = false,
                destructive = true,
                onClick = { showClearDialog = true },
            )

            SectionHeader(text = stringResource(R.string.settings_section_about))

            SettingsRow(
                title = stringResource(R.string.settings_version),
                subtitle = "0.1.0",
                showChevron = false,
                onClick = null,
            )

            Column(modifier = Modifier.padding(bottom = 24.dp)) {}
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(text = stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(text = stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAll()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 采样档位选择。用 RadioButton 而不是下拉：只有三项，平铺更少点击。 */
@Composable
private fun SamplingTierSelector(
    selected: SamplingTier,
    onSelect: (SamplingTier) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SamplingTier.entries.forEach { tier ->
                val label =
                    when (tier) {
                        SamplingTier.POWER_SAVER -> "省电优先：静止 10 分 / 步行 3 分 / 驾车 1 分"
                        SamplingTier.BALANCED -> "平衡：静止 5 分 / 步行 90 秒 / 驾车 45 秒"
                        SamplingTier.HIGH_ACCURACY -> "高精度：静止 2 分 / 步行 30 秒 / 驾车 15 秒"
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onSelect(tier) }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 12.dp),
                ) {
                    RadioButton(
                        selected = tier == selected,
                        onClick = null,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** 普通设置行：标题 + 说明 + 可选右侧箭头。 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ).heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 开关行。整行可点击，点击切换开关，命中区域大于开关本身。 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onCheckedChange(!checked) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "设置")
@Composable
private fun SettingsPreview() {
    DayTrailTheme {
        SettingsContent(
            state = SettingsUiState(pointCount = 1234),
            onTierChange = {},
            onLearningChange = {},
            onClearAll = {},
        )
    }
}
