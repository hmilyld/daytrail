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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
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

    Scaffold(
        modifier = modifier,
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
