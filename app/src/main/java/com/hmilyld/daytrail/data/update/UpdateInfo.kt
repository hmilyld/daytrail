package com.hmilyld.daytrail.data.update

/**
 * 一个已发布的版本，裁到更新提示需要的字段。
 *
 * [versionCode] 与 [apkSha256] 只有 `update.json` 能提供；
 * 回退到 GitHub API 时保持默认值（API 拿不到可靠的可执行版本号与哈希）。
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int = 0,
    val tagName: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
    val apkSha256: String? = null,
)
