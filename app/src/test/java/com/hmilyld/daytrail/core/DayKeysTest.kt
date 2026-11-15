package com.hmilyld.daytrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class DayKeysTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    /** 2026-11-15 00:00:00 +08:00 的 epoch 毫秒。 */
    private val midnightOfNov15 = 1_794_672_000_000L

    @Test
    fun `timestamp maps to local date`() {
        // 2026-11-15 08:00:00 +08:00
        val timestamp = midnightOfNov15 + 8 * 3600 * 1000L
        val key = DayKeys.of(timestamp, zone)

        assertEquals("2026-11-15", key.value)
    }

    @Test
    fun `late night still belongs to same day`() {
        // 2026-11-15 23:58:00 +08:00
        val timestamp = midnightOfNov15 + (23 * 3600 + 58 * 60) * 1000L
        val key = DayKeys.of(timestamp, zone)

        assertEquals("2026-11-15", key.value)
    }

    @Test
    fun `just after midnight rolls to next day`() {
        // 2026-11-16 00:02:00 +08:00
        val timestamp = midnightOfNov15 + (24 * 3600 + 2 * 60) * 1000L
        val key = DayKeys.of(timestamp, zone)

        assertEquals("2026-11-16", key.value)
    }

    @Test
    fun `parse rejects malformed input`() {
        assertNull(DayKeys.parse("2026-13-01"))
        assertNull(DayKeys.parse("not-a-date"))
        assertNull(DayKeys.parse("2026/11/15"))
    }

    @Test
    fun `parse accepts valid date`() {
        assertEquals("2026-11-15", DayKeys.parse("2026-11-15")?.value)
    }

    @Test
    fun `start of day is midnight local`() {
        val key = DayKey("2026-11-15")
        val start = DayKeys.startOfDay(key, zone)

        assertEquals(key, DayKeys.of(start, zone))
    }

    @Test
    fun `next day start is exactly one day later`() {
        val key = DayKey("2026-11-15")
        val start = DayKeys.startOfDay(key, zone)
        val nextStart = DayKeys.startOfNextDay(key, zone)

        assertEquals(24 * 3600 * 1000L, nextStart - start)
    }

    @Test
    fun `days in month covers full month`() {
        val days = DayKeys.daysInMonth(2026, 11, zone)

        assertEquals(30, days.size)
        assertEquals("2026-11-01", days.first().value)
        assertEquals("2026-11-30", days.last().value)
    }

    @Test
    fun `leap year february has 29 days`() {
        assertEquals(29, DayKeys.daysInMonth(2028, 2, zone).size)
        assertEquals(28, DayKeys.daysInMonth(2026, 2, zone).size)
    }
}

class DurationFormatTest {
    @Test
    fun `under one hour shows minutes only`() {
        assertEquals("0m", formatDuration(0))
        assertEquals("43m", formatDuration(43 * 60))
    }

    @Test
    fun `over one hour shows hours and minutes`() {
        assertEquals("2h14m", formatDuration(2 * 3600 + 14 * 60))
        assertEquals("1h0m", formatDuration(3600))
    }

    @Test
    fun `negative duration clamps to zero`() {
        assertEquals("0m", formatDuration(-100))
    }
}
