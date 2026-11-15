package com.hmilyld.daytrail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

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
        surfaceContainer = TrailColors.SurfaceContainerLight,
        surfaceContainerLow = TrailColors.SurfaceContainerLight,
        surfaceContainerHigh = TrailColors.SurfaceContainerHighLight,
        surfaceContainerHighest = TrailColors.SurfaceContainerHighLight,
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
        secondaryContainer = TrailColors.Green10,
        onSecondaryContainer = TrailColors.Green90,
        tertiary = TrailColors.Amber40,
        onTertiary = Color.Black,
        tertiaryContainer = TrailColors.Amber10,
        onTertiaryContainer = TrailColors.Amber90,
        error = TrailColors.Red40,
        onError = Color.White,
        errorContainer = TrailColors.Red10,
        onErrorContainer = TrailColors.Red90,
        background = TrailColors.SurfaceDark,
        onBackground = TrailColors.OnSurfaceDark,
        surface = TrailColors.SurfaceDark,
        onSurface = TrailColors.OnSurfaceDark,
        surfaceVariant = TrailColors.SurfaceContainerHighDark,
        onSurfaceVariant = TrailColors.OnSurfaceVariantDark,
        surfaceContainer = TrailColors.SurfaceContainerDark,
        surfaceContainerLow = TrailColors.SurfaceContainerDark,
        surfaceContainerHigh = TrailColors.SurfaceContainerHighDark,
        surfaceContainerHighest = TrailColors.SurfaceContainerHighDark,
        outline = TrailColors.OutlineDark,
        outlineVariant = TrailColors.OutlineVariantDark,
        inverseSurface = TrailColors.OnSurfaceDark,
        inverseOnSurface = TrailColors.SurfaceDark,
    )

/**
 * 应用主题。故意不启用动态取色：轨迹线颜色必须稳定，
 * 不能随用户壁纸变化，否则地图上的路径会失去一致性。
 */
@Composable
fun DayTrailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
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
