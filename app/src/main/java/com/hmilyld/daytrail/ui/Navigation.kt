package com.hmilyld.daytrail.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.ui.nav.TopLevelDestination

/** 顶层导航项的图标。图标只是辅助，语义由文字标签承担。 */
internal val TopLevelDestination.icon: ImageVector
    get() =
        when (this) {
            TopLevelDestination.TODAY -> Icons.Outlined.Map
            TopLevelDestination.HISTORY -> Icons.Outlined.History
            TopLevelDestination.SETTINGS -> Icons.Outlined.Settings
        }

/**
 * 窄屏底部导航栏（< 600dp）。
 * NavigationBar 自身处理导航栏 insets，不要再给父容器加 padding。
 *
 * 选中态用统一的蓝色指示器，不使用默认的 secondaryContainer：
 * 本应用把 secondary 留给「起点 / 记录中」语义，
 * 导航选中态用 secondary 会让语义混淆。
 */
@Composable
internal fun DayTrailBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                selected = item == current,
                onClick = { onSelect(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(text = label) },
                alwaysShowLabel = true,
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

/**
 * 宽屏导航栏（>= 600dp）。NavigationRail 自带 insets 处理。
 */
@Composable
internal fun DayTrailRail(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationRail {
        TopLevelDestination.entries.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationRailItem(
                selected = item == current,
                onClick = { onSelect(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(text = label) },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

/** 导航区域宽度，供列表内容对齐使用。 */
internal val NavigationRailWidth = 80.dp
