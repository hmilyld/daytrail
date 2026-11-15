package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GapDetectorTest {
    private val detector = GapDetector()
    private val base = GeoPoint(31.2304, 121.4737)

    private fun sample(
        metersNorth: Double,
        minutesLater: Long,
    ): TrackPointSample =
        TrackPointSample(
            timestampMs = 1_000_000L + minutesLater * 60_000L,
            point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude),
        )

    @Test
    fun `empty input has no gaps`() {
        assertTrue(detector.detect(emptyList()).isEmpty())
    }

    @Test
    fun `single point has no gaps`() {
        assertTrue(detector.detect(listOf(sample(0.0, 0))).isEmpty())
    }

    @Test
    fun `normal sampling interval is not a gap`() {
        // 10 分钟间隔、只移动 100 米：静止采样，不算缺失
        val points =
            listOf(
                sample(0.0, 0),
                sample(100.0, 10),
            )

        assertTrue(detector.detect(points).isEmpty())
    }

    @Test
    fun `long gap with displacement is detected`() {
        // 30 分钟、移动 20 公里：真实缺失（例如高铁过隧道）
        val points =
            listOf(
                sample(0.0, 0),
                sample(20_000.0, 30),
            )

        val gaps = detector.detect(points)

        assertEquals(1, gaps.size)
        assertEquals(30 * 60_000L, gaps.first().durationMs)
    }

    @Test
    fun `long gap without displacement is not a gap`() {
        // 一整晚静止，手机在桌上，不算数据缺失
        val points =
            listOf(
                sample(0.0, 0),
                sample(5.0, 480),
            )

        assertTrue(detector.detect(points).isEmpty())
    }

    @Test
    fun `short gap with displacement is not a gap`() {
        // 5 分钟内移动 3 公里，虽然快但采样没断
        val points =
            listOf(
                sample(0.0, 0),
                sample(3000.0, 5),
            )

        assertTrue(detector.detect(points).isEmpty())
    }

    @Test
    fun `multiple gaps are all detected`() {
        val points =
            listOf(
                sample(0.0, 0),
                sample(20_000.0, 30),
                sample(40_000.0, 60),
            )

        val gaps = detector.detect(points)

        assertEquals(2, gaps.size)
    }
}
