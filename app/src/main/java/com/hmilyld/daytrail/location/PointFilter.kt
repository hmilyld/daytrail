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

    /** 精度差于阈值，通常是基站粗定位的抖动。 */
    POOR_ACCURACY,

    /** 与上一点推算速度过快，判定为跳变。 */
    TELEPORT,

    /** 与上一点几乎重合且时间太近，合并掉。 */
    DUPLICATE,

    /** 时间戳早于上一点，乱序数据。 */
    OUT_OF_ORDER,
}

/**
 * 采样点过滤器（纯逻辑，可单测）。
 *
 * 顺序有意为之：
 * 1. 先校验坐标合法性——后续所有计算都依赖它
 * 2. 再查时间顺序——乱序点会让速度推算得出负值
 * 3. 再查精度——精度差时速度推算也不可信
 * 4. 最后查跳变与重复
 *
 * 精度阈值取 200 米：
 * - 静止态用的是基站/WiFi 粗定位，精度常在 100–500 米
 * - 若沿用 100 米，室内或弱 GPS 环境下会几乎采不到点，
 *   用户看到的是「走了半小时但轨迹是空的」
 * - 200 米是「城市尺度仍可用」的上限：再差的点会让相邻段
 *   产生 200 米以上的误差，里程会明显虚高
 */
class PointFilter(
    private val maxAccuracyMeters: Float = 200f,
    private val maxSpeedMps: Double = 300_000.0 / 3600.0, // 300 km/h
    private val duplicateRadiusMeters: Double = 3.0,
    private val duplicateWindowMs: Long = 30_000L,
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

        if (distance < duplicateRadiusMeters && elapsedMs < duplicateWindowMs) {
            return FilterVerdict.Reject(RejectReason.DUPLICATE)
        }

        // 至少 1 秒的窗口，避免同一毫秒的两个点算出无穷大速度
        val elapsedSec = (elapsedMs / 1000.0).coerceAtLeast(1.0)
        if (distance / elapsedSec > maxSpeedMps) {
            return FilterVerdict.Reject(RejectReason.TELEPORT)
        }

        return FilterVerdict.Accept
    }
}
