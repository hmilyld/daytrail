package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSimplifierTest {
    private val base = GeoPoint(31.2304, 121.4737)

    private fun point(
        metersNorth: Double,
        metersEast: Double = 0.0,
    ): GeoPoint =
        GeoPoint(
            latitude = base.latitude + metersNorth / 111_320.0,
            longitude = base.longitude + metersEast / (111_320.0 * 0.855),
        )

    @Test
    fun `two points are kept as is`() {
        val points = listOf(point(0.0), point(100.0))

        assertEquals(points, TrackSimplifier.simplify(points))
    }

    @Test
    fun `single point is kept`() {
        val points = listOf(point(0.0))

        assertEquals(points, TrackSimplifier.simplify(points))
    }

    @Test
    fun `empty list stays empty`() {
        assertTrue(TrackSimplifier.simplify(emptyList()).isEmpty())
    }

    @Test
    fun `collinear points collapse to endpoints`() {
        // 一条向北的直线，中间点全部共线
        val points = (0..10).map { point(it * 50.0) }

        val simplified = TrackSimplifier.simplify(points)

        assertEquals(2, simplified.size)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun `sharp corner is preserved`() {
        // L 形：向北走再向东走，拐点必须保留
        val points =
            listOf(
                point(0.0, 0.0),
                point(500.0, 0.0),
                point(500.0, 500.0),
            )

        val simplified = TrackSimplifier.simplify(points)

        assertEquals(3, simplified.size)
    }

    @Test
    fun `small deviation is removed`() {
        // 中间点只偏离 1 米，低于 5 米阈值
        val points =
            listOf(
                point(0.0, 0.0),
                point(500.0, 1.0),
                point(1000.0, 0.0),
            )

        val simplified = TrackSimplifier.simplify(points)

        assertEquals(2, simplified.size)
    }

    @Test
    fun `larger deviation is kept`() {
        // 中间点偏离 20 米，超过阈值
        val points =
            listOf(
                point(0.0, 0.0),
                point(500.0, 20.0),
                point(1000.0, 0.0),
            )

        val simplified = TrackSimplifier.simplify(points)

        assertEquals(3, simplified.size)
    }

    @Test
    fun `tolerance controls aggressiveness`() {
        val points =
            listOf(
                point(0.0, 0.0),
                point(500.0, 10.0),
                point(1000.0, 0.0),
            )

        assertEquals(2, TrackSimplifier.simplify(points, toleranceMeters = 20.0).size)
        assertEquals(3, TrackSimplifier.simplify(points, toleranceMeters = 5.0).size)
    }

    @Test
    fun `endpoints are always kept`() {
        val points = (0..20).map { point(it * 30.0, (it % 3) * 2.0) }

        val simplified = TrackSimplifier.simplify(points)

        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun `split by gaps separates distant segments`() {
        // 第一段 3 个点，然后跳到 10 公里外再来 3 个点
        val points =
            listOf(
                point(0.0, 0.0),
                point(100.0, 0.0),
                point(200.0, 0.0),
                point(10_000.0, 0.0),
                point(10_100.0, 0.0),
                point(10_200.0, 0.0),
            )

        val segments = TrackSimplifier.splitByGaps(points, maxGapMeters = 2_000.0)

        assertEquals(2, segments.size)
        assertEquals(3, segments[0].size)
        assertEquals(3, segments[1].size)
    }

    @Test
    fun `split by gaps keeps single segment when continuous`() {
        val points = (0..5).map { point(it * 100.0) }

        val segments = TrackSimplifier.splitByGaps(points, maxGapMeters = 2_000.0)

        assertEquals(1, segments.size)
        assertEquals(6, segments[0].size)
    }

    @Test
    fun `split by gaps handles single point`() {
        val points = listOf(point(0.0))

        val segments = TrackSimplifier.splitByGaps(points)

        assertEquals(1, segments.size)
        assertEquals(1, segments[0].size)
    }

    @Test
    fun `split by gaps handles empty input`() {
        assertTrue(TrackSimplifier.splitByGaps(emptyList()).isEmpty())
    }
}
