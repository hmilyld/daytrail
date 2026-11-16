package com.hmilyld.daytrail

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.hmilyld.daytrail.data.prefs.SettingsStore

/**
 * 应用入口。
 *
 * 高德的隐私合规接口必须在任何 MapView / AMapLocationClient 实例化之前调用，
 * 因此放在 Application.onCreate 的最前面。顺序错了会直接抛异常或闪退。
 */
class DayTrailApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 高德地图合规：必须早于任何 MapView 创建。
        // 注意这里只处理地图 SDK；定位走 Android 系统 LocationManager，
        // 不使用高德定位 SDK（见 LocationSource 的注释）。
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        container = AppContainer(this)
    }

    /**
     * 进程正常终止时释放地图。
     *
     * 注意：真实设备上 `onTerminate` 几乎不会被调用（进程被系统直接杀掉），
     * 所以这里只是兜底。地图实例随进程结束由系统回收，不会泄漏。
     */
    override fun onTerminate() {
        com.hmilyld.daytrail.ui.components.map.MapViewCache
            .destroy()
        super.onTerminate()
    }

    /** 供 Composable 之外的地方读取容器（如前台服务）。 */
    val settingsStore: SettingsStore
        get() = container.settingsStore
}
