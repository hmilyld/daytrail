package com.hmilyld.daytrail.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixSilenceTest {
    private val minute = 60_000L

    @Test
    fun `no signal right after a fix`() {
        val decision = FixSilence.evaluate(silentMs = 0L, sleeping = false)

        assertFalse(decision.switchNotice)
        assertFalse(decision.markStale)
    }

    @Test
    fun `notice switches before the interface warns`() {
        val decision = FixSilence.evaluate(silentMs = 10L * minute, sleeping = false)

        assertTrue(decision.switchNotice)
        assertFalse(decision.markStale)
    }

    @Test
    fun `interface warns only after a full missed cycle`() {
        val decision = FixSilence.evaluate(silentMs = 15L * minute, sleeping = false)

        assertTrue(decision.switchNotice)
        assertTrue(decision.markStale)
    }

    @Test
    fun `boundaries are inclusive`() {
        assertFalse(FixSilence.evaluate(FixSilence.NOTICE_AFTER_MS - 1, false).switchNotice)
        assertTrue(FixSilence.evaluate(FixSilence.NOTICE_AFTER_MS, false).switchNotice)
        assertTrue(FixSilence.evaluate(FixSilence.STALE_AFTER_MS, false).markStale)
        assertFalse(FixSilence.evaluate(FixSilence.STALE_AFTER_MS - 1, false).markStale)
    }

    @Test
    fun `sleeping suppresses both signals`() {
        // 休眠间隔就是一小时，「很久没有点位」属于预期
        val decision = FixSilence.evaluate(silentMs = 60L * minute, sleeping = true)

        assertEquals(FixSilence.Decision.NONE, decision)
    }

    @Test
    fun `clock moving backwards never warns`() {
        val decision = FixSilence.evaluate(silentMs = -5L * minute, sleeping = false)

        assertEquals(FixSilence.Decision.NONE, decision)
    }
}
