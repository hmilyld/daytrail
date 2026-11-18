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
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.repo.todayKey
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
        appContainer.recordingState.update(RecordingState.RECORDING)
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
                sampleInterval.value = tempo.intervalMs
                observeTierChanges()

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

    private fun pauseRecording() {
        cancelJobs()
        recorder.stopAutoFlush()
        appContainer.recordingState.update(RecordingState.PAUSED)
        appContainer.recordingState.setLocationIssue(null)
        scope.launch { recorder.finish() }
        notify(getString(R.string.record_paused_status))
    }

    /** 定位异常后的手动重试。重置状态并重新开始采集。 */
    private fun retryRecording() {
        Log.d(TAG, "用户手动重试定位")
        appContainer.recordingState.setLocationIssue(null)
        startRecording()
    }

    private fun stopRecording() {
        cancelJobs()
        recorder.stopAutoFlush()
        scope.launch {
            recorder.finish()
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

        tempo.observe(
            previous = previousAccepted,
            sample = TempoSample(fix.timestampMs, point),
        )

        // 地图上的位置圆点跟着这条路走，不再自己轮询定位
        appContainer.recordingState.setLastFix(point)

        if (accepted) {
            updateNotification()
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
                    val silentMs = System.currentTimeMillis() - lastFixAtMs
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
        private const val CHANNEL_ID = "recording"
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

        /** 看门狗轮询间隔。 */
        private const val WATCHDOG_TICK_MS = 60_000L

        /** 超过这么久没有回调，就把通知切回「正在获取定位」。 */
        private const val NO_FIX_NOTICE_MS = 10 * 60_000L

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
