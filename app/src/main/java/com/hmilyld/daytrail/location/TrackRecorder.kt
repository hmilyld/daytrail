package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 采样缓冲与批量落库。
 *
 * 为什么不逐点写库：省电档下静止是 10 分钟一点，但驾车会到 1 分钟一点，
 * 若每次都开事务、刷盘、触发 Flow 重组，唤醒次数和 IO 都会翻倍。
 * 这里攒够 [flushSize] 个点或满 [flushIntervalMs] 才写一次。
 *
 * 所有方法都是挂起或线程安全的，由 Service 在单一协程里调用。
 */
class TrackRecorder(
    private val repository: TrackRepository,
    private val scope: CoroutineScope,
    private val filter: PointFilter = PointFilter(),
    private val flushSize: Int = 20,
    private val flushIntervalMs: Long = 60_000L,
) {
    private val buffer = mutableListOf<TrackPointEntity>()
    private var lastAccepted: TrackSample? = null
    private var pendingDayKeys = mutableSetOf<DayKey>()
    private var autoFlushJob: Job? = null

    /** 已接受但还没写入的点数，供通知展示。 */
    val bufferedCount: Int get() = buffer.size

    /**
     * 处理一个定位结果。
     *
     * @return 是否被接受。被拒绝的点不会进缓冲，也不影响 [lastAccepted]
     */
    suspend fun offer(fix: LocationFix): Boolean {
        if (fix.errorCode != 0) return false

        val sample =
            TrackSample(
                timestampMs = fix.timestampMs,
                point = fix.point,
                accuracyMeters = fix.accuracyMeters,
                speedMps = fix.speedMps,
                bearingDegrees = fix.bearingDegrees,
                isMock = fix.isMock,
            )

        val verdict = filter.judge(lastAccepted, sample)
        if (verdict is FilterVerdict.Reject) return false

        lastAccepted = sample
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
            flush()
        }
        return true
    }

    /**
     * 把缓冲写库并重算涉及的每一天。
     *
     * 重算是幂等的，所以这里不做增量判断。
     */
    suspend fun flush() {
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
        flush()
        lastAccepted = null
    }

    /** 恢复记录时用库里最后一个点做接续，避免首个点被判成跳变。 */
    suspend fun restoreLastPoint() {
        val last = repository.lastPoint() ?: return
        lastAccepted =
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
