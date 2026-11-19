package com.hmilyld.daytrail.location

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceProbeTest {
    @Test
    fun `inside the radius keeps watching`() {
        assertEquals(ProbeAction.WATCH, PlaceProbe.decide(35.0))
    }

    @Test
    fun `exactly on the radius is still inside`() {
        assertEquals(ProbeAction.WATCH, PlaceProbe.decide(200.0))
    }

    @Test
    fun `outside the radius wakes`() {
        assertEquals(ProbeAction.WAKE, PlaceProbe.decide(201.0))
    }

    @Test
    fun `no known place cannot prove we stayed`() {
        assertEquals(ProbeAction.WAKE, PlaceProbe.decide(null))
    }
}
