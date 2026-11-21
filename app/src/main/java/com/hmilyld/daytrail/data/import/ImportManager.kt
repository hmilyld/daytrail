package com.hmilyld.daytrail.data.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 导入的 Android 侧胶水：读 SAF URI、取文件名，然后把纯逻辑交给 [TrackImporter]。
 *
 * **不申请存储权限**：`ACTION_OPEN_DOCUMENT` 返回的 URI 已经带了读授权，
 * 只能读用户显式选中的那一个文件，不扫描外部存储。
 */
class ImportManager(
    private val context: Context,
    repository: TrackRepository,
    private val importer: TrackImporter = TrackImporter(repository),
) {
    suspend fun preview(uri: Uri): PreviewResult =
        withContext(Dispatchers.IO) {
            val name = displayName(uri)
            val text =
                readText(uri)
                    ?: return@withContext PreviewResult.Failure(
                        "无法读取这个文件（可能超过 ${MAX_BYTES / 1024 / 1024} MB，或没有读取权限）",
                    )
            importer.preview(name, text)
        }

    suspend fun commit(
        preview: ImportPreview,
        strategy: ConflictStrategy,
    ): ImportOutcome = importer.commit(preview, strategy)

    /** 显示的原始文件名。取不到就退回 URI 最后一段。 */
    private fun displayName(uri: Uri): String {
        val fromProvider =
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "import"
    }

    /**
     * 按上限读取，**多读 1 字节**用于判断是否超限。
     *
     * 不先查文件大小：`OpenableColumns.SIZE` 在部分 provider（网盘、压缩包内文件）
     * 上是空的或不可靠，直接按字节数兜底更实在。
     */
    private fun readText(uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) return@use null
                    buffer.write(chunk, 0, read)
                }
                buffer.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()

    private companion object {
        /**
         * 与 [TrackImporter] 的上限一致。50 MB 已经远超一年的个人轨迹导出量，
         * 再大就是「拿整个相册当输入」这类误操作。
         */
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}
