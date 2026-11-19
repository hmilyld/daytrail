package com.hmilyld.daytrail.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 数字专用字型。
 *
 * 与 M3 的语义槽位分开：数字需要**等宽**（`tnum`），否则「9」变成「10」时
 * 整行会左右跳一下；同时用负字距让大数字更紧凑。
 *
 * 页面里凡是展示里程、时长、点数的 `Text`，都应该走这里，而不是写裸 `TextStyle`。
 */
object TrailType {
    /** 单日详情的主数字。 */
    val heroNumber =
        TextStyle(
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1.2).sp,
            fontFeatureSettings = NUMERIC_FEATURES,
        )

    /** 卡片里的主数字（今日里程）。 */
    val numericLg =
        TextStyle(
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6).sp,
            fontFeatureSettings = NUMERIC_FEATURES,
        )

    /** 三宫格指标、列表右侧的数值。 */
    val numericMd =
        TextStyle(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = NUMERIC_FEATURES,
        )

    /** 时间轴、标签里的小号数字。 */
    val numericSm =
        TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = NUMERIC_FEATURES,
        )

    /** 等宽数字。Compose 的 `fontFeatureSettings` 直接透传给底层字体。 */
    private const val NUMERIC_FEATURES = "tnum"
}
