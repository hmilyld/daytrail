package com.hmilyld.daytrail.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 备份包的元信息。只描述内容，不含任何坐标。 */
@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    /** 备份时 Room 的 schema 版本。 */
    val dbVersion: Int,
    val applicationId: String,
    /** ISO-8601。 */
    val createdAt: String,
    /** 8 张表的行数，用来在恢复前给用户一个「这份备份有多少东西」的概念。 */
    val tableRowCounts: Map<String, Long>,
    /** daytrail.db 的 SHA-256，用来发现传输/解压过程中的损坏。 */
    val dbSha256: String,
) {
    companion object {
        const val FORMAT_VERSION = 1

        /** 与 `@Database(entities = ...)` 一一对应。少一张就说明备份不完整。 */
        val TABLES =
            listOf(
                "track_day",
                "track_point",
                "stay_segment",
                "day_gap",
                "place",
                "route_override",
                "diagnostic_event",
                "track_edit",
            )

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): BackupManifest? =
            runCatching { json.decodeFromString<BackupManifest>(text) }.getOrNull()

        fun encode(manifest: BackupManifest): String = json.encodeToString(manifest)
    }
}

/** 恢复前的校验结果。三个条件决定能不能继续，一个条件决定是否要二次确认。 */
data class ManifestCheck(
    val formatSupported: Boolean,
    val dbSupported: Boolean,
    val hasAllTables: Boolean,
    val sameApplicationId: Boolean,
) {
    val acceptable: Boolean get() = formatSupported && dbSupported && hasAllTables

    /** 包名不同（fork 用户改过 applicationId）时不拒绝，但要让用户明确确认。 */
    val needsAppIdConfirmation: Boolean get() = acceptable && !sameApplicationId
}

/** 恢复被拒绝的原因。界面按这个显示具体原因，不要笼统说「备份无效」。 */
enum class ManifestReject {
    FORMAT_TOO_NEW,
    DB_TOO_NEW,
    TABLES_INCOMPLETE,
}

fun BackupManifest.check(
    currentDbVersion: Int,
    currentApplicationId: String,
): ManifestCheck =
    ManifestCheck(
        formatSupported = formatVersion <= BackupManifest.FORMAT_VERSION,
        // 低版本备份允许恢复：Room 会跑 Migration 升上来。
        dbSupported = dbVersion <= currentDbVersion,
        hasAllTables = BackupManifest.TABLES.all { it in tableRowCounts },
        sameApplicationId = applicationId == currentApplicationId,
    )

fun BackupManifest.rejectReason(check: ManifestCheck): ManifestReject? =
    when {
        !check.formatSupported -> ManifestReject.FORMAT_TOO_NEW
        !check.dbSupported -> ManifestReject.DB_TOO_NEW
        !check.hasAllTables -> ManifestReject.TABLES_INCOMPLETE
        else -> null
    }
