package com.hmilyld.daytrail.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.DayTrailApplication
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import kotlinx.coroutines.launch

/**
 * 首启引导。
 *
 * 顺序有意为之：先位置，再后台位置（必须跳系统设置页单独引导，
 * 不能与应用内弹窗合并），再通知，最后电池优化。
 * 全部完成后写入 onboardingCompleted，下次启动直接进主页。
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    OnboardingContent(
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
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 48.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        PermissionItem(
            icon = Icons.Outlined.MyLocation,
            title = stringResource(R.string.onboarding_location_title),
            body = stringResource(R.string.onboarding_location_body),
        )
        PermissionItem(
            icon = Icons.Outlined.LocationOn,
            title = stringResource(R.string.onboarding_background_title),
            body = stringResource(R.string.onboarding_background_body),
        )
        PermissionItem(
            icon = Icons.Outlined.Notifications,
            title = stringResource(R.string.onboarding_notification_title),
            body = stringResource(R.string.onboarding_notification_body),
        )
        PermissionItem(
            icon = Icons.Outlined.BatterySaver,
            title = stringResource(R.string.onboarding_battery_title),
            body = stringResource(R.string.onboarding_battery_body),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = stringResource(R.string.onboarding_action_finish))
        }

        TextButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(text = stringResource(R.string.action_cancel))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    body: String,
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
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
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
            }
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "引导页")
@Composable
private fun OnboardingPreview() {
    DayTrailTheme {
        OnboardingContent(onFinish = {})
    }
}
