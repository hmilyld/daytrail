package com.hmilyld.daytrail.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** zip 条目的读取结果。 */
sealed interface ArchiveResult {
    data class Success(
        val names: List<String>,
    ) : ArchiveResult

    data class Failure(
        val reason: ArchiveFailure,
    ) : ArchiveResult
}

enum class ArchiveFailure {
    UNSAFE_NAME,
    UNKNOWN_ENTRY,
    ENTRY_TOO_LARGE,
    TOTAL_TOO_LARGE,
    MISSING_ENTRY,
    MALFORMED_ZIP,
}

/**
 * zip 读写与防护。纯 JVM，可直接单测。
 *
 * 备份文件来自外部存储，必须当不可信输入处理：
 * - 只读白名单内的三个条目，别的名字一律拒绝（防 zip-slip 与夹带）；
 * - 单条 1GB、总计 2GB 上限。**上限在读取过程中强制执行**，不是读完再判断：
 *   `ZipInputStream` 对 DEFLATED 条目的 `entry.size` 常为 -1（数据描述符），
 *   依赖它做预检等于没有防护；分块计数才能挡住解压炸弹。
 */
object BackupArchive {
    const val MANIFEST = "manifest.json"
    const val DATABASE = "daytrail.db"
    const val SETTINGS = "settings.json"

    val ALLOWED = setOf(MANIFEST, DATABASE, SETTINGS)

    const val MAX_ENTRY_BYTES = 1L * 1024 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024

    /** 小数据（测试、manifest/settings）用。 */
    fun write(
        output: OutputStream,
        entries: Map<String, ByteArray>,
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /**
     * 流式写：数据库从文件复制，不整体读进内存。
     *
     * 长期用户的库可能有几十上百 MB，`readBytes()` + `ByteArrayOutputStream`
     * 会在低内存设备上直接 OOM。
     */
    fun writeStreaming(
        output: OutputStream,
        manifest: ByteArray,
        database: File,
        settings: ByteArray,
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATABASE))
            database.inputStream().buffered().use { input -> input.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(SETTINGS))
            zip.write(settings)
            zip.closeEntry()
        }
    }

    /**
     * 逐条回调。回调里的 [InputStream] 只在本次回调期间有效，
     * 调用方必须读完（或由 [read] 主动排空），否则会破坏 zip 流。
     */
    fun read(
        input: InputStream,
        onEntry: (name: String, data: InputStream) -> Unit,
        maxEntryBytes: Long = MAX_ENTRY_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ): ArchiveResult {
        val names = mutableListOf<String>()
        var total = 0L
        return try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!isSafeName(name)) return ArchiveResult.Failure(ArchiveFailure.UNSAFE_NAME)
                    if (name !in ALLOWED) return ArchiveResult.Failure(ArchiveFailure.UNKNOWN_ENTRY)

                    val limited = LimitedInputStream(zip, maxEntryBytes)
                    onEntry(name, limited)
                    limited.drain()
                    total += limited.count
                    if (total > maxTotalBytes) {
                        return ArchiveResult.Failure(ArchiveFailure.TOTAL_TOO_LARGE)
                    }
                    names += name
                }
            }
            if (names.isEmpty()) {
                return ArchiveResult.Failure(ArchiveFailure.MALFORMED_ZIP)
            }
            if (!ALLOWED.all { it in names }) {
                return ArchiveResult.Failure(ArchiveFailure.MISSING_ENTRY)
            }
            ArchiveResult.Success(names)
        } catch (_: EntryLimitExceeded) {
            ArchiveResult.Failure(ArchiveFailure.ENTRY_TOO_LARGE)
        } catch (_: Exception) {
            ArchiveResult.Failure(ArchiveFailure.MALFORMED_ZIP)
        }
    }

    /** 只判断路径是否安全；白名单由 [read] 单独检查，便于区分拒绝原因。 */
    fun isSafeName(name: String): Boolean =
        name.isNotBlank() &&
            name == name.trim() &&
            !name.contains("..") &&
            !name.startsWith("/") &&
            !name.contains("\\") &&
            !name.contains(":")

    private class EntryLimitExceeded : Exception()

    /**
     * 在读的过程中计数并抛错。委托给 `ZipInputStream`，读取即消耗当前条目。
     */
    private class LimitedInputStream(
        private val delegate: InputStream,
        private val limit: Long,
    ) : InputStream() {
        var count = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) account(1)
            return value
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) account(read.toLong())
            return read
        }

        private fun account(bytes: Long) {
            count += bytes
            if (count > limit) throw EntryLimitExceeded()
        }

        fun drain() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (read(buffer) >= 0) {
                // 读空当前条目即可
            }
        }
    }
}
