package com.hmilyld.daytrail.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.Changelog
import com.hmilyld.daytrail.data.export.ExportFormat
import com.hmilyld.daytrail.data.export.ExportRange
import com.hmilyld.daytrail.ui.components.trailSegmentedColors
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import com.hmilyld.daytrail.ui.update.UpdateNotesView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 隐私政策摘要 + 撤回入口。 */
@Composable
internal fun PrivacyDialog(
    onDismiss: () -> Unit,
    onRevoke: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onRevoke) {
                Text(
                    text = stringResource(R.string.privacy_revoke),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.privacy_close))
            }
        },
    )
}

/** 导出范围选项。 */
private enum class ExportRangeOption { ALL, LAST_7, THIS_MONTH, CUSTOM }

/** 选择导出格式与范围。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportDialog(
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
                Spacer(modifier = Modifier.height(TrailSpacing.sm))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val formats = ExportFormat.entries
                    formats.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = format == value,
                            onClick = { format = value },
                            shape = SegmentedButtonDefaults.itemShape(index, formats.size),
                            colors = trailSegmentedColors(),
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
                Spacer(modifier = Modifier.height(TrailSpacing.sm))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = ExportRangeOption.entries
                    options.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = option == value,
                            onClick = { option = value },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            colors = trailSegmentedColors(),
                        ) {
                            Text(text = stringResource(rangeTitle(value)), maxLines = 1)
                        }
                    }
                }

                if (option == ExportRangeOption.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm)) {
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

/** CHANGELOG 在 assets 里的名字，由 `app/build.gradle.kts` 的生成任务提供。 */
private const val CHANGELOG_ASSET = "CHANGELOG.md"

/**
 * 「关于 → 版本」点开的本版本更新内容。
 *
 * 内容来自随 APK 打包的 CHANGELOG.md（离线可看，见 [Changelog]）：
 * 只取当前安装版本的那一节，而不是最新版——用户升级前看到的应当是
 * 「我装的这一版改了什么」。
 */
@Composable
internal fun VersionNotesDialog(
    versionName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var markdown by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(versionName) {
        markdown =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets
                        .open(CHANGELOG_ASSET)
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrNull()
            }
        loaded = true
    }

    val notes =
        remember(markdown, versionName) {
            markdown?.let { Changelog.sectionFor(it, versionName) }
        }
    val version = remember(versionName) { Changelog.normalizeVersion(versionName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
        title = { Text(text = stringResource(R.string.settings_version_notes_title, version)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                when {
                    !loaded ->
                        Text(
                            text = stringResource(R.string.settings_version_notes_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                    notes.isNullOrBlank() ->
                        Text(
                            text = stringResource(R.string.settings_version_notes_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                    else -> UpdateNotesView(notes = notes.orEmpty())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close))
            }
        },
    )
}
