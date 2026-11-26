package com.hmilyld.daytrail.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManifestTest {
    private fun manifest(
        formatVersion: Int = BackupManifest.FORMAT_VERSION,
        dbVersion: Int = 5,
        applicationId: String = "com.hmilyld.daytrail",
        tables: List<String> = BackupManifest.TABLES,
    ): BackupManifest =
        BackupManifest(
            formatVersion = formatVersion,
            appVersionName = "0.6.0",
            appVersionCode = 12,
            dbVersion = dbVersion,
            applicationId = applicationId,
            createdAt = "2026-11-26T12:00:00Z",
            tableRowCounts = tables.associateWith { 3L },
            dbSha256 = "abc123",
        )

    @Test
    fun `round trip keeps all fields`() {
        val original = manifest()

        val decoded = BackupManifest.parse(BackupManifest.encode(original))

        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `parse accepts unknown fields`() {
        val json = BackupManifest.encode(manifest()).dropLast(1) + ",\"futureField\":42}"
        assertNotNull(BackupManifest.parse(json))
    }

    @Test
    fun `parse rejects missing field`() {
        val json = BackupManifest.encode(manifest()).replace("\"dbSha256\":\"abc123\"", "")
        assertNull(BackupManifest.parse(json))
    }

    @Test
    fun `parse rejects garbage`() {
        assertNull(BackupManifest.parse("not json"))
    }

    @Test
    fun `current versions are accepted`() {
        val check =
            manifest().check(
                currentDbVersion = 5,
                currentApplicationId = "com.hmilyld.daytrail",
            )

        assertTrue(check.acceptable)
        assertFalse(check.needsAppIdConfirmation)
        assertNull(manifest().rejectReason(check))
    }

    @Test
    fun `higher format version is rejected`() {
        val m = manifest(formatVersion = 2)
        val check = m.check(5, m.applicationId)

        assertEquals(ManifestReject.FORMAT_TOO_NEW, m.rejectReason(check))
    }

    @Test
    fun `higher db version is rejected but lower is allowed`() {
        val tooNew = manifest(dbVersion = 6)
        val checkNew = tooNew.check(5, tooNew.applicationId)
        assertEquals(ManifestReject.DB_TOO_NEW, tooNew.rejectReason(checkNew))

        val older = manifest(dbVersion = 4)
        assertTrue(older.check(5, older.applicationId).acceptable)
    }

    @Test
    fun `missing table is rejected`() {
        val m = manifest(tables = BackupManifest.TABLES.dropLast(1))
        val check = m.check(5, m.applicationId)

        assertEquals(ManifestReject.TABLES_INCOMPLETE, m.rejectReason(check))
    }

    @Test
    fun `different application id needs confirmation`() {
        val m = manifest(applicationId = "com.fork.daytrail")
        val check = m.check(5, "com.hmilyld.daytrail")

        assertTrue(check.acceptable)
        assertTrue(check.needsAppIdConfirmation)
    }
}
