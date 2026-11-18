package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.bearingDegrees
import com.hmilyld.daytrail.core.distanceMeters
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 轨迹纠偏的请求构造（纯逻辑，可单测）。
 *
 * 只把**驾车段**送去纠偏：步行段贴到路上会明显不对（人走在人行道甚至楼里）。
 *
 * 接口是「按坐标点、方位角、速度把轨迹吸附到路网」，三个参数都必须合理，
 * 官方文档明确说速度或方位角不合理会大概率导致纠偏失败。所以：
 * - `speed` 用相邻两点的距离/时间算，不依赖 Location 里可空的 speed
 * - `bearing` 用相邻两点的方位角算
 * - `tm` 首点是**绝对 Unix 秒**，其余是**相对首点的差值**（文档要求，容易写错）
 *
 * 接口一次最多 500 个点，超出就切块，每块重新以绝对时间开头。
 */
object TrackCorrection {
    /** 接口的硬上限：一个请求最多 500 个 JsonObject。 */
    const val MAX_POINTS_PER_REQUEST = 500

    /** 低于这个速度的相邻点不算驾车。3 m/s 约 11 km/h。 */
    const val MIN_DRIVING_SPEED_MPS = 3.0

    /** 相邻点间隔超过这个值就断开：中间没有数据，不能当成一段连续轨迹。 */
    const val MAX_RUN_GAP_MS = 2 * 60_000L

    /** 一个带速度和方位角的轨迹点。 */
    data class TracePoint(
        val timestampMs: Long,
        val point: GeoPoint,
        val speedMps: Double,
        val bearingDegrees: Double,
    )

    /** 一个请求点，字段与接口一一对应。 */
    data class RequestPoint(
        val longitude: Double,
        val latitude: Double,
        val bearingDegrees: Int,
        val speedKmh: Double,
        /** 首点是绝对 Unix 秒，其余是相对首点的差值。 */
        val timeSeconds: Long,
    )

    /**
     * 从一天的采样里挑出连续的驾车段。
     *
     * 相邻两点速度够快、时间间隔合理才算驾车；任何一条不满足就断开，
     * 避免把「停车等红灯 → 两小时后继续开」连成一段。
     */
    fun drivingRuns(samples: List<TrackPointSample>): List<List<TracePoint>> {
        if (samples.size < 2) return emptyList()

        val runs = mutableListOf<List<TracePoint>>()
        var current = mutableListOf<TracePoint>()

        fun flush() {
            if (current.size >= 2) runs += current
            current = mutableListOf()
        }

        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val sample = samples[index]
            val elapsedMs = sample.timestampMs - previous.timestampMs
            val elapsedSec = elapsedMs / 1000.0

            if (elapsedSec <= 0.0) {
                flush()
                continue
            }

            val distance = distanceMeters(previous.point, sample.point)
            val speed = distance / elapsedSec
            val isDriving = speed >= MIN_DRIVING_SPEED_MPS && elapsedMs <= MAX_RUN_GAP_MS
            if (!isDriving) {
                flush()
                continue
            }

            val bearing = bearingDegrees(previous.point, sample.point)
            if (current.isEmpty()) {
                current += TracePoint(previous.timestampMs, previous.point, speed, bearing)
            }
            current += TracePoint(sample.timestampMs, sample.point, speed, bearing)
        }
        flush()

        return runs
    }

    /** 把一段轨迹切成不超过 [maxPoints] 的块。 */
    fun chunks(
        run: List<TracePoint>,
        maxPoints: Int = MAX_POINTS_PER_REQUEST,
    ): List<List<TracePoint>> {
        if (run.size <= maxPoints) return listOf(run)
        return run.chunked(maxPoints).filter { it.size >= 2 }
    }

    /** 构造请求点。 */
    fun requestPoints(run: List<TracePoint>): List<RequestPoint> {
        if (run.isEmpty()) return emptyList()

        val firstMs = run.first().timestampMs
        return run.mapIndexed { index, trace ->
            RequestPoint(
                longitude = round6(trace.point.longitude),
                latitude = round6(trace.point.latitude),
                bearingDegrees = trace.bearingDegrees.roundToInt().mod(360),
                speedKmh = (trace.speedMps * 3.6 * 10).roundToLong() / 10.0,
                timeSeconds =
                    if (index == 0) {
                        firstMs / 1000
                    } else {
                        (trace.timestampMs - firstMs) / 1000
                    },
            )
        }
    }

    /** 接口要求小数点后最多 6 位。 */
    private fun round6(value: Double): Double = (value * 1_000_000).roundToLong() / 1_000_000.0

    /**
     * 把纠偏结果按「过长的直连段」切开。
     *
     * 接口在某些点匹配不上路网时，会在两个已匹配点之间**直接连一条直线**——
     * 实测出现过一段 6.6 公里的直线，比原始折线还假。正常结果相邻点只有十几米，
     * 所以超过 [maxStepMeters] 的步长必然是这种直连。
     *
     * 切开而不是整段丢弃：前后匹配上的部分仍然有价值，中间留白比画一条错的线诚实。
     */
    fun splitAtLongJumps(
        points: List<GeoPoint>,
        maxStepMeters: Double = MAX_ROAD_STEP_METERS,
    ): List<List<GeoPoint>> {
        if (points.isEmpty()) return emptyList()

        val segments = mutableListOf<List<GeoPoint>>()
        var current = mutableListOf(points.first())

        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val point = points[index]
            if (distanceMeters(previous, point) > maxStepMeters) {
                if (current.size >= 2) segments += current
                current = mutableListOf(point)
            } else {
                current += point
            }
        }
        if (current.size >= 2) segments += current
        return segments
    }

    /** 相邻纠偏点的最大合理间距。正常结果中位数只有十几米。 */
    const val MAX_ROAD_STEP_METERS = 1_000.0
}
