package com.hmilyld.daytrail.ui.screens.permission

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.permission.Permissions
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 权限自检页。
 *
 * 与首启引导的区别：
 * - 引导页是「一次性流程」，完成后不再出现
 * - 本页是「随时可查的诊断工具」，任何一项异常都能在这里定位并修复
 *
 * 现实场景：应用被系统回收权限、用户手动关掉定位开关、ROM 升级后重置白名单。
 * 这些情况都需要一个入口让用户自己排查，而不是只能看到一个「没在记录」的结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var hasLocation by remember { mutableStateOf(Permissions.hasFineLocation(context)) }
    var hasBackground by remember { mutableStateOf(Permissions.hasBackgroundLocation(context)) }
    var hasNotification by remember { mutableStateOf(Permissions.hasNotification(context)) }
    var ignoringBattery by remember {
        mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context))
    }
    var locationServiceOn by remember {
        mutableStateOf(Permissions.isLocationServiceEnabled(context))
    }

    // 每次从系统设置返回都重新检查：用户可能刚改过
    LifecycleResumeEffect(Unit) {
        hasLocation = Permissions.hasFineLocation(context)
        hasBackground = Permissions.hasBackgroundLocation(context)
        hasNotification = Permissions.hasNotification(context)
        ignoringBattery = Permissions.isIgnoringBatteryOptimizations(context)
        locationServiceOn = Permissions.isLocationServiceEnabled(context)
        onPauseOrDispose {}
    }

    PermissionContent(
        hasLocation = hasLocation,
        hasBackground = hasBackground,
        hasNotification = hasNotification,
        ignoringBattery = ignoringBattery,
        locationServiceOn = locationServiceOn,
        onOpenLocationSettings = { Permissions.openAppSettings(context) },
        onOpenLocationSwitch = { Permissions.openLocationSettings(context) },
        onOpenNotificationSettings = { Permissions.openNotificationSettings(context) },
        onOpenBatterySettings = { Permissions.openBatteryOptimizationSettings(context) },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionContent(
    hasLocation: Boolean,
    hasBackground: Boolean,
    hasNotification: Boolean,
    ignoringBattery: Boolean,
    locationServiceOn: Boolean,
    onOpenLocationSettings: () -> Unit,
    onOpenLocationSwitch: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.permission_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
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
                    Text(
                        text = stringResource(R.string.permission_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                top = TrailSpacing.sm,
                                bottom = TrailSpacing.md,
                            ),
                    )

                    PermissionRow(
                        icon = Icons.Outlined.MyLocation,
                        title = stringResource(R.string.permission_location_title),
                        detail = stringResource(R.string.permission_location_detail),
                        ok = hasLocation,
                        actionLabel = stringResource(R.string.permission_action_open),
                        onAction = onOpenLocationSettings,
                    )

                    // 定位开关：权限给了也可能被关掉，必须单独列
                    PermissionRow(
                        icon = Icons.Outlined.LocationOn,
                        title = stringResource(R.string.permission_switch_title),
                        detail = stringResource(R.string.permission_switch_detail),
                        ok = locationServiceOn,
                        actionLabel = stringResource(R.string.permission_action_turn_on),
                        onAction = onOpenLocationSwitch,
                    )

                    PermissionRow(
                        icon = Icons.Outlined.LocationOn,
                        title = stringResource(R.string.permission_background_title),
                        detail = stringResource(R.string.permission_background_detail),
                        ok = hasBackground,
                        actionLabel = stringResource(R.string.permission_action_open),
                        onAction = onOpenLocationSettings,
                    )

                    PermissionRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.permission_notification_title),
                        detail = stringResource(R.string.permission_notification_detail),
                        ok = hasNotification,
                        actionLabel = stringResource(R.string.permission_action_open),
                        onAction = onOpenNotificationSettings,
                    )

                    PermissionRow(
                        icon = Icons.Outlined.BatterySaver,
                        title = stringResource(R.string.permission_battery_title),
                        detail = stringResource(R.string.permission_battery_detail),
                        ok = ignoringBattery,
                        actionLabel = stringResource(R.string.permission_action_add),
                        onAction = onOpenBatterySettings,
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = TrailSpacing.lg),
                    ) {
                        Column(modifier = Modifier.padding(TrailSpacing.lg)) {
                            Text(
                                text = stringResource(R.string.permission_tips_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.permission_tips_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(bottom = TrailSpacing.xxl)) {}
                }
            }
        }
    }
}

/** 一项权限的状态行：正常显示绿色对勾，异常显示红色感叹号与修复按钮。 */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = TrailSpacing.xs),
    ) {
        Row(
            modifier = Modifier.padding(TrailSpacing.lg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color =
                    if (ok) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                        if (ok) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    modifier = Modifier.padding(10.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector =
                            if (ok) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.ErrorOutline
                            },
                        contentDescription = null,
                        tint =
                            if (ok) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!ok) {
                    FilledTonalButton(
                        onClick = onAction,
                        // heightIn 而不是 height：字体放大 200% 时固定高度会把文字裁掉
                        modifier = Modifier.padding(top = TrailSpacing.xs).heightIn(min = 40.dp),
                    ) {
                        Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 权限是否齐备。同步读取，可在组合期直接算第一帧的颜色。 */
private fun permissionsHealthy(context: Context): Boolean =
    Permissions.hasFineLocation(context) &&
        Permissions.hasBackgroundLocation(context) &&
        Permissions.hasNotification(context) &&
        Permissions.isLocationServiceEnabled(context)

/** 供设置页复用：一行显示当前权限健康度。 */
@Composable
fun PermissionHealthSummary(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 初始值必须同步读真实状态。早期写死 false，导致每次进入页面第一帧是红色，
    // resume 后才变绿——用户看到的是「先红后绿」的闪烁，像是权限刚坏又恢复。
    var healthy by remember { mutableStateOf(permissionsHealthy(context)) }

    LifecycleResumeEffect(Unit) {
        healthy = permissionsHealthy(context)
        onPauseOrDispose {}
    }

    Surface(
        color =
            if (healthy) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (healthy) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TrailSpacing.lg, vertical = TrailSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md),
        ) {
            Icon(
                imageVector =
                    if (healthy) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
            )
            Text(
                text =
                    if (healthy) {
                        stringResource(R.string.permission_all_ok)
                    } else {
                        stringResource(R.string.permission_has_issue)
                    },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onOpen) {
                Text(text = stringResource(R.string.permission_check))
            }
        }
    }
}

@Preview(showBackground = true, name = "权限 - 全部正常")
@Composable
private fun PermissionOkPreview() {
    DayTrailTheme {
        PermissionContent(
            hasLocation = true,
            hasBackground = true,
            hasNotification = true,
            ignoringBattery = true,
            locationServiceOn = true,
            onOpenLocationSettings = {},
            onOpenLocationSwitch = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "权限 - 有问题")
@Composable
private fun PermissionIssuePreview() {
    DayTrailTheme {
        PermissionContent(
            hasLocation = true,
            hasBackground = false,
            hasNotification = false,
            ignoringBattery = false,
            locationServiceOn = false,
            onOpenLocationSettings = {},
            onOpenLocationSwitch = {},
            onOpenNotificationSettings = {},
            onOpenBatterySettings = {},
            onBack = {},
        )
    }
}
