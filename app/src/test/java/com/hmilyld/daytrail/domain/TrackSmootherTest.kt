package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSmootherTest {
    private val baseLat = 31.2304
    private val baseLon = 121.4737

    private fun sample(
        metersNorth: Double,
        accuracy: Float,
    ): TrackPointSample =
        TrackPointSample(
            timestampMs = 1_000_000L,
            point = GeoPoint(baseLat + metersNorth / 111_320.0, baseLon),
            accuracyMeters = accuracy,
        )

    @Test
    fun `two points are returned unchanged`() {
        val points = listOf(sample(0.0, 10f), sample(200.0, 10f))

        val result = TrackSmoother.smooth(points)

        assertEquals(points.map { it.point }, result)
    }

    @Test
    fun `equal accuracy points are not moved`() {
        // 精度相同就不动，否则每个拐角都会被抹掉三分之一
        val points =
            listOf(
                sample(0.0, 10f),
                sample(300.0, 10f),
                sample(0.0, 10f),
            )

        val result = TrackSmoother.smooth(points)

        assertEquals(points.map { it.point }, result)
    }

    @Test
    fun `a much worse point is pulled toward its neighbours`() {
        // 中间点精度 300 米、偏离前后连线 300 米，应该被拉回附近
        val points =
            listOf(
                sample(0.0, 10f),
                sample(300.0, 300f),
                sample(0.0, 10f),
            )

        val result = TrackSmoother.smooth(points)

        val movedMeters = (result[1].latitude - baseLat) * 111_320.0
        assertTrue("应被拉回中线附近，实际 $movedMeters 米", movedMeters < 30.0)
    }

    @Test
    fun `endpoints are never moved`() {
        val points =
            listOf(
                sample(0.0, 500f),
                sample(100.0, 10f),
                sample(200.0, 500f),
            )

        val result = TrackSmoother.smooth(points)

        assertEquals(points.first().point, result.first())
        assertEquals(points.last().point, result.last())
    }
}
