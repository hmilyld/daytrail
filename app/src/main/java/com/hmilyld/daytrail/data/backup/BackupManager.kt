package com.hmilyld.daytrail.data.backup

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.hmilyld.daytrail.BuildConfig
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.db.DayTrailDatabase
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.data.prefs.SettingsStore
import com.hmilyld.daytrail.location.RecordingStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant

/** 备份结果。 */
sealed interface BackupResult {
    data class Success(
        val tableRowCounts: Map<String, Long>,
        val sizeBytes: Long,
    ) : BackupResult

    data class Failure(
        val message: String,
    ) : BackupResult
}

/** 恢复前的校验结果，界面据此决定直接恢复还是先弹二次确认。 */
data class RestoreInspection(
    val manifest: BackupManifest,
    val check: ManifestCheck,
    val rowCounts: Map<String, Long>,
)

sealed interface InspectionResult {
    data class Ready(
        val inspection: RestoreInspection,
    ) : InspectionResult

    data class Rejected(
        val reason: String,
    ) : InspectionResult
}

sealed interface RestorePrepareResult {
    data class Success(
        val manifest: BackupManifest,
        val rowCounts: Map<String, Long>,
    ) : RestorePrepareResult

    data class Failure(
        val message: String,
    ) : RestorePrepareResult
}

/**
 * 备份 / 恢复的 Android 胶水层。
 *
 * 纯逻辑（manifest 校验、zip 防护、启动期文件替换）都在同包的其他类里，
 * 这里只负责 SAF、SQLite 一致性快照、行数和 SHA-256。
 *
 * 所有大文件都走流式：长期用户的数据库可能有几十上百 MB，
 * 一次性读入内存会在低内存设备上 OOM。
 */
