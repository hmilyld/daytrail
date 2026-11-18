package com.hmilyld.daytrail.ui.screens.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.ui.appViewModelFactory
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
    onBack: () -> Unit,
    onOpenPlace: (Long) -> Unit,
    onAddPlace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlacesViewModel = viewModel(factory = appViewModelFactory(container))
    val places by viewModel.places.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
            contentAlignment = Alignment.TopCenter,
        ) {
            if (places.isEmpty()) {
                Text(
                    text = stringResource(R.string.places_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    // 底部留出 FAB 的高度，否则最后一个地点会被按钮压住
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.widthIn(max = 840.dp),
                ) {
                    items(places, key = { it.place.id }) { summary ->
                        PlaceRow(
                            summary = summary,
                            onClick = { onOpenPlace(summary.place.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
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

    ListItem(
        headlineContent = {
            Text(summary.place.label ?: stringResource(R.string.places_unnamed))
        },
        supportingContent = { Text(supporting) },
        leadingContent = {
            Icon(imageVector = Icons.Outlined.Place, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
