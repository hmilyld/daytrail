package com.hmilyld.daytrail.data.update

import com.hmilyld.daytrail.core.SemVer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `update.json` —— 每个 Release 附带的静态资产。
 *
 * 为什么要它：`releases/latest/download/update.json` 是 CDN 上的**静态文件**，
 * 不消耗 GitHub API 的 60 次/小时匿名配额。它还能提供 API 报文里拿不到的两样东西：
 * `versionCode` 与 `apkSha256`。
 *
 * 纯逻辑、无 Android 依赖，可直接单测。
 */
internal object UpdateManifest {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): UpdateInfo? {
        val root =
            runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val versionName = root.string("versionName")?.takeIf { it.isNotBlank() } ?: return null
        // 版本名是最终比较依据，解析不出来就当这份清单不可用，让调用方回退
        val version = SemVer.parse(versionName) ?: return null
        val tag = root.string("tag")?.takeIf { it.isNotBlank() } ?: "v$version"
        return UpdateInfo(
            versionName = version.toString(),
            versionCode = root.string("versionCode")?.toIntOrNull() ?: 0,
            tagName = tag,
            title = root.string("title")?.takeIf { it.isNotBlank() } ?: tag,
            notes = root.string("notes").orEmpty().trim(),
            pageUrl =
                root.string("pageUrl")?.takeIf { it.isNotBlank() }
                    ?: UpdateChecker.releasePageUrl(tag),
            apkUrl = root.string("apkUrl")?.takeIf { it.isNotBlank() },
            apkSize = root.string("apkSize")?.toLongOrNull() ?: 0L,
            apkSha256 = root.string("apkSha256")?.takeIf { it.isNotBlank() }?.lowercase(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
