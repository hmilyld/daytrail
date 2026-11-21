package com.hmilyld.daytrail.data.update

import com.hmilyld.daytrail.core.SemVer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 解析 `GET /repos/{owner}/{repo}/releases/latest` 的报文。
 *
 * 只在 `update.json` 缺失时使用（老版本发布），因此这里拿不到
 * `versionCode` 与 `apkSha256`——它们不在 API 报文里，或在 assets 里
 * 也只是一个无法校验的文件名。
 *
 * 找不到 APK 资产时 [UpdateInfo.apkUrl] 为 null，界面会退化成
 * 「打开 Release 页面」而不是假装能下载。
 */
internal object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): UpdateInfo? {
        val root =
            runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null

        // 草稿与预发布不参与更新：它们可能是半成品，也可能是刻意留给测试者的
        if (root.bool("draft") == true || root.bool("prerelease") == true) return null

        val tag = root.text("tag_name")?.takeIf { it.isNotBlank() } ?: return null
        val version = SemVer.parse(tag)?.toString() ?: return null

        val asset = (root["assets"] as? JsonArray)?.firstApk()
        return UpdateInfo(
            versionName = version,
            tagName = tag,
            title = root.text("name")?.takeIf { it.isNotBlank() } ?: tag,
            notes = root.text("body").orEmpty().trim(),
            pageUrl =
                root.text("html_url")?.takeIf { it.isNotBlank() }
                    ?: UpdateChecker.releasePageUrl(tag),
            apkUrl = asset?.text("browser_download_url"),
            apkSize = asset?.text("size")?.toLongOrNull() ?: 0L,
        )
    }

    private fun JsonArray.firstApk(): JsonObject? =
        firstOrNull { element ->
            val name = (element as? JsonObject)?.text("name").orEmpty()
            name.endsWith(".apk", ignoreCase = true)
        } as? JsonObject

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
