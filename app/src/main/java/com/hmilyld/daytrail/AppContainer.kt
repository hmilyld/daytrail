package com.hmilyld.daytrail

import android.content.Context
import com.hmilyld.daytrail.data.backup.BackupCoordinator
import com.hmilyld.daytrail.data.backup.BackupManager
import com.hmilyld.daytrail.data.backup.BackupRestore
import com.hmilyld.daytrail.data.db.DayTrailDatabase
import com.hmilyld.daytrail.data.diagnostics.DiagnosticLog
import com.hmilyld.daytrail.data.export.ExportManager
import com.hmilyld.daytrail.data.export.PlaceExportManager
import com.hmilyld.daytrail.data.geocode.PlaceAutoNamer
import com.hmilyld.daytrail.data.geocode.ReverseGeocodeClient
import com.hmilyld.daytrail.data.import.ImportManager
import com.hmilyld.daytrail.data.prefs.SettingsStore
import com.hmilyld.daytrail.data.repo.LocalTrackRepository
import com.hmilyld.daytrail.data.repo.NoOpSyncGateway
import com.hmilyld.daytrail.data.repo.SyncGateway
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.data.route.GrasproadClient
import com.hmilyld.daytrail.data.update.UpdateChecker
import com.hmilyld.daytrail.location.RecordingStateHolder
import com.hmilyld.daytrail.ui.update.UpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手动依赖注入容器。
 *
 * 不用 Hilt：本应用的依赖图很小，少一个注解处理器可以显著缩短构建时间，
 * 出错面也更窄。KSP 只服务 Room。
 *
 * 所有依赖都从这里暴露，ViewModel 通过 [com.hmilyld.daytrail.ui.appViewModelFactory] 获取。
 */
class AppContainer(
    private val appContext: Context,
) {
    val settingsStore: SettingsStore = SettingsStore(appContext)

    /** 应用外壳（导航栏）显示状态。地图全屏时由地图页写入。 */
    val chrome: ChromeState = ChromeState()

    /** 记录状态只存在内存里，进程重启即归零，与实际服务状态保持一致。 */
    val recordingState: RecordingStateHolder = RecordingStateHolder()

    /** 备份模块需要一致性快照与行数；仍然 lazy，启动期替换数据库不受影响。 */
    internal val database: DayTrailDatabase by lazy { DayTrailDatabase.create(appContext) }

    /** 逆地理编码：坐标 → 粗略地名。复用纠偏那个 Web 服务 Key。 */
    val reverseGeocodeClient: ReverseGeocodeClient by lazy { ReverseGeocodeClient() }

    /** 新建地点时顺手补名字（fire-and-forget，失败保持未命名）。 */
    private val placeAutoNamer: PlaceAutoNamer by lazy {
        PlaceAutoNamer(reverseGeocodeClient, database.placeDao())
    }

    /** 云端接缝 ①：上层只依赖接口。 */
    val trackRepository: TrackRepository by lazy {
        LocalTrackRepository(database, autoNamer = placeAutoNamer)
    }

    /** 完整备份 / 恢复。与导出不同：它是整个数据库的无损快照。 */
    val backupManager: BackupManager by lazy {
        BackupManager(appContext, database, settingsStore, recordingState)
    }

    /**
     * 启动期数据库替换的结果，供设置页提示一次。
     * 由 [DayTrailApplication] 在创建容器后写入，读取后置空。
     */
    @Volatile
    internal var restoreOutcome: BackupRestore.ApplyResult? = null

    /** 云端接缝 ②：当前为空实现，二期替换即可。 */
    val syncGateway: SyncGateway = NoOpSyncGateway()

    /** 轨迹纠偏客户端。只在用户点「补全路线」时发请求。 */
    val grasproadClient: GrasproadClient by lazy { GrasproadClient() }

    /** 诊断日志。只记决策，不记坐标。 */
    val diagnosticLog: DiagnosticLog by lazy { DiagnosticLog(database.diagnosticDao()) }

    /** 数据导出。用 SAF 让用户选择保存位置，不需要存储权限。 */
    val exportManager: ExportManager by lazy { ExportManager(appContext, trackRepository) }

    /** 地点分享文本与地点导出（GPX / GeoJSON），与轨迹导出共用 SAF 模式。 */
    val placeExportManager: PlaceExportManager by lazy {
        PlaceExportManager(appContext, trackRepository)
    }

    /**
     * 数据导入。
     *
     * 与导出对称：SAF 只拿到用户显式选中的那个文件，不扫描外部存储。
     */
    val importManager: ImportManager by lazy { ImportManager(appContext, trackRepository) }

    // ---- 在线更新 ----

    /** 应用级作用域：更新检查与下载不能绑在某个页面的生命周期上。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 读取 Release 的 update.json（静态资产，不消耗 API 配额）。 */
    val updateChecker: UpdateChecker by lazy { UpdateChecker(settingsStore) }

    /** 更新流程的状态与动作，设置页与启动检查共用同一个实例。 */
    val updateCoordinator: UpdateCoordinator by lazy {
        UpdateCoordinator(appContext, updateChecker, appScope)
    }

    /** 备份 / 恢复动作。用应用级 scope，旋转屏幕不会取消正在进行的备份。 */
    val backupCoordinator: BackupCoordinator by lazy {
        BackupCoordinator(appContext, backupManager, appScope)
    }
}
