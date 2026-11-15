package com.hmilyld.daytrail

import android.content.Context
import com.hmilyld.daytrail.data.prefs.SettingsStore

/**
 * 手动依赖注入容器。
 *
 * 不用 Hilt：本应用的依赖图很小，少一个注解处理器可以显著缩短构建时间，
 * 出错面也更窄。KSP 只服务 Room。
 *
 * 所有依赖都从这里暴露，ViewModel 通过 [viewModelFactory] 获取。
 */
class AppContainer(
    private val appContext: Context,
) {
    val settingsStore: SettingsStore = SettingsStore(appContext)
}
