package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 采样缓冲与批量落库。
 *
 * 为什么不逐点写库：省电档下静止是 10 分钟一点，但驾车会到 1 分钟一点，
 * 若每次都开事务、刷盘、触发 Flow 重组，唤醒次数和 IO 都会翻倍。
 * 这里攒够 [flushSize] 个点或满 [flushIntervalMs] 才写一次。
 *
 * 所有入口都串行化（[mutex]）：采样回调、定时刷盘、暂停/结束时各有一个协程，
 * 并发 flush 会把同一批点写两遍。
 */
class TrackRecorder(
    private val repository: TrackRepository,
    private val scope: CoroutineScope,
    private val filter: PointFilter = PointFilter(),
    private val flushSize: Int = 20,
    private val flushIntervalMs: Long = 60_000L,
) {
    private val mutex = Mutex()
    private val buffer = mutableListOf<TrackPointEntity>()
    private var lastAcceptedSample: TrackSample? = null
    private var pendingDayKeys = mutableSetOf<DayKey>()
    private var autoFlushJob: Job? = null

    /** 最近一次点位被拒绝的原因。仅用于日志排查。 */
    var lastRejectReason: RejectReason? = null
        private set

    /**
     * 上一个被接受的点（已转成 GCJ-02）。
     *
     * 采样节奏要用它算「这段路的平均速度」，所以必须是**已接受**的点：
     * 被过滤器丢掉的点没有代表性，拿它算速度会让节奏跟着噪声走。
     */
    val lastAccepted: TrackSample? get() = lastAcceptedSample

    /** 已接受但还没写入的点数，供通知展示。 */
    val bufferedCount: Int get() = buffer.size

    /**
     * 处理一个定位结果。
     *
     * 坐标统一转成 GCJ-02 再入库：高德来源本身就是 GCJ-02，
     * 系统定位来源是 WGS-84，不转换会让轨迹偏移 50–500 米。
     *
     * @return 是否被接受。被拒绝的点不会进缓冲，也不影响 [lastAccepted]。
     *   拒绝原因记录在 [lastRejectReason]，便于日志排查。
     */
    suspend fun offer(fix: LocationFix): Boolean =
        mutex.withLock {
            if (fix.errorCode != 0) return@withLock false

            val normalized =
                if (fix.coordinateSystem == CoordinateSystem.WGS84) {
                    fix.copy(
                        point = CoordinateTransform.wgs84ToGcj02(fix.point),
                        coordinateSystem = CoordinateSystem.GCJ02,
                    )
                } else {
                    fix
                }

            val sample =
                TrackSample(
                    timestampMs = normalized.timestampMs,
                    point = normalized.point,
                    accuracyMeters = normalized.accuracyMeters,
                    speedMps = normalized.speedMps,
                    bearingDegrees = normalized.bearingDegrees,
                    isMock = normalized.isMock,
                )

            val verdict = filter.judge(lastAcceptedSample, sample)
            if (verdict is FilterVerdict.Reject) {
                lastRejectReason = verdict.reason
                return@withLock false
            }
            lastRejectReason = null

            lastAcceptedSample = sample
            val dayKey = DayKeys.of(sample.timestampMs)
            pendingDayKeys += dayKey

            buffer +=
                TrackPointEntity(
                    timestampMs = sample.timestampMs,
                    latitude = sample.point.latitude,
                    longitude = sample.point.longitude,
                    accuracyMeters = sample.accuracyMeters,
                    speedMps = sample.speedMps,
                    bearing = sample.bearingDegrees,
                    dayKey = dayKey.value,
                    isMock = sample.isMock,
                )

            if (buffer.size >= flushSize) {
                flushLocked()
            }
            true
        }

    /**
     * 把缓冲写库并重算涉及的每一天。
     *
     * 重算是幂等的，所以这里不做增量判断。
     */
    suspend fun flush() {
        mutex.withLock { flushLocked() }
    }

    /** 调用方必须已经持有 [mutex]。 */
    private suspend fun flushLocked() {
        if (buffer.isEmpty()) return

        val batch = buffer.toList()
        buffer.clear()

        repository.appendPoints(batch)
        val days = pendingDayKeys.toSet()
        pendingDayKeys = mutableSetOf()
        days.forEach { dayKey -> repository.recomputeDay(dayKey) }
    }

    /** 启动定时刷盘。Service 在开始记录时调用一次，重复调用不会叠加循环。 */
    fun startAutoFlush() {
        if (autoFlushJob?.isActive == true) return
        autoFlushJob =
            scope.launch {
                while (true) {
                    delay(flushIntervalMs)
                    runCatching { flush() }
                }
            }
    }

    /** 停止定时刷盘。 */
    fun stopAutoFlush() {
        autoFlushJob?.cancel()
        autoFlushJob = null
    }

    /** 停止记录时调用，确保最后一批数据落库。 */
    suspend fun finish() {
        mutex.withLock {
            flushLocked()
            lastAcceptedSample = null
        }
    }

    /** 恢复记录时用库里最后一个点做接续，避免首个点被判成跳变。 */
    suspend fun restoreLastPoint() {
        mutex.withLock {
            val last = repository.lastPoint() ?: return@withLock
            lastAcceptedSample =
                TrackSample(
                    timestampMs = last.timestampMs,
                    point =
                        com.hmilyld.daytrail.core
                            .GeoPoint(last.latitude, last.longitude),
                    accuracyMeters = last.accuracyMeters,
                    speedMps = last.speedMps,
                    bearingDegrees = last.bearing,
                    isMock = last.isMock,
                )
        }
    }

    /** 供测试与调试：等待下一次 flush 完成。 */
    suspend fun awaitFlush(timeoutMs: Long = 5_000L): Boolean =
        withTimeoutOrNull(timeoutMs) {
            flush()
            true
        } ?: false

    /** 单点直接写库，用于休眠唤醒后的确认点。 */
    suspend fun offerAndFlush(fix: LocationFix): Boolean {
        val accepted = offer(fix)
        flush()
        return accepted
    }
}
