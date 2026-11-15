package com.hmilyld.daytrail.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.prefs.ThemeMode
import com.hmilyld.daytrail.permission.Permissions
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.screens.permission.PermissionHealthSummary
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 设置页。
 *
 * 按 Material 3 规范组织：
 * - `TopAppBar` 作为页面标题，不再用大标题塞进滚动内容
 * - 分组用「小节标题 + 卡片」而不是满屏分割线
 * - 每行用 `ListItem`：前置图标 + 主标题 + 副标题 + 尾部控件
 * - 主题用 `SingleChoiceSegmentedButtonRow`，三项平铺比下拉少一次点击
 * - 危险操作用红色文字 + 二次确认对话框
 * - 正文最宽 840dp 居中，平板上不会拉成一行
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = appViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsContent(
        state = state,
        onTierChange = viewModel::setSamplingTier,
        onLearningChange = viewModel::setLearningEnabled,
        onThemeChange = viewModel::setThemeMode,
        onClearAll = viewModel::clearAllData,
        onOpenPermissions = onOpenPermissions,
        onOpenLocationSettings = { Permissions.openAppSettings(context) },
        onOpenNotificationSettings = { Permissions.openNotificationSettings(context) },
        onOpenBatterySettings = { Permissions.requestIgnoreBatteryOptimizations(context) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onTierChange: (SamplingTier) -> Unit,
    onLearningChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onClearAll: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.widthIn(max = 840.dp)) {
                    // ---- 外观 ----
                    SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                        ThemeSelector(
                            selected = state.themeMode,
                            onSelect = onThemeChange,
                        )
                    }

                    // ---- 记录 ----
                    SettingsSection(title = stringResource(R.string.settings_section_recording)) {
                        SamplingTierSelector(
                            selected = state.samplingTier,
                            onSelect = onTierChange,
                        )
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Psychology,
                            title = stringResource(R.string.settings_learning),
                            subtitle = stringResource(R.string.settings_learning_desc),
                            checked = state.learningEnabled,
                            onCheckedChange = onLearningChange,
                        )
                    }

                    // ---- 权限 ----
                    SettingsSection(title = stringResource(R.string.settings_section_permission)) {
                        PermissionHealthSummary(
                            onOpen = onOpenPermissions,
                            modifier = Modifier.padding(8.dp),
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.MyLocation,
                            title = stringResource(R.string.settings_permission_location),
                            subtitle = stringResource(R.string.settings_permission_location_desc),
                            onClick = onOpenLocationSettings,
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.Notifications,
                            title = stringResource(R.string.settings_permission_notification),
                            subtitle =
                                stringResource(
                                    R.string.settings_permission_notification_desc,
                                ),
                            onClick = onOpenNotificationSettings,
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.BatterySaver,
                            title = stringResource(R.string.settings_permission_battery),
                            subtitle = stringResource(R.string.settings_permission_battery_desc),
                            onClick = onOpenBatterySettings,
                        )
                    }

                    // ---- 数据 ----
                    SettingsSection(title = stringResource(R.string.settings_section_data)) {
                        SettingsInfoRow(
                            icon = Icons.Outlined.Storage,
                            title = stringResource(R.string.settings_storage),
                            subtitle =
                                stringResource(
                                    R.string.settings_storage_points,
                                    state.pointCount,
                                ),
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.DeleteOutline,
                            title = stringResource(R.string.settings_clear_all),
                            subtitle = stringResource(R.string.settings_clear_confirm_body),
                            onClick = { showClearDialog = true },
                            destructive = true,
                        )
                    }

                    // ---- 关于 ----
                    SettingsSection(title = stringResource(R.string.settings_section_about)) {
                        SettingsInfoRow(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.settings_version),
                            subtitle = "0.1.0",
                        )
                    }

                    Column(modifier = Modifier.padding(bottom = 32.dp)) {}
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
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

/**
 * 设置分组：小节标题 + 圆角卡片。
 *
 * 用卡片而不是分割线：Material 3 里分组靠「容器色调 + 圆角」表达层级，
 * 满屏分割线会让页面显得碎片化。
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
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
private fun ThemeSelector(
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

    Column(modifier = Modifier.padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, labelAndIcon) ->
                val (label, icon) = labelAndIcon
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
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
private fun SamplingTierSelector(
    selected: SamplingTier,
    onSelect: (SamplingTier) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                RadioButton(
                    selected = tier == selected,
                    onClick = null,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** 带开关的行。整行可点击，命中区域大于开关本身。 */
@Composable
private fun SettingsSwitchRow(
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
private fun SettingsNavRow(
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
private fun SettingsInfoRow(
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

@Preview(showBackground = true, name = "设置 - 浅色")
@Composable
private fun SettingsLightPreview() {
    DayTrailTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsUiState(pointCount = 1234),
            onTierChange = {},
            onLearningChange = {},
            onThemeChange = {},
            onClearAll = {},
            onOpenPermissions = {},
            onOpenLocationSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
        )
    }
}

@Preview(showBackground = true, name = "设置 - 深色")
@Composable
private fun SettingsDarkPreview() {
    DayTrailTheme(darkTheme = true) {
        SettingsContent(
            state =
                SettingsUiState(
                    pointCount = 1234,
                    themeMode = ThemeMode.DARK,
                    learningEnabled = false,
                ),
            onTierChange = {},
            onLearningChange = {},
            onThemeChange = {},
            onClearAll = {},
            onOpenPermissions = {},
            onOpenLocationSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
        )
    }
}
