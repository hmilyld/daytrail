package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPlaybackTest {
    private fun sample(
        tsMs: Long,
        lat: Double = 31.0,
        lon: Double = 121.0,
    ): PlaybackSample = PlaybackSample(tsMs, GeoPoint(lat, lon))

    private fun span(
        startMs: Long,
        endMs: Long,
        points: List<PlaybackSample>,
    ): PlaybackSpan = PlaybackSpan(startMs, endMs, points)

    // ---- 切段 ----

    @Test
    fun `spans splits at gap end`() {
        val samples =
            listOf(
                sample(0L),
                sample(1_000L),
                sample(7_200_000L),
                sample(7_201_000L),
            )

        val spans = TrackPlayback.spans(samples, listOf(PlaybackGap(1_000L, 7_200_000L)))

        assertEquals(2, spans.size)
        assertEquals(listOf(0L, 1_000L), spans[0].samples.map { it.tsMs })
        assertEquals(listOf(7_200_000L, 7_201_000L), spans[1].samples.map { it.tsMs })
    }

    @Test
    fun `spans with no gaps is one span`() {
        val samples = listOf(sample(0L), sample(1_000L), sample(2_000L))

        val spans = TrackPlayback.spans(samples, emptyList())

        assertEquals(1, spans.size)
        assertEquals(0L, spans[0].startMs)
        assertEquals(2_000L, spans[0].endMs)
    }

    @Test
    fun `spans ignores gap after last sample`() {
        val samples = listOf(sample(0L), sample(1_000L))

        val spans = TrackPlayback.spans(samples, listOf(PlaybackGap(1_000L, 9_000L)))

        assertEquals(1, spans.size)
    }

    @Test
    fun `spans of empty samples is empty`() {
        assertTrue(TrackPlayback.spans(emptyList(), emptyList()).isEmpty())
    }

    // ---- 总时长与倍速 ----

    @Test
    fun `timeline duration includes gap pause`() {
        val spans =
            listOf(
                span(0L, 1_000L, listOf(sample(0L), sample(1_000L))),
                span(2_000L, 3_000L, listOf(sample(2_000L), sample(3_000L))),
            )

        val timeline = TrackPlayback.timeline(spans, 1.0)

        assertEquals(2_000L + TrackPlayback.GAP_PAUSE_MS, timeline.durationMs)
    }

    @Test
    fun `speed two halves the span duration`() {
        val spans = listOf(span(0L, 4_000L, listOf(sample(0L), sample(4_000L))))

        assertEquals(4_000L, TrackPlayback.timeline(spans, 1.0).durationMs)
        assertEquals(2_000L, TrackPlayback.timeline(spans, 2.0).durationMs)
    }

    @Test
    fun `single sample span is still visible`() {
        val spans = listOf(span(0L, 0L, listOf(sample(0L))))

        assertEquals(
            TrackPlayback.MIN_SPAN_DISPLAY_MS,
            TrackPlayback.timeline(spans, 1.0).durationMs,
        )
    }

    @Test
    fun `auto speed has no eight times cap`() {
        val spans =
            listOf(span(0L, 10 * 60 * 60_000L, listOf(sample(0L), sample(10 * 60 * 60_000L))))

        val speed = TrackPlayback.autoSpeed(spans)

        assertTrue("speed=$speed", speed > 100.0)
        val timeline = TrackPlayback.timeline(spans, speed)
        assertTrue(
            "duration=${timeline.durationMs}",
            timeline.durationMs in 39_000L..41_000L,
        )
    }

    @Test
    fun `auto speed never goes below one`() {
        val spans = listOf(span(0L, 100L, listOf(sample(0L), sample(100L))))

        assertEquals(TrackPlayback.MIN_SPEED, TrackPlayback.autoSpeed(spans), 1e-9)
    }

    @Test
    fun `auto speed of empty spans is one`() {
        assertEquals(TrackPlayback.MIN_SPEED, TrackPlayback.autoSpeed(emptyList()), 1e-9)
    }

    // ---- 插值与边界 ----

    @Test
    fun `frame interpolates between samples by real time`() {
        val spans =
            listOf(
                span(
                    0L,
                    1_000L,
                    listOf(sample(0L, 31.0, 121.0), sample(1_000L, 31.001, 121.002)),
                ),
            )
        val timeline = TrackPlayback.timeline(spans, 1.0)

        val frame = timeline.frameAt(500L)!!

        assertEquals(31.0005, frame.point.latitude, 1e-9)
        assertEquals(121.001, frame.point.longitude, 1e-9)
        assertEquals(500L, frame.tsMs)
        assertFalse(frame.inGap)
    }

    @Test
    fun `frames advance monotonically instead of looping back`() {
        val samples =
            listOf(
                sample(0L, 31.0, 121.0),
                sample(1_000L, 31.001, 121.002),
                sample(2_000L, 31.002, 121.004),
            )
        val timeline = TrackPlayback.timeline(listOf(span(0L, 2_000L, samples)), 1.0)

        var previous = Double.NEGATIVE_INFINITY
        for (t in 0L..timeline.durationMs step 50L) {
            val lon = timeline.frameAt(t)!!.point.longitude
            assertTrue("t=$t lon=$lon", lon >= previous - 1e-9)
            previous = lon
        }
    }

    @Test
    fun `stay window snaps drifting points to a fixed anchor`() {
        // 真实场景：人在办公室没动，GPS 在几百米内来回跳。
        val anchor = GeoPoint(31.5, 121.5)
        val samples =
            listOf(
                sample(0L, 31.0, 121.0),
                sample(1_000L, 31.5003, 121.5004),
                sample(2_000L, 31.4997, 121.4996),
                sample(3_000L, 31.5004, 121.5003),
                sample(4_000L, 31.8, 121.8),
            )
        val stay =
            StaySegment(
                startMs = 1_000L,
                endMs = 3_000L,
                point = anchor,
                radiusMeters = 120.0,
            )
        val timeline =
            TrackPlayback.timeline(
                TrackPlayback.spans(samples, emptyList(), listOf(stay)),
                1.0,
                listOf(stay),
            )

        // 停留窗内任意时刻都停在锚点，不再跟着漂移点乱窜
        for (t in 1_000L..3_000L step 200L) {
            val frame = timeline.frameAt(t)!!
            assertEquals("t=$t", anchor.latitude, frame.point.latitude, 1e-9)
            assertEquals("t=$t", anchor.longitude, frame.point.longitude, 1e-9)
        }
        // 停留状态要传给界面，否则用户会以为指针卡住了
        assertTrue(timeline.frameAt(2_000L)!!.inStay)
        assertFalse(timeline.frameAt(0L)!!.inStay)
        // 窗外仍是原始点
        assertEquals(31.0, timeline.frameAt(0L)!!.point.latitude, 1e-9)
        assertEquals(31.8, timeline.frameAt(4_000L)!!.point.latitude, 1e-9)
    }

    @Test
    fun `stay anchoring keeps the original timestamps`() {
        val anchor = GeoPoint(31.5, 121.5)
        val samples = listOf(sample(1_000L, 31.6, 121.6), sample(2_000L, 31.7, 121.7))
        val stay = StaySegment(1_000L, 2_000L, anchor, 120.0)

        val spans = TrackPlayback.spans(samples, emptyList(), listOf(stay))

        assertEquals(1_000L, spans[0].samples[0].tsMs)
        assertEquals(2_000L, spans[0].samples[1].tsMs)
    }

    @Test
    fun `frame in gap uses previous span anchor`() {
        val spans =
            listOf(
                span(0L, 1_000L, listOf(sample(0L, 31.0, 121.0), sample(1_000L, 31.0, 121.0))),
                span(
                    2_000L,
                    3_000L,
                    listOf(sample(2_000L, 31.1, 121.1), sample(3_000L, 31.1, 121.1)),
                ),
            )
        val timeline = TrackPlayback.timeline(spans, 1.0)

        val frame = timeline.frameAt(1_200L)!!

        assertTrue(frame.inGap)
        assertEquals(0, frame.spanIndex)
        assertEquals(31.0, frame.point.latitude, 1e-9)
    }

    @Test
    fun `frame clamps negative and overflow`() {
        val spans = listOf(span(0L, 1_000L, listOf(sample(0L), sample(1_000L))))
        val timeline = TrackPlayback.timeline(spans, 1.0)

        assertEquals(0L, timeline.frameAt(-1L)!!.tsMs)
        assertEquals(1_000L, timeline.frameAt(999_999L)!!.tsMs)
    }

    @Test
    fun `frame of empty timeline is null`() {
        assertNull(TrackPlayback.timeline(emptyList(), 1.0).frameAt(0L))
    }

    @Test
    fun `out of china coordinates are not shifted`() {
        // 回放只读原始点，不做任何坐标转换（库内已是 GCJ-02）
        val spans = listOf(span(0L, 0L, listOf(sample(0L, 35.6762, 139.6503))))
        val frame = TrackPlayback.timeline(spans, 1.0).frameAt(0L)!!

        assertEquals(35.6762, frame.point.latitude, 1e-9)
        assertEquals(139.6503, frame.point.longitude, 1e-9)
    }
}
