package com.hmilyld.daytrail.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 固定品牌色。地图类应用需要稳定的轨迹线颜色，因此关闭动态取色，
 * 所有颜色只从这里取，不在页面里写死十六进制值。
 *
 * **浅色与深色必须用不同档位**：同一个色值在两种背景上不可能同时满足
 * WCAG AA（4.5:1）。早期浅深共用一套值，结果浅色下 primary 只有 4.19:1、
 * 深色下 error 只有 3.79:1。每个色相都按「浅色用深一点、深色用浅一点」拆开。
 *
 * 括号里的比值是用 WCAG 相对亮度公式实测的，修改颜色时请重新计算并更新注释。
 */
internal object TrailColors {
    // ---- 强调色（轨迹线、选中态、主按钮）----

    /** 浅色主题 primary。#F5F7FA 上 6.24:1，白字在其上 6.70:1。 */
    val Blue40 = Color(0xFF1D4ED8)

    /** 深色主题 primary。与 #12151A 上 10.38:1。 */
    val Blue80 = Color(0xFFA8C3FF)

    val Blue90 = Color(0xFFDDE7FF)
    val Blue10 = Color(0xFF00174A)

    /** 深色主题的 primaryContainer，配 [Blue90] 文字为 8.68:1。 */
    val BlueContainerDark = Color(0xFF1B3B7A)

    // ---- 语义色：起点 / 记录中（secondary）----

    /** 浅色主题 secondary。#F5F7FA 上 4.67:1，白字在其上 5.02:1。 */
    val Green40 = Color(0xFF15803D)

    /** 深色主题 secondary。与 #12151A 上 5.55:1。 */
    val Green60 = Color(0xFF16A34A)

    val Green90 = Color(0xFFD6F5E0)
    val Green10 = Color(0xFF00300F)

    /**
     * 深色主题下的「成功」容器色。
     *
     * 不能直接用 [Green10]（#00300F）：它在深色背景上几乎与背景同色，
     * 卡片会变成一团黑。用低饱和的深绿保证可辨识，同时不刺眼。
     */
    val GreenContainerDark = Color(0xFF0C3A1F)

    // ---- 语义色：数据缺失 / 提醒（tertiary）----
    //
    // 注意：琥珀色**只用于图形**（进度、圆点、描边）。作为文字时它在浅色背景上
    // 只有 2.0:1，远低于 AA。需要的文字一律用 onSurfaceVariant。

    val Amber40 = Color(0xFFF59E0B)
    val Amber90 = Color(0xFFFDEFCF)
    val Amber10 = Color(0xFF3D2A00)

    // ---- 语义色：错误 ----

    /** 浅色主题 error。#F5F7FA 上 6.03:1，白字在其上 6.47:1。 */
    val Red40 = Color(0xFFB91C1C)

    /** 深色主题 error。与 #12151A 上 10.77:1。 */
    val Red80 = Color(0xFFFFB4AB)

    val Red90 = Color(0xFFFBDDDD)
    val Red10 = Color(0xFF410002)

    /** 深色主题下配 [Red80] 的文字色（7.72:1）。 */
    val RedOnDark = Color(0xFF690005)

    /** 深色主题的错误容器色，理由同 [GreenContainerDark]。 */
    val RedContainerDark = Color(0xFF93000A)

    /** 深色主题下错误容器上的文字（7.24:1）。 */
    val RedOnContainerDark = Color(0xFFFFDAD6)

    /**
     * 热力方块上的字色。
     *
     * 不随主题变化：色块本身就是背景，主题的 onSurface 在深色下是浅字、
     * 浅色下是深字，直接套上去总有一种主题会看不清。
     */
    val OnHeatDark = Color(0xFF0B1220)
    val OnHeatLight = Color(0xFFFFFFFF)

    // ---- 中性色（浅色）----

    val SurfaceLight = Color(0xFFF5F7FA)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val SurfaceContainerLight = Color(0xFFFFFFFF)
    val SurfaceContainerHighLight = Color(0xFFEDF0F5)
    val SurfaceContainerHighestLight = Color(0xFFE4E8EF)
    val OnSurfaceLight = Color(0xFF15171C)
    val OnSurfaceVariantLight = Color(0xFF5A6270)
    val OutlineLight = Color(0xFFE3E7EE)
    val OutlineVariantLight = Color(0xFFF0F2F6)

    // ---- 中性色（深色）----

    val SurfaceDark = Color(0xFF12151A)
    val SurfaceContainerLowestDark = Color(0xFF0D1014)
    val SurfaceContainerDark = Color(0xFF1A1E25)
    val SurfaceContainerHighDark = Color(0xFF232830)
    val SurfaceContainerHighestDark = Color(0xFF2B313B)
    val OnSurfaceDark = Color(0xFFE7EAEF)
    val OnSurfaceVariantDark = Color(0xFF9AA2AF)
    val OutlineDark = Color(0xFF2E3440)
    val OutlineVariantDark = Color(0xFF252A33)
}
