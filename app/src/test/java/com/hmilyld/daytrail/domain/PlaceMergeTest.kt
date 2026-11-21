package com.hmilyld.daytrail.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMergeTest {
    @Test
    fun `grows the radius to cover both anchors`() {
        val radius =
            PlaceMerge.mergedRadius(
                toRadiusMeters = 120.0,
                distanceMeters = 260.0,
                fromRadiusMeters = 120.0,
            )

        assertEquals(260.0 + 120.0 + PlaceMerge.MARGIN_METERS, radius, 0.001)
    }

    /** 合并结果必须能在下一次重算里把源地点的停留判回目标，否则合并会被打回。 */
    @Test
    fun `the merged radius always covers the source centroid`() {
        val distance = 260.0
        val radius = PlaceMerge.mergedRadius(120.0, distance, 120.0)

        assertTrue(radius >= distance)
    }

    @Test
    fun `never shrinks an already wider place`() {
        assertEquals(310.0, PlaceMerge.mergedRadius(310.0, 10.0, 120.0), 0.001)
    }

    @Test
    fun `caps the radius so a merge cannot swallow the whole district`() {
        assertEquals(
            PlaceMerge.MAX_RADIUS_METERS,
            PlaceMerge.mergedRadius(120.0, 2_000.0, 120.0),
            0.001,
        )
        assertFalse(PlaceMerge.isCoverable(2_000.0))
    }

    /**
     * 候选列表能给多远，就必须保证那个距离上的合并站得住。
     * 0.3.2 审计发现过 1000 / 500 不一致：500 米以外的合并会被下一次重算重建出来。
     */
    @Test
    fun `every pickable distance ends up covered by the merged radius`() {
        var distance = 0.0
        while (distance <= PlaceNeighbors.PICK_RADIUS_METERS) {
            val radius = PlaceMerge.mergedRadius(120.0, distance, 120.0)
            assertTrue("distance=$distance radius=$radius", radius >= distance)
            assertTrue(PlaceMerge.isCoverable(distance))
            distance += 10.0
        }
    }

    @Test
    fun `the picker never offers a distance the cap cannot cover`() {
        assertTrue(PlaceNeighbors.PICK_RADIUS_METERS <= PlaceMerge.MAX_DISTANCE_METERS)
    }
}
