package com.hmilyld.daytrail.ui.screens.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hmilyld.daytrail.DayTrailApplication
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.permission.Permissions
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import kotlinx.coroutines.launch

/**
 * 首启引导。
 *
 * 这一页**必须真正发起权限请求**，而不只是展示说明。
 * 早期版本只写文案、点「开始使用」直接进主页，结果：
 * - 通知权限没授予 → Android 13+ 静默丢弃通知，前台服务没有可见通知
 * - 定位权限没授予 → 高德定位直接失败，用户以为「GPS 没调起来」
 *
 * 顺序有意为之：前台位置 → 后台位置（必须跳系统设置页）→ 通知 → 电池优化。
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 权限状态。每次从系统设置页返回都要重新读取（ON_RESUME）。
    var hasLocation by remember { mutableStateOf(Permissions.hasFineLocation(context)) }
    var hasBackground by remember { mutableStateOf(Permissions.hasBackgroundLocation(context)) }
    var hasNotification by remember { mutableStateOf(Permissions.hasNotification(context)) }
    var ignoringBattery by remember {
        mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context))
    }
    var locationServiceOn by remember {
        mutableStateOf(
            Permissions.isLocationServiceEnabled(context),
        )
    }

    LifecycleResumeEffect(Unit) {
        hasLocation = Permissions.hasFineLocation(context)
        hasBackground = Permissions.hasBackgroundLocation(context)
        hasNotification = Permissions.hasNotification(context)
        ignoringBattery = Permissions.isIgnoringBatteryOptimizations(context)
        locationServiceOn = Permissions.isLocationServiceEnabled(context)
        onPauseOrDispose {}
    }

    val locationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasLocation = Permissions.hasFineLocation(context)
        }

    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasNotification = Permissions.hasNotification(context)
        }

    // 隐私同意的草稿状态：只有点「开始使用」时才落库。
    // 不边勾边存，避免用户勾了又退出、下次进来以为已完成引导。
    var consentChecked by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }

    OnboardingContent(
        hasLocation = hasLocation,
        hasBackground = hasBackground,
        hasNotification = hasNotification,
        ignoringBattery = ignoringBattery,
        locationServiceOn = locationServiceOn,
        consentChecked = consentChecked,
        onConsentChange = { consentChecked = it },
        onShowPolicy = { showPolicy = true },
        onRequestLocation = {
            // 精确定位被永久拒绝时，系统对话框不会再弹，只能去设置页
            locationLauncher.launch(Permissions.LOCATION_FOREGROUND)
        },
        onOpenLocationSettings = { Permissions.openLocationSettings(context) },
        onRequestBackground = { Permissions.openBackgroundLocationGuide(context) },
        onRequestNotification = {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onOpenNotificationSettings = { Permissions.openNotificationSettings(context) },
        onRequestBattery = { Permissions.openBatteryOptimizationSettings(context) },
        onFinish = finish@{
            // 按钮本身已按 consentChecked 禁用，这里再挡一次：
            // 同意是记录行踪轨迹（敏感个人信息）的前置条件，不能被任何路径绕过。
            if (!consentChecked) return@finish
            val app = context.applicationContext as DayTrailApplication
            // 先把同意同步给地图 SDK（必须早于任何 MapView），再落库。
            app.agreeAmapPrivacy()
            scope.launch {
                app.settingsStore.setPrivacyAccepted(true)
                app.settingsStore.setOnboardingCompleted(true)
                onFinished()
            }
        },
        modifier = modifier,
    )

    if (showPolicy) {
        AlertDialog(
            onDismissRequest = { showPolicy = false },
            title = { Text(text = stringResource(R.string.privacy_dialog_title)) },
            text = {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.privacy_dialog_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPolicy = false }) {
                    Text(text = stringResource(R.string.privacy_close))
                }
            },
        )
    }
}

@Composable
private fun OnboardingContent(
    hasLocation: Boolean,
    hasBackground: Boolean,
    hasNotification: Boolean,
    ignoringBattery: Boolean,
    locationServiceOn: Boolean,
    consentChecked: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onShowPolicy: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRequestBackground: () -> Unit,
    onRequestNotification: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestBattery: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TrailSpacing.xl),
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp)) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 48.dp, bottom = TrailSpacing.sm),
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = TrailSpacing.xl),
            )

            // 同意块放在权限项之前：告知-同意必须在任何信息被处理之前完成。
            PrivacyConsentBlock(
                checked = consentChecked,
                onCheckedChange = onConsentChange,
                onShowPolicy = onShowPolicy,
            )

            PermissionItem(
                icon = Icons.Outlined.MyLocation,
                title = stringResource(R.string.onboarding_location_title),
                body = stringResource(R.string.onboarding_location_body),
                granted = hasLocation,
                actionLabel =
                    if (hasLocation) {
                        null
                    } else {
                        stringResource(R.string.onboarding_action_grant)
                    },
                onAction = onRequestLocation,
            )

            // 定位开关单独一行：权限给了但开关关掉，仍然拿不到位置
            if (hasLocation && !locationServiceOn) {
                WarningBanner(
                    text = stringResource(R.string.onboarding_location_service_off),
                    actionLabel = stringResource(R.string.onboarding_action_open_settings),
                    onAction = onOpenLocationSettings,
                )
            }

            PermissionItem(
                icon = Icons.Outlined.LocationOn,
                title = stringResource(R.string.onboarding_background_title),
                body = stringResource(R.string.onboarding_background_body),
                granted = hasBackground,
                actionLabel =
                    if (hasBackground) {
                        null
                    } else {
                        stringResource(R.string.onboarding_action_open_settings)
                    },
                onAction = onRequestBackground,
            )

            PermissionItem(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.onboarding_notification_title),
                body = stringResource(R.string.onboarding_notification_body),
                granted = hasNotification,
                actionLabel =
                    if (hasNotification) {
                        null
                    } else {
                        stringResource(R.string.onboarding_action_grant)
                    },
                onAction = onRequestNotification,
            )

            if (!hasNotification) {
                TextButton(onClick = onOpenNotificationSettings) {
                    Text(
                        text =
                            stringResource(
                                R.string.onboarding_action_open_notification_settings,
                            ),
                    )
                }
            }

            PermissionItem(
                icon = Icons.Outlined.BatterySaver,
                title = stringResource(R.string.onboarding_battery_title),
                body = stringResource(R.string.onboarding_battery_body),
                granted = ignoringBattery,
                actionLabel =
                    if (ignoringBattery) {
                        null
                    } else {
                        stringResource(R.string.onboarding_action_open_settings)
                    },
                onAction = onRequestBattery,
            )

            Spacer(modifier = Modifier.height(TrailSpacing.xxl))

            Button(
                onClick = onFinish,
                enabled = consentChecked,
                // heightIn 而不是 height：字体放大 200% 时固定高度会把文字裁掉
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(text = stringResource(R.string.onboarding_action_finish))
            }

            if (!consentChecked) {
                Text(
                    text = stringResource(R.string.privacy_consent_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = TrailSpacing.sm),
                )
            }

            if (!hasLocation) {
                Text(
                    text = stringResource(R.string.onboarding_location_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = TrailSpacing.sm),
                )
            }

            TextButton(
                onClick = onFinish,
                enabled = consentChecked,
                modifier = Modifier.fillMaxWidth().padding(top = TrailSpacing.xs),
            ) {
                Text(text = stringResource(R.string.onboarding_action_skip))
            }

            Spacer(modifier = Modifier.height(TrailSpacing.xxl))
        }
    }
}

/** 一条权限项。已授予显示对勾，未授予显示操作按钮。 */
@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(TrailSpacing.lg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color =
                    if (granted) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                        if (granted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!granted && actionLabel != null) {
                    FilledTonalButton(
                        onClick = onAction,
                        modifier = Modifier.padding(top = TrailSpacing.xs),
                    ) {
                        Text(text = actionLabel)
                    }
                }
            }
            if (granted) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.onboarding_granted),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** 需要立即处理的警告，例如系统定位开关被关掉。 */