class BackupManager(
    private val context: Context,
    private val database: DayTrailDatabase,
    private val settingsStore: SettingsStore,
    private val recordingState: RecordingStateHolder,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun suggestedName(): String = BackupNaming.suggestedFileName(System.currentTimeMillis())

    suspend fun backup(uri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val createdAt = System.currentTimeMillis()
                var size = 0L
                val manifest =
                    context.contentResolver.openOutputStream(uri)?.use { raw ->
                        val counting = CountingOutputStream(raw)
                        val written = writeArchive(counting, createdAt)
                        size = counting.count
                        written
                    } ?: return@withContext BackupResult.Failure(
                        context.getString(R.string.backup_write_failed),
                    )
                settingsStore.setLastBackupAt(createdAt)
                BackupResult.Success(manifest.tableRowCounts, size)
            } catch (error: Exception) {
                BackupResult.Failure(
                    error.message ?: context.getString(R.string.backup_write_failed),
                )
            }
        }

    /** 恢复前自动备份当前数据用。写到应用私有 cache，不需要 SAF。 */
    suspend fun backupTo(file: File): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                file.parentFile?.mkdirs()
                val manifest =
                    file.outputStream().use { raw ->
                        writeArchive(raw, System.currentTimeMillis())
                    }
                BackupResult.Success(manifest.tableRowCounts, file.length())
            } catch (error: Exception) {
                BackupResult.Failure(
                    error.message ?: context.getString(R.string.backup_write_failed),
                )
            }
        }

    /** 只读校验，不改任何数据。 */
    suspend fun inspect(uri: Uri): InspectionResult =
        withContext(Dispatchers.IO) {
            val snapshot = inspectArchive(uri)
            when (snapshot) {
                is InspectionSnapshot.Failure ->
                    InspectionResult.Rejected(snapshot.reason)
                is InspectionSnapshot.Ready ->
                    InspectionResult.Ready(
                        RestoreInspection(snapshot.manifest, snapshot.check, snapshot.rowCounts),
                    )
            }
        }

    /**
     * 准备恢复：先校验并把数据库流式写到临时文件，再自动备份当前数据，
     * 最后提交 staged 文件并应用设置。真正替换发生在下次启动。
     */
    suspend fun prepareRestore(
        uri: Uri,
        allowDifferentApplicationId: Boolean,
    ): RestorePrepareResult =
        withContext(Dispatchers.IO) {
            if (recordingState.state.value != RecordingState.IDLE) {
                return@withContext RestorePrepareResult.Failure(
                    context.getString(R.string.backup_recording_blocked),
                )
            }

            val paths = BackupRestore.paths(context)
            val temp = File(paths.db.parentFile, BackupRestore.TEMP_NAME)
            temp.parentFile?.mkdirs()
            temp.delete()

            val staged = stageArchive(uri, temp, allowDifferentApplicationId)
            if (staged is StageResult.Failure) {
                temp.delete()
                return@withContext RestorePrepareResult.Failure(staged.reason)
            }
            val ready = staged as StageResult.Ready

            // 当前数据必须能成功备份，否则中止恢复——这是最后一道保险。
            val safety =
                backupTo(File(context.cacheDir, "restore-backup/daytrail-before-restore.zip"))
            if (safety is BackupResult.Failure) {
                temp.delete()
                return@withContext RestorePrepareResult.Failure(
                    context.getString(R.string.backup_restore_safety_failed, safety.message),
                )
            }

            val committed =
                runCatching {
                    if (paths.restore.exists()) paths.restore.delete()
                    if (!temp.renameTo(paths.restore)) error("无法写入恢复文件")
                    BackupRestore.writePending(paths, ready.manifest.createdAt)
                }
            if (committed.isFailure) {
                temp.delete()
                return@withContext RestorePrepareResult.Failure(
                    committed.exceptionOrNull()?.message
                        ?: context.getString(R.string.backup_write_failed),
                )
            }

            // 设置写失败不回滚数据库：备份文件本身是完好的，用户可以再试一次。
            runCatching { settingsStore.applyBackupSettings(ready.settings) }
            RestorePrepareResult.Success(ready.manifest, ready.manifest.tableRowCounts)
        }

    // ---- 读取与校验 ----

    private sealed interface InspectionSnapshot {
        data class Ready(
            val manifest: BackupManifest,
            val check: ManifestCheck,
            val rowCounts: Map<String, Long>,
        ) : InspectionSnapshot

        data class Failure(
            val reason: String,
        ) : InspectionSnapshot
    }

    /** 流式读取：manifest/settings 很小，db 边读边算 SHA-256。 */
    private fun inspectArchive(uri: Uri): InspectionSnapshot {
        var manifestBytes: ByteArray? = null
        var settingsBytes: ByteArray? = null
        var dbSha: String? = null

        val result =
            readArchive(uri) { name, data ->
                when (name) {
                    BackupArchive.MANIFEST -> manifestBytes = data.readBytes()
                    BackupArchive.SETTINGS -> settingsBytes = data.readBytes()
                    BackupArchive.DATABASE -> dbSha = sha256(data)
                }
            }
        if (result is ArchiveResult.Failure) {
            return InspectionSnapshot.Failure(
                reasonText(result.reason),
            )
        }

        // settings 也必须存在且可解析；否则用户确认恢复后才发现设置坏了。
        val settingsText = settingsBytes?.toString(Charsets.UTF_8)
        if (settingsText == null || parseSettings(settingsText) == null) {
            return InspectionSnapshot.Failure(context.getString(R.string.backup_reason_settings))
        }
        val manifest =
            manifestBytes
                ?.toString(Charsets.UTF_8)
                ?.let { BackupManifest.parse(it) }
                ?: return InspectionSnapshot.Failure(
                    context.getString(R.string.backup_reason_manifest),
                )
        val check = manifest.check(DayTrailDatabase.DB_VERSION, BuildConfig.APPLICATION_ID)
        manifest.rejectReason(check)?.let { reason ->
            return InspectionSnapshot.Failure(reasonText(reason))
        }
        val actualSha =
            dbSha
                ?: return InspectionSnapshot.Failure(
                    context.getString(R.string.backup_reason_missing),
                )
        if (!actualSha.equals(manifest.dbSha256, ignoreCase = true)) {
            return InspectionSnapshot.Failure(context.getString(R.string.backup_reason_sha))
        }
        return InspectionSnapshot.Ready(manifest, check, manifest.tableRowCounts)
    }

    private sealed interface StageResult {
        data class Ready(
            val manifest: BackupManifest,
            val settings: BackupSettings,
        ) : StageResult

        data class Failure(
            val reason: String,
        ) : StageResult
    }

    /**
     * 把 db 流式写到 [temp]，同时算 SHA-256；全部校验通过才返回 Ready。
     * 任何一步失败都只删临时文件，不碰 `.restore` 和当前数据库。
     */
    private fun stageArchive(
        uri: Uri,
        temp: File,
        allowDifferentApplicationId: Boolean,
    ): StageResult {
        var manifestBytes: ByteArray? = null
        var settingsBytes: ByteArray? = null
        var dbSha: String? = null

        val result =
            readArchive(uri) { name, data ->
                when (name) {
                    BackupArchive.MANIFEST -> manifestBytes = data.readBytes()
                    BackupArchive.SETTINGS -> settingsBytes = data.readBytes()
                    BackupArchive.DATABASE -> {
                        val digest = MessageDigest.getInstance("SHA-256")
                        temp.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = data.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                        dbSha = digest.digest().toHex()
                    }
                }
            }
        if (result is ArchiveResult.Failure) {
            return StageResult.Failure(reasonText(result.reason))
        }

        val manifest =
            manifestBytes
                ?.toString(Charsets.UTF_8)
                ?.let { BackupManifest.parse(it) }
                ?: return StageResult.Failure(context.getString(R.string.backup_reason_manifest))
        val check = manifest.check(DayTrailDatabase.DB_VERSION, BuildConfig.APPLICATION_ID)
        manifest.rejectReason(check)?.let { return StageResult.Failure(reasonText(it)) }
        if (check.needsAppIdConfirmation && !allowDifferentApplicationId) {
            return StageResult.Failure(context.getString(R.string.backup_reason_app_id))
        }
        val actualSha =
            dbSha ?: return StageResult.Failure(context.getString(R.string.backup_reason_missing))
        if (!actualSha.equals(manifest.dbSha256, ignoreCase = true)) {
            return StageResult.Failure(context.getString(R.string.backup_reason_sha))
        }
        val settings =
            settingsBytes
                ?.toString(Charsets.UTF_8)
                ?.let { parseSettings(it) }
                ?: return StageResult.Failure(context.getString(R.string.backup_reason_settings))
        return StageResult.Ready(manifest, settings)
    }

    private fun readArchive(
        uri: Uri,
        onEntry: (name: String, data: InputStream) -> Unit,
    ): ArchiveResult =
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BackupArchive.read(input, onEntry)
            } ?: ArchiveResult.Failure(ArchiveFailure.MALFORMED_ZIP)
        } catch (_: Exception) {
            ArchiveResult.Failure(ArchiveFailure.MALFORMED_ZIP)
        }

    // ---- 写入 ----

    private suspend fun writeArchive(
        output: OutputStream,
        createdAtMs: Long,
    ): BackupManifest {
        val snapshot = snapshotDatabase()
        try {
            ensureSpace(snapshot.length())
            val createdAt = Instant.ofEpochMilli(createdAtMs).toString()
            val counts = database.rowCounts(BackupManifest.TABLES)
            val settings = settingsStore.backupSnapshot(createdAtMs)
            val manifest =
                BackupManifest(
                    formatVersion = BackupManifest.FORMAT_VERSION,
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    dbVersion = DayTrailDatabase.DB_VERSION,
                    applicationId = BuildConfig.APPLICATION_ID,
                    createdAt = createdAt,
                    tableRowCounts = counts,
                    dbSha256 = sha256(snapshot),
                )
            BackupArchive.writeStreaming(
                output = output,
                manifest = BackupManifest.encode(manifest).toByteArray(Charsets.UTF_8),
                database = snapshot,
                settings = json.encodeToString(settings).toByteArray(Charsets.UTF_8),
            )
            return manifest
        } finally {
            snapshot.delete()
        }
    }

    /** 一致性快照：VACUUM INTO 不关闭数据库，也不影响正在进行的录制。 */
    private fun snapshotDatabase(): File {
        val dir = File(context.cacheDir, "backup")
        dir.mkdirs()
        val target = File(dir, "snapshot-${System.currentTimeMillis()}.db")
        if (target.exists()) target.delete()
        val escaped = target.absolutePath.replace("'", "''")
        val ok =
            runCatching {
                database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escaped'")
            }.isSuccess
        if (!ok) {
            // 老设备或特殊 ROM 不支持 VACUUM INTO：先 FULL checkpoint 再复制主库。
            database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
            context.getDatabasePath(DayTrailDatabase.NAME).copyTo(target, overwrite = true)
        }
        return target
    }

    private fun ensureSpace(needed: Long) {
        val dir = File(context.cacheDir, "backup")
        val available = StatFs(dir.absolutePath).availableBytes
        // 快照 + zip 各占一份，留 2 倍余量。
        if (available < needed * 2) {
            error(context.getString(R.string.backup_no_space))
        }
    }

    private fun parseSettings(text: String): BackupSettings? =
        runCatching { json.decodeFromString<BackupSettings>(text) }.getOrNull()

    private fun sha256(file: File): String = file.inputStream().buffered().use { sha256(it) }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun reasonText(reason: ArchiveFailure): String =
        context.getString(
            when (reason) {
                ArchiveFailure.UNSAFE_NAME -> R.string.backup_reason_unsafe
                ArchiveFailure.UNKNOWN_ENTRY -> R.string.backup_reason_unknown
                ArchiveFailure.ENTRY_TOO_LARGE, ArchiveFailure.TOTAL_TOO_LARGE ->
                    R.string.backup_reason_too_large
                ArchiveFailure.MISSING_ENTRY -> R.string.backup_reason_missing
                ArchiveFailure.MALFORMED_ZIP -> R.string.backup_reason_malformed
            },
        )

    private fun reasonText(reason: ManifestReject): String =
        context.getString(
            when (reason) {
                ManifestReject.FORMAT_TOO_NEW -> R.string.backup_reject_format
                ManifestReject.DB_TOO_NEW -> R.string.backup_reject_db
                ManifestReject.TABLES_INCOMPLETE -> R.string.backup_reject_tables
            },
        )
}

/** 计数字节数，用于报告备份文件大小。 */
private class CountingOutputStream(
    private val delegate: OutputStream,
) : OutputStream() {
    var count = 0L
        private set

    override fun write(value: Int) {
        delegate.write(value)
        count++
    }

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        delegate.write(buffer, offset, length)
        count += length
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}
