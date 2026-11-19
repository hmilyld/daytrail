package com.hmilyld.daytrail.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.hmilyld.daytrail.data.export.ExportFormat
import com.hmilyld.daytrail.data.export.ExportRange
import com.hmilyld.daytrail.data.export.ExportResult
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.prefs.ThemeMode
import com.hmilyld.daytrail.permission.Permissions
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.screens.permission.PermissionHealthSummary
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    onOpenPlaces: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = appViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    // 选好的导出范围。launcher 回调只给 URI，所以先存在这里。
    var pendingRange by remember { mutableStateOf(ExportRange()) }

    // 在组合期解析文案：LocalContext.getString 不是配置感知的，
    // 语言或字体缩放变化时可能返回过期值（lint 会报错）。
    val exportSuccessTemplate = stringResource(R.string.export_success)
    val exportEmptyText = stringResource(R.string.export_empty)
    val exportFailedTemplate = stringResource(R.string.export_failed)

    /** 导出结果反馈。用 Toast 而不是 Snackbar：设置页没有 Scaffold 宿主，最省事。 */
    fun report(result: ExportResult) {
        val message =
            when (result) {
                is ExportResult.Success ->
                    String.format(
                        exportSuccessTemplate,
                        result.dayCount,
                        result.pointCount,
                    )
                ExportResult.Empty -> exportEmptyText
                is ExportResult.Failure -> String.format(exportFailedTemplate, result.message)
            }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun runExport(
        uri: android.net.Uri,
        format: ExportFormat,
    ) {
        scope.launch {
            report(container.exportManager.export(uri, format, pendingRange))
        }
    }

    // 两种格式各一个 launcher：CreateDocument 的 MIME 类型在创建时固定
    val geojsonLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/geo+json"),
        ) { uri -> uri?.let { runExport(it, ExportFormat.GEOJSON) } }

    val gpxLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gpx+xml"),
        ) { uri -> uri?.let { runExport(it, ExportFormat.GPX) } }

    SettingsContent(
        state = state,
        onTierChange = viewModel::setSamplingTier,
        onLearningChange = viewModel::setLearningEnabled,
        onAutoSleepChange = viewModel::setAutoSleep,
        onThemeChange = viewModel::setThemeMode,
        onClearAll = viewModel::clearAllData,
        onOpenPermissions = onOpenPermissions,
        onOpenPlaces = onOpenPlaces,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenLocationSettings = { Permissions.openAppSettings(context) },
        onOpenNotificationSettings = { Permissions.openNotificationSettings(context) },
        onOpenBatterySettings = { Permissions.requestIgnoreBatteryOptimizations(context) },
        onExportClick = { showExportDialog = true },
        modifier = modifier,
    )

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onPick = { format, range ->
                showExportDialog = false
                pendingRange = range
                scope.launch {
                    val name = container.exportManager.suggestedName(format, range)
                    when (format) {
                        ExportFormat.GEOJSON -> geojsonLauncher.launch(name)
                        ExportFormat.GPX -> gpxLauncher.launch(name)
                    }
                }
            },
        )
    }
}

/** 导出范围选项。 */
private enum class ExportRangeOption { ALL, LAST_7, THIS_MONTH, CUSTOM }

/** 选择导出格式与范围。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onPick: (ExportFormat, ExportRange) -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.GEOJSON) }
    var option by remember { mutableStateOf(ExportRangeOption.LAST_7) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val range =
        when (option) {
            ExportRangeOption.ALL -> ExportRange()
            ExportRangeOption.LAST_7 -> {
                ExportRange(today.minusDays(6).toString(), today.toString())
            }
            ExportRangeOption.THIS_MONTH -> {
                ExportRange(today.withDayOfMonth(1).toString(), today.toString())
            }
            ExportRangeOption.CUSTOM -> ExportRange(customFrom?.toString(), customTo?.toString())
        }
    val fromDate = customFrom
    val toDate = customTo
    val rangeReady =
        option != ExportRangeOption.CUSTOM ||
            (fromDate != null && toDate != null && !fromDate.isAfter(toDate))

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Outlined.IosShare, contentDescription = null) },
        title = { Text(text = stringResource(R.string.export_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.export_format_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val formats = ExportFormat.entries
                    formats.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = format == value,
                            onClick = { format = value },
                            shape = SegmentedButtonDefaults.itemShape(index, formats.size),
                        ) {
                            Text(
                                text = stringResource(formatTitle(value)),
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.export_range),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = ExportRangeOption.entries
                    options.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = option == value,
                            onClick = { option = value },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) {
                            Text(text = stringResource(rangeTitle(value)), maxLines = 1)
                        }
                    }
                }

                if (option == ExportRangeOption.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickingStart = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text =
                                    customFrom?.toString()
                                        ?: stringResource(R.string.export_range_start),
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = { pickingEnd = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text =
                                    customTo?.toString()
                                        ?: stringResource(R.string.export_range_end),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(format, range) },
                enabled = rangeReady,
            ) {
                Text(text = stringResource(R.string.export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )

    if (pickingStart) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    customFrom?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            customFrom =
                                Instant
                                    .ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                        }
                        pickingStart = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingEnd) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    customTo?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            customTo =
                                Instant
                                    .ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                        }
                        pickingEnd = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingEnd = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun formatTitle(format: ExportFormat): Int =
    when (format) {
        ExportFormat.GEOJSON -> R.string.export_geojson_title
        ExportFormat.GPX -> R.string.export_gpx_title
    }

private fun rangeTitle(option: ExportRangeOption): Int =
    when (option) {
        ExportRangeOption.ALL -> R.string.export_range_all
        ExportRangeOption.LAST_7 -> R.string.export_range_last_7
        ExportRangeOption.THIS_MONTH -> R.string.export_range_month
        ExportRangeOption.CUSTOM -> R.string.export_range_custom
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onTierChange: (SamplingTier) -> Unit,
    onLearningChange: (Boolean) -> Unit,
    onAutoSleepChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onClearAll: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onExportClick: () -> Unit,
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
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Bedtime,
                            title = stringResource(R.string.settings_auto_sleep),
                            subtitle = stringResource(R.string.settings_auto_sleep_desc),
                            checked = state.autoSleep,
                            onCheckedChange = onAutoSleepChange,
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.Place,
                            title = stringResource(R.string.settings_places),
                            subtitle = stringResource(R.string.settings_places_desc),
                            onClick = onOpenPlaces,
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
                        SettingsNavRow(
                            icon = Icons.Outlined.IosShare,
                            title = stringResource(R.string.settings_export),
                            subtitle = stringResource(R.string.settings_export_desc),
                            onClick = onExportClick,
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.ReceiptLong,
                            title = stringResource(R.string.settings_diagnostics),
                            subtitle = stringResource(R.string.settings_diagnostics_desc),
                            onClick = onOpenDiagnostics,
                        )
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
            onAutoSleepChange = {},
            onThemeChange = {},
            onClearAll = {},
            onOpenPermissions = {},
            onOpenPlaces = {},
            onOpenDiagnostics = {},
            onOpenLocationSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onExportClick = {},
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
            onAutoSleepChange = {},
            onThemeChange = {},
            onClearAll = {},
            onOpenPermissions = {},
            onOpenPlaces = {},
            onOpenDiagnostics = {},
            onOpenLocationSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onExportClick = {},
        )
    }
}
