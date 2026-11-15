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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hmilyld.daytrail.DayTrailApplication
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.permission.Permissions
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
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

    OnboardingContent(
        hasLocation = hasLocation,
        hasBackground = hasBackground,
        hasNotification = hasNotification,
        ignoringBattery = ignoringBattery,
        locationServiceOn = locationServiceOn,
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
        onRequestBattery = { Permissions.requestIgnoreBatteryOptimizations(context) },
        onFinish = {
            scope.launch {
                val app = context.applicationContext as DayTrailApplication
                app.settingsStore.setOnboardingCompleted(true)
                onFinished()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun OnboardingContent(
    hasLocation: Boolean,
    hasBackground: Boolean,
    hasNotification: Boolean,
    ignoringBattery: Boolean,
    locationServiceOn: Boolean,
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
                .padding(horizontal = 24.dp),
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp)) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 48.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
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

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(text = stringResource(R.string.onboarding_action_finish))
            }

            if (!hasLocation) {
                Text(
                    text = stringResource(R.string.onboarding_location_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(text = stringResource(R.string.onboarding_action_skip))
            }

            Spacer(modifier = Modifier.height(32.dp))
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
            modifier = Modifier.padding(16.dp),
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                        modifier = Modifier.padding(top = 4.dp),
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