@Composable
private fun WarningBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = TrailSpacing.xs),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

/**
 * 隐私同意块。
 *
 * 用独立勾选项，而不是「点继续即视为同意」：行踪轨迹属于《个人信息保护法》
 * 定义的敏感个人信息，需要**单独告知 + 单独同意**。把同意混进权限弹窗或
 * 通用条款链接，都不构成有效同意。
 *
 * 整行用 toggleable 承载语义（而不是让 Checkbox 自己可点），
 * 这样读屏只会看到一个节点，点文字也能勾选。
 */
@Composable
private fun PrivacyConsentBlock(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onShowPolicy: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = TrailSpacing.lg),
    ) {
        Column(modifier = Modifier.padding(TrailSpacing.lg)) {
            Text(
                text = stringResource(R.string.privacy_consent_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.privacy_consent_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TrailSpacing.sm),
            )
            TextButton(onClick = onShowPolicy) {
                Text(text = stringResource(R.string.privacy_view_policy))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = checked,
                            onValueChange = onCheckedChange,
                            role = Role.Checkbox,
                        ),
            ) {
                // onCheckedChange = null：语义由外层 toggleable 承载，
                // 避免读屏把同一件事念两遍。
                Checkbox(checked = checked, onCheckedChange = null)
                Text(
                    text = stringResource(R.string.privacy_consent_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = TrailSpacing.sm),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "引导 - 未授权")
@Composable
private fun OnboardingPreview() {
    DayTrailTheme {
        OnboardingContent(
            hasLocation = false,
            hasBackground = false,
            hasNotification = false,
            ignoringBattery = false,
            locationServiceOn = true,
            consentChecked = false,
            onConsentChange = {},
            onShowPolicy = {},
            onRequestLocation = {},
            onOpenLocationSettings = {},
            onRequestBackground = {},
            onRequestNotification = {},
            onOpenNotificationSettings = {},
            onRequestBattery = {},
            onFinish = {},
        )
    }
}

@Preview(showBackground = true, name = "引导 - 已授权")
@Composable
private fun OnboardingGrantedPreview() {
    DayTrailTheme {
        OnboardingContent(
            hasLocation = true,
            hasBackground = true,
            hasNotification = true,
            ignoringBattery = true,
            locationServiceOn = true,
            consentChecked = true,
            onConsentChange = {},
            onShowPolicy = {},
            onRequestLocation = {},
            onOpenLocationSettings = {},
            onRequestBackground = {},
            onRequestNotification = {},
            onOpenNotificationSettings = {},
            onRequestBattery = {},
            onFinish = {},
        )
    }
}
