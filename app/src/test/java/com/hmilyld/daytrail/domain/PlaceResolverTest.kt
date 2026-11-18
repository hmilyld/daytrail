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
}
