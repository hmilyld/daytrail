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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.EmptyState
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 历史页：月历热力 + 日卡片列表。
 *
 * 用 LazyVerticalGrid + GridCells.Adaptive(160.dp)：
 * 手机上是单列，平板上自动变多列，不需要手写断点判断。
 */
@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HistoryViewModel = viewModel(factory = appViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryContent(
        state = state,
        onOpenDay = onOpenDay,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        modifier = modifier,
    )
}

@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onOpenDay: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.history_month_label,
                                    state.year,
                                    state.month,
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    // 月份切换：早期版本月历写死在当月，旧数据永远看不到
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.history_previous_month),
                        )
                    }
                    IconButton(onClick = onNextMonth, enabled = state.canGoNext) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.history_next_month),
                        )
                    }
                }

                if (state.monthTotalKm > 0.0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.history_month_total_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text =
                                        DistanceFormat.kilometers(
                                            state.monthTotalKm * 1000.0,
                                        ),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.history_unit_km),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                                )
                            }
                            MonthHeatmap(
                                year = state.year,
                                month = state.month,
                                monthlyKm = state.monthlyKm,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            HeatLegend(modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                } else {
                    MonthHeatmap(
                        year = state.year,
                        month = state.month,
                        monthlyKm = state.monthlyKm,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    HeatLegend(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (state.days.isEmpty()) {
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
                items(items = state.days, key = { it.dateKey }) { day ->
                    DayCardItem(day = day, onClick = { onOpenDay(day.dateKey) })
                }
            }
        }
    }
}

/**
 * 月历热力：按当月实际天数与首日偏移排布，不足一周的补空格。
 *
 * 整体合并成一句语义描述：这是纯装饰性网格，
 * 具体里程在下方日卡片里逐个可读，避免读屏念一串空白格子。
 */
@Composable
private fun MonthHeatmap(
    year: Int,
    month: Int,
    monthlyKm: List<Double>,
    modifier: Modifier = Modifier,
) {
    val leading = HistoryViewModel.leadingBlanks(year, month)
    val total = HistoryViewModel.cellCount(year, month)
    val description = "${year}年${month}月，共 ${monthlyKm.count { it > 0 }} 天有记录"

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total / 7) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val dayIndex = index - leading
                    // null 表示不在本月范围内（月初前或月末后），用透明格
                    val km = monthlyKm.getOrNull(dayIndex)
                    val level = if (km == null) EMPTY_CELL else HistoryViewModel.heatLevelOf(km)
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

/** 超出本月范围的格子（首日之前、月末之后）用透明，避免误以为是「零里程」。 */
@Composable
private fun heatColor(level: Int) =
    when (level) {
        EMPTY_CELL -> Color.Transparent
        0 -> MaterialTheme.colorScheme.surfaceContainerHigh
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

/** 非本月格子的哨兵值。 */
private const val EMPTY_CELL = -1

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
private fun DayCardItem(
    day: DayCard,
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HeatBar(
                    level = day.heatLevel,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Text(
                    text = formatKm(day.distanceKm),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.history_unit_km),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** 日卡片底部的里程条。宽度按分档比例，避免视觉上所有日子都一样重。 */
@Composable
private fun HeatBar(
    level: Int,
    modifier: Modifier = Modifier,
) {
    val fraction =
        when (level) {
            0 -> 0.0f
            1 -> 0.25f
            2 -> 0.5f
            3 -> 0.75f
            else -> 1.0f
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .size(height = 3.dp, width = 0.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.extraSmall,
                ),
    ) {
        if (fraction > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .size(height = 3.dp, width = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
            )
        }
    }
}

private fun formatKm(km: Double): String = if (km >= 10) "%.0f".format(km) else "%.1f".format(km)

@Preview(showBackground = true, name = "历史 - 空")
@Composable
private fun HistoryEmptyPreview() {
    DayTrailTheme {
        HistoryContent(
            state = HistoryUiState(year = 2026, month = 11),
            onOpenDay = {},
            onPreviousMonth = {},
            onNextMonth = {},
        )
    }
}

@Preview(showBackground = true, name = "历史 - 有数据")
@Composable
private fun HistoryListPreview() {
    DayTrailTheme {
        HistoryContent(
            state =
                HistoryUiState(
                    year = 2026,
                    month = 11,
                    monthlyKm = List(30) { if (it % 3 == 0) 40.0 else 0.0 },
                    days =
                        listOf(
                            DayCard("2026-11-14", "11月14日", 86.2, 4),
                            DayCard("2026-11-13", "11月13日", 41.7, 3),
                        ),
                    canGoNext = true,
                    monthTotalKm = 127.9,
                ),
            onOpenDay = {},
            onPreviousMonth = {},
            onNextMonth = {},
        )
    }
}
