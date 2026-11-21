package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class StayMarkersTest {
    private val home = GeoPoint(34.6754, 113.8057)
    private val office = GeoPoint(34.7728, 113.7491)

    /** 实机数据：11-21 出门前 00:01–13:19、回来后 16:39–18:30 都在家。 */
    private val longEnough = TrackDisplay.MIN_STAY_MARKER_MS + 60_000

    @Test
    fun `two visits to the same place yield one marker`() {
        val stays =
            listOf(
                StayMarkers.Stay(placeId = 2L, point = home, durationMs = 798 * 60_000L),
                StayMarkers.Stay(placeId = 2L, point = home, durationMs = 110 * 60_000L),
            )

        assertEquals(listOf(home), StayMarkers.plan(stays).markers)
    }

    /**
     * 起终点是否已被停留代表，看的是**当次质心**而不是锚点：
     * 合并过的大地点锚点可能在几百米外，只看锚点会让起终点重新冒出来。
     */
    @Test
    fun `centroids keep every visit even when the marker sits on the anchor`() {
        val anchor = office
        val plan =
            StayMarkers.plan(
                listOf(
                    StayMarkers.Stay(
                        placeId = 7L,
                        point = home,
                        durationMs = longEnough,
                        anchor = anchor,
                    ),
                ),
            )

        assertEquals(listOf(anchor), plan.markers)
        assertEquals(listOf(home), plan.centroids)
    }

    @Test
    fun `different places keep their own markers in first-seen order`() {
        val stays =
            listOf(
                StayMarkers.Stay(placeId = 2L, point = home, durationMs = longEnough),
                StayMarkers.Stay(placeId = 7L, point = office, durationMs = longEnough),
                StayMarkers.Stay(placeId = 2L, point = home, durationMs = longEnough),
            )

        val plan = StayMarkers.plan(stays)

        assertEquals(listOf(home, office), plan.markers)
        // 质心按停留段数保留：三段停留，不是两个地点
        assertEquals(listOf(home, office, home), plan.centroids)
    }

    @Test
    fun `stays without a place stay separate`() {
        val other = GeoPoint(34.6760, 113.8060)
        val stays =
            listOf(
                StayMarkers.Stay(placeId = null, point = home, durationMs = longEnough),
                StayMarkers.Stay(placeId = null, point = other, durationMs = longEnough),
            )

        assertEquals(listOf(home, other), StayMarkers.plan(stays).markers)
    }

    @Test
    fun `short stays are skipped`() {
        val stays =
            listOf(
                StayMarkers.Stay(placeId = 2L, point = home, durationMs = 60_000),
            )

        val plan = StayMarkers.plan(stays)

        assertEquals(emptyList<GeoPoint>(), plan.markers)
        assertEquals(emptyList<GeoPoint>(), plan.centroids)
    }
}
