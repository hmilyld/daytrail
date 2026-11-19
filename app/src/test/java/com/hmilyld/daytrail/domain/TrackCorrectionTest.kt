package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackCorrectionTest {
    private val base = GeoPoint(31.2304, 121.4737)

    private fun sample(
        metersNorth: Double,
        secondsLater: Long,
    ): TrackPointSample =
        TrackPointSample(
            timestampMs = 1_000_000L + secondsLater * 1000L,
            point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude),
            accuracyMeters = 10f,
        )

    @Test
    fun `walking is not part of a driving run`() {
        val samples =
            listOf(
                sample(0.0, 0),
                sample(10.0, 60), // 0.17 m/s，走路
                sample(1_010.0, 120), // 16.7 m/s，驾车
                sample(2_010.0, 180),
                sample(2_020.0, 240), // 又停下
            )

        val runs = TrackCorrection.drivingRuns(samples)

        assertEquals(1, runs.size)
        assertEquals(3, runs.first().size)
    }

    @Test
    fun `a long pause splits the run`() {
        val samples =
            listOf(
                sample(0.0, 0),
                sample(1_000.0, 60),
                // 5 分钟后才继续：中间没有数据，不能当成一段连续轨迹
                sample(2_000.0, 360),
                sample(3_000.0, 420),
            )

        val runs = TrackCorrection.drivingRuns(samples)

        assertEquals(2, runs.size)
    }

    @Test
    fun `request points use an absolute start and relative offsets`() {
        val samples = listOf(sample(0.0, 0), sample(1_000.0, 60))

        val run = TrackCorrection.drivingRuns(samples).single()
        val points = TrackCorrection.requestPoints(run)

        assertEquals(2, points.size)
        assertEquals(1_000L, points[0].timeSeconds)
        assertEquals(60L, points[1].timeSeconds)
    }

    @Test
    fun `speed is converted to kilometres per hour`() {
        val samples = listOf(sample(0.0, 0), sample(1_000.0, 60))

        val points = TrackCorrection.requestPoints(TrackCorrection.drivingRuns(samples).single())

        // 1000 m / 60 s = 60 km/h
        assertEquals(60.0, points[0].speedKmh, 0.5)
    }

    @Test
    fun `bearing follows the direction of travel`() {
        val samples = listOf(sample(0.0, 0), sample(1_000.0, 60))

        val points = TrackCorrection.requestPoints(TrackCorrection.drivingRuns(samples).single())

        // 正北
        assertEquals(0, points[0].bearingDegrees)
    }

    @Test
    fun `coordinates are rounded to six decimals`() {
        val samples = listOf(sample(0.0, 0), sample(1_000.0, 60))

        val points = TrackCorrection.requestPoints(TrackCorrection.drivingRuns(samples).single())

        val decimals = points[0].latitude.toString().substringAfter('.', "")
        assertTrue("小数位=$decimals", decimals.length <= 6)
    }

    @Test
    fun `runs longer than the request limit are chunked`() {
        val samples = (0..1_199).map { index -> sample(index * 1_000.0, index * 60L) }

        val run = TrackCorrection.drivingRuns(samples).single()
        val chunks = TrackCorrection.chunks(run)

        assertEquals(3, chunks.size)
        assertEquals(500, chunks[0].size)
        assertEquals(500, chunks[1].size)
        assertEquals(200, chunks[2].size)
    }

    @Test
    fun `a single sample produces no run`() {
        assertTrue(TrackCorrection.drivingRuns(listOf(sample(0.0, 0))).isEmpty())
    }

    @Test
    fun `a long straight jump splits the corrected line`() {
        // 实测：接口在匹配不上的一段会直接连一条 6.6 公里的直线
        val near = geo(0.0)
        val near2 = geo(10.0)
        val far = geo(6_600.0)
        val far2 = geo(6_610.0)

        val segments = TrackCorrection.splitAtLongJumps(listOf(near, near2, far, far2))

        assertEquals(2, segments.size)
        assertEquals(2, segments[0].size)
        assertEquals(2, segments[1].size)
    }

    @Test
    fun `a lone point left by a split is dropped`() {
        val segments =
            TrackCorrection.splitAtLongJumps(
                listOf(geo(0.0), geo(10.0), geo(6_600.0)),
            )

        assertEquals(1, segments.size)
        assertEquals(2, segments.first().size)
    }

    private fun geo(metersNorth: Double): GeoPoint =
        GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude)
}
