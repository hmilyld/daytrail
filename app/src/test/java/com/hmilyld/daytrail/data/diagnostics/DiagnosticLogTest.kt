package com.hmilyld.daytrail.data.diagnostics

import com.hmilyld.daytrail.data.db.entity.DiagnosticEventEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DiagnosticLogTest {
    private val event =
        DiagnosticEventEntity(
            timestampMs = 1_700_000_000_000L,
            category = "tier",
            detail = "STILL→MOVING 速度=1.2m/s",
        )

    @Test
    fun `json contains exportedAt and events`() {
        val json =
            DiagnosticLog.toJson(
                events = listOf(event),
                exportedAtMs = 1_700_000_060_000L,
                zone = ZoneId.of("Asia/Shanghai"),
            )

        assertTrue(json.contains("\"exportedAt\""))
        assertTrue(json.contains("\"category\":\"tier\""))
        assertTrue(json.contains("\"detail\":\"STILL→MOVING 速度=1.2m/s\""))
    }

    @Test
    fun `timestamp carries the local offset`() {
        val json =
            DiagnosticLog.toJson(
                events = listOf(event),
                exportedAtMs = 1_700_000_000_000L,
                zone = ZoneId.of("Asia/Shanghai"),
            )

        assertTrue(json.contains("\"ts\":\"2023-11-15T06:13:20+08:00\""))
    }

    @Test
    fun `quotes in detail are escaped`() {
        val escaped =
            DiagnosticLog.toJson(
                events = listOf(event.copy(detail = "他说\"到了\"")),
                exportedAtMs = 0L,
                zone = ZoneId.of("Asia/Shanghai"),
            )

        assertTrue(escaped.contains("他说\\\"到了\\\""))
    }

    @Test
    fun `empty events produce an empty array`() {
        val json =
            DiagnosticLog.toJson(
                events = emptyList(),
                exportedAtMs = 0L,
                zone = ZoneId.of("Asia/Shanghai"),
            )

        assertTrue(json.contains("\"events\":[]"))
    }
}
