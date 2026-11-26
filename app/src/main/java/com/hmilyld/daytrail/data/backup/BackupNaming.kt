package com.hmilyld.daytrail.data.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 备份文件名。纯函数，便于单测。 */
object BackupNaming {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmm",
            Locale.US,
        )

    fun suggestedFileName(
        timestampMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String =
        "daytrail-backup-${formatter.format(Instant.ofEpochMilli(timestampMs).atZone(zone))}.zip"
}
