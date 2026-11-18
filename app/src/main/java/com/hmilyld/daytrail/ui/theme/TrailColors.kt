package com.hmilyld.daytrail.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 固定品牌色。地图类应用需要稳定的轨迹线颜色，因此关闭动态取色，
 * 所有颜色只从这里取，不在页面里写死十六进制值。
 */
internal object TrailColors {
    // 强调色：轨迹线、选中态、主按钮
    val Blue40 = Color(0xFF2F6BFF)
    val Blue90 = Color(0xFFDDE7FF)
    val Blue10 = Color(0xFF00174A)

    // 语义色
    val Green40 = Color(0xFF16A34A)
    val Green90 = Color(0xFFD6F5E0)
    val Green10 = Color(0xFF00300F)

    /**
     * 深色主题下的「成功」容器色。
     *
     * 不能直接用 Green10（#00300F）：它在深色背景上几乎与背景同色，
     * 卡片会变成一团黑。用低饱和的深绿保证可辨识，同时不刺眼。
     */
    val GreenContainerDark = Color(0xFF0C3A1F)

    val Amber40 = Color(0xFFF59E0B)
    val Amber90 = Color(0xFFFDEFCF)
    val Amber10 = Color(0xFF3D2A00)

    val Red40 = Color(0xFFDC2626)
    val Red90 = Color(0xFFFBDDDD)
    val Red10 = Color(0xFF410002)

    /** 深色主题下的错误容器色，理由同 [GreenContainerDark]。 */
    val RedContainerDark = Color(0xFF4A1414)

    // 中性色（浅色）
    val SurfaceLight = Color(0xFFF5F7FA)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val SurfaceContainerLight = Color(0xFFFFFFFF)
    val SurfaceContainerHighLight = Color(0xFFEDF0F5)
    val SurfaceContainerHighestLight = Color(0xFFE4E8EF)
    val OnSurfaceLight = Color(0xFF15171C)
    val OnSurfaceVariantLight = Color(0xFF5A6270)
    val OutlineLight = Color(0xFFE3E7EE)
    val OutlineVariantLight = Color(0xFFF0F2F6)

    // 中性色（深色）
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
