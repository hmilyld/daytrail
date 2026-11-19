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
        val verdict = filter.judge(null, sample(accuracy = 6000f))
        assertEquals(FilterVerdict.Reject(RejectReason.POOR_ACCURACY), verdict)
    }

    @Test
    fun `accuracy at boundary is accepted`() {
        assertTrue(filter.judge(null, sample(accuracy = 5000f)) is FilterVerdict.Accept)
    }

    @Test
    fun `coarse location accuracy is accepted`() {
        // 基站定位常见精度 150 米，必须接受，否则室内几乎采不到点
        assertTrue(filter.judge(null, sample(accuracy = 150f)) is FilterVerdict.Accept)
    }

    @Test
    fun `very coarse accuracy is still accepted`() {
        // 地下车库、地铁里只有基站定位，精度 500–900 米也要记下来：
        // 用户要的是「大概去了哪」，不是精确轨迹
        assertTrue(filter.judge(null, sample(accuracy = 3000f)) is FilterVerdict.Accept)
    }

    @Test
    fun `movement smaller than accuracy is treated as noise`() {
        // 位移 50 米但精度 300 米：分不清真的动了还是信号漂移
        val previous = sample(metersNorth = 0.0, secondsLater = 0, accuracy = 300f)
        val candidate = sample(metersNorth = 50.0, secondsLater = 60, accuracy = 300f)

        val verdict = filter.judge(previous, candidate)

        assertEquals(FilterVerdict.Reject(RejectReason.WITHIN_NOISE), verdict)
    }

    @Test
    fun `movement larger than accuracy is accepted`() {
        // 位移 500 米、精度 300 米：可以确认真的移动了
        val previous = sample(metersNorth = 0.0, secondsLater = 0, accuracy = 300f)
        val candidate = sample(metersNorth = 500.0, secondsLater = 300, accuracy = 300f)

        assertTrue(filter.judge(previous, candidate) is FilterVerdict.Accept)
    }

    @Test
    fun `stationary heartbeat is kept even within noise`() {
        // 静止不动时按心跳间隔保留一个点，否则界面会长时间显示「0 个点」
        val previous = sample(metersNorth = 0.0, secondsLater = 0, accuracy = 300f)
        val candidate = sample(metersNorth = 5.0, secondsLater = 11 * 60, accuracy = 300f)

        assertTrue(filter.judge(previous, candidate) is FilterVerdict.Accept)
    }

    @Test
    fun `still interval point is not swallowed by the heartbeat`() {
        // 静止档每 10 分钟一个点。心跳必须比它短，否则每个点都会被当成噪声丢掉，
        // 界面看起来像没在记录。
        val previous = sample(metersNorth = 0.0, secondsLater = 0, accuracy = 30f)
        val candidate = sample(metersNorth = 2.0, secondsLater = 601, accuracy = 30f)

        assertTrue(filter.judge(previous, candidate) is FilterVerdict.Accept)
    }

    @Test
    fun `duplicate point within window is rejected`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val candidate = sample(metersNorth = 1.0, secondsLater = 10)

        val verdict = filter.judge(previous, candidate)

        assertEquals(FilterVerdict.Reject(RejectReason.WITHIN_NOISE), verdict)
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
    fun `a duplicate delivery within seconds is rejected`() {
        // 实测：唤醒后 requestOnce 与常驻订阅几乎同时各给一个点，4 秒、45 米
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val candidate = sample(metersNorth = 45.0, secondsLater = 4)

        val verdict = filter.judge(previous, candidate)

        assertEquals(FilterVerdict.Reject(RejectReason.WITHIN_NOISE), verdict)
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
