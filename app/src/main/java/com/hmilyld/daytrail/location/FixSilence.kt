package com.hmilyld.daytrail.location

/**
 * 「多久没有新定位」的判定。
 *
 * 两条阈值不同，含义也不同：
 * - [NOTICE_AFTER_MS]（10 分钟）：把通知文案从「正在记录」改为「正在获取定位」，
 *   同时向诊断日志之外的用户说明当前在等信号；
 * - [STALE_AFTER_MS]（15 分钟）：今日页显示琥珀色提示「暂时没有新定位」。
 *
 * 之所以给通知更短的阈值：静止档本身就是 10 分钟一个点，10 分钟没有回调可能只是
 * 刚好跨过一个周期，此时改文案成本很低；而界面上的琥珀提示必须更有信息量，
 * 因此在至少漏过一轮之后才出现。
 *
 * 休眠状态必须整体抑制：采样间隔本来就是 1 小时，
 * 「很久没有新点位」属于预期行为。此时若仍然提示，用户会同时看到
 * 「已休眠」与「定位异常」两条互相矛盾的信息。
 */
object FixSilence {
    const val NOTICE_AFTER_MS = 10L * 60 * 1000
    const val STALE_AFTER_MS = 15L * 60 * 1000

    data class Decision(
        val switchNotice: Boolean,
        val markStale: Boolean,
    ) {
        companion object {
            val NONE = Decision(switchNotice = false, markStale = false)
        }
    }

    /**
     * @param silentMs 距上一次收到定位的毫秒数
     * @param sleeping 当前是否处于休眠档位
     */
    fun evaluate(
        silentMs: Long,
        sleeping: Boolean,
    ): Decision {
        if (sleeping) return Decision.NONE
        // 系统时间被往回调时会出现负值，按「刚收到定位」处理，避免误报
        if (silentMs < 0L) return Decision.NONE
        return Decision(
            switchNotice = silentMs >= NOTICE_AFTER_MS,
            markStale = silentMs >= STALE_AFTER_MS,
        )
    }
}
