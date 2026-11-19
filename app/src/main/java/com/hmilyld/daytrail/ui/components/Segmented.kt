package com.hmilyld.daytrail.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable

/**
 * 分段按钮的选中色。
 *
 * Material 默认用 `secondaryContainer` 表示选中，而本应用把 secondary
 * 留给了「记录中 / 起点」这个语义——主题选择器上出现一个绿色勾会让人
 * 以为「绿色 = 已启用记录」。统一改成 `primaryContainer`。
 */
@Composable
fun trailSegmentedColors(): SegmentedButtonColors =
    SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        activeBorderColor = MaterialTheme.colorScheme.primary,
    )
