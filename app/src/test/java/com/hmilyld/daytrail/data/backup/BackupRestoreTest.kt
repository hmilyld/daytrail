package com.hmilyld.daytrail.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupRestoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `plan uses the expected file names`() {
        val dir = folder.newFolder("databases")

        val p = BackupRestore.plan(dir)

        assertEquals("daytrail.db", p.db.name)
        assertEquals("daytrail.db.restore", p.restore.name)
        assertEquals("restore.pending", p.pending.name)
        assertEquals("daytrail.db.before-restore", p.before.name)
        assertEquals("daytrail.db-wal", p.wal.name)
        assertEquals("daytrail.db-shm", p.shm.name)
        assertEquals("daytrail.db-journal", p.journal.name)
    }

    @Test
    fun `nothing to do without pending marker`() {
        val dir = folder.newFolder("databases")
        File(dir, "daytrail.db").writeText("current")

        assertEquals(
            BackupRestore.ApplyResult.NothingToDo,
            BackupRestore.applyPending(BackupRestore.plan(dir)),
        )
    }

    @Test
    fun `apply swaps the database and drops old sidecars`() {
        val dir = folder.newFolder("databases")
        val p = BackupRestore.plan(dir)
        p.db.writeText("current")
        p.restore.writeText("restored")
        p.wal.writeText("wal")
        p.shm.writeText("shm")
        p.journal.writeText("journal")
        BackupRestore.writePending(p, "2026-11-26T12:00:00Z")

        val result = BackupRestore.applyPending(p)

        assertEquals(BackupRestore.ApplyResult.Applied("2026-11-26T12:00:00Z"), result)
        assertEquals("restored", p.db.readText())
        assertEquals("current", p.before.readText())
        assertFalse(p.wal.exists())
        assertFalse(p.shm.exists())
        assertFalse(p.journal.exists())
        assertFalse(p.pending.exists())
    }

    @Test
    fun `apply cleans a stale marker when restore file is gone`() {
        val dir = folder.newFolder("databases")
        val p = BackupRestore.plan(dir)
        p.db.writeText("current")
        BackupRestore.writePending(p, "2026-11-26T12:00:00Z")

        assertEquals(BackupRestore.ApplyResult.NothingToDo, BackupRestore.applyPending(p))
        assertEquals("current", p.db.readText())
        assertFalse(p.pending.exists())
    }

    @Test
    fun `write pending stores created at verbatim`() {
        val dir = folder.newFolder("databases")
        val p = BackupRestore.plan(dir)

        BackupRestore.writePending(p, "2026-11-26T12:00:00Z")

        assertEquals("2026-11-26T12:00:00Z", p.pending.readText())
        assertTrue(p.pending.exists())
    }
}
