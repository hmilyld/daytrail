package com.hmilyld.daytrail.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.DayListRow
import com.hmilyld.daytrail.ui.components.EmptyState
import com.hmilyld.daytrail.ui.components.HeatCalendar
import com.hmilyld.daytrail.ui.components.HeatLegend
import com.hmilyld.daytrail.ui.components.SectionCard
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import com.hmilyld.daytrail.ui.theme.TrailType

/**
 * 历史页：月份切换 + 本月合计（热力日历）+ 每天的列表。
 *
 * 三块内容描述同一件事的三个尺度：月、天、日。所以放在一条滚动流里，
 * 而不是分成「日历页」和「列表页」两个 Tab。
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TrailSpacing.gutter,
                        end = TrailSpacing.sm,
                        top = TrailSpacing.md,
                        bottom = TrailSpacing.sm,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.history_previous_month),
                )
            }
            Text(
                text = stringResource(R.string.history_month_label, state.year, state.month),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onNextMonth, enabled = state.canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.history_next_month),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.days.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.history_empty_title),
                    body = stringResource(R.string.history_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = TrailSpacing.contentMaxWidth)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                PaddingValues(
                                    start = TrailSpacing.gutter,
                                    end = TrailSpacing.gutter,
                                    top = TrailSpacing.sm,
                                    bottom = TrailSpacing.xl,
                                ),
                            ),
                    verticalArrangement = Arrangement.spacedBy(TrailSpacing.md),
                ) {
                    MonthSummaryCard(state = state)

                    SectionCard(title = stringResource(R.string.history_days_title)) {
                        state.days.forEachIndexed { index, day ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            DayListRow(
                                dayOfMonth = day.dayOfMonth,
                                weekday = day.weekday,
                                distanceKm = day.distanceKm,
                                onClick = { onOpenDay(day.dateKey) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 本月合计。数字是主角，热力日历是它的脚注。 */
@Composable
private fun MonthSummaryCard(state: HistoryUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(TrailSpacing.lg)) {
            Text(
                text = stringResource(R.string.history_month_total_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = TrailSpacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = DistanceFormat.kilometers(state.monthTotalKm * 1000.0),
                    style = TrailType.heroNumber,
                    color =
                        if (state.monthTotalKm > 0.0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = stringResource(R.string.unit_km),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.history_active_days, state.activeDays),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            HeatCalendar(
                year = state.year,
                month = state.month,
                monthKm = state.monthlyKm,
                modifier = Modifier.padding(top = TrailSpacing.lg),
            )

            Row(
                modifier = Modifier.padding(top = TrailSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeatLegend()
                Spacer(modifier = Modifier.weight(1f))
                if (state.averageKm.isNotBlank()) {
                    Text(
                        text =
                            stringResource(
                                R.string.history_daily_average,
                                state.averageKm,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    name = "历史 - 有数据",
)@Composable
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
                            DayCard("2026-11-18", 18, "周二", 86.2),
                            DayCard("2026-11-17", 17, "周一", 41.7),
                            DayCard("2026-11-16", 16, "周日", 12.4),
                        ),
                    canGoNext = true,
                    monthTotalKm = 486.2,
                    activeDays = 19,
                    averageKm = "25.6",
                ),
            onOpenDay = {},
            onPreviousMonth = {},
            onNextMonth = {},
        )
    }
}

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
