package com.hmilyld.daytrail.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 下载 APK、校验、交给系统安装器。
 *
 * 三道闸（缺一不可）：
 * 1. **SHA-256**：`update.json` 里公布的哈希与下载字节比对，截断或串包立即失败；
 * 2. **包名 + 签名一致性**：包名必须相同，签名证书必须与本机一致；
 * 3. **版本不降级**：由 [UpdateGate] 在检查阶段保证，安装前不再重复判断。
 *
 * 第 2 道系统安装器本身也会做（异签名 APK 会被拒绝安装），但那时用户看到的是
 * 系统级报错。自己先查能给出「这个包不是官方发布的」这种可解释的信息。
 */
object UpdateInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val AUTHORITY_SUFFIX = ".updates"

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    /** 系统是否允许本应用拉起安装器。 */
    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** 跳「安装未知应用」授权页。没有 Activity 响应时返回 false，由界面解释。 */
    fun openInstallPermissionSettings(context: Context): Boolean =
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData("package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)

    /**
     * 下载到应用缓存。进度回调在 IO 线程上，取值 0..1；总长度未知时给 -1。
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val url = info.apkUrl ?: throw IOException("这个版本没有附带安装包")
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "daytrail-${info.versionName}.apk")
            val partial = File(directory, target.name + ".part")

            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "daytrail-android")
                }

            val digest = MessageDigest.getInstance("SHA-256")
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("下载失败（HTTP $code）")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            onProgress(
                                if (total >
                                    0
                                ) {
                                    (written.toFloat() / total).coerceIn(0f, 1f)
                                } else {
                                    -1f
                                },
                            )
                        }
                        output.flush()
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (partial.length() <= 0L) {
                partial.delete()
                throw IOException("下载的安装包是空的")
            }

            val expected = info.apkSha256
            if (!expected.isNullOrBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    partial.delete()
                    throw ChecksumMismatchException()
                }
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IOException("无法保存安装包")
            }
            target
        }

    /** 下载字节与公布哈希不符。 */
    class ChecksumMismatchException : IOException("安装包校验失败，请重新下载")

    enum class Rejection {
        /** 读不到 APK 的包信息（文件损坏、不是 APK）。 */
        UNREADABLE,

        /** 包名不同：装上去就是另一个应用，覆盖不到自己。 */
        WRONG_PACKAGE,

        /** 签名不同：不是同一个发布者，必须拒绝。 */
        WRONG_SIGNER,
    }

    /**
     * 校验下载到的 APK 是否就是本应用的下一个版本。
     *
     * 用 `GET_SIGNING_CERTIFICATES`（API 28+，本项目 minSdk 31）比较证书 SHA-256，
     * 而不是比较 `versionCode`——版本号可以被随便填，签名不能。
     */
    fun verify(
        context: Context,
        apk: File,
    ): Rejection? {
        val manager = context.packageManager
        val archive =
            runCatching {
                manager.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            }.getOrNull() ?: return Rejection.UNREADABLE

        if (archive.packageName != context.packageName) return Rejection.WRONG_PACKAGE

        val ours =
            runCatching {
                manager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
            }.getOrNull()
        val theirs = archive.signingInfo
        if (ours == null || theirs == null) return Rejection.UNREADABLE

        val oursHashes = certHashes(ours.apkContentsSigners)
        val theirsHashes = certHashes(theirs.apkContentsSigners)
        return if (oursHashes.isNotEmpty() &&
            oursHashes == theirsHashes
        ) {
            null
        } else {
            Rejection.WRONG_SIGNER
        }
    }

    private fun certHashes(signatures: Array<Signature>?): Set<String> =
        signatures
            ?.map { signature ->
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }?.toSet()
            .orEmpty()

    /**
     * 交给系统安装器。
     *
     * 文件必须**保留**：`ACTION_VIEW` 只是把 URI 递过去，真正的读取发生在
     * 用户点确认之后。这里删文件会让那次确认失败。
     */
    fun install(
        context: Context,
        apk: File,
    ): Boolean =
        runCatching {
            val uri = FileProvider.getUriForFile(context, authority(context), apk)
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

    /** 清掉除 [keep] 之外的历史安装包。下载前调用，安装后不要调用。 */
    fun clearCacheExcept(
        context: Context,
        keep: File?,
    ) {
        File(context.cacheDir, "updates")
            .listFiles()
            ?.filter { it != keep }
            ?.forEach { it.delete() }
    }
}
