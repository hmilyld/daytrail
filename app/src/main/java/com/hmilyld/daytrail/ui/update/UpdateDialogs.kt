package com.hmilyld.daytrail.ui.update

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.update.UpdateInfo
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import java.util.Locale

/**
 * 更新流程的全部弹窗。
 *
 * 挂在应用根组件上而不是设置页：启动时的自动检查发生在没有任何页面的时刻，
 * 弹窗必须能独立出现。设置页的手动检查走同一个 [UpdateCoordinator]，
 * 所以两处不会出现两份进度。
 */
@Composable
fun UpdateDialogs(coordinator: UpdateCoordinator) {
    val state by coordinator.state.collectAsStateWithLifecycle()

    when (val current = state) {
        UpdateUiState.Idle, UpdateUiState.Checking -> Unit

        UpdateUiState.NotConfigured ->
            MessageDialog(
                title = stringResource(R.string.update_not_configured_title),
                body = stringResource(R.string.update_not_configured_body),
                onDismiss = coordinator::dismiss,
            )

        UpdateUiState.UpToDate ->
            MessageDialog(
                title = stringResource(R.string.settings_check_update),
                body = stringResource(R.string.update_up_to_date),
                onDismiss = coordinator::dismiss,
            )

        is UpdateUiState.Failed ->
            MessageDialog(
                title = stringResource(R.string.update_failed_title),
                body = current.message,
                onDismiss = coordinator::dismiss,
            )

        is UpdateUiState.Available -> AvailableDialog(coordinator, current.info)

        is UpdateUiState.Downloading ->
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        text =
                            if (current.progress > 0f) {
                                stringResource(
                                    R.string.update_downloading,
                                    (current.progress * 100).toInt(),
                                )
                            } else {
                                stringResource(R.string.update_preparing)
                            },
                    )
                },
                text = {
                    LinearProgressIndicator(
                        progress = { current.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {},
            )

        is UpdateUiState.Ready -> ReadyDialog(coordinator, current.info)
    }
}

@Composable
private fun AvailableDialog(
    coordinator: UpdateCoordinator,
    info: UpdateInfo,
) {
    val context = LocalContext.current
    val hasApk = info.apkUrl != null

    AlertDialog(
        onDismissRequest = coordinator::postpone,
        title = { Text(text = stringResource(R.string.update_available_title, info.versionName)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (info.apkSize > 0) {
                    Text(
                        text = stringResource(R.string.update_size, formatSize(info.apkSize)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text =
                        info.notes.ifBlank { stringResource(R.string.update_no_notes) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = TrailSpacing.sm),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (hasApk) coordinator.download() else openPage(context, info.pageUrl)
                },
            ) {
                Text(
                    text =
                        stringResource(
                            if (hasApk) R.string.update_download else R.string.update_open_page,
                        ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = coordinator::postpone) {
                Text(text = stringResource(R.string.update_later))
            }
        },
    )
}

@Composable
private fun ReadyDialog(
    coordinator: UpdateCoordinator,
    info: UpdateInfo,
) {
    val needsPermission = coordinator.installPermissionRequired

    AlertDialog(
        onDismissRequest = coordinator::dismiss,
        title = { Text(text = stringResource(R.string.update_ready_title)) },
        text = {
            Text(
                text =
                    stringResource(R.string.update_ready_body) +
                        if (needsPermission) {
                            "\n\n" + stringResource(R.string.update_install_permission)
                        } else {
                            ""
                        },
            )
        },
        confirmButton = {
            TextButton(onClick = { coordinator.install() }) {
                Text(text = stringResource(R.string.update_install))
            }
        },
        dismissButton = {
            TextButton(onClick = coordinator::dismiss) {
                Text(text = stringResource(R.string.update_later))
            }
        },
    )
}

@Composable
private fun MessageDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.update_close))
            }
        },
    )
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

private fun openPage(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
