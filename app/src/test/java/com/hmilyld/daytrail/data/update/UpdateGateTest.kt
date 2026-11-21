package com.hmilyld.daytrail.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGateTest {
    private val day = 24L * 60 * 60 * 1000

    private fun shouldCheck(
        force: Boolean = false,
        autoEnabled: Boolean = true,
        lastCheckAt: Long = 0L,
        now: Long = 0L,
    ): Boolean = UpdateGate.shouldCheck(force, autoEnabled, lastCheckAt, now)

    private fun shouldOffer(
        published: String?,
        installed: String?,
        skipped: String? = null,
        force: Boolean = false,
        publishedCode: Int = 0,
        installedCode: Int = 0,
    ): Boolean =
        UpdateGate.shouldOffer(force, published, installed, skipped, publishedCode, installedCode)

    @Test
    fun `manual check always allowed`() {
        assertTrue(
            shouldCheck(force = true, autoEnabled = false, lastCheckAt = 1_000L, now = 1_000L),
        )
    }

    @Test
    fun `auto check respects the switch`() {
        assertFalse(shouldCheck(autoEnabled = false, now = day))
    }

    @Test
    fun `first ever check runs immediately`() {
        assertTrue(shouldCheck(lastCheckAt = 0L, now = 1L))
    }

    @Test
    fun `checks are throttled for a day`() {
        val last = 1_000_000L
        assertFalse(shouldCheck(lastCheckAt = last, now = last + day - 1))
        assertTrue(shouldCheck(lastCheckAt = last, now = last + day))
    }

    @Test
    fun `clock moving backwards does not silence checks forever`() {
        assertTrue(shouldCheck(lastCheckAt = day * 10, now = 1L))
    }

    @Test
    fun `offers a newer version`() {
        assertTrue(shouldOffer(published = "0.10.0", installed = "0.9.0"))
    }

    @Test
    fun `does not offer the same or older version`() {
        assertFalse(shouldOffer("0.3.2", "0.3.2"))
        assertFalse(shouldOffer("0.3.1", "0.3.2"))
    }

    @Test
    fun `hotfix that only bumps the build number still counts`() {
        assertTrue(shouldOffer("0.3.2", "0.3.2", publishedCode = 6, installedCode = 5))
        assertFalse(shouldOffer("0.3.2", "0.3.2", publishedCode = 5, installedCode = 5))
    }

    @Test
    fun `build number never overrides a lower version name`() {
        assertFalse(shouldOffer("0.3.1", "0.3.2", publishedCode = 99, installedCode = 5))
    }

    @Test
    fun `a postponed version stays quiet but a manual check shows it`() {
        assertFalse(shouldOffer("0.4.0", "0.3.2", skipped = "0.4.0"))
        assertTrue(shouldOffer("0.4.0", "0.3.2", skipped = "0.4.0", force = true))
    }

    @Test
    fun `unparseable versions are never offered`() {
        assertFalse(shouldOffer("nightly", "0.3.2"))
        assertFalse(shouldOffer("0.4.0", null))
    }
}
