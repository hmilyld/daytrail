package com.hmilyld.daytrail.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角统一在这里定义，页面里不写 RoundedCornerShape(12.dp) 这类字面量。
 *
 * 取值靠 Material 3 Expressive 一档：卡片更圆、药丸更饱满。
 * small 用于小控件与提示条，medium 是卡片与对话框的默认值，
 * large 用于浮层（今日页统计面板、单日详情的时间轴）。
 */
internal val DayTrailShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
