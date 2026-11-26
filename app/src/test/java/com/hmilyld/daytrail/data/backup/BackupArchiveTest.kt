package com.hmilyld.daytrail.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, entries)
        return out.toByteArray()
    }

    private fun read(
        bytes: ByteArray,
        maxEntryBytes: Long = BackupArchive.MAX_ENTRY_BYTES,
        maxTotalBytes: Long = BackupArchive.MAX_TOTAL_BYTES,
    ): Pair<ArchiveResult, Map<String, ByteArray>> {
        val found = linkedMapOf<String, ByteArray>()
        val result =
            BackupArchive.read(
                ByteArrayInputStream(bytes),
                { name, data -> found[name] = data.readBytes() },
                maxEntryBytes,
                maxTotalBytes,
            )
        return result to found
    }

    @Test
    fun `write and read round trip`() {
        val entries =
            mapOf(
                BackupArchive.MANIFEST to "{}".toByteArray(),
                BackupArchive.DATABASE to byteArrayOf(1, 2, 3),
                BackupArchive.SETTINGS to "{}".toByteArray(),
            )

        val (result, found) = read(zipOf(entries))

        assertTrue(result is ArchiveResult.Success)
        assertEquals(3, found.size)
        assertArrayEquals(entries[BackupArchive.DATABASE], found[BackupArchive.DATABASE])
    }

    @Test
    fun `unsafe names are rejected`() {
        assertFalse(BackupArchive.isSafeName("../evil"))
        assertFalse(BackupArchive.isSafeName("/absolute"))
        assertFalse(BackupArchive.isSafeName("a\\b"))
        assertFalse(BackupArchive.isSafeName("C:evil"))
        assertTrue(BackupArchive.isSafeName(BackupArchive.DATABASE))
    }

    @Test
    fun `zip slip entry is rejected`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("../evil"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }

        val (result, _) = read(out.toByteArray())

        assertEquals(ArchiveResult.Failure(ArchiveFailure.UNSAFE_NAME), result)
    }

    @Test
    fun `unknown entry is rejected`() {
        val (result, _) = read(zipOf(mapOf("extra.txt" to "x".toByteArray())))

        assertEquals(ArchiveResult.Failure(ArchiveFailure.UNKNOWN_ENTRY), result)
    }

    @Test
    fun `missing required entry is rejected`() {
        val (result, _) =
            read(
                zipOf(
                    mapOf(
                        BackupArchive.MANIFEST to "{}".toByteArray(),
                        BackupArchive.DATABASE to byteArrayOf(1),
                    ),
                ),
            )

        assertEquals(ArchiveResult.Failure(ArchiveFailure.MISSING_ENTRY), result)
    }

    @Test
    fun `entry over the limit is rejected while reading`() {
        val entries =
            mapOf(
                BackupArchive.MANIFEST to ByteArray(100),
                BackupArchive.DATABASE to ByteArray(1),
                BackupArchive.SETTINGS to ByteArray(1),
            )

        val (result, _) = read(zipOf(entries), maxEntryBytes = 10)

        assertEquals(ArchiveResult.Failure(ArchiveFailure.ENTRY_TOO_LARGE), result)
    }

    @Test
    fun `total over the limit is rejected`() {
        val entries =
            mapOf(
                BackupArchive.MANIFEST to ByteArray(8),
                BackupArchive.DATABASE to ByteArray(8),
                BackupArchive.SETTINGS to ByteArray(8),
            )

        val (result, _) = read(zipOf(entries), maxTotalBytes = 16)

        assertEquals(ArchiveResult.Failure(ArchiveFailure.TOTAL_TOO_LARGE), result)
    }

    @Test
    fun `garbage is reported as malformed`() {
        val (result, _) = read("not a zip".toByteArray())

        assertEquals(ArchiveResult.Failure(ArchiveFailure.MALFORMED_ZIP), result)
    }

    @Test
    fun `default limits are one gig and two gig`() {
        assertEquals(1L * 1024 * 1024 * 1024, BackupArchive.MAX_ENTRY_BYTES)
        assertEquals(2L * 1024 * 1024 * 1024, BackupArchive.MAX_TOTAL_BYTES)
    }
}
