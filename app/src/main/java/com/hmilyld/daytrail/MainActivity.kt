package com.hmilyld.daytrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hmilyld.daytrail.ui.DayTrailApp
import com.hmilyld.daytrail.ui.theme.DayTrailTheme

/**
 * 唯一的 Activity。所有界面由 Compose + Navigation 3 承载。
 *
 * edge-to-edge 是 Android 15+ 的默认行为，这里显式声明，
 * 并通过 Scaffold 的 PaddingValues 处理 insets，不叠加 safeDrawingPadding。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 三键导航栏默认会加半透明蒙层，与底部导航条颜色冲突
        window.isNavigationBarContrastEnforced = false

        val container = (application as DayTrailApplication).container

        setContent {
            DayTrailTheme {
                DayTrailApp(container = container)
            }
        }
    }
}
