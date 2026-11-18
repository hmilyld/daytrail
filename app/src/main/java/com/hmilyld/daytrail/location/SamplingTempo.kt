package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/** 一个档位下的三档采样间隔。 */
data class TempoProfile(
    val drivingMs: Long,
    val movingMs: Long,
    val stillMs: Long,
)

/**
 * 三种档位。省电档是默认值。
 *
 * 数值的含义只有一句「每多久记一个点」。用户看设置页就能算出一天记多少点，
 * 不需要理解状态机。
 */
enum class SamplingPreset(
    val profile: TempoProfile,
) {
    /** 驾车 1 分 / 步行 2 分 / 静止 10 分。产品默认。 */
    POWER_SAVER(
        TempoProfile(drivingMs = 60_000L, movingMs = 120_000L, stillMs = 600_000L),
    ),

    /** 驾车 1 分 / 步行 1 分 / 静止 5 分。 */
    BALANCED(
        TempoProfile(drivingMs = 60_000L, movingMs = 60_000L, stillMs = 300_000L),
    ),

    /** 驾车 30 秒 / 步行 30 秒 / 静止 2 分。 */
    HIGH_ACCURACY(
        TempoProfile(drivingMs = 30_000L, movingMs = 30_000L, stillMs = 120_000L),
    ),
}

/** 当前该用哪一档间隔。 */
enum class TempoTier {
    DRIVING,
    MOVING,
    STILL,
}

/** 判定节奏所需的一次采样。 */
data class TempoSample(
    val timestampMs: Long,
    val point: GeoPoint,
)

/**
 * 采样节奏（纯逻辑，可单测）。
 *
 * 只回答一个问题：**下一次隔多久再记一个点**。
 * 答案只取决于「上一个被接受的点到这个点的平均速度」，
 * 不看速度传感器、不看定位精度，也不做连坐式降级。
 *
 * 为什么砍掉原来的四状态 + 滞回计数（见 AGENTS.md 4.x）：
 * - 原来用「位移 > 50 米」判断在移动，而 5 公里精度的点也算数，
 *   室内一个漂移点就会把采样提速到 3 分钟一档，并持续约 40 分钟；
 * - 任意一次「移动」都会把 30 分钟的休眠计时清零，夜里常常永远不休眠；
 * - 状态机让「现在为什么是这个间隔」无法向用户解释。
 *
 * 现在只用平均速度：静止 10 分钟、步行 2 分钟、驾车 1 分钟。
 * 降档（变慢）要连续 [DOWNGRADE_DELAY_MS] 满足条件，避免等红灯时
 * 在驾车和静止之间来回跳；升档（变快）立即生效。
 */
class SamplingTempo(
    /**
     * 当前档位。可变：用户在设置里切换后立即生效。
     *
     * 用 `var` 而不是重建对象，是为了保住降档计时。
     */
    var preset: SamplingPreset,
) {
    var tier: TempoTier = TempoTier.MOVING
        private set

    /** 降档候选从何时开始。null 表示没有待确认的降档。 */
    private var slowerSinceMs: Long? = null

    /** 当前档位对应的采样间隔。 */
    val intervalMs: Long
        get() =
            when (tier) {
                TempoTier.DRIVING -> preset.profile.drivingMs
                TempoTier.MOVING -> preset.profile.movingMs
                TempoTier.STILL -> preset.profile.stillMs
            }

    /**
     * 用最新一次采样更新档位。
     *
     * @param previous 上一个**被接受**的点。被过滤器丢掉的点不参与判定，
     *   否则「走得慢、点又被丢掉」会把自己判定成更静止。
     */
    fun observe(
        previous: TempoSample?,
        sample: TempoSample,
    ) {
        if (previous == null) return

        val elapsedSec = (sample.timestampMs - previous.timestampMs) / 1000.0
        if (elapsedSec <= 0.0) return

        val speed = distanceMeters(previous.point, sample.point) / elapsedSec
        val target =
            when {
                speed >= DRIVING_SPEED_MPS -> TempoTier.DRIVING
                speed >= MOVING_SPEED_MPS -> TempoTier.MOVING
                else -> TempoTier.STILL
            }

        // 变快或不变：立即生效
        if (target.ordinal <= tier.ordinal) {
            tier = target
            slowerSinceMs = null
            return
        }

        // 变慢：先观察一段时间，避免等红灯、进隧道时来回跳
        var since = slowerSinceMs
        if (since == null) {
            since = sample.timestampMs
            slowerSinceMs = since
        }
        if (sample.timestampMs - since >= DOWNGRADE_DELAY_MS) {
            tier = target
            slowerSinceMs = null
        }
    }

    /** 停止记录后重置。 */
    fun reset() {
        tier = TempoTier.MOVING
        slowerSinceMs = null
    }

    companion object {
        /** 进驾车档的平均速度阈值，约 14 km/h。 */
        const val DRIVING_SPEED_MPS = 4.0

        /** 进移动档的平均速度阈值，约 3.2 km/h。低于它算没动。 */
        const val MOVING_SPEED_MPS = 0.9

        /** 降档需要持续满足的时间。 */
        const val DOWNGRADE_DELAY_MS = 5 * 60_000L
    }
}
