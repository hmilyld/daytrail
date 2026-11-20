package com.hmilyld.daytrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmilyld.daytrail.data.prefs.ThemeMode
import com.hmilyld.daytrail.ui.DayTrailApp
import com.hmilyld.daytrail.ui.components.map.MapViewCache
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
            val themeMode by
                container.settingsStore.themeMode.collectAsStateWithLifecycle(
                    initialValue = ThemeMode.SYSTEM,
                )

            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

            DayTrailTheme(darkTheme = darkTheme) {
                DayTrailApp(container = container)
            }
        }
    }

    /**
     * 地图共享实例的渲染开关只由这里驱动，见 [MapViewCache] 的注释。
     *
     * 不能让页面各自去听 `LocalLifecycleOwner`：Navigation 3 里每个 entry 有自己的
     * lifecycle，一离开页面就会把别人正在用的地图暂停掉。
     */
    override fun onResume() {
        super.onResume()
        MapViewCache.onHostResume()
    }

    override fun onPause() {
        MapViewCache.onHostPause()
        super.onPause()
    }
}
