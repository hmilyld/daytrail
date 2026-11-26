package com.hmilyld.daytrail.core

/**
 * 从 `CHANGELOG.md` 里抽出某个版本的条目。
 *
 * 为什么要有它：「关于 → 版本」点开要看到的是**这个安装包**的更新内容，
 * 而不是网络上最新版的内容。CHANGELOG 随 APK 一起打包（见 `app/build.gradle.kts`
 * 的 `generateChangelogAsset`），所以离线也能看，而且与 GitHub Release 的说明同源。
 *
 * 只认 `## 版本号` 这一级标题：
 * - `### 新增` / `### 修复` 是小节，属于上一个版本的内容；
 * - `## [Unreleased]` 匹配不到任何已发布版本，返回 null。
 */
object Changelog {
    /** `## [0.5.3] - 2026-11-26`、`## 0.5.3`、`## [Unreleased]`。 */
    private val HEADING = Regex("^##\\s+\\[?([^\\]\\s]+)]?.*$")

    /** 版本之间的分隔线，不属于正文。 */
    private const val RULE = "---"

    /**
     * 返回 [versionName] 对应的小节正文（markdown 片段），没有这一节时返回 null。
     *
     * [versionName] 允许带构建后缀（debug 包的 `0.5.3-debug`）与前缀 `v`。
     */
    fun sectionFor(
        markdown: String,
        versionName: String,
    ): String? {
        val target = normalizeVersion(versionName)
        if (target.isEmpty()) return null

        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
        val headings =
            lines.mapIndexedNotNull { index, line ->
                val match = HEADING.matchEntire(line.trim()) ?: return@mapIndexedNotNull null
                index to normalizeVersion(match.groupValues[1])
            }
        val start = headings.firstOrNull { it.second == target }?.first ?: return null
        val end = headings.firstOrNull { it.first > start }?.first ?: lines.size

        val body =
            lines
                .subList(start + 1, end)
                .dropWhile { it.isBlank() || it.trim() == RULE }
                .dropLastWhile { it.isBlank() || it.trim() == RULE }
                .joinToString("\n")
        return body.ifBlank { null }
    }

    /** 去掉前缀 `v` 与 `-debug` 这类构建后缀，得到用于比较的版本号。 */
    fun normalizeVersion(raw: String): String =
        raw
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .trim()
}
