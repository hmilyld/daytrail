package com.hmilyld.daytrail.core

/**
 * 语义化版本比较。
 *
 * 标签形如 `v1.2.3`，`BuildConfig.VERSION_NAME` 形如 `1.2.3` 或 `1.2.3-debug`。
 * **只有数字三元组参与比较**：自托管应用的发布渠道不需要在 `1.0.0-rc1` 与
 * `1.0.0` 之间排序，而发布工作流已经要求 tag 与 `app.versionName` 一致。
 *
 * 用字符串直接比较是错的：`0.10.0` 会被判成比 `0.9.0` 旧。
 */
object SemVer {
    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }

        override fun toString(): String = "$major.$minor.$patch"
    }

    /**
     * 从 [raw] 里读出数字三元组：忽略前导 `v`、预发布后缀（`-debug`/`-rc1`）
     * 与构建元数据（`+4`）。读不出可用数字时返回 null。
     */
    fun parse(raw: String?): Version? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val withoutPrefix = trimmed.removePrefix("v").removePrefix("V")
        val numeric = withoutPrefix.substringBefore('-').substringBefore('+').trim()
        if (numeric.isEmpty()) return null
        val parts = numeric.split('.')
        if (parts.size > 3) return null
        val numbers =
            parts.map { part ->
                if (part.isEmpty() || !part.all { it.isDigit() }) return null
                part.toIntOrNull() ?: return null
            }
        return Version(
            major = numbers.getOrElse(0) { 0 },
            minor = numbers.getOrElse(1) { 0 },
            patch = numbers.getOrElse(2) { 0 },
        )
    }

    /** [candidate] 是否严格新于 [current]。任一无法解析时返回 false。 */
    fun isNewer(
        candidate: String?,
        current: String?,
    ): Boolean {
        val next = parse(candidate) ?: return false
        val installed = parse(current) ?: return false
        return next > installed
    }
}
