package com.hmilyld.daytrail.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BackupNamingTest {
    @Test
    fun `file name uses local date and minute`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val timestamp =
            ZonedDateTime
                .of(2026, 11, 26, 12, 34, 56, 0, zone)
                .toInstant()
                .toEpochMilli()

        assertEquals(
            "daytrail-backup-20261126-1234.zip",
            BackupNaming.suggestedFileName(timestamp, zone),
        )
    }
}
