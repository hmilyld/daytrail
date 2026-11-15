package com.hmilyld.daytrail.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.ui.nav.Destination
import com.hmilyld.daytrail.ui.nav.TopLevelDestination
import com.hmilyld.daytrail.ui.screens.daydetail.DayDetailScreen
import com.hmilyld.daytrail.ui.screens.history.HistoryScreen
import com.hmilyld.daytrail.ui.screens.onboarding.OnboardingScreen
import com.hmilyld.daytrail.ui.screens.permission.PermissionScreen
import com.hmilyld.daytrail.ui.screens.settings.SettingsScreen
import com.hmilyld.daytrail.ui.screens.today.TodayScreen
import com.hmilyld.daytrail.ui.screens.trip.TripDetailScreen

/**
 * 应用根组件。
 *
 * 布局策略：
 * - 顶层三页共用同一个导航容器（窄屏底部栏 / 宽屏导航栏）
 * - 详情页（单日、行程）全屏展示，隐藏导航区域
 * - 引导页全屏，无导航
 *
 * insets 由各屏自己的 Scaffold 处理；这里不叠加 safeDrawingPadding，
 * 避免与子级 Scaffold 的 innerPadding 重复计算。
 */
@Composable
fun DayTrailApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val onboardingCompleted by
        container.settingsStore.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)

    when (onboardingCompleted) {
        null -> Box(modifier = modifier.fillMaxSize())
        false ->
            OnboardingScreen(
                onFinished = { /* SettingsStore 内部已切换为 true，会触发重组 */ },
                modifier = modifier,
            )
        true -> MainShell(container = container, modifier = modifier)
    }
}

@Composable
private fun MainShell(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(Destination.Today)

    val currentDestination = backStack.lastOrNull()
    val currentTopLevel =
        TopLevelDestination.entries.firstOrNull { it.destination == currentDestination }
            ?: TopLevelDestination.TODAY
    val showNavigationArea =
        TopLevelDestination.entries.any {
            it.destination == currentDestination
        }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 600.dp

        if (wideLayout && showNavigationArea) {
            // 宽屏：左侧 NavigationRail + 内容区。
            // 没有 Scaffold，所以 insets 必须在这里显式消费，
            // 否则内容会顶到状态栏、被手势条遮住。
            Row(modifier = Modifier.fillMaxSize()) {
                DayTrailRail(
                    current = currentTopLevel,
                    onSelect = { target -> backStack.navigateTopLevel(target.destination) },
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top +
                                        WindowInsetsSides.End +
                                        WindowInsetsSides.Bottom,
                                ),
                            ),
                ) {
                    NavHost(backStack = backStack, container = container)
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (showNavigationArea) {
                        DayTrailBottomBar(
                            current = currentTopLevel,
                            onSelect = { target -> backStack.navigateTopLevel(target.destination) },
                        )
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { innerPadding ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                ) {
                    NavHost(backStack = backStack, container = container)
                }
            }
        }
    }
}

@Composable
private fun NavHost(
    backStack: NavBackStack<NavKey>,
    container: AppContainer,
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // 转场策略：
        // - 顶层 Tab 之间切换用无动画（Material 规范：底部导航切换不滑动、不淡入）
        // - 进入详情页用水平滑入，返回用水平滑出，符合层级关系的空间连续性
        // 顶层 Tab 之间如果保留淡入淡出，地图页退出时即使有 TextureView 也仍有残影，
        // 而且底部导航切换加动画本身就是反模式。
        transitionSpec = {
            if (isTopLevelSwitch(backStack)) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                slideInHorizontally(
                    animationSpec = tween(TRANSITION_DURATION_MS),
                    initialOffsetX = { fullWidth -> fullWidth / 4 },
                ) togetherWith
                    fadeOut(animationSpec = tween(TRANSITION_DURATION_MS))
            }
        },
        popTransitionSpec = {
            fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) togetherWith
                slideOutHorizontally(
                    animationSpec = tween(TRANSITION_DURATION_MS),
                    targetOffsetX = { fullWidth -> fullWidth / 4 },
                )
        },
        predictivePopTransitionSpec = {
            fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) togetherWith
                slideOutHorizontally(
                    animationSpec = tween(TRANSITION_DURATION_MS),
                    targetOffsetX = { fullWidth -> fullWidth / 4 },
                )
        },
        entryProvider =
            entryProvider {
                entry<Destination.Today> {
                    TodayScreen(
                        container = container,
                        onOpenDay = { dateKey -> backStack.add(Destination.DayDetail(dateKey)) },
                    )
                }
                entry<Destination.History> {
                    HistoryScreen(
                        container = container,
                        onOpenDay = { dateKey -> backStack.add(Destination.DayDetail(dateKey)) },
                    )
                }
                entry<Destination.Settings> {
                    SettingsScreen(
                        container = container,
                        onOpenPermissions = {
                            backStack.add(Destination.Permissions)
                        },
                    )
                }
                entry<Destination.Permissions> {
                    PermissionScreen(onBack = { backStack.removeLastOrNull() })
                }
                entry<Destination.DayDetail> { key ->
                    DayDetailScreen(
                        dateKey = key.dateKey,
                        container = container,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenTrip = { tripId -> backStack.add(Destination.TripDetail(tripId)) },
                    )
                }
                entry<Destination.TripDetail> { key ->
                    TripDetailScreen(
                        tripId = key.tripId,
                        container = container,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}

/** 详情页转场时长。200ms 是 Material 推荐的短距离转场上限。 */
private const val TRANSITION_DURATION_MS = 200

/**
 * 判断本次转场是否为顶层 Tab 之间的切换。
 *
 * 顶层 Tab 的切换由 [navigateTopLevel] 完成，它保证栈长度始终为 1。
 * 进入详情页时栈长度为 2，因此用目标栈长度即可区分。
 */
private fun isTopLevelSwitch(backStack: NavBackStack<NavKey>): Boolean = backStack.size <= 1

/**
 * 切换顶层目的地：原地替换栈底，不堆叠返回栈。
 *
 * 不能写成 clear() + add()：Navigation 3 的 NavBackStack 在快照合并后
 * 可能只看到「空了又满」的结果，导致界面不重组。原地 set 是单次原子变更。
 */
private fun NavBackStack<NavKey>.navigateTopLevel(target: Destination) {
    if (lastOrNull() == target) return
    if (isEmpty()) {
        add(target)
        return
    }
    // 先把栈压到只剩一个元素，再原地替换，保证始终非空
    while (size > 1) {
        removeAt(lastIndex)
    }
    set(0, target)
}
