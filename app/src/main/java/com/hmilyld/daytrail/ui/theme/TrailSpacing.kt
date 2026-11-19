package com.hmilyld.daytrail.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距标尺。
 *
 * 只取 4 的倍数，页面里不再出现 `PaddingValues(18.dp)` 这类随手写的数值。
 * 同一层级的元素用同一个档位，避免「这一页 12、那一页 14」的漂移。
 */
object TrailSpacing {
    /** 标签与数值之间。 */
    val xs = 4.dp

    /** 行内元素、图标与文字之间。 */
    val sm = 8.dp

    /** 卡片内部的行间距。 */
    val md = 12.dp

    /** 卡片内边距、页面左右边距。 */
    val lg = 16.dp

    /** 卡片之间、分节之间。 */
    val xl = 24.dp

    /** 大分区间距。 */
    val xxl = 32.dp

    /** 页面左右边距。 */
    val gutter = 16.dp

    /** 宽屏页面左右边距。 */
    val gutterWide = 24.dp

    /** 正文最大宽度。再宽会拉成一行，中文很难读。 */
    val contentMaxWidth = 840.dp

    /** 宽屏断点：600dp 以上改用导航栏 + 左右分栏。 */
    val wideBreakpoint = 600.dp
}
