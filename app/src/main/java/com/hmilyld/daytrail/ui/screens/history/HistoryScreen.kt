package com.hmilyld.daytrail.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.ui.components.EmptyState
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 历史页：月历热力 + 日卡片列表。
 *
 * 用 LazyVerticalGrid + GridCells.Adaptive(160.dp)：
 * 手机上是单列，平板上自动变两列，不需要手写断点判断。
 */
@Composable
fun HistoryScreen(
    container: com.hmilyld.daytrail.AppContainer,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = emptyList<DaySummary>()

    HistoryContent(
        days = days,
        onOpenDay = onOpenDay,
        modifier = modifier,
    )
}

/** 日摘要。真实数据接入后由 Repository 提供。 */
internal data class DaySummary(
    val dateKey: String,
    val title: String,
    val subtitle: String,
    val distanceKm: Double,
    val heatLevel: Int,
)

@Composable
private fun HistoryContent(
    days: List<DaySummary>,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MonthHeatmap(
                    values = List(35) { 0 },
                    modifier = Modifier.padding(top = 12.dp),
                )
                HeatLegend(modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (days.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = days, key = { it.dateKey }) { day ->
                    DayCard(day = day, onClick = { onOpenDay(day.dateKey) })
                }
            }
        }
    }
}

/**
 * 月历热力：7 列 x 5 行，格子按当日里程深浅着色。
 *
 * 这是纯装饰性网格，因此整体合并成一句语义描述，
 * 具体里程在下方日卡片里逐个可读，避免读屏念 35 个空白格子。
 */
@Composable
private fun MonthHeatmap(
    values: List<Int>,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.history_title)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(5) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val level = values.getOrElse(index) { 0 }
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    color = heatColor(level),
                                    shape = MaterialTheme.shapes.extraSmall,
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun heatColor(level: Int) =
    when (level) {
        0 -> MaterialTheme.colorScheme.surfaceContainerHigh
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun HeatLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.history_legend_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(0, 1, 2, 3, 4).forEach { level ->
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(
                            color = heatColor(level),
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
            )
        }
        Text(
            text = stringResource(R.string.history_legend_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCard(
    day: DaySummary,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${day.distanceKm} km",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = day.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Preview(showBackground = true, name = "历史 - 空")
@Composable
private fun HistoryEmptyPreview() {
    DayTrailTheme {
        HistoryContent(days = emptyList(), onOpenDay = {})
    }
}

@Preview(showBackground = true, name = "历史 - 有数据")
@Composable
private fun HistoryListPreview() {
    DayTrailTheme {
        HistoryContent(
            days =
                listOf(
                    DaySummary("2026-11-14", "11月14日 周六", "上海 → 杭州 · 6 处停留", 86.2, 4),
                    DaySummary("2026-11-13", "11月13日 周五", "杭州市内 · 9 处停留", 41.7, 3),
                ),
            onOpenDay = {},
        )
    }
}
