package com.hmilyld.daytrail.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.ui.components.SectionHeader
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 设置页。
 *
 * 布局约束：
 * - 正文最宽 840dp 居中，避免平板上一行拉太长
 * - 可点击行最小高度 56dp（超过 48dp 的触达下限）
 * - 分节用 [SectionHeader]，不画额外分割线
 */
@Composable
fun SettingsScreen(
    container: com.hmilyld.daytrail.AppContainer,
    modifier: Modifier = Modifier,
) {
    SettingsContent(
        learningEnabled = true,
        onLearningChange = {},
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    learningEnabled: Boolean,
    onLearningChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )

        SectionHeader(text = stringResource(R.string.settings_section_recording))

        SettingsRow(
            title = stringResource(R.string.settings_sampling_tier),
            subtitle = stringResource(R.string.settings_sampling_tier_desc),
            onClick = {},
        )

        SwitchRow(
            title = stringResource(R.string.settings_learning),
            subtitle = stringResource(R.string.settings_learning_desc),
            checked = learningEnabled,
            onCheckedChange = onLearningChange,
        )

        SectionHeader(text = stringResource(R.string.settings_section_data))

        SettingsRow(
            title = stringResource(R.string.settings_export),
            subtitle = stringResource(R.string.settings_export_desc),
            onClick = {},
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

/** 普通设置行：标题 + 说明 + 右侧箭头。 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    onClick: (() -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
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
                    color = MaterialTheme.colorScheme.onSurface,
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
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
        SettingsContent(learningEnabled = true, onLearningChange = {})
    }
}
