package com.hmilyld.daytrail.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.import.ConflictStrategy
import com.hmilyld.daytrail.data.import.ImportFormat
import com.hmilyld.daytrail.data.import.ImportPreview
import com.hmilyld.daytrail.ui.components.trailSegmentedColors
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 导入预览。
 *
 * 先把「会发生什么」讲清楚再让用户点确认：天数、点数、时间范围、
 * 有多少天会被跳过、丢弃了多少坏点。用户点「开始导入」之后不再解析，
 * 所以这里展示的数字就是最终结果。
 */
@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    strategy: ConflictStrategy,
    onStrategyChange: (ConflictStrategy) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.import_dialog_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.import_file_line,
                            preview.fileName,
                            formatLabel(preview.format),
                        ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text =
                        stringResource(
                            R.string.import_summary,
                            preview.batches.size,
                            preview.totalPoints,
                            preview.firstDate,
                            preview.lastDate,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TrailSpacing.md)) {
                    Text(
                        text = stringResource(R.string.import_days_new, preview.newDays.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (preview.existingDays.isNotEmpty()) {
                        Text(
                            text =
                                stringResource(
                                    R.string.import_days_existing,
                                    preview.existingDays.size,
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (preview.droppedPoints > 0) {
                    Text(
                        text = stringResource(R.string.import_dropped, preview.droppedPoints),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.import_coord_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (preview.existingDays.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.import_strategy),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = TrailSpacing.sm),
                    )
                    StrategyPicker(selected = strategy, onSelect = onStrategyChange)
                    if (strategy == ConflictStrategy.REPLACE) {
                        Text(
                            text = stringResource(R.string.import_replace_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.import_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun StrategyPicker(
    selected: ConflictStrategy,
    onSelect: (ConflictStrategy) -> Unit,
) {
    val options =
        listOf(
            ConflictStrategy.SKIP to stringResource(R.string.import_strategy_skip),
            ConflictStrategy.MERGE to stringResource(R.string.import_strategy_merge),
            ConflictStrategy.REPLACE to stringResource(R.string.import_strategy_replace),
        )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (strategy, label) ->
            SegmentedButton(
                selected = selected == strategy,
                onClick = { onSelect(strategy) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = trailSegmentedColors(),
            ) {
                Text(text = label)
            }
        }
    }
}

@Composable
private fun formatLabel(format: ImportFormat): String =
    stringResource(
        when (format) {
            ImportFormat.GEOJSON -> R.string.import_format_geojson
            ImportFormat.GPX -> R.string.import_format_gpx
        },
    )
