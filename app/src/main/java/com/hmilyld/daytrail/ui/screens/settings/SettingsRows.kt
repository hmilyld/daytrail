package com.hmilyld.daytrail.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.prefs.ThemeMode
import com.hmilyld.daytrail.ui.components.trailSegmentedColors
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 设置分组：小节标题 + 圆角卡片。
 *
 * 用卡片而不是分割线：Material 3 里分组靠「容器色调 + 圆角」表达层级，
 * 满屏分割线会让页面显得碎片化。
 */
@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.padding(
                    start = TrailSpacing.xs,
                    top = 20.dp,
                    bottom = TrailSpacing.sm,
                ),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                content()
            }
        }
    }
}

/** 主题选择：跟随系统 / 浅色 / 深色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options =
        listOf(
            ThemeMode.SYSTEM to
                (
                    stringResource(
                        R.string.settings_theme_system,
                    ) to Icons.Outlined.SettingsBrightness
                ),
            ThemeMode.LIGHT to
                (stringResource(R.string.settings_theme_light) to Icons.Outlined.LightMode),
            ThemeMode.DARK to
                (stringResource(R.string.settings_theme_dark) to Icons.Outlined.DarkMode),
        )

    Column(modifier = Modifier.padding(TrailSpacing.lg)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, labelAndIcon) ->
                val (label, icon) = labelAndIcon
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = trailSegmentedColors(),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = mode == selected) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        }
                    },
                ) {
                    Text(text = label)
                }
            }
        }
    }
}

/** 采样档位选择。只有三项，用单选列表比下拉更直接。 */
@Composable
internal fun SamplingTierSelector(
    selected: SamplingTier,
    onSelect: (SamplingTier) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = TrailSpacing.xs)) {
        SamplingTier.entries.forEach { tier ->
            val label =
                when (tier) {
                    SamplingTier.POWER_SAVER -> stringResource(R.string.settings_tier_power_saver)
                    SamplingTier.BALANCED -> stringResource(R.string.settings_tier_balanced)
                    SamplingTier.HIGH_ACCURACY ->
                        stringResource(
                            R.string.settings_tier_high_accuracy,
                        )
                }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onSelect(tier) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.sm),
            ) {
                RadioButton(
                    selected = tier == selected,
                    onClick = null,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = TrailSpacing.md),
                )
            }
        }
    }
}

/** 带开关的行。整行可点击，命中区域大于开关本身。 */
@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        },
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onCheckedChange(!checked) }
                .heightIn(min = 56.dp),
    )
}

/** 可跳转的行：尾部有箭头。 */
@Composable
internal fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color =
                    if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        },
        supportingContent = { Text(text = subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        },
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp),
    )
}

/** 纯信息行：不可点击，无箭头。 */
@Composable
internal fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}
