package com.hmilyld.daytrail

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用外壳（导航栏、导航边栏）的显示状态。
 *
 * 地图放大查看时要把底部导航栏一起让开，否则「全屏」只是把地图拉高一点，
 * 底部还压着一条 80dp 的栏。这个状态放在容器里而不是参数一路透传：
 * 外壳在 `DayTrailApp`，地图页在导航栈内部，两者之间隔着 NavDisplay。
 *
 * 只有地图页会写它，写入方必须在离开时复位（见 `DisposableEffect`），
 * 否则返回上一页后导航栏会一直消失。
 */
class ChromeState {
    private val _immersive = MutableStateFlow(false)

    /** 是否处于沉浸模式：外壳隐藏，内容铺满可视区域。 */
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    fun setImmersive(enabled: Boolean) {
        _immersive.value = enabled
    }
}
