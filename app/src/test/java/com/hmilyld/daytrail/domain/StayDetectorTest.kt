package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StayDetectorTest {
    private val detector = StayDetector()

    private val base = GeoPoint(31.2304, 121.4737)

    private fun point(
        metersNorth: Double,
        minutesLater: Long,
    ): StayDetector.TimedPoint =
        StayDetector.TimedPoint(
            timestampMs = 1_000_000L + minutesLater * 60_000L,
            point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude),
        )

    @Test
    fun `empty input returns nothing`() {
        assertTrue(detector.detect(emptyList()).isEmpty())
    }

    @Test
    fun `single point returns nothing`() {
        assertTrue(detector.detect(listOf(point(0.0, 0))).isEmpty())
    }

    @Test
    fun `short stay is ignored`() {
        // 5 分钟，短于 8 分钟阈值
        val points = (0..5).map { point(metersNorth = 0.0, minutesLater = it.toLong()) }

        assertTrue(detector.detect(points).isEmpty())
    }

    @Test
    fun `stay of eight minutes is detected`() {
        val points = (0..8).map { point(metersNorth = 0.0, minutesLater = it.toLong()) }

        val stays = detector.detect(points)

        assertEquals(1, stays.size)
        assertEquals(8 * 60_000L, stays.first().durationMs)
    }

    @Test
    fun `movement outside radius is not a stay`() {
        // 每分钟走 200 米，远超 80m 半径
        val points = (0..10).map { point(metersNorth = it * 200.0, minutesLater = it.toLong()) }

        assertTrue(detector.detect(points).isEmpty())
    }

    @Test
    fun `centroid is used as stay point`() {
        // 在半径内小幅漂移
        val points =
            listOf(
                point(0.0, 0),
                point(20.0, 3),
                point(10.0, 6),
                point(30.0, 9),
            )

        val stays = detector.detect(points)

        assertEquals(1, stays.size)
        val centroid = stays.first().point
        // 质心纬度应落在 0~30m 之间
        assertTrue(centroid.latitude > base.latitude)
        assertTrue(centroid.latitude < base.latitude + 30.0 / 111_320.0)
    }

    @Test
    fun `two separate stays are both detected`() {
        val first = (0..8).map { point(metersNorth = 0.0, minutesLater = it.toLong()) }
        // 走远 5 公里后停下
        val second =
            (20..28).map { point(metersNorth = 5000.0, minutesLater = it.toLong()) }

        val stays = detector.detect(first + second)

        assertEquals(2, stays.size)
    }

    @Test
    fun `stays do not overlap`() {
        val first = (0..8).map { point(metersNorth = 0.0, minutesLater = it.toLong()) }
        val second =
            (20..28).map { point(metersNorth = 5000.0, minutesLater = it.toLong()) }

        val stays = detector.detect(first + second)

        assertEquals(2, stays.size)
        assertTrue(stays[0].endMs < stays[1].startMs)
    }
}
