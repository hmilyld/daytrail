package com.hmilyld.daytrail

import android.content.Context
import com.hmilyld.daytrail.data.db.DayTrailDatabase
import com.hmilyld.daytrail.data.diagnostics.DiagnosticLog
import com.hmilyld.daytrail.data.export.ExportManager
import com.hmilyld.daytrail.data.geocode.PlaceAutoNamer
import com.hmilyld.daytrail.data.geocode.ReverseGeocodeClient
import com.hmilyld.daytrail.data.prefs.SettingsStore
import com.hmilyld.daytrail.data.repo.LocalTrackRepository
import com.hmilyld.daytrail.data.repo.NoOpSyncGateway
import com.hmilyld.daytrail.data.repo.SyncGateway
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.data.route.GrasproadClient
import com.hmilyld.daytrail.location.RecordingStateHolder

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

    private val database: DayTrailDatabase by lazy { DayTrailDatabase.create(appContext) }

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

    /** 云端接缝 ②：当前为空实现，二期替换即可。 */
    val syncGateway: SyncGateway = NoOpSyncGateway()

    /** 轨迹纠偏客户端。只在用户点「补全路线」时发请求。 */
    val grasproadClient: GrasproadClient by lazy { GrasproadClient() }

    /** 诊断日志。只记决策，不记坐标。 */
    val diagnosticLog: DiagnosticLog by lazy { DiagnosticLog(database.diagnosticDao()) }

    /** 数据导出。用 SAF 让用户选择保存位置，不需要存储权限。 */
    val exportManager: ExportManager by lazy { ExportManager(appContext, trackRepository) }
}
