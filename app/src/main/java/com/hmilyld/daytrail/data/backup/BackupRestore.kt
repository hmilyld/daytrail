package com.hmilyld.daytrail.data.backup

import android.content.Context
import java.io.File

/**
 * 启动期的数据库替换。
 *
 * 必须在 Room 打开之前调用（`AppContainer.database` 是 lazy，满足条件）。
 * 用 staged 文件而不是直接覆盖：任一步失败都能把 `.before-restore` 换回去，
 * 不会留下「半套」数据库。
 *
 * WAL / SHM / rollback journal 属于旧库，不能留给新库：旧日志会被 SQLite
 * 当成新库的热日志回滚进去。但它们也必须在失败时能一起恢复，否则回滚后的
 * 旧库会丢掉尚未 checkpoint 的写入。所以先「暂存」、成功后再删。
 *
 * 标记文件用普通文件而不是 DataStore：启动期读 DataStore 要 runBlocking，
 * 这里只读几个字节的纯文本，不引入第二次阻塞读。
 */
object BackupRestore {
    const val DB_NAME = "daytrail.db"

    /** 准备阶段的临时数据库；启动时顺手清掉上次被中断留下的残留。 */
    const val TEMP_NAME = "daytrail.db.restore.tmp"

    private const val RESTORE_NAME = "daytrail.db.restore"
    private const val PENDING_NAME = "restore.pending"
    private const val BEFORE_NAME = "daytrail.db.before-restore"
    private const val WAL_NAME = "daytrail.db-wal"
    private const val SHM_NAME = "daytrail.db-shm"
    private const val JOURNAL_NAME = "daytrail.db-journal"
    private const val PARKED_SUFFIX = ".before-restore"

    data class Paths(
        val db: File,
        val restore: File,
        val pending: File,
        val before: File,
        val wal: File,
        val shm: File,
        val journal: File,
    ) {
        val sidecars: List<File> get() = listOf(wal, shm, journal)
    }

    /** 纯路径规划，便于单测。 */
    fun plan(dbDir: File): Paths =
        Paths(
            db = File(dbDir, DB_NAME),
            restore = File(dbDir, RESTORE_NAME),
            pending = File(dbDir, PENDING_NAME),
            before = File(dbDir, BEFORE_NAME),
            wal = File(dbDir, WAL_NAME),
            shm = File(dbDir, SHM_NAME),
            journal = File(dbDir, JOURNAL_NAME),
        )

    fun paths(context: Context): Paths {
        val dir = context.getDatabasePath(DB_NAME).parentFile ?: File(context.filesDir, "databases")
        return plan(dir)
    }

    sealed interface ApplyResult {
        data object NothingToDo : ApplyResult

        /** [createdAt] 来自备份的 manifest，恢复后「上次备份」用它。 */
        data class Applied(
            val createdAt: String,
        ) : ApplyResult

        data class Failed(
            val message: String,
        ) : ApplyResult
    }

    fun applyPending(context: Context): ApplyResult = applyPending(paths(context))

    /** 真正的文件替换。文件 IO 集中在 [paths] 上，便于用临时目录测试。 */
    fun applyPending(p: Paths): ApplyResult {
        // 准备阶段被中断时可能留下临时库；不影响当前数据，直接清掉。
        runCatching { File(p.db.parentFile, TEMP_NAME).delete() }
        if (!p.pending.exists()) return ApplyResult.NothingToDo
        if (!p.restore.exists()) {
            // 上次已经完成替换、只是标记没删干净：顺手清掉并结束。
            p.pending.delete()
            p.sidecars.forEach { sidecar -> File(sidecar.path + PARKED_SUFFIX).delete() }
            return ApplyResult.NothingToDo
        }
        val createdAt =
            runCatching { p.pending.readText().trim() }
                .getOrDefault("")

        val parked = mutableListOf<Pair<File, File>>()

        fun rollback() {
            // 先还原 sidecar，再还原主库：顺序反过来会让旧库带着新日志被打开。
            parked.forEach { (original, backup) ->
                runCatching {
                    if (backup.exists() && !original.exists()) backup.renameTo(original)
                }
            }
            runCatching {
                if (p.before.exists() && !p.db.exists()) p.before.renameTo(p.db)
            }
        }

        return try {
            if (p.before.exists() && !p.before.delete()) {
                return ApplyResult.Failed("无法清理旧的 .before-restore")
            }
            if (p.db.exists() && !p.db.renameTo(p.before)) {
                return ApplyResult.Failed("无法备份当前数据库")
            }
            // 暂存旧库的 sidecar；失败时能连同主库一起换回去。
            p.sidecars.forEach { sidecar ->
                if (!sidecar.exists()) return@forEach
                val backup = File(sidecar.path + PARKED_SUFFIX)
                if (backup.exists()) backup.delete()
                if (sidecar.renameTo(backup)) {
                    parked += sidecar to backup
                } else {
                    rollback()
                    return ApplyResult.Failed("无法暂存 ${sidecar.name}")
                }
            }
            if (!p.restore.renameTo(p.db)) {
                rollback()
                return ApplyResult.Failed("无法写入恢复的数据库")
            }
            // 替换成功，旧 sidecar 不再需要。
            parked.forEach { (_, backup) -> backup.delete() }
            p.pending.delete()
            ApplyResult.Applied(createdAt)
        } catch (error: Exception) {
            rollback()
            ApplyResult.Failed(error.message ?: "恢复失败")
        }
    }

    /** 恢复准备阶段写标记文件。 */
    fun writePending(
        p: Paths,
        createdAt: String,
    ) {
        p.pending.parentFile?.mkdirs()
        p.pending.writeText(createdAt)
    }
}
