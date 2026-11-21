package com.hmilyld.daytrail.data.update

import com.hmilyld.daytrail.core.SemVer

/**
 * 更新检查的两个决策，不碰偏好也不读时钟，因此可以纯单测。
 */
internal object UpdateGate {
    /** 自动检查的静默期。自托管应用没有「必须尽快知道」的诉求。 */
    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    /**
     * 自动检查是否应该真的发请求。
     *
     * 手动检查（[force]）永远放行；否则要求开关打开且距上次尝试超过一天。
     * 时钟被往回调（[now] < [lastCheckAt]）时放行，否则一次错误的系统时间
     * 会让检查永久静默。
     */
    fun shouldCheck(
        force: Boolean,
        autoEnabled: Boolean,
        lastCheckAt: Long,
        now: Long,
    ): Boolean {
        if (force) return true
        if (!autoEnabled) return false
        if (lastCheckAt <= 0L) return true
        val elapsed = now - lastCheckAt
        if (elapsed < 0L) return true
        return elapsed >= CHECK_INTERVAL_MS
    }

    /**
     * 是否应该提示用户。
     *
     * 「更新」以语义化版本为准，[publishedCode] 只作决胜：只涨 versionCode
     * 的热修也算更新，但 build number 永远不能把更低的版本名判成更新。
     * 回退 API 时拿不到 code，退化为只比版本名。
     */
    fun shouldOffer(
        force: Boolean,
        published: String?,
        installed: String?,
        skipped: String?,
        publishedCode: Int = 0,
        installedCode: Int = 0,
    ): Boolean {
        if (!isNewer(published, installed, publishedCode, installedCode)) return false
        // 手动检查忽略「跳过」，否则用户再也找不到那个版本
        if (force) return true
        val skippedVersion = SemVer.parse(skipped)?.toString() ?: return true
        return SemVer.parse(published)?.toString() != skippedVersion
    }

    private fun isNewer(
        published: String?,
        installed: String?,
        publishedCode: Int,
        installedCode: Int,
    ): Boolean {
        val next = SemVer.parse(published) ?: return false
        val current = SemVer.parse(installed) ?: return false
        if (next != current) return next > current
        return publishedCode > 0 && installedCode > 0 && publishedCode > installedCode
    }
}
