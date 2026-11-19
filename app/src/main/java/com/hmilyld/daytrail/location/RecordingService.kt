package com.hmilyld.daytrail.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hmilyld.daytrail.DayTrailApplication
import com.hmilyld.daytrail.MainActivity
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.repo.todayKey
import com.hmilyld.daytrail.domain.PlaceResolver
import com.hmilyld.daytrail.permission.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 记录服务。
 *
 * 采样模型是「手表式」的，只有一句话：
 * **注册一次定位订阅，系统每 N 分钟回调一个点，写库，连线。**
 *
 * 关键约束：
 * - `foregroundServiceType="location"`，Android 14+ 必须声明，否则 startForeground 抛异常
 * - Android 12+ 禁止后台启动前台服务，因此只能由用户点击或通知动作触发
 * - 服务用 START_STICKY，被系统杀掉后尽量恢复
 *
 * 为什么不再「每轮申请一次定位」（见 AGENTS.md 4.x）：
 * 反复 request + remove 会让 GNSS 每轮重新搜星，取不到点时一次要挂满 25–45 秒，
 * 而且失败后 15 秒就重试。改成一次注册、按间隔回调之后，耗电与间隔成正比，
 * 用户也能从设置页算出一天记多少点。
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var systemSource: SystemLocationSource
    private lateinit var recorder: TrackRecorder

    /** 采样节奏。只决定「下一次隔多久」，不再推断运动状态。 */
    private val tempo = SamplingTempo(SamplingPreset.POWER_SAVER)

    private var loopJob: Job? = null
    private var tierJob: Job? = null
    private var watchdogJob: Job? = null
    private var placesJob: Job? = null
    private var sleepSettingJob: Job? = null
    private var motionJob: Job? = null
    private var networkJob: Job? = null

    /** 显著运动触发器。设备没有该传感器时只能靠每小时心跳兜底。 */
    private val motionGate by lazy { MotionGate(this) }

    /** 默认网络变化（WiFi ↔ 蜂窝）。用作「可能离开某栋楼」的免费线索。 */
    private val networkWatcher by lazy { NetworkWatcher(this) }

    private var lastNetworkProbeAtMs = 0L

    /**
     * 已知地点（家、公司）的坐标。
     *
     * 只用于一件事：判断「是不是已经到目的地了」，到了就早点进休眠。
     * 后台持续订阅地点表，用户刚命名完也不需要重启记录。
     */
    @Volatile
    private var knownPlaces: List<GeoPoint> = emptyList()

    /**
     * 当前订阅用的间隔。值变化时 [flatMapLatest] 会重新注册，
     * 同值不会重复注册——这是「只注册一次」的关键。
     */
    private val sampleInterval = MutableStateFlow(0L)

    /** 上一次拿到位置的时间（wall clock）。用于判断是否长时间没有回调。 */
    private var lastFixAtMs = 0L

    /** 上一次发出去的通知文案。相同文案不重复 post，避免无谓唤醒。 */
    private var lastNotificationText: String? = null

    private val appContainer
        get() = (application as DayTrailApplication).container

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        systemSource = SystemLocationSource(this)
        recorder =
            TrackRecorder(
                repository = appContainer.trackRepository,
                scope = scope,
            )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_STOP -> stopRecording()
            ACTION_RETRY -> retryRecording()
            else -> startRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从最近任务划掉不应中断记录，保持服务存活
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        cancelJobs()
        systemSource.release()
        appContainer.recordingState.setFixStale(false)
        appContainer.recordingState.update(RecordingState.IDLE)
        scope.cancel()
        super.onDestroy()
    }

    // ---- 记录控制 ----

    private fun startRecording() {
        if (loopJob?.isActive == true) return

        // 新会话：清掉上一次的异常状态
        tempo.reset()
        lastFixAtMs = System.currentTimeMillis()
        lastNotificationText = null
        appContainer.recordingState.setLocationIssue(null)
        appContainer.recordingState.setFixStale(false)
        appContainer.recordingState.setSleeping(false)
        appContainer.recordingState.update(RecordingState.RECORDING)
        log("service", "开始记录")
        startForegroundCompat(buildNotification(getString(R.string.record_starting)))

        // 权限或开关有问题时，采集无法进行。这类问题必须让用户去处理，
        // 而不是原地反复请求定位——那只会耗电且永远取不到。
        val issue = currentIssue()
        if (issue != null) {
            appContainer.recordingState.setLocationIssue(issue)
            Log.w(TAG, "无法开始采集：$issue")
            notify(getString(R.string.record_location_failed), ongoing = false)
            appContainer.recordingState.update(RecordingState.IDLE)
            // 保持通知可见，让离开应用的用户也能看到原因
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return
        }

        loopJob =
            scope.launch {
                // 档位必须在循环开始前读：早期实现写死了 POWER_SAVER，
                // 用户在设置里选「高精度」完全没效果。
                applyTier(appContainer.settingsStore.samplingTier.first())
                // 记住「用户想记录」这件事，开机时据此提示恢复（不自动启动）
                appContainer.settingsStore.setRecordingIntent(true)
                sampleInterval.value = tempo.intervalMs
                observeTierChanges()
                observePlaces()
                observeAutoSleep()
                startMotionWake()
                observeNetwork()

                recorder.restoreLastPoint()
                recorder.startAutoFlush()
                runSamplingLoop()
            }
    }

    /**
     * 应用采样档位。
     *
     * 只改 [SamplingTempo] 的 preset，不重建对象：降档计时要保留，
     * 否则切档会让「已经静止多久」重新计时。
     */
    private fun applyTier(tier: SamplingTier) {
        val next = presetOf(tier)
        if (tempo.preset == next) return
        Log.d(TAG, "采样档位切换为 $next")
        tempo.preset = next
        if (loopJob?.isActive == true) {
            sampleInterval.value = tempo.intervalMs
        }
    }

    /** 录制过程中改档位也要生效，不必等下一次开始记录。 */
    private fun observeTierChanges() {
        tierJob?.cancel()
        tierJob =
            scope.launch {
                appContainer.settingsStore.samplingTier.collect { applyTier(it) }
            }
    }

    /** 地点表变了（新增、改名、删除）时立刻更新本地缓存。 */
    private fun observePlaces() {
        placesJob?.cancel()
        placesJob =
            scope.launch {
                appContainer.trackRepository.observePlaces().collect { summaries ->
                    knownPlaces =
                        summaries.map { summary ->
                            GeoPoint(summary.place.latitude, summary.place.longitude)
                        }
                }
            }
    }

    /** 「自动休眠」开关。关掉之后采样间隔只由速度决定。 */
    private fun observeAutoSleep() {
        sleepSettingJob?.cancel()
        sleepSettingJob =
            scope.launch {
                appContainer.settingsStore.autoSleep.collect { enabled ->
                    tempo.autoSleepEnabled = enabled
                }
            }
    }

    /**
     * 显著运动唤醒。
     *
     * 休眠（1 小时）和静止档（10 分钟）都会把间隔拉长，**两种都要处理**：
     * 早上出门时人还在静止档，如果只在休眠时响应传感器，下一次回调最坏要等
     * 10 分钟——实测 07:32 出发、07:43 才拿到下一个点，中间 6.4 公里整段丢失。
     *
     * 传感器一响就恢复节奏并主动补一个点；如果其实没动，降档的 5 分钟延迟
     * 会把它自然降回静止档。
     */
    private fun startMotionWake() {
        motionJob?.cancel()
        if (!motionGate.available) {
            Log.d(TAG, "设备没有显著运动传感器，只能靠每小时心跳兜底")
            log("motion", "设备没有显著运动传感器")
            return
        }
        motionJob =
            scope.launch {
                motionGate.events().collect {
                    val tier = tempo.tier
                    if (tier != TempoTier.SLEEP && tier != TempoTier.STILL) return@collect

                    Log.d(
                        TAG,
                        if (tier == TempoTier.SLEEP) "显著运动，退出休眠" else "显著运动，退出静止档",
                    )
                    log("motion", if (tier == TempoTier.SLEEP) "退出休眠" else "退出静止档")
                    val wasSleeping = tier == TempoTier.SLEEP
                    tempo.wake(System.currentTimeMillis())
                    if (wasSleeping) appContainer.recordingState.setSleeping(false)
                    sampleInterval.value = tempo.intervalMs
                    systemSource
                        .requestOnce(highAccuracy = true, timeoutMs = WAKE_FIX_TIMEOUT_MS)
                        ?.let { onFix(it) }
                    sampleInterval.value = tempo.intervalMs
                }
            }
    }

    /**
     * 网络切换探测。
     *
     * WiFi 掉≠出门（电梯口、楼下都会掉），所以这里只做一次定位：
     * 人还在原地就开 15 分钟守候窗口（按静止档采、不许睡），真离开了才切 2 分钟档。
     *
     * 两个护栏：新网络稳定 60 秒才探（过滤电梯里反复掉线），5 分钟内最多探一次。
     */
    private fun observeNetwork() {
        networkJob?.cancel()
        networkJob =
            scope.launch {
                networkWatcher.events().collect { transport ->
                    delay(NETWORK_SETTLE_MS)
                    if (networkWatcher.current() != transport) return@collect

                    val now = System.currentTimeMillis()
                    if (now - lastNetworkProbeAtMs < NETWORK_PROBE_COOLDOWN_MS) return@collect
                    if (tempo.tier != TempoTier.STILL && tempo.tier != TempoTier.SLEEP) {
                        return@collect
                    }

                    lastNetworkProbeAtMs = now
                    log("network", "网络切换为 $transport")
                    probeAfterNetworkChange()
                }
            }
    }

    /** 网络切换后取一个点，判断是不是真的离开了。 */
    private suspend fun probeAfterNetworkChange() {
        val fix =
            systemSource.requestOnce(
                highAccuracy = true,
                timeoutMs = NETWORK_PROBE_TIMEOUT_MS,
            )

        if (fix == null) {
            // 电梯、地库拿不到位置：不确定，先别睡，等下一次采样判断
            Log.d(TAG, "网络切换探针失败，进入守候窗口")
            log("probe", "探针失败，进入守候窗口")
            enterWatchWindow()
            return
        }

        onFix(fix)

        val point = fix.toGcj02()
        val nearest = knownPlaces.minOfOrNull { place -> distanceMeters(place, point) }
        val distanceText = nearest?.toInt()?.toString() ?: "未知"

        when (PlaceProbe.decide(nearest)) {
            ProbeAction.WATCH -> {
                Log.d(TAG, "网络切换后仍在地点内（$distanceText m），进入守候窗口")
                log("probe", "距地点 $distanceText m，进入守候窗口")
                enterWatchWindow()
            }

            ProbeAction.WAKE -> {
                Log.d(TAG, "网络切换后已离开地点，按出发处理")
                log(
                    "probe",
                    if (nearest == null) "没有已知地点，按出发处理" else "距地点 $distanceText m，按出发处理",
                )
                tempo.wake(System.currentTimeMillis())
                finishProbe()
            }
        }
    }

    /** 守候：不唤醒，只保证 15 分钟内不睡。 */
    private suspend fun enterWatchWindow() {
        tempo.watch(System.currentTimeMillis())
        finishProbe()
    }

    /**
     * 探针之后的收尾。
     *
     * `onFix` 可能刚把通知改成「已休眠」，而 `watch()` 又把档位拉回静止档；
     * 不刷新的话通知会停在错误的文案上，最长持续到下一个采样点（10 分钟）。
     */
    private suspend fun finishProbe() {
        appContainer.recordingState.setSleeping(false)
        sampleInterval.value = tempo.intervalMs
        updateNotification()
    }

    private fun pauseRecording() {
        cancelJobs()
        recorder.stopAutoFlush()
        log("service", "暂停")
        appContainer.recordingState.update(RecordingState.PAUSED)
        appContainer.recordingState.setLocationIssue(null)
        appContainer.recordingState.setFixStale(false)
        scope.launch { recorder.finish() }
        notify(getString(R.string.record_paused_status))
    }

    /** 定位异常后的手动重试。重置状态并重新开始采集。 */
    private fun retryRecording() {
        Log.d(TAG, "用户手动重试定位")
        // 采集循环还在跑时 startRecording 会直接返回，所以必须先停掉旧任务。
        // 否则「录制途中被关掉定位」这种情况，点重试等于没点。
        cancelJobs()
        appContainer.recordingState.setLocationIssue(null)
        startRecording()
    }

    private fun stopRecording() {
        cancelJobs()
        recorder.stopAutoFlush()
        log("service", "停止记录")
        scope.launch {
            recorder.finish()
            appContainer.settingsStore.setRecordingIntent(false)
            appContainer.recordingState.setFixStale(false)
            appContainer.recordingState.update(RecordingState.IDLE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancelJobs() {
        loopJob?.cancel()
        loopJob = null
        tierJob?.cancel()
        tierJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        placesJob?.cancel()
        placesJob = null
        sleepSettingJob?.cancel()
        sleepSettingJob = null
        motionJob?.cancel()
        motionJob = null
        networkJob?.cancel()
        networkJob = null
    }

    // ---- 采样 ----

    /**
     * 采样主体。
     *
     * 1. 先主动取一次，让用户点完「开始记录」几秒内就能看到一个点；
     * 2. 之后完全交给按间隔的订阅，档位变了才重新注册；
     * 3. 订阅本身失败（provider 被关掉等）时，等 30 秒重试，不退出服务。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runSamplingLoop() {
        systemSource
            .requestOnce(highAccuracy = true, timeoutMs = INITIAL_FIX_TIMEOUT_MS)
            ?.let { onFix(it) }

        startWatchdog()

        sampleInterval
            .filter { it > 0 }
            .distinctUntilChanged()
            .flatMapLatest { interval -> systemSource.updates(interval) }
            .retryWhen { cause, _ ->
                Log.w(TAG, "定位订阅中断：${cause.message}，${RESUBSCRIBE_DELAY_MS / 1000}s 后重试")
                delay(RESUBSCRIBE_DELAY_MS)
                true
            }.collect { fix ->
                onFix(fix)
                // 档位可能在 onFix 里变了。同值不会触发重新注册。
                sampleInterval.value = tempo.intervalMs
            }
    }

    /**
     * 处理一个定位结果。
     *
     * 两件事必须按顺序做：先用**上一个已接受的点**更新节奏，
     * 再写入缓冲。反过来的话就是拿新点和自己比，速度永远是 0。
     */
    private suspend fun onFix(fix: LocationFix) {
        if (fix.errorCode != 0) return
        lastFixAtMs = System.currentTimeMillis()

        val point = fix.toGcj02()
        val previousAccepted =
            recorder.lastAccepted?.let { TempoSample(it.timestampMs, it.point) }

        val accepted = recorder.offer(fix)

        val insideKnownPlace =
            knownPlaces.any { place ->
                distanceMeters(place, point) <= PlaceResolver.MATCH_RADIUS_METERS
            }
        val previousTier = tempo.tier
        tempo.observe(
            previous = previousAccepted,
            sample = TempoSample(fix.timestampMs, point),
            insideKnownPlace = insideKnownPlace,
        )
        if (tempo.tier != previousTier) {
            val elapsedSec =
                previousAccepted
                    ?.let { (fix.timestampMs - it.timestampMs) / 1000.0 }
                    ?.coerceAtLeast(1.0)
                    ?: 1.0
            val speed =
                previousAccepted?.let { distanceMeters(it.point, point) / elapsedSec } ?: 0.0
            log(
                "tier",
                "$previousTier→${tempo.tier} 速度=${String.format(Locale.US, "%.1f", speed)}m/s " +
                    "在已知地点=$insideKnownPlace",
            )
        }
        syncSleepState()

        // 地图上的位置圆点跟着这条路走，不再自己轮询定位
        appContainer.recordingState.setLastFix(point)
        appContainer.recordingState.setFixStale(false)

        // 休眠中不要用「已记录 N 分钟」覆盖休眠文案，否则用户看不出它在省电
        if (accepted && tempo.tier != TempoTier.SLEEP) {
            updateNotification()
        }
    }

    /** 休眠状态变化时更新界面与通知。 */
    private fun syncSleepState() {
        val sleeping = tempo.tier == TempoTier.SLEEP
        if (appContainer.recordingState.sleeping.value == sleeping) return

        appContainer.recordingState.setSleeping(sleeping)
        appContainer.recordingState.setFixStale(false)
        if (sleeping) {
            Log.d(TAG, "进入休眠：采样间隔 ${tempo.intervalMs / 60_000} 分钟")
            notify(getString(R.string.record_sleeping))
        }
    }

    /**
     * 长时间没有回调时把通知切回「正在获取定位」。
     *
     * 这里只改提示，**不停止采集**：室内、地下连续几分钟拿不到位置是正常的，
     * 按间隔的订阅会自己等到下一次回调。早期实现把「连续 5 次失败」当成
     * 错误并停止记录，结果用户明明在走动，应用却显示「定位失败」。
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob =
            scope.launch {
                while (isActive) {
                    delay(WATCHDOG_TICK_MS)

                    // 录制途中关掉定位开关 / 权限被回收：只在启动时检查会一直装作在记录。
                    // 这里每 60 秒对一次状态，恢复后自动清掉提示。
                    val issue = currentIssue()
                    if (issue != appContainer.recordingState.locationIssue.value) {
                        appContainer.recordingState.setLocationIssue(issue)
                        log("issue", issue?.name ?: "已恢复")
                        if (issue != null) notify(getString(R.string.record_location_failed))
                    }

                    // 休眠时「很久没有新点位」是预期行为，不是异常：
                    // 采样间隔本来就是 1 小时。既不能给界面打 fixStale，
                    // 也不能把通知改成「正在获取定位」——那会把「已休眠」的提示顶掉，
                    // 而且之后不会再刷回来（休眠中不更新通知）。
                    if (tempo.tier == TempoTier.SLEEP) {
                        appContainer.recordingState.setFixStale(false)
                        continue
                    }

                    val silentMs = System.currentTimeMillis() - lastFixAtMs
                    appContainer.recordingState.setFixStale(silentMs >= STALE_AFTER_MS)
                    if (silentMs >= NO_FIX_NOTICE_MS) {
                        notify(getString(R.string.record_waiting_location))
                    }
                }
            }
    }

    /** 判断采集是否根本无法进行。只有权限与定位开关算，信号差不在此列。 */
    private fun currentIssue(): LocationFailureReason? =
        when {
            !Permissions.hasAnyLocation(this) -> LocationFailureReason.PERMISSION_DENIED
            !Permissions.isLocationServiceEnabled(this) -> LocationFailureReason.LOCATION_DISABLED
            !systemSource.hasAnyProvider() -> LocationFailureReason.LOCATION_DISABLED
            else -> null
        }

    // ---- 通知 ----

    /** 写一条诊断日志。失败不影响记录主流程。 */
    private fun log(
        category: String,
        detail: String,
    ) {
        scope.launch { appContainer.diagnosticLog.record(category, detail) }
    }

    /**
     * 刷新通知文案。
     *
     * 用当天的「累积移动 + 静止时长」而不是「距离首个点过了多久」：
     * 后者会把两次记录之间的空档也算进去——早上记完、傍晚再记，
     * 通知会显示「已记录 8 小时」，明显不对。
     */
    private suspend fun updateNotification() {
        val day = appContainer.trackRepository.observeDay(todayKey()).first()
        val minutes = ((day?.movingMillis ?: 0L) + (day?.stoppedMillis ?: 0L)) / 60_000
        val points = day?.pointCount ?: 0
        notify(getString(R.string.record_elapsed, minutes, points))
    }

    /** 文案相同就不重复 post。通知刷屏既吵也费电。 */
    private fun notify(
        text: String,
        ongoing: Boolean = true,
    ) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, ongoing))
    }

    private fun buildNotification(
        statusText: String,
        ongoing: Boolean = true,
    ): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val issue = appContainer.recordingState.locationIssue.value
        val isRecording = appContainer.recordingState.state.value == RecordingState.RECORDING

        // 定位异常时主操作变成「重试」，而不是「暂停」——
        // 此时采集已经停了，暂停没有意义。
        val actionIntent =
            when {
                issue != null -> ACTION_RETRY
                isRecording -> ACTION_PAUSE
                else -> ACTION_START
            }
        val actionLabel =
            when {
                issue != null -> getString(R.string.action_retry)
                isRecording -> getString(R.string.today_action_pause)
                else -> getString(R.string.today_action_start)
            }

        val actionPending =
            PendingIntent.getService(
                this,
                1,
                Intent(this, RecordingService::class.java).setAction(actionIntent),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val stopPending =
            PendingIntent.getService(
                this,
                2,
                Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_stat_track)
            .setContentIntent(openApp)
            .setOngoing(ongoing)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, actionLabel, actionPending)
            .addAction(0, getString(R.string.today_action_stop), stopPending)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        // minSdk 31，foregroundServiceType 参数一定可用，无需版本分支
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_recording_desc)
                setShowBadge(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001

        /**
         * 会话首个点的等待上限。
         *
         * GPS 冷启动要搜星，通常 30–60 秒。这一次给足，之后完全交给订阅，
         * 不再有「一轮一轮超时」的问题。
         */
        private const val INITIAL_FIX_TIMEOUT_MS = 45_000L

        /** 订阅中断后的重试间隔。 */
        private const val RESUBSCRIBE_DELAY_MS = 30_000L

        /** 休眠被运动唤醒后，主动补一个点的等待上限。 */
        private const val WAKE_FIX_TIMEOUT_MS = 20_000L

        /** 网络切换后等多久确认「新网络稳定」，过滤电梯、地库里的反复掉线。 */
        private const val NETWORK_SETTLE_MS = 60_000L

        /** 两次网络探针之间的最短间隔。 */
        private const val NETWORK_PROBE_COOLDOWN_MS = 5 * 60_000L

        /** 网络探针的定位超时。拿不到就进入守候窗口，不重试。 */
        private const val NETWORK_PROBE_TIMEOUT_MS = 20_000L

        /** 看门狗轮询间隔。 */
        private const val WATCHDOG_TICK_MS = 60_000L

        /** 超过这么久没有回调，就把通知切回「正在获取定位」。 */
        private const val NO_FIX_NOTICE_MS = 10 * 60_000L

        /**
         * 超过这么久没有回调，界面上给一句「暂时没有新的定位」。
         *
         * 比通知的 10 分钟更保守：静止档本身就是 10 分钟一个点，
         * 10 分钟没点可能只是刚好跨过一个周期。15 分钟说明至少漏了一轮，
         * 这时提示才对用户有信息量，否则就是在健康状态下闪来闪去。
         */
        private const val STALE_AFTER_MS = 15 * 60_000L

        const val ACTION_START = "com.hmilyld.daytrail.action.START"
        const val ACTION_PAUSE = "com.hmilyld.daytrail.action.PAUSE"
        const val ACTION_STOP = "com.hmilyld.daytrail.action.STOP"
        const val ACTION_RETRY = "com.hmilyld.daytrail.action.RETRY"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, RecordingService::class.java).setAction(ACTION_START),
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_PAUSE),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
            )
        }

        /** 定位异常后的手动重试。 */
        fun retry(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_RETRY),
            )
        }

        /** 采样档位到预设的映射。 */
        fun presetOf(tier: SamplingTier): SamplingPreset =
            when (tier) {
                SamplingTier.POWER_SAVER -> SamplingPreset.POWER_SAVER
                SamplingTier.BALANCED -> SamplingPreset.BALANCED
                SamplingTier.HIGH_ACCURACY -> SamplingPreset.HIGH_ACCURACY
            }
    }
}
