package com.hmilyld.daytrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun `distance between same point is zero`() {
        val point = GeoPoint(31.2304, 121.4737)
        assertEquals(0.0, distanceMeters(point, point), 0.001)
    }

    @Test
    fun `distance shanghai to hangzhou is about 165 km`() {
        val shanghai = GeoPoint(31.2304, 121.4737)
        val hangzhou = GeoPoint(30.2741, 120.1551)

        val distance = distanceMeters(shanghai, hangzhou)

        // 实际直线距离约 165 km，允许 2 km 误差
        assertTrue("expected ~165km, got ${distance / 1000}", distance in 163_000.0..167_000.0)
    }

    @Test
    fun `distance is symmetric`() {
        val a = GeoPoint(39.9042, 116.4074)
        val b = GeoPoint(31.2304, 121.4737)

        assertEquals(distanceMeters(a, b), distanceMeters(b, a), 0.001)
    }

    @Test
    fun `distance across antimeridian takes short path`() {
        val west = GeoPoint(0.0, 179.9)
        val east = GeoPoint(0.0, -179.9)

        val distance = distanceMeters(west, east)

        // 0.2 度经度在赤道约 22 km，而不是绕地球 40 000 km
        assertTrue("expected ~22km, got ${distance / 1000}", distance in 21_000.0..23_000.0)
    }

    @Test
    fun `bearing due north is zero`() {
        val from = GeoPoint(30.0, 120.0)
        val to = GeoPoint(31.0, 120.0)
        assertEquals(0.0, bearingDegrees(from, to), 0.5)
    }

    @Test
    fun `bearing due east is ninety`() {
        val from = GeoPoint(0.0, 120.0)
        val to = GeoPoint(0.0, 121.0)
        assertEquals(90.0, bearingDegrees(from, to), 0.5)
    }

    @Test
    fun `bearing is always in range`() {
        val from = GeoPoint(30.0, 120.0)
        val to = GeoPoint(29.0, 119.0)
        val bearing = bearingDegrees(from, to)
        assertTrue("bearing $bearing out of range", bearing in 0.0..360.0)
    }

    @Test
    fun `null island is invalid`() {
        assertFalse(GeoPoint(0.0, 0.0).isValid)
    }

    @Test
    fun `out of range coordinates are invalid`() {
        assertFalse(GeoPoint(91.0, 120.0).isValid)
        assertFalse(GeoPoint(30.0, 181.0).isValid)
    }

    @Test
    fun `normal coordinate is valid`() {
        assertTrue(GeoPoint(31.2304, 121.4737).isValid)
    }

    @Test
    fun `polyline length sums segments`() {
        val points =
            listOf(
                GeoPoint(31.2304, 121.4737),
                GeoPoint(31.1000, 121.3000),
                GeoPoint(30.9000, 121.1000),
            )

        val total = polylineLengthMeters(points)
        val manual =
            distanceMeters(points[0], points[1]) + distanceMeters(points[1], points[2])

        assertEquals(manual, total, 0.001)
    }

    @Test
    fun `polyline with single point has zero length`() {
        assertEquals(0.0, polylineLengthMeters(listOf(GeoPoint(31.0, 121.0))), 0.0)
    }

    @Test
    fun `polyline with no points has zero length`() {
        assertEquals(0.0, polylineLengthMeters(emptyList()), 0.0)
    }
}
