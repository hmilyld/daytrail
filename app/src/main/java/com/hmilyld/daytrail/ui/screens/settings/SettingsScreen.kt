package com.hmilyld.daytrail.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.BuildConfig
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.export.ExportFormat
import com.hmilyld.daytrail.data.export.ExportRange
import com.hmilyld.daytrail.data.export.ExportResult
import com.hmilyld.daytrail.data.import.ConflictStrategy
import com.hmilyld.daytrail.data.import.ImportPreview
import com.hmilyld.daytrail.data.import.PreviewResult
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.prefs.ThemeMode
import com.hmilyld.daytrail.location.RecordingService
import com.hmilyld.daytrail.permission.Permissions
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.screens.permission.PermissionHealthSummary
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import kotlinx.coroutines.launch

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
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = appViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDialog by rememberSaveable { mutableStateOf(false) }
    var showRevokeDialog by rememberSaveable { mutableStateOf(false) }
    // 选好的导出范围。launcher 回调只给 URI，所以先存在这里。
    var pendingRange by remember { mutableStateOf(ExportRange()) }
    val snackbarHostState = remember { SnackbarHostState() }

    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var importStrategy by remember { mutableStateOf(ConflictStrategy.SKIP) }
    var importing by remember { mutableStateOf(false) }

    val autoUpdateCheck by
        container.settingsStore.autoUpdateCheck.collectAsStateWithLifecycle(initialValue = true)

    // 在组合期解析文案：LocalContext.getString 不是配置感知的，
    // 语言或字体缩放变化时可能返回过期值（lint 会报错）。
    val exportSuccessTemplate = stringResource(R.string.export_success)
    val exportEmptyText = stringResource(R.string.export_empty)
    val exportFailedTemplate = stringResource(R.string.export_failed)

    /** 导出结果反馈。用 Snackbar：跟随主题、可被 TalkBack 播报，也不需要长驻屏幕。 */
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
        scope.launch { snackbarHostState.showSnackbar(message) }
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

    // 导入：SAF 只返回一个 URI，不申请任何存储权限。
    // 用 */* 而不是按 MIME 过滤：GPX 在不同 provider 上可能是
    // application/gpx+xml、text/xml 或 application/octet-stream，
    // 按扩展名过滤会把大量正常文件挡在外面。真正的格式判断靠内容嗅探。
    val importFailedTemplate = stringResource(R.string.import_failed)
    val importSuccessTemplate = stringResource(R.string.import_success)
    val importNothingText = stringResource(R.string.import_nothing)

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                when (val result = container.importManager.preview(uri)) {
                    is PreviewResult.Ready -> importPreview = result.preview
                    is PreviewResult.Failure ->
                        snackbarHostState.showSnackbar(
                            String.format(importFailedTemplate, result.message),
                        )
                }
            }
        }

    fun runImport(preview: ImportPreview) {
        importing = true
        scope.launch {
            val message =
                try {
                    val outcome = container.importManager.commit(preview, importStrategy)
                    if (outcome.importedPoints == 0) {
                        importNothingText
                    } else {
                        String.format(
                            importSuccessTemplate,
                            outcome.importedDays,
                            outcome.importedPoints,
                            outcome.skippedDays,
                        )
                    }
                } catch (error: Exception) {
                    String.format(importFailedTemplate, error.message ?: "写入失败")
                }
            importing = false
            snackbarHostState.showSnackbar(message)
        }
    }

    SettingsContent(
        state = state,
        onTierChange = viewModel::setSamplingTier,
        onLearningChange = viewModel::setLearningEnabled,
        onAutoSleepChange = viewModel::setAutoSleep,
        onThemeChange = viewModel::setThemeMode,
        onClearAll = viewModel::clearAllData,
        onOpenPermissions = onOpenPermissions,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenLocationSettings = { Permissions.openAppSettings(context) },
        onOpenNotificationSettings = { Permissions.openNotificationSettings(context) },
        onOpenBatterySettings = { Permissions.openBatteryOptimizationSettings(context) },
        onOpenPrivacy = { showPrivacyDialog = true },
        onImportClick = { importLauncher.launch(arrayOf("*/*")) },
        onExportClick = { showExportDialog = true },
        autoUpdateCheck = autoUpdateCheck,
        onAutoUpdateCheckChange = { enabled ->
            scope.launch { container.settingsStore.setAutoUpdateCheck(enabled) }
        },
        onCheckUpdate = { container.updateCoordinator.check(force = true) },
        snackbarHostState = snackbarHostState,
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

    if (showPrivacyDialog) {
        PrivacyDialog(
            onDismiss = { showPrivacyDialog = false },
            onRevoke = {
                showPrivacyDialog = false
                showRevokeDialog = true
            },
        )
    }

    importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            strategy = importStrategy,
            onStrategyChange = { importStrategy = it },
            onDismiss = { importPreview = null },
            onConfirm = {
                importPreview = null
                runImport(preview)
            },
        )
    }

    if (importing) {
        // 导入期间不可取消：事务已经提交，中断只会留下「以为失败了但其实成功」的困惑。
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = stringResource(R.string.import_progress)) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(text = stringResource(R.string.privacy_revoke_title)) },
            text = { Text(text = stringResource(R.string.privacy_revoke_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeDialog = false
                        // 先停服务，再落库：顺序反了会有一小段时间
                        // 「已撤回但仍在采集」。
                        RecordingService.stop(context)
                        scope.launch {
                            container.settingsStore.setPrivacyAccepted(false)
                            container.settingsStore.setOnboardingCompleted(false)
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.privacy_revoke),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
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
    onOpenDiagnostics: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
    autoUpdateCheck: Boolean = true,
    onAutoUpdateCheckChange: (Boolean) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_me),
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
                        .padding(horizontal = TrailSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.widthIn(max = 840.dp)) {
                    // ---- 概览 ----
                    // 权限是否就绪放在最上面：轨迹记不上时，九成原因是这里的某一项掉了。
                    SettingsSection(title = stringResource(R.string.mine_overview)) {
                        PermissionHealthSummary(
                            onOpen = onOpenPermissions,
                            modifier = Modifier.padding(TrailSpacing.sm),
                        )
                    }

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
                    }

                    // ---- 权限 ----
                    SettingsSection(title = stringResource(R.string.settings_section_permission)) {
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
                            icon = Icons.Outlined.FileDownload,
                            title = stringResource(R.string.settings_import),
                            subtitle = stringResource(R.string.settings_import_desc),
                            onClick = onImportClick,
                        )
                        SettingsNavRow(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
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

                    // ---- 隐私与合规 ----
                    // 单独成组而不是塞进「数据」：撤回同意是一项法律权利，
                    // 需要能被明确找到，而不是藏在导出/删除之间。
                    SettingsSection(title = stringResource(R.string.privacy_section_title)) {
                        SettingsNavRow(
                            icon = Icons.Outlined.Lock,
                            title = stringResource(R.string.privacy_row_title),
                            subtitle = stringResource(R.string.privacy_row_desc),
                            onClick = onOpenPrivacy,
                        )
                    }

                    // ---- 关于 ----
                    SettingsSection(title = stringResource(R.string.settings_section_about)) {
                        SettingsInfoRow(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.settings_version),
                            // 从 BuildConfig 读，别再写死：之前这里硬编码 0.1.0，
                            // 连发三版都没变，反馈问题时版本号是错的。
                            subtitle =
                                stringResource(
                                    R.string.settings_version_value,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE,
                                ),
                        )
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Refresh,
                            title = stringResource(R.string.settings_auto_update),
                            subtitle = stringResource(R.string.settings_auto_update_desc),
                            checked = autoUpdateCheck,
                            onCheckedChange = onAutoUpdateCheckChange,
                        )
                        SettingsNavRow(
                            icon = Icons.Outlined.Refresh,
                            title = stringResource(R.string.settings_check_update),
                            subtitle =
                                stringResource(
                                    R.string.settings_check_update_desc,
                                    BuildConfig.VERSION_NAME,
                                ),
                            onClick = onCheckUpdate,
                        )
                    }

                    Column(modifier = Modifier.padding(bottom = TrailSpacing.xxl)) {}
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
            onOpenDiagnostics = {},
            onOpenLocationSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onOpenPrivacy = {},
            onImportClick = {},
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
            onOpenDiagnostics = {},
            onOpenLocationSettings = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onOpenPrivacy = {},
            onImportClick = {},
            onExportClick = {},
        )
    }
}
