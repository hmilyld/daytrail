package com.hmilyld.daytrail

import android.app.Application
import android.util.Log
import com.amap.api.maps.MapsInitializer
import com.hmilyld.daytrail.data.backup.BackupRestore
import com.hmilyld.daytrail.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 应用入口。
 *
 * 高德的隐私合规接口必须在任何 MapView 实例化之前调用，因此放在
 * `onCreate` 的最前面；并且**参数必须如实反映用户是否同意过隐私政策**。
 *
 * 早期实现无条件传 `(true, true)`，等于「未经用户同意即声明已同意」——
 * 这既违反高德 SDK 的集成规范，也违反《个人信息保护法》的告知-同意要求。
 * 现在的做法：
 * - 启动时按已存的同意状态传参（未同意 → false）；
 * - 用户在引导页勾选同意后，调用 [agreeAmapPrivacy] 再传一次 true。
 *
 * 注意：定位走 Android 系统 LocationManager，不使用高德定位 SDK，
 * 所以这里只需要处理地图 SDK 的合规接口。
 */
class DayTrailApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 数据库替换必须早于 AppContainer：container 里的 database 是 lazy，
        // 一旦有任何代码先触发 Room 打开，staged 的恢复文件就换不上去了。
        // 设置已在准备阶段写入 DataStore，这里不需要第二次阻塞读。
        val outcome = BackupRestore.applyPending(this)

        // 先建容器，再读同意状态：同意状态决定 SDK 初始化参数，
        // 必须在任何 MapView 创建之前确定。
        container = AppContainer(this)
        container.restoreOutcome = outcome
        when (outcome) {
            is BackupRestore.ApplyResult.Failed ->
                Log.w(TAG, "恢复失败：${outcome.message}")
            is BackupRestore.ApplyResult.Applied ->
                Log.i(TAG, "已应用恢复的数据库（备份时间 ${outcome.createdAt}）")
            BackupRestore.ApplyResult.NothingToDo -> Unit
        }

        // 同步读一次 DataStore：同意状态是 SDK 初始化的入参，没有异步余地。
        // 文件只有几 KB，首次读取在毫秒级；这也是 Application 里唯一允许的阻塞读。
        //
        // 读取失败一律按「未同意」处理：宁可让用户重新勾一次，
        // 也不能在无法确认同意状态时给 SDK 传 true。
        val agreed =
            runCatching {
                runBlocking { container.settingsStore.privacyAccepted.first() }
            }.getOrDefault(false)
        applyAmapPrivacy(agreed)
    }

    /**
     * 用户在引导页同意隐私政策后调用：把同意状态同步给地图 SDK。
     *
     * 必须早于任何 MapView 的创建。引导页在进入主界面前展示，
     * 因此这里天然满足顺序要求。
     */
    fun agreeAmapPrivacy() {
        applyAmapPrivacy(agreed = true)
    }

    private fun applyAmapPrivacy(agreed: Boolean) {
        MapsInitializer.updatePrivacyShow(this, true, agreed)
        MapsInitializer.updatePrivacyAgree(this, agreed)
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

    private companion object {
        const val TAG = "DayTrail"
    }
}
