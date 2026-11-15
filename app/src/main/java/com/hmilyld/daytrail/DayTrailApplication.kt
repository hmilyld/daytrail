package com.hmilyld.daytrail

import android.app.Application
import com.amap.api.location.AMapLocationClient
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

        // 高德合规：必须早于任何地图或定位对象创建
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        container = AppContainer(this)
    }

    /** 供 Composable 之外的地方读取容器（如前台服务）。 */
    val settingsStore: SettingsStore
        get() = container.settingsStore
}
