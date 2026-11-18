package com.hmilyld.daytrail.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

private val LightScheme =
    lightColorScheme(
        primary = TrailColors.Blue40,
        onPrimary = Color.White,
        primaryContainer = TrailColors.Blue90,
        onPrimaryContainer = TrailColors.Blue10,
        secondary = TrailColors.Green40,
        onSecondary = Color.White,
        secondaryContainer = TrailColors.Green90,
        onSecondaryContainer = TrailColors.Green10,
        tertiary = TrailColors.Amber40,
        onTertiary = Color.White,
        tertiaryContainer = TrailColors.Amber90,
        onTertiaryContainer = TrailColors.Amber10,
        error = TrailColors.Red40,
        onError = Color.White,
        errorContainer = TrailColors.Red90,
        onErrorContainer = TrailColors.Red10,
        background = TrailColors.SurfaceLight,
        onBackground = TrailColors.OnSurfaceLight,
        surface = TrailColors.SurfaceLight,
        onSurface = TrailColors.OnSurfaceLight,
        surfaceVariant = TrailColors.SurfaceContainerHighLight,
        onSurfaceVariant = TrailColors.OnSurfaceVariantLight,
        surfaceContainerLowest = TrailColors.SurfaceContainerLowestLight,
        surfaceContainerLow = TrailColors.SurfaceContainerLight,
        surfaceContainer = TrailColors.SurfaceContainerLight,
        surfaceContainerHigh = TrailColors.SurfaceContainerHighLight,
        surfaceContainerHighest = TrailColors.SurfaceContainerHighestLight,
        outline = TrailColors.OutlineLight,
        outlineVariant = TrailColors.OutlineVariantLight,
        inverseSurface = TrailColors.OnSurfaceLight,
        inverseOnSurface = TrailColors.SurfaceLight,
    )

private val DarkScheme =
    darkColorScheme(
        primary = TrailColors.Blue40,
        onPrimary = Color.White,
        primaryContainer = TrailColors.Blue10,
        onPrimaryContainer = TrailColors.Blue90,
        secondary = TrailColors.Green40,
        onSecondary = Color.Black,
        // 深色下「成功」状态用低饱和绿底，不能直接用 Green10（太暗发黑）
        secondaryContainer = TrailColors.GreenContainerDark,
        onSecondaryContainer = TrailColors.Green90,
        tertiary = TrailColors.Amber40,
        onTertiary = Color.Black,
        tertiaryContainer = TrailColors.Amber10,
        onTertiaryContainer = TrailColors.Amber90,
        error = TrailColors.Red40,
        onError = Color.White,
        errorContainer = TrailColors.RedContainerDark,
        onErrorContainer = TrailColors.Red90,
        background = TrailColors.SurfaceDark,
        onBackground = TrailColors.OnSurfaceDark,
        surface = TrailColors.SurfaceDark,
        onSurface = TrailColors.OnSurfaceDark,
        surfaceVariant = TrailColors.SurfaceContainerHighDark,
        onSurfaceVariant = TrailColors.OnSurfaceVariantDark,
        surfaceContainerLowest = TrailColors.SurfaceContainerLowestDark,
        surfaceContainerLow = TrailColors.SurfaceContainerDark,
        surfaceContainer = TrailColors.SurfaceContainerDark,
        surfaceContainerHigh = TrailColors.SurfaceContainerHighDark,
        surfaceContainerHighest = TrailColors.SurfaceContainerHighestDark,
        outline = TrailColors.OutlineDark,
        outlineVariant = TrailColors.OutlineVariantDark,
        inverseSurface = TrailColors.OnSurfaceDark,
        inverseOnSurface = TrailColors.SurfaceDark,
    )

/**
 * 应用主题。故意不启用动态取色：轨迹线颜色必须稳定，
 * 不能随用户壁纸变化，否则地图上的路径会失去一致性。
 *
 * 同时同步状态栏 / 导航栏图标颜色：深色主题下必须是浅色图标，
 * 否则状态栏会变成「浅底浅字」看不清。
 */
@Composable
fun DayTrailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = DayTrailTypography,
        shapes = DayTrailShapes,
        content = content,
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ThemeLightPreview() {
    DayTrailTheme(darkTheme = false) {
        Surface { Text(text = "行迹", style = MaterialTheme.typography.titleLarge) }
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ThemeDarkPreview() {
    DayTrailTheme(darkTheme = true) {
        Surface { Text(text = "行迹", style = MaterialTheme.typography.titleLarge) }
    }
}
