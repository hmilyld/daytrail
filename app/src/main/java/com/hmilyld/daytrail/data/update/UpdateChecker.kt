package com.hmilyld.daytrail.data.update

import com.hmilyld.daytrail.BuildConfig
import com.hmilyld.daytrail.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 读取最新的 Release。
 *
 * **主源是 `releases/latest/download/update.json`**：CDN 上的静态文件，
 * 不消耗 GitHub API 的 60 次/小时匿名配额。只有该资产缺失或不可解析时
 * 才回退到 API —— 老版本的 Release 本来就没有这份文件。
 *
 * 用 `HttpURLConnection` 而不是 OkHttp：这是两个 GET 请求（清单 + APK），
 * 为一个 GET 引入一个 HTTP 框架不划算。跟随重定向是默认行为，
 * `releases/latest/download/` 会跳到 CDN 主机。
 */
class UpdateChecker(
    private val settings: SettingsStore,
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val manifestUrlOverride: String = BuildConfig.UPDATE_FEED_URL,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** 没配置仓库时整条链路不可用，界面据此禁用入口，而不是请求一个不存在的地址。 */
    val configured: Boolean get() = repo.isNotBlank() || manifestUrlOverride.isNotBlank()

    private val manifestUrl: String
        get() =
            manifestUrlOverride.ifBlank {
                "https://github.com/$repo/releases/latest/download/update.json"
            }

    private val apiUrl: String get() = "https://api.github.com/repos/$repo/releases/latest"

    suspend fun check(force: Boolean = false): UpdateCheckResult {
        if (!configured) return UpdateCheckResult.NotConfigured

        val lastCheck = settings.lastUpdateCheckAt.first()
        val autoEnabled = settings.autoUpdateCheck.first()
        if (!UpdateGate.shouldCheck(force, autoEnabled, lastCheck, now())) {
            return UpdateCheckResult.Skipped
        }

        val info =
            try {
                fetchLatest()
            } catch (error: UpdateHttpException) {
                // GitHub 已经答复了，这次尝试要计入间隔：否则限流期间
                // 每次启动都会再撞一次。
                settings.setLastUpdateCheckAt(now())
                return UpdateCheckResult.Failed(error.message)
            } catch (_: IOException) {
                // 网络根本没通。记录下来的话，地铁里的一次失败会让用户
                // 一整天看不到新版本。
                return UpdateCheckResult.Failed(NETWORK_FAILURE)
            } catch (error: Exception) {
                return UpdateCheckResult.Failed(error.message ?: GENERIC_FAILURE)
            }

        settings.setLastUpdateCheckAt(now())
        if (info == null) return UpdateCheckResult.UpToDate

        val skipped = settings.skippedUpdateVersion.first()
        if (!UpdateGate.shouldOffer(
                force = force,
                published = info.versionName,
                installed = BuildConfig.VERSION_NAME,
                skipped = skipped,
                publishedCode = info.versionCode,
                installedCode = BuildConfig.VERSION_CODE,
            )
        ) {
            return UpdateCheckResult.Skipped
        }
        return UpdateCheckResult.Available(info)
    }

    /** 记住用户推迟的版本，之后不再自动提示。 */
    suspend fun markSkipped(version: String) {
        settings.setSkippedUpdateVersion(version)
    }

    /**
     * 最新版本；仓库里一个 Release 都没有时返回 null。
     *
     * 先试静态清单，正常情况零配额消耗；只有清单缺失/不可读才走 API。
     */
    private suspend fun fetchLatest(): UpdateInfo? =
        UpdateFallback.resolve(manifest = fetchManifest(), api = { fetchLatestFromApi() })

    private suspend fun fetchManifest(): UpdateFallback.ManifestOutcome =
        withContext(Dispatchers.IO) {
            try {
                val (code, body) = get(manifestUrl, "application/json")
                if (code !in 200..299) return@withContext UpdateFallback.ManifestOutcome.Unusable
                val info = UpdateManifest.parse(body)
                if (info == null) {
                    UpdateFallback.ManifestOutcome.Unusable
                } else {
                    UpdateFallback.ManifestOutcome.Found(info)
                }
            } catch (error: IOException) {
                UpdateFallback.ManifestOutcome.Failed(error)
            }
        }

    private suspend fun fetchLatestFromApi(): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val (code, body) = get(apiUrl, "application/vnd.github+json")
            if (code == 404) return@withContext null
            if (code !in 200..299) throw UpdateHttpException(githubFailureMessage(code, body))
            ReleaseParser.parse(body)
        }

    private fun get(
        url: String,
        accept: String,
    ): Pair<Int, String> {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // GitHub 对没有 User-Agent 的请求直接拒绝
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
            }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 匿名配额耗尽时 GitHub 返回 **403**（不是 429），body 里带 "rate limit"。
     * 把它当成「没有权限」会让用户去找一个根本不需要的 token。
     */
    private fun githubFailureMessage(
        code: Int,
        body: String,
    ): String {
        val limited =
            code == 429 || (code == 403 && body.contains("rate limit", ignoreCase = true))
        return if (limited) RATE_LIMITED else "检查更新失败（HTTP $code）"
    }

    /** GitHub 答复了但不是 Release；与「网络不通」区分开。 */
    class UpdateHttpException(
        override val message: String,
    ) : IOException(message)

    companion object {
        /** 与 `gradle.properties` 的 `app.updateRepo` 共用同一个格式。 */
        fun manifestUrlFor(repo: String): String =
            "https://github.com/$repo/releases/latest/download/update.json"

        fun releasePageUrl(
            tag: String,
            repo: String = BuildConfig.UPDATE_REPO,
        ): String = "https://github.com/$repo/releases/tag/$tag"

        private const val USER_AGENT = "daytrail-android"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000

        const val NETWORK_FAILURE = "无法连接更新服务，请检查网络"
        const val RATE_LIMITED = "GitHub 暂时限制了检查频率，请稍后再试"
        const val GENERIC_FAILURE = "检查更新失败，请稍后重试"
    }
}

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult

    /** 没检查：开关关着、距上次不足一天，或这个版本用户已推迟。 */
    data object Skipped : UpdateCheckResult

    /** 没有配置更新源（`app.updateRepo` 为空）。 */
    data object NotConfigured : UpdateCheckResult

    data class Available(
        val info: UpdateInfo,
    ) : UpdateCheckResult

    data class Failed(
        val message: String,
    ) : UpdateCheckResult
}
