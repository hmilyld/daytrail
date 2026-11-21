package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNeighborsTest {
    private val base = GeoPoint(34.6754, 113.8057)

    private fun north(meters: Double): GeoPoint =
        GeoPoint(base.latitude + meters / 111_320.0, base.longitude)

    @Test
    fun `neighbors are returned by distance and exclude the place itself`() {
        val entries =
            listOf(
                PlaceNeighbors.Entry(1L, base),
                PlaceNeighbors.Entry(2L, north(260.0)),
                PlaceNeighbors.Entry(3L, north(90.0)),
            )

        val neighbors = PlaceNeighbors.around(entries, fromId = 1L)

        assertEquals(listOf(3L, 2L), neighbors.map { it.id })
        assertEquals(90.0, neighbors.first().distanceMeters, 1.0)
    }

    @Test
    fun `places beyond the radius are ignored`() {
        val entries =
            listOf(
                PlaceNeighbors.Entry(1L, base),
                PlaceNeighbors.Entry(2L, north(900.0)),
            )

        assertTrue(PlaceNeighbors.around(entries, fromId = 1L).isEmpty())
    }

    /** 实测那三组重复分别是 163 m、260 m、334 m，都要报出来。 */
    @Test
    fun `suspects mark both sides of every close pair`() {
        val entries =
            listOf(
                PlaceNeighbors.Entry(1L, base),
                PlaceNeighbors.Entry(2L, north(163.0)),
                PlaceNeighbors.Entry(3L, north(1_600.0)),
            )

        val suspects = PlaceNeighbors.suspects(entries)

        assertTrue(suspects.containsAll(listOf(1L, 2L)))
        assertFalse(suspects.contains(3L))
    }

    @Test
    fun `a lone place is not a suspect`() {
        val entries = listOf(PlaceNeighbors.Entry(1L, base))

        assertTrue(PlaceNeighbors.suspects(entries).isEmpty())
    }

    /**
     * 合并过的地点半径会被撑大（最多 500 米），落在它半径里的邻居按匹配规则
     * 本来就会归给它；再标「疑似重复」只会诱导用户做一次没必要的合并。
     */
    @Test
    fun `a place already inside another ones radius is not a suspect`() {
        val entries =
            listOf(
                PlaceNeighbors.Entry(1L, base, radiusMeters = 430.0),
                PlaceNeighbors.Entry(2L, north(300.0), radiusMeters = 120.0),
            )

        assertTrue(PlaceNeighbors.suspects(entries).isEmpty())
    }

    @Test
    fun `pairs beyond every radius are still suspects`() {
        val entries =
            listOf(
                PlaceNeighbors.Entry(1L, base, radiusMeters = 120.0),
                PlaceNeighbors.Entry(2L, north(163.0), radiusMeters = 120.0),
            )

        assertEquals(setOf(1L, 2L), PlaceNeighbors.suspects(entries))
    }
}
