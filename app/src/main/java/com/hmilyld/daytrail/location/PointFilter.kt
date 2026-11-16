package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/** 一个待落库的采样点。已通过过滤，可以直接写库。 */
data class TrackSample(
    val timestampMs: Long,
    val point: GeoPoint,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val isMock: Boolean,
)

/** 过滤结果。 */
sealed interface FilterVerdict {
    /** 接受该点。 */
    data object Accept : FilterVerdict

    /** 丢弃，附带原因（用于日志和调试）。 */
    data class Reject(
        val reason: RejectReason,
    ) : FilterVerdict
}

/** 丢弃原因。 */
enum class RejectReason {
    /** 坐标越界或 (0,0)。 */
    INVALID_COORDINATE,

    /** 精度差到没有参考价值。 */
    POOR_ACCURACY,

    /** 与上一点推算速度过快，判定为跳变。 */
    TELEPORT,

    /**
     * 位移小于定位误差，分不清是真的移动还是 GPS 抖动。
     *
     * 不代表「定位失败」——位置是有效的，只是没有信息量。
     */
    WITHIN_NOISE,

    /** 时间戳早于上一点，乱序数据。 */
    OUT_OF_ORDER,
}

/**
 * 采样点过滤器（纯逻辑，可单测）。
 *
 * **设计前提**：本应用记录的是「大概的行程」，不追求轨迹精度。
 * 因此策略是「尽量多记录」，而不是「只记录精确的点」。
 *
 * 精度阈值取 5000 米：
 * - 只授予「大致位置」时，高德/系统返回的常是基站级定位，精度可达 2000 米
 * - 实测：无精确权限时高德返回 error=0 但 accuracy=2000m
 * - 这类点画出来的轨迹很粗，但足以回答「今天去了哪几个区」\n * - 超过 5 公里基本只能算「在某个基站范围内」，再记也没意义
 * - 里程计算另有 2 倍误差门槛（见 DayAggregator），不会被粗点污染
 *
 * 关键区分：**「定位失败」与「点位被丢弃」是两件事**。
 * 精度不够只说明这个点没有信息量，采集本身是成功的，
 * 不应该计入失败预算（否则会因为「室内信号差」而停止记录）。
 */
class PointFilter(
    private val maxAccuracyMeters: Float = 5000f,
    private val maxSpeedMps: Double = 300_000.0 / 3600.0, // 300 km/h
    private val duplicateRadiusMeters: Double = 3.0,
    private val duplicateWindowMs: Long = 30_000L,
    /**
     * 静止心跳间隔。
     *
     * 位移小于定位误差时默认丢弃，否则静止时的 GPS 抖动会画出杂乱折线。
     * 但如果一直丢弃，界面会长时间显示「0 个点」，让用户以为没在记录。
     * 因此每隔这么久强制保留一个点，作为「仍在记录」的证据。
     */
    private val stationaryHeartbeatMs: Long = 10 * 60_000L,
) {
    /**
     * 判定一个采样点是否应当入库。
     *
     * @param previous 上一个已接受的点，首点时传 null
     */
    fun judge(
        previous: TrackSample?,
        candidate: TrackSample,
    ): FilterVerdict {
        if (!candidate.point.isValid) {
            return FilterVerdict.Reject(RejectReason.INVALID_COORDINATE)
        }

        if (candidate.accuracyMeters > maxAccuracyMeters) {
            return FilterVerdict.Reject(RejectReason.POOR_ACCURACY)
        }

        if (previous == null) {
            return FilterVerdict.Accept
        }

        val elapsedMs = candidate.timestampMs - previous.timestampMs
        if (elapsedMs < 0) {
            return FilterVerdict.Reject(RejectReason.OUT_OF_ORDER)
        }

        val distance = distanceMeters(previous.point, candidate.point)

        // 完全重合的快速重复点：直接丢
        if (distance < duplicateRadiusMeters && elapsedMs < duplicateWindowMs) {
            return FilterVerdict.Reject(RejectReason.WITHIN_NOISE)
        }

        // 至少 1 秒的窗口，避免同一毫秒的两个点算出无穷大速度
        val elapsedSec = (elapsedMs / 1000.0).coerceAtLeast(1.0)
        if (distance / elapsedSec > maxSpeedMps) {
            return FilterVerdict.Reject(RejectReason.TELEPORT)
        }

        // 位移小于两者的定位误差：无法区分「真的动了」还是「信号漂移」。
        // 除非到了心跳时间，否则不记录——否则静止时会画出一团乱线。
        val uncertainty = maxOf(previous.accuracyMeters, candidate.accuracyMeters).toDouble()
        if (distance < uncertainty && elapsedMs < stationaryHeartbeatMs) {
            return FilterVerdict.Reject(RejectReason.WITHIN_NOISE)
        }

        return FilterVerdict.Accept
    }
}
