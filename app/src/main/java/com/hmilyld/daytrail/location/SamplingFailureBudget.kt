package com.hmilyld.daytrail.location

/**
 * 定位失败预算。
 *
 * 为什么需要它：早期实现在采点失败后只是「等 15 秒再试」，没有上限。
 * 如果权限被回收、定位开关被关掉、或者用户一直在地下室，
 * 就会无限循环地请求 GPS——既耗电，又让通知长期停在「正在获取定位」，
 * 用户完全不知道发生了什么。
 *
 * 规则：连续失败达到上限后停止采集，转为「定位异常」状态，
 * 由界面和通知明确告知原因，并给出一键重试。
 *
 * 这不是纯 UI 逻辑，所以抽成独立类以便单测。
 */
class SamplingFailureBudget(
    private val maxConsecutiveFailures: Int = DEFAULT_MAX,
) {
    var consecutiveFailures: Int = 0
        private set

    /** 是否已经放弃。放弃后调用方应停止采集循环。 */
    val exhausted: Boolean
        get() = consecutiveFailures >= maxConsecutiveFailures

    /**
     * 记录一次失败。
     *
     * @return true 表示已达到上限，调用方应停止采集
     */
    fun recordFailure(): Boolean {
        consecutiveFailures++
        return exhausted
    }

    /** 记录一次成功，预算清零。 */
    fun recordSuccess() {
        consecutiveFailures = 0
    }

    /** 手动重试时重置。 */
    fun reset() {
        consecutiveFailures = 0
    }

    companion object {
        /**
         * 连续失败多少次后放弃。
         *
         * 5 次：按「单次尝试最多 25 秒 + 重试间隔 15 秒」估算，
         * 大约 3 分钟内能判断出是环境问题而不是偶发失败，
         * 又不至于让用户觉得「一直没反应」。
         */
        const val DEFAULT_MAX = 5
    }
}

/** 定位失败的原因，用于给用户明确的提示。 */
enum class LocationFailureReason {
    /** 没有定位权限，或只有大致位置权限。 */
    PERMISSION_DENIED,

    /** 系统定位开关被关掉。 */
    LOCATION_DISABLED,

    /** 权限与开关都正常，但始终取不到位置（室内、地下、信号差）。 */
    NO_FIX,

    /** 未知原因。 */
    UNKNOWN,
}
