package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/** 采样档位对应的具体间隔参数。 */
data class SamplingProfile(
    val stillIntervalMs: Long,
    val walkingIntervalMs: Long,
    val drivingIntervalMs: Long,
    /** 静止多久后进入休眠 */
    val sleepAfterStillMs: Long,
    /** 休眠期间的兜底定位间隔 */
    val sleepFallbackMs: Long,
)

/** 三种档位的参数表。省电档是产品默认值。 */
enum class SamplingPreset(
    val profile: SamplingProfile,
) {
    POWER_SAVER(
        SamplingProfile(
            stillIntervalMs = 600_000L, // 10 分钟
            walkingIntervalMs = 180_000L, // 3 分钟
            drivingIntervalMs = 60_000L, // 1 分钟
            sleepAfterStillMs = 30 * 60_000L, // 静止 30 分钟休眠
            sleepFallbackMs = 60 * 60_000L, // 60 分钟兜底
        ),
    ),
    BALANCED(
        SamplingProfile(
            stillIntervalMs = 300_000L,
            walkingIntervalMs = 90_000L,
            drivingIntervalMs = 45_000L,
            sleepAfterStillMs = 20 * 60_000L,
            sleepFallbackMs = 45 * 60_000L,
        ),
    ),
    HIGH_ACCURACY(
        SamplingProfile(
            stillIntervalMs = 120_000L,
            walkingIntervalMs = 30_000L,
            drivingIntervalMs = 15_000L,
            sleepAfterStillMs = 10 * 60_000L,
            sleepFallbackMs = 30 * 60_000L,
        ),
    ),
}

/** 记录器的运动状态。Sleeping 是省电的关键状态。 */
enum class MovementState {
    SLEEPING,
    STILL,
    WALKING,
    DRIVING,
}

/** 一次采样判定所需的输入。 */
data class MovementSample(
    val point: GeoPoint,
    val timestampMs: Long,
    val speedMps: Float?,
)

/**
 * 采样状态机（纯逻辑，可单测）。
 *
 * 设计要点：
 * - 状态迁移有滞回，避免等红灯时在 Still/Walking 之间反复横跳
 * - 速度未知时（部分设备不返回）用位移/时间推算
 * - 进入 Sleeping 后不再主动采样，由传感器或兜底闹钟唤醒
 * - **只要在移动就必须用 GPS**：基站定位精度约 100–500 米，
 *   用它画轨迹会得到一条锯齿状的粗线，完全没有参考价值
 */
class SamplingPolicy(
    /**
     * 当前档位。可变：用户在设置里切换档位后必须立即生效。
     *
     * 用 `var` 而不是重建整个 Policy，是为了保住滞回状态
     * （`consecutiveStill` / `stillSinceMs`）——否则每次切换档位
     * 都会让「静止多久后休眠」从零重新计时。
     */
    var preset: SamplingPreset,
) {
    private val profile: SamplingProfile get() = preset.profile

    /** 静止判定的位移半径（米）。 */
    private val stillRadiusM = 50.0

    /** 判定为驾车的速度阈值。 */
    private val drivingSpeedMps = 15_000.0 / 3600.0 // 15 km/h

    /** 驾车降级到步行的滞回阈值（更低，避免抖动）。 */
    private val drivingExitSpeedMps = 12_000.0 / 3600.0

    private var consecutiveStill = 0
    private var consecutiveMoving = 0
    private var stillSinceMs: Long? = null

    /**
     * 该状态是否需要 GPS。
     *
     * 只有静止时才允许用粗定位：此时轨迹几乎不变化，
     * 精度差一点无所谓，而省下的电很可观。
     * 一旦在移动，必须走高精度 GPS，否则轨迹不可用。
     */
    fun requiresHighAccuracy(state: MovementState): Boolean =
        when (state) {
            MovementState.STILL, MovementState.SLEEPING -> false
            MovementState.WALKING, MovementState.DRIVING -> true
        }

    /**
     * 根据最新采样更新状态。
     *
     * @param previous 上一个有效点，首点时传 null
     * @param sample 本次采样
     * @param currentState 当前状态
     */
    fun nextState(
        previous: MovementSample?,
        sample: MovementSample,
        currentState: MovementState,
    ): MovementState {
        // 首个点只作为基准，无法判断运动方向，保持原状态
        if (previous == null) return currentState

        if (currentState == MovementState.SLEEPING) {
            // 休眠中只有真实位移才唤醒，避免兜底定位把状态搅乱
            val moved = distanceMeters(previous.point, sample.point)
            return if (moved > 100.0) MovementState.WALKING else MovementState.SLEEPING
        }

        val moved = distanceMeters(previous.point, sample.point)
        val elapsedSec =
            ((sample.timestampMs - previous.timestampMs) / 1000.0).coerceAtLeast(1.0)
        val derivedSpeed = moved / elapsedSec
        val speed = sample.speedMps?.toDouble()?.takeIf { it >= 0.0 } ?: derivedSpeed

        val isStill = moved < stillRadiusM && speed < 0.5

        if (isStill) {
            consecutiveStill++
            consecutiveMoving = 0
            // 静止起点取上一个点的时刻，否则每次都会从"现在"重新计时，永远攒不满
            if (stillSinceMs == null) stillSinceMs = previous.timestampMs

            val stillDuration = sample.timestampMs - (stillSinceMs ?: previous.timestampMs)
            return when {
                stillDuration >= profile.sleepAfterStillMs -> MovementState.SLEEPING
                consecutiveStill >= 2 -> MovementState.STILL
                else -> currentState
            }
        }

        consecutiveMoving++
        consecutiveStill = 0
        stillSinceMs = null

        // 驾车需要连续两次高速才确认；降级需要连续两次低于滞回阈值
        return when {
            speed >= drivingSpeedMps -> MovementState.DRIVING
            currentState == MovementState.DRIVING && speed >= drivingExitSpeedMps ->
                MovementState.DRIVING
            consecutiveMoving >= 2 -> MovementState.WALKING
            else -> currentState
        }
    }

    /** 当前状态对应的采样间隔。休眠时不采样，返回 null。 */
    fun intervalFor(state: MovementState): Long? =
        when (state) {
            MovementState.SLEEPING -> null
            MovementState.STILL -> profile.stillIntervalMs
            MovementState.WALKING -> profile.walkingIntervalMs
            MovementState.DRIVING -> profile.drivingIntervalMs
        }

    /** 重置内部计数器。停止记录或跨天时调用。 */
    fun reset() {
        consecutiveStill = 0
        consecutiveMoving = 0
        stillSinceMs = null
    }
}
