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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 记录服务。
 *
 * 关键约束：
 * - `foregroundServiceType="location"`，Android 14+ 必须声明，否则 startForeground 抛异常
 * - Android 12+ 禁止后台启动前台服务，因此只能由用户点击或通知动作触发
 * - 服务用 START_STICKY，被系统杀掉后尽量恢复
 *
 * 采样由自己的调度循环驱动，而不是让 SDK 连续上报：
 * 连续模式间隔固定，无法在静止时降到 10 分钟、休眠时完全停下。
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var amapEngine: LocationEngine
    private lateinit var systemSource: SystemLocationSource
    private lateinit var recorder: TrackRecorder
    private lateinit var samplingPolicy: SamplingPolicy
    private var sleepController: SleepController? = null

    private var loopJob: Job? = null
    private var currentState = MovementState.WALKING
    private var lastFix: LocationFix? = null
    private var acceptedCount = 0
    private var startedAtMs = 0L

    /** 本会话是否已切到系统定位。 */
    private var useSystemSource = false

    /** 高德连续失败次数，达到上限后切系统定位。 */
    private var amapFailures = 0

    /** 本会话是否已经成功采到过点。用于给首次定位更长的超时。 */
    private var hasEverSampled = false

    /** 连续失败预算。耗尽后停止采集，避免无限重试耗电。 */
    private val failureBudget = SamplingFailureBudget()

    private var pendingWake: (() -> Unit)? = null

    private val appContainer
        get() = (application as DayTrailApplication).container

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        amapEngine = LocationEngine(this)
        systemSource = SystemLocationSource(this)
        samplingPolicy = SamplingPolicy(SamplingPreset.POWER_SAVER)
        recorder =
            TrackRecorder(
                repository = appContainer.trackRepository,
                scope = scope,
            )
        sleepController =
            SleepController(
                context = this,
                fallbackIntervalMs = SamplingPreset.POWER_SAVER.profile.sleepFallbackMs,
                onWake = { handleWake() },
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
        loopJob?.cancel()
        sleepController?.release()
        amapEngine.release()
        systemSource.release()
        appContainer.recordingState.update(RecordingState.IDLE)
        scope.cancel()
        super.onDestroy()
    }

    // ---- 记录控制 ----

    private fun startRecording() {
        if (loopJob?.isActive == true) return

        // 新会话：清掉上一次的异常状态与失败计数
        failureBudget.reset()
        hasEverSampled = false
        appContainer.recordingState.setLocationIssue(null)

        startForegroundCompat(buildNotification(getString(R.string.record_starting)))

        loopJob =
            scope.launch {
                // 从当天的汇总恢复计数与起始时间：
                // 服务被系统杀掉后 START_STICKY 重启时，内存里的计数已经丢失，
                // 不恢复的话通知会突兀地从「0 分钟 0 个点」重新开始。
                val today = todayKey()
                val day = appContainer.trackRepository.observeDay(today).first()
                startedAtMs = day?.startedAt ?: System.currentTimeMillis()
                acceptedCount = day?.pointCount ?: 0

                appContainer.recordingState.update(RecordingState.RECORDING)
                recorder.restoreLastPoint()
                recorder.startAutoFlush()
                runSamplingLoop()
            }
    }

    private fun pauseRecording() {
        loopJob?.cancel()
        loopJob = null
        recorder.stopAutoFlush()
        sleepController?.wake()
        amapEngine.release()
        systemSource.release()
        appContainer.recordingState.update(RecordingState.PAUSED)
        appContainer.recordingState.setLocationIssue(null)
        scope.launch { recorder.finish() }
        notify(buildNotification(getString(R.string.record_paused_status)))
    }

    /** 定位异常后的手动重试。重置预算并重新开始采集。 */
    private fun retryRecording() {
        Log.d(TAG, "用户手动重试定位")
        startRecording()
    }

    private fun stopRecording() {
        loopJob?.cancel()
        loopJob = null
        recorder.stopAutoFlush()
        sleepController?.wake()
        scope.launch {
            recorder.finish()
            appContainer.recordingState.update(RecordingState.IDLE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---- 采样循环 ----

    private suspend fun runSamplingLoop() {
        while (true) {
            // null 表示当前应休眠，不主动采样
            if (samplingPolicy.intervalFor(currentState) == null) {
                // 休眠：停掉定位，交给传感器与兜底闹钟
                sleepController?.sleep()
                notify(buildNotification(getString(R.string.record_sleeping_status)))

                suspendCancellableCoroutine<Unit> { continuation ->
                    pendingWake = { continuation.resumeWith(Result.success(Unit)) }
                }

                // 关键：唤醒后必须退出 SLEEPING，否则下一轮 intervalFor 仍返回 null，
                // 会立刻再次休眠，永远不采样。
                currentState = MovementState.WALKING
                samplingPolicy.reset()
                sleepController?.wake()
                continue
            }

            sleepController?.wake()

            val fix =
                requestFixWithFallback(
                    highAccuracy = samplingPolicy.requiresHighAccuracy(currentState),
                    firstFix = !hasEverSampled,
                )

            val accepted = fix != null && fix.errorCode == 0 && recordFix(fix)

            if (accepted) {
                hasEverSampled = true
                failureBudget.recordSuccess()
                // 必须重新计算：recordFix 里可能已经把状态从 WALKING 改成 DRIVING，
                // 沿用循环开头算的间隔会慢一拍
                val nextInterval = samplingPolicy.intervalFor(currentState) ?: 0L
                Log.d(TAG, "采样成功，等待 ${nextInterval / 1000}s，状态=$currentState")
                delay(nextInterval)
            } else {
                // 失败时不能等完整间隔：步行档是 3 分钟，
                // 用户会觉得「点了开始记录没反应」。改用短间隔重试。
                val reason = detectFailureReason(fix?.errorCode)
                val exhausted = failureBudget.recordFailure()
                Log.d(
                    TAG,
                    "本轮未采到点（reason=$reason error=${fix?.errorCode}），" +
                        "第 ${failureBudget.consecutiveFailures} 次失败",
                )

                if (exhausted) {
                    // 放弃采集：继续重试只会持续唤醒 GPS，纯耗电且没有意义。
                    // 交给界面与通知明确告知原因，等用户处理。
                    enterLocationIssue(reason)
                    return
                }

                notify(buildNotification(getString(R.string.record_waiting_location)))
                delay(RETRY_DELAY_MS)
            }
        }
    }

    /**
     * 判断失败原因，给用户可操作的提示。
     *
     * 这三类原因的处理方式完全不同：权限要去设置页、开关要去快捷面板、
     * 纯粹信号差只需换个地方再试。
     */
    private fun detectFailureReason(lastErrorCode: Int?): LocationFailureReason =
        when {
            !Permissions.hasFineLocation(this) -> LocationFailureReason.PERMISSION_DENIED
            !Permissions.isLocationServiceEnabled(this) -> LocationFailureReason.LOCATION_DISABLED
            !systemSource.hasAnyProvider() -> LocationFailureReason.LOCATION_DISABLED
            lastErrorCode == null -> LocationFailureReason.NO_FIX
            else -> LocationFailureReason.NO_FIX
        }

    /** 进入「定位异常」状态：停止采集，保留通知与界面提示。 */
    private fun enterLocationIssue(reason: LocationFailureReason) {
        loopJob?.cancel()
        loopJob = null
        amapEngine.release()
        systemSource.release()

        appContainer.recordingState.setLocationIssue(reason)
        notify(buildNotification(getString(R.string.record_location_failed)))
    }

    /**
     * 把定位结果写入缓冲。
     *
     * @return 是否被接受（过滤器可能因精度差、跳变等原因丢弃）
     */
    private suspend fun recordFix(fix: LocationFix): Boolean {
        val previousSample = lastFix?.let { MovementSample(it.point, it.timestampMs, it.speedMps) }
        currentState =
            samplingPolicy.nextState(
                previous = previousSample,
                sample = MovementSample(fix.point, fix.timestampMs, fix.speedMps),
                currentState = currentState,
            )
        lastFix = fix

        val accepted = recorder.offer(fix)
        if (accepted) {
            acceptedCount++
            updateNotification()
        } else {
            Log.d(TAG, "定位结果被过滤器丢弃（精度 ${fix.accuracyMeters}m）")
        }
        return accepted
    }

    /**
     * 请求一次定位，并处理高德不可用的降级。
     *
     * 降级策略：
     * 1. 优先高德（多源融合、精度更好）
     * 2. 高德连续失败达到阈值 → 本会话永久切到系统定位
     * 3. 系统定位也不可用时，这一轮就放弃，等重试
     *
     * @param firstFix 是否本次会话的第一次定位。GPS 冷启动通常要 30–60 秒，
     *   首次给更长的超时，否则几乎必然失败。
     */
    private suspend fun requestFixWithFallback(
        highAccuracy: Boolean,
        firstFix: Boolean,
    ): LocationFix? {
        val timeout =
            if (firstFix) INITIAL_FIX_TIMEOUT_MS else FIX_TIMEOUT_MS

        val useSystem = useSystemSource

        val fix =
            if (useSystem) {
                systemSource.requestOnce(highAccuracy, timeout)
            } else {
                amapEngine.requestOnce(highAccuracy, timeout)
            }

        Log.d(TAG, "定位结果 source=${if (useSystem) "system" else "amap"} error=${fix?.errorCode}")

        if (useSystem) return fix

        if (fix != null && fix.errorCode == 0) {
            amapFailures = 0
            return fix
        }

        amapFailures++
        if (amapFailures >= AMAP_FAILURE_LIMIT) {
            if (systemSource.isAvailable) {
                Log.w(
                    TAG,
                    "高德定位连续失败 $amapFailures 次（错误码 ${fix?.errorCode}），" +
                        "本会话切换到系统定位",
                )
                useSystemSource = true
                amapEngine.release()
                // 立刻用系统定位重试一次，不必等到下一个间隔
                return systemSource.requestOnce(highAccuracy, FIX_TIMEOUT_MS)
            }
            Log.w(TAG, "高德定位失败且系统定位不可用，本轮放弃")
        }
        return fix
    }

    private fun handleWake() {
        pendingWake?.invoke()
        pendingWake = null
    }

    // ---- 通知 ----

    private fun updateNotification() {
        val elapsed = (System.currentTimeMillis() - startedAtMs) / 1000
        notify(
            buildNotification(
                getString(R.string.record_elapsed, elapsed / 60, acceptedCount),
            ),
        )
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
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
            .setOngoing(true)
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

        /** 单次定位的等待上限。粗定位在室内可能要十几秒，给足余量。 */
        private const val FIX_TIMEOUT_MS = 25_000L

        /** 高德定位的超时。高德在不可用时会很快返回错误码，不必等满。 */
        private const val AMAP_TIMEOUT_MS = 12_000L

        /**
         * 首次定位的超时。
         *
         * GPS 冷启动需要搜星，通常 30–60 秒。用 25 秒会几乎必然失败，
         * 用户看到的就是「点了开始记录没反应」。
         */
        private const val INITIAL_FIX_TIMEOUT_MS = 45_000L

        /**
         * 采点失败后的重试间隔。
         *
         * 不能用状态间隔：步行档是 3 分钟，用户点「开始记录」后
         * 要等 3 分钟才看到第一个点，会以为功能坏了。
         */
        private const val RETRY_DELAY_MS = 15_000L

        /** 高德连续失败多少次后切系统定位。 */
        private const val AMAP_FAILURE_LIMIT = 2

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
