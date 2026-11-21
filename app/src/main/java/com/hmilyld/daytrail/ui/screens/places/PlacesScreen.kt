package com.hmilyld.daytrail.ui.screens.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.domain.PlaceNeighbors
import com.hmilyld.daytrail.ui.appViewModelFactory
import com.hmilyld.daytrail.ui.components.EmptyState
import java.time.Instant
import java.time.ZoneId

/**
 * 地点列表。
 *
 * 只展示「名字 + 去过多久 + 最近一次」，不再显示坐标——坐标对用户没有意义，
 * 想知道在哪就点进去看地图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    container: AppContainer,
    onOpenPlace: (Long) -> Unit,
    onAddPlace: () -> Unit,
    modifier: Modifier = Modifier,
    /** 一级入口进入时为 null：没有「上一页」可回。 */
    onBack: (() -> Unit)? = null,
) {
    val viewModel: PlacesViewModel = viewModel(factory = appViewModelFactory(container))
    val places by viewModel.places.collectAsStateWithLifecycle()
    val naming by viewModel.naming.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()

    val unnamedCount = places.count { it.place.label.isNullOrBlank() }
    val snackbar = remember { SnackbarHostState() }

    // 相距 400 米内的地点互相标「疑似重复」：同一个小区里两次停车能差两三百米，
    // 归并半径只有 120 米，于是会建出两个地点。合不合并由用户点进详情决定。
    val suspects =
        remember(places) {
            PlaceNeighbors.suspects(
                places.map { summary ->
                    PlaceNeighbors.Entry(
                        id = summary.place.id,
                        point = GeoPoint(summary.place.latitude, summary.place.longitude),
                        radiusMeters = summary.place.radiusMeters,
                    )
                },
            )
        }

    // 文案必须在组合期用 stringResource 取：用 LocalContext.getString 拿到的是
    // 配置变更前的旧值（lint: LocalContextGetResourceValueCall）。
    val result = outcome
    val message =
        when {
            result == null -> null
            result.named > 0 -> stringResource(R.string.places_autoname_done, result.named)
            else -> stringResource(R.string.places_autoname_failed)
        }

    // 补全结果只展示一次，展示完就清掉，避免旋转屏幕后又弹一遍
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeOutcome()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    // 只在还有未命名地点时出现：没有可补的就别占位
                    if (unnamedCount > 0) {
                        TextButton(onClick = { viewModel.backfillNames() }, enabled = !naming) {
                            Text(
                                text =
                                    stringResource(
                                        if (naming) {
                                            R.string.places_autoname_running
                                        } else {
                                            R.string.places_autoname
                                        },
                                    ),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPlace,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.places_add)) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = if (places.isEmpty()) Alignment.Center else Alignment.TopCenter,
        ) {
            if (places.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Place,
                    title = stringResource(R.string.places_empty_title),
                    body = stringResource(R.string.places_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            // 底部留出 FAB 的高度，否则最后一个地点会被按钮压住
                            bottom = 96.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.widthIn(max = 840.dp),
                ) {
                    items(places, key = { it.place.id }) { summary ->
                        PlaceCard(
                            summary = summary,
                            suspect = summary.place.id in suspects,
                            onClick = { onOpenPlace(summary.place.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(
    summary: PlaceSummary,
    suspect: Boolean,
    onClick: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val lastSeen = Instant.ofEpochMilli(summary.place.lastSeenMs).atZone(zone).toLocalDate()
    val supporting =
        if (summary.visitDays > 0) {
            stringResource(R.string.places_visit_days, summary.visitDays) +
                " · " +
                stringResource(R.string.places_last_seen, lastSeen.monthValue, lastSeen.dayOfMonth)
        } else {
            stringResource(R.string.places_never_visited)
        }

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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
            ) {
                Text(
                    text = summary.place.label ?: stringResource(R.string.places_unnamed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (suspect) {
                    // 只做提示，不自动合并：相距 400 米也可能是两家相邻的店
                    Text(
                        text = stringResource(R.string.places_suspect),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
