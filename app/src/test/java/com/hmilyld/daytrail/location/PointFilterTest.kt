package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointFilterTest {
    private val filter = PointFilter()

    private fun sample(
        metersNorth: Double = 0.0,
        secondsLater: Long = 0,
        accuracy: Float = 10f,
        point: GeoPoint? = null,
    ): TrackSample {
        val geo =
            point ?: GeoPoint(31.2304 + metersNorth / 111_320.0, 121.4737)
        return TrackSample(
            timestampMs = 1_000_000L + secondsLater * 1000L,
            point = geo,
            accuracyMeters = accuracy,
            speedMps = null,
            bearingDegrees = null,
            isMock = false,
        )
    }

    @Test
    fun `first point is accepted`() {
        assertTrue(filter.judge(null, sample()) is FilterVerdict.Accept)
    }

    @Test
    fun `null island is rejected`() {
        val verdict = filter.judge(null, sample(point = GeoPoint(0.0, 0.0)))
        assertEquals(FilterVerdict.Reject(RejectReason.INVALID_COORDINATE), verdict)
    }

    @Test
    fun `out of range coordinate is rejected`() {
        val verdict = filter.judge(null, sample(point = GeoPoint(91.0, 121.0)))
        assertEquals(FilterVerdict.Reject(RejectReason.INVALID_COORDINATE), verdict)
    }

    @Test
    fun `poor accuracy is rejected`() {
        // 阈值 200 米：更差的点会让里程明显虚高
        val verdict = filter.judge(null, sample(accuracy = 250f))
        assertEquals(FilterVerdict.Reject(RejectReason.POOR_ACCURACY), verdict)
    }

    @Test
    fun `accuracy at boundary is accepted`() {
        assertTrue(filter.judge(null, sample(accuracy = 200f)) is FilterVerdict.Accept)
    }

    @Test
    fun `coarse location accuracy is accepted`() {
        // 基站定位常见精度 150 米，必须接受，否则室内几乎采不到点
        assertTrue(filter.judge(null, sample(accuracy = 150f)) is FilterVerdict.Accept)
    }

    @Test
    fun `duplicate point within window is rejected`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val candidate = sample(metersNorth = 1.0, secondsLater = 10)

        val verdict = filter.judge(previous, candidate)

        assertEquals(FilterVerdict.Reject(RejectReason.DUPLICATE), verdict)
    }

    @Test
    fun `same location after long window is accepted`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val candidate = sample(metersNorth = 1.0, secondsLater = 600)

        assertTrue(filter.judge(previous, candidate) is FilterVerdict.Accept)
    }

    @Test
    fun `teleport is rejected`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        // 10 秒移动 10 km = 3600 km/h
        val candidate = sample(metersNorth = 10_000.0, secondsLater = 10)

        val verdict = filter.judge(previous, candidate)

        assertEquals(FilterVerdict.Reject(RejectReason.TELEPORT), verdict)
    }

    @Test
    fun `out of order timestamp is rejected`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 100)
        val candidate = sample(metersNorth = 50.0, secondsLater = 50)

        val verdict = filter.judge(previous, candidate)

        assertEquals(FilterVerdict.Reject(RejectReason.OUT_OF_ORDER), verdict)
    }

    @Test
    fun `normal movement is accepted`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        // 60 秒走 200 米，约 12 km/h
        val candidate = sample(metersNorth = 200.0, secondsLater = 60)

        assertTrue(filter.judge(previous, candidate) is FilterVerdict.Accept)
    }

    @Test
    fun `driving speed is accepted`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        // 60 秒跑 1500 米 = 90 km/h
        val candidate = sample(metersNorth = 1500.0, secondsLater = 60)

        assertTrue(filter.judge(previous, candidate) is FilterVerdict.Accept)
    }
}
