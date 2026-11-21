package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceResolverTest {
    private val base = GeoPoint(31.2304, 121.4737)

    private fun north(meters: Double): GeoPoint =
        GeoPoint(base.latitude + meters / 111_320.0, base.longitude)

    @Test
    fun `matches the nearest candidate`() {
        val candidates =
            listOf(
                PlaceResolver.Candidate(id = 1L, point = north(100.0)),
                PlaceResolver.Candidate(id = 2L, point = north(10.0)),
            )

        val match = PlaceResolver.nearest(candidates, north(0.0))

        assertEquals(2L, match?.id)
    }

    @Test
    fun `no match when every candidate is out of range`() {
        val candidates =
            listOf(
                PlaceResolver.Candidate(id = 1L, point = north(200.0)),
                PlaceResolver.Candidate(id = 2L, point = north(500.0)),
            )

        assertNull(PlaceResolver.nearest(candidates, north(0.0)))
    }

    @Test
    fun `a place just inside the radius still matches`() {
        val candidates = listOf(PlaceResolver.Candidate(id = 1L, point = north(119.0)))

        assertEquals(1L, PlaceResolver.nearest(candidates, north(0.0))?.id)
    }

    @Test
    fun `a place just outside the radius does not match`() {
        val candidates = listOf(PlaceResolver.Candidate(id = 1L, point = north(121.0)))

        assertNull(PlaceResolver.nearest(candidates, north(0.0)))
    }

    @Test
    fun `empty candidate list yields no match`() {
        assertNull(PlaceResolver.nearest(emptyList(), north(0.0)))
    }

    /**
     * 合并过的地点半径被撑大，之后同一个小区里 260 米的停留也要归给它。
     * 这是「富田太阳城 / 富田太阳城3期」那类重复地点的修复前提。
     */
    @Test
    fun `a merged place matches inside its own wider radius`() {
        val candidates =
            listOf(
                PlaceResolver.Candidate(id = 1L, point = north(260.0), radiusMeters = 430.0),
            )

        assertEquals(1L, PlaceResolver.nearest(candidates, north(0.0))?.id)
    }

    @Test
    fun `a place with the default radius still rejects 260 meters`() {
        val candidates = listOf(PlaceResolver.Candidate(id = 1L, point = north(260.0)))

        assertNull(PlaceResolver.nearest(candidates, north(0.0)))
    }

    /** 半径大的地点不能抢走更近的地点：先比距离，再比是否在圈内。 */
    @Test
    fun `the nearest candidate wins even if a farther one has a wider radius`() {
        val candidates =
            listOf(
                PlaceResolver.Candidate(id = 1L, point = north(80.0)),
                PlaceResolver.Candidate(id = 2L, point = north(200.0), radiusMeters = 400.0),
            )

        assertEquals(1L, PlaceResolver.nearest(candidates, north(0.0))?.id)
    }
}
