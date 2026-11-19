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

    @Test
    fun `single outlier does not split a long stay`() {
        // 20 分钟停留，第 10 分钟有一个 150m 的漂移点。
        // 旧实现遇到它就收尾，一段停留会被切成两段。
        val points =
            (0..19).map { minute ->
                val drift = if (minute == 10) 150.0 else 0.0
                point(metersNorth = drift, minutesLater = minute.toLong())
            }

        val stays = detector.detect(points)

        assertEquals(1, stays.size)
        assertEquals(1_000_000L + 19 * 60_000L, stays.first().endMs)
    }

    @Test
    fun `two consecutive outliers end the stay at the last inside point`() {
        val points =
            (0..9).map { point(metersNorth = 0.0, minutesLater = it.toLong()) } +
                (10..11).map { point(metersNorth = 150.0, minutesLater = it.toLong()) } +
                (12..14).map { point(metersNorth = 0.0, minutesLater = it.toLong()) }

        val stays = detector.detect(points)

        assertEquals(1, stays.size)
        // 结束时间是最后一个圈内点，不是确认离开的那个点
        assertEquals(1_000_000L + 9 * 60_000L, stays.first().endMs)
    }

    @Test
    fun `outlier is excluded from the centroid`() {
        val points =
            listOf(
                point(0.0, 0),
                point(0.0, 3),
                point(0.0, 6),
                point(150.0, 9), // 单个漂移点，跨过它继续
                point(0.0, 12),
            )

        val stays = detector.detect(points)

        assertEquals(1, stays.size)
        // 质心应贴近 0 米那四个点，不被 150m 的点拉走
        assertTrue(stays.first().point.latitude < base.latitude + 10.0 / 111_320.0)
    }

    @Test
    fun `an edge anchor does not split the stay`() {
        // 首点落在停留区边缘（离质心约 38m）。一个 150m 的漂移点之后，
        // 紧接着一个离首点 85m、离质心只有 46m 的点——旧实现会把它算成
        // 连续第二个出圈点，把一段停留切成两段。
        val points =
            listOf(
                point(0.0, 0),
                point(40.0, 2),
                point(45.0, 4),
                point(50.0, 6),
                point(40.0, 8),
                point(45.0, 10),
                point(50.0, 12),
                point(150.0, 14),
                point(85.0, 16),
                point(45.0, 18),
                point(50.0, 20),
                point(40.0, 22),
                point(45.0, 24),
            )

        val stays = detector.detect(points)

        assertEquals(1, stays.size)
        assertEquals(1_000_000L + 24 * 60_000L, stays.first().endMs)
    }
}
