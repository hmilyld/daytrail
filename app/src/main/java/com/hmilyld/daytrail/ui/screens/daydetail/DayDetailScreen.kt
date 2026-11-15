package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.ui.components.map.AmapView
import com.hmilyld.daytrail.ui.components.map.MapUnavailablePlaceholder
import com.hmilyld.daytrail.ui.components.map.rememberAmapAvailable
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 单日详情：上方固定高度地图折线，下方时间轴。
 *
 * 时间轴有四类节点：出发 / 停留 / 数据缺失 / 结束。
 * 「数据缺失」必须显式画出——高铁过隧道会频繁出现，
 * 不标出来用户会以为轨迹画错了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    dateKey: String,
    container: com.hmilyld.daytrail.AppContainer,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    DayDetailContent(
        title = dateKey,
        entries = emptyList(),
        onBack = onBack,
        modifier = modifier,
    )
}

/** 时间轴节点类型。 */
internal enum class TimelineKind {
    START,
    STOP,
    GAP,
    END,
}

internal data class TimelineEntry(
    val time: String,
    val title: String,
    val detail: String,
    val kind: TimelineKind,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailContent(
    title: String,
    entries: List<TimelineEntry>,
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (rememberAmapAvailable()) {
                AmapView(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                )
            } else {
                MapUnavailablePlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                )
            }
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(20.dp),
                ) {
                    items(items = entries) { entry ->
                        TimelineRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    val dotColor =
        when (entry.kind) {
            TimelineKind.START -> MaterialTheme.colorScheme.secondary
            TimelineKind.STOP -> MaterialTheme.colorScheme.onSurfaceVariant
            TimelineKind.GAP -> MaterialTheme.colorScheme.tertiary
            TimelineKind.END -> MaterialTheme.colorScheme.primary
        }

    val spoken =
        when (entry.kind) {
            TimelineKind.GAP -> "${entry.time}，${entry.title}，${entry.detail}"
            else -> "${entry.time}，${entry.title}，${entry.detail}"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = spoken }
                .padding(vertical = 6.dp),
    ) {
        Text(
            text = entry.time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(color = dotColor, shape = CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, name = "单日详情")
@Composable
private fun DayDetailPreview() {
    DayTrailTheme {
        DayDetailContent(
            title = "11月14日",
            entries =
                listOf(
                    TimelineEntry("08:12", "从上海虹桥出发", "开始记录", TimelineKind.START),
                    TimelineEntry("09:41", "嘉兴服务区", "停留 18 分钟", TimelineKind.STOP),
                    TimelineEntry("10:20", "—", "约 40 分钟无数据", TimelineKind.GAP),
                    TimelineEntry("18:02", "返回酒店", "结束记录", TimelineKind.END),
                ),
            onBack = {},
        )
    }
}
