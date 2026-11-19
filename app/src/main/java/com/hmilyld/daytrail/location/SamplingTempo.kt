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

    /**
     * 长时间没动。
     *
     * 静止档本身是 10 分钟一个点，一夜 8 小时就是 48 次定位唤醒——而夜里
     * 移动 0 米，这些点没有信息量。进入休眠后降到 1 小时一次，
     * 并挂上显著运动传感器：手机一动就立刻恢复。
     */
    SLEEP,
}

/** 判定节奏所需的一次采样。 */
data class TempoSample(
    val timestampMs: Long,
    val point: GeoPoint,
)

/**
 * 采样节奏（纯逻辑，可单测）。
 *
 * 只回答一个问题：**下一次隔多久再记一个点**。答案取决于两件事：
 * 1. 上一个被接受的点到这个点的平均速度
 * 2. 已经连续静止了多久（决定要不要进休眠）
 *
 * 为什么砍掉原来的四状态 + 滞回计数（见 AGENTS.md 4.x）：
 * - 原来用「位移 > 50 米」判断在移动，而 5 公里精度的点也算数，
 *   室内一个漂移点就会把采样提速到 3 分钟一档，并持续约 40 分钟；
 * - 任意一次「移动」都会把 30 分钟的休眠计时清零，夜里常常永远不休眠；
 * - 状态机让「现在为什么是这个间隔」无法向用户解释。
 *
 * 现在只用平均速度：静止 10 分钟、步行 2 分钟、驾车 1 分钟；连续静止
 * 超过 [SLEEP_AFTER_MS] 进休眠（1 小时一次）。降档（变慢）要连续
 * [DOWNGRADE_DELAY_MS] 满足条件，避免等红灯时在驾车和静止之间来回跳；
 * 升档（变快）立即生效。
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

    /** 用户关掉「自动休眠」后不再进入 SLEEP。 */
    var autoSleepEnabled: Boolean = true

    /** 降档候选从何时开始。null 表示没有待确认的降档。 */
    private var slowerSinceMs: Long? = null

    /** 连续静止从何时开始。用来判断有没有资格进休眠。 */
    private var stillSinceMs: Long? = null

    /**
     * 观察窗口：在此之前档位不低于对应下限。
     *
     * 两种窗口独立计时、可以叠加：
     * - 运动事件 → 5 分钟、下限 MOVING（认真看一眼是不是要出门）
     * - WiFi 切换 → 15 分钟、下限 STILL（先别睡，等下一次采样判断）
     *
     * 窗口只限制「最慢能慢到哪」，**不重置静止计时**——否则在公司拿一次手机
     * 就等于重新开始计 15 分钟，一整天都进不了休眠。
     */
    private var motionUntilMs: Long = 0L

    private var watchUntilMs: Long = 0L

    /** 当前档位对应的采样间隔。 */
    val intervalMs: Long
        get() =
            when (tier) {
                TempoTier.DRIVING -> preset.profile.drivingMs
                TempoTier.MOVING -> preset.profile.movingMs
                TempoTier.STILL -> preset.profile.stillMs
                TempoTier.SLEEP -> SLEEP_INTERVAL_MS
            }

    /**
     * 用最新一次采样更新档位。
     *
     * @param previous 上一个**被接受**的点。被过滤器丢掉的点不参与判定，
     *   否则「走得慢、点又被丢掉」会把自己判定成更静止。
     * @param insideKnownPlace 当前是否在已知地点（家、公司）的范围内。
     *   在已知地点里只需要 [PLACE_SLEEP_AFTER_MS] 就能进休眠——
     *   已经到了目的地，没必要再按 10 分钟采一小时。
     */
    fun observe(
        previous: TempoSample?,
        sample: TempoSample,
        insideKnownPlace: Boolean = false,
    ) {
        if (previous == null) return

        val elapsedMs = sample.timestampMs - previous.timestampMs
        if (elapsedMs <= 0L) return

        // 同一轮采样的重复投递：实测出现过 4 秒内来两个点、相距 45 米。
        // 用这种间隔算速度会得到 12 m/s，把静止档直接打成驾车档，
        // 还会清掉「已静止多久」，让整夜都进不了休眠。
        // 最短档位是 30 秒，15 秒以下的间隔不可能是新的一轮采样。
        if (elapsedMs < MIN_SPEED_WINDOW_MS) return

        val elapsedSec = elapsedMs / 1000.0
        val speed = distanceMeters(previous.point, sample.point) / elapsedSec
        val still = speed < MOVING_SPEED_MPS

        if (still) {
            if (stillSinceMs == null) stillSinceMs = previous.timestampMs
        } else {
            stillSinceMs = null
        }

        val stillMs = stillSinceMs?.let { sample.timestampMs - it } ?: 0L
        val sleepAfterMs = if (insideKnownPlace) PLACE_SLEEP_AFTER_MS else SLEEP_AFTER_MS
        val target =
            when {
                speed >= DRIVING_SPEED_MPS -> TempoTier.DRIVING
                speed >= MOVING_SPEED_MPS -> TempoTier.MOVING
                autoSleepEnabled && stillMs >= sleepAfterMs -> TempoTier.SLEEP
                else -> TempoTier.STILL
            }

        // 观察窗口内不允许比下限更慢（运动窗口 MOVING，WiFi 守候窗口 STILL）。
        // 两个窗口独立计时：运动窗口期间守候窗口照样在走，运动窗口结束后守候仍然生效。
        val floor =
            when {
                sample.timestampMs < motionUntilMs -> TempoTier.MOVING
                sample.timestampMs < watchUntilMs -> TempoTier.STILL
                else -> null
            }
        val effective =
            if (floor != null && target.ordinal > floor.ordinal) floor else target

        // 休眠没有「降档延迟」：静止时长本身就是延迟。
        if (effective == TempoTier.SLEEP) {
            tier = TempoTier.SLEEP
            slowerSinceMs = null
            return
        }

        // 变快或不变：立即生效（SLEEP 的 ordinal 最大，所以任何更快的档位都立即生效）
        if (effective.ordinal <= tier.ordinal) {
            tier = effective
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
            tier = effective
            slowerSinceMs = null
        }
    }

    /**
     * 显著运动触发：开一个 5 分钟的运动窗口。
     *
     * 窗口内按 MOVING（2 分钟）采，并禁止进入休眠；**不动静止计时**。
     * 只有真的测到位移（速度 ≥ [MOVING_SPEED_MPS]）才会重置静止计时——
     * 否则在公司拿一次手机就等于重新开始计 15 分钟，一天都睡不了。
     */
    fun wake(nowMs: Long) {
        // 只升不降：探针是异步的，回来时可能已经是 DRIVING
        if (tier.ordinal > TempoTier.MOVING.ordinal) {
            tier = TempoTier.MOVING
        }
        slowerSinceMs = null
        motionUntilMs = nowMs + MOTION_WINDOW_MS
    }

    /**
     * WiFi 切换探针：开一个 15 分钟的守候窗口。
     *
     * 不唤醒（下限 STILL），只是不允许马上去睡——人可能还在电梯里或楼下，
     * 真正出发会在下一次采样里体现。
     *
     * 如果此刻正在休眠，要**立刻掉到静止档**，否则下一次采样要等一小时，
     * 窗口就形同虚设。
     */
    fun watch(nowMs: Long) {
        watchUntilMs = nowMs + WATCH_WINDOW_MS
        if (tier == TempoTier.SLEEP) {
            tier = TempoTier.STILL
            slowerSinceMs = null
        }
    }

    /** 停止记录后重置。 */
    fun reset() {
        tier = TempoTier.MOVING
        slowerSinceMs = null
        stillSinceMs = null
        motionUntilMs = 0L
        watchUntilMs = 0L
    }

    companion object {
        /** 进驾车档的平均速度阈值，约 14 km/h。 */
        const val DRIVING_SPEED_MPS = 4.0

        /** 进移动档的平均速度阈值，约 3.2 km/h。低于它算没动。 */
        const val MOVING_SPEED_MPS = 0.9

        /** 降档需要持续满足的时间。 */
        const val DOWNGRADE_DELAY_MS = 5 * 60_000L

        /** 连续静止多久进休眠。 */
        const val SLEEP_AFTER_MS = 60 * 60_000L

        /**
         * 计算速度所需的最短间隔。
         *
         * 低于它的两次投递视为同一轮采样的重复（实测 4 秒内来两个点、相距 45 米）。
         * 取 15 秒：最短档位（高精度驾车）是 30 秒，不会被误伤。
         */
        const val MIN_SPEED_WINDOW_MS = 15_000L

        /** 在已知地点内，连续静止多久就进休眠。到了目的地就不必再密集采。 */
        const val PLACE_SLEEP_AFTER_MS = 15 * 60_000L

        /** 休眠时的采样间隔。同时作为「还在记录」的心跳。 */
        const val SLEEP_INTERVAL_MS = 60 * 60_000L

        /** 运动窗口：5 分钟内按 2 分钟档认真看一眼。 */
        const val MOTION_WINDOW_MS = 5 * 60_000L

        /** WiFi 守候窗口：15 分钟内不进入休眠，但保持静止档。 */
        const val WATCH_WINDOW_MS = 15 * 60_000L
    }
}
