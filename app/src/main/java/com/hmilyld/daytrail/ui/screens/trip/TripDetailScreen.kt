package com.hmilyld.daytrail.ui.screens.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import com.hmilyld.daytrail.ui.theme.DayTrailTheme
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 行程详情：一次出差覆盖多天。
 *
 * 结构：标题 + 多日折线地图 + 每日摘要 + 文字摘要。
 * 文字摘要由 TripSummaryProvider 生成，默认是模板实现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    container: com.hmilyld.daytrail.AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TripDetailContent(
        title = stringResource(R.string.trip_title),
        summary = "",
        days = emptyList(),
        onBack = onBack,
        modifier = modifier,
    )
}

internal data class TripDaySummary(
    val dateKey: String,
    val label: String,
    val distanceKm: Double,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailContent(
    title: String,
    summary: String,
    days: List<TripDaySummary>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = TrailSpacing.xl),
        ) {
            item {
                if (rememberAmapAvailable()) {
                    AmapView(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                    )
                } else {
                    MapUnavailablePlaceholder(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                    )
                }
            }
            if (summary.isNotBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    ) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(TrailSpacing.lg),
                        )
                    }
                }
            }
            items(items = days, key = { it.dateKey }) { day ->
                Column(
                    modifier =
                        Modifier.fillMaxWidth().padding(
                            horizontal = 20.dp,
                            vertical = 10.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${day.distanceKm} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "行程详情")
@Composable
private fun TripDetailPreview() {
    DayTrailTheme {
        TripDetailContent(
            title = "上海 → 杭州 → 宁波",
            summary = "11月12–15日，4 天，386 公里，停留 9 处。",
            days =
                listOf(
                    TripDaySummary("2026-11-12", "11月12日 周四", 86.2),
                    TripDaySummary("2026-11-13", "11月13日 周五", 41.7),
                ),
            onBack = {},
        )
    }
}
