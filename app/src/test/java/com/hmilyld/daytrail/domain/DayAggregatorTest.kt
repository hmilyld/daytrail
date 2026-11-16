package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayAggregatorTest {
    private val base = GeoPoint(31.2304, 121.4737)

    private fun sample(
        metersNorth: Double,
        secondsLater: Long,
    ): TrackPointSample =
        TrackPointSample(
            timestampMs = 1_000_000L + secondsLater * 1000L,
            point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude),
        )

    @Test
    fun `empty day has zero stats`() {
        val stats = DayAggregator.aggregate(emptyList())

        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(0, stats.pointCount)
        assertNull(stats.startedAt)
        assertNull(stats.endedAt)
    }

    @Test
    fun `single point has zero distance but keeps timestamps`() {
        val stats = DayAggregator.aggregate(listOf(sample(0.0, 0)))

        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(1, stats.pointCount)
        assertEquals(1_000_000L, stats.startedAt)
        assertEquals(1_000_000L, stats.endedAt)
    }

    @Test
    fun `distance sums consecutive segments`() {
        val points =
            listOf(
                sample(0.0, 0),
                sample(1000.0, 60),
                sample(2000.0, 120),
            )

        val stats = DayAggregator.aggregate(points)

        // 约 2000 米，允许 1% 误差
        assertTrue("distance=${stats.distanceMeters}", stats.distanceMeters in 1980.0..2020.0)
    }

    @Test
    fun `moving time counts when speed above threshold`() {
        val points =
            listOf(
                sample(0.0, 0),
                sample(1000.0, 60),
            )

        val stats = DayAggregator.aggregate(points)

        assertEquals(60_000L, stats.movingMillis)
        assertEquals(0L, stats.stoppedMillis)
    }

    @Test
    fun `stopped time counts when barely moving`() {
        val points =
            listOf(
                sample(0.0, 0),
                sample(1.0, 600),
            )

        val stats = DayAggregator.aggregate(points)

        assertEquals(0L, stats.movingMillis)
        assertEquals(600_000L, stats.stoppedMillis)
    }

    @Test
    fun `long gap is excluded from durations`() {
        val points =
            listOf(
                sample(0.0, 0),
                // 间隔 1 小时，超过 10 分钟阈值，时长不计入
                sample(5000.0, 3600),
            )

        val stats = DayAggregator.aggregate(points)

        assertEquals(0L, stats.movingMillis)
        assertEquals(0L, stats.stoppedMillis)
        // 距离仍然累加，因为确实移动了
        assertTrue(stats.distanceMeters > 4000.0)
    }

    @Test
    fun `timestamps reflect first and last point`() {
        val points =
            listOf(
                sample(0.0, 100),
                sample(500.0, 200),
                sample(1000.0, 300),
            )

        val stats = DayAggregator.aggregate(points)

        assertEquals(1_000_000L + 100_000L, stats.startedAt)
        assertEquals(1_000_000L + 300_000L, stats.endedAt)
        assertEquals(3, stats.pointCount)
    }

    @Test
    fun `jitter below accuracy does not add distance`() {
        // 静止不动，但基站定位每次漂移 200 米。
        // 如果直接累加，坐着不动也能凭空多出几公里。
        val points =
            listOf(
                TrackPointSample(1_000_000L, base, accuracyMeters = 300f),
                TrackPointSample(
                    1_060_000L,
                    GeoPoint(base.latitude + 200.0 / 111_320.0, base.longitude),
                    300f,
                ),
                TrackPointSample(
                    1_120_000L,
                    GeoPoint(base.latitude - 150.0 / 111_320.0, base.longitude),
                    300f,
                ),
            )

        val stats = DayAggregator.aggregate(points)

        assertEquals("位移小于定位误差不应计入里程", 0.0, stats.distanceMeters, 0.001)
    }

    @Test
    fun `movement above accuracy adds distance`() {
        // 真实移动 2 公里，精度 300 米：位移远大于误差，应计入
        val points =
            listOf(
                TrackPointSample(1_000_000L, base, accuracyMeters = 300f),
                TrackPointSample(
                    1_300_000L,
                    GeoPoint(base.latitude + 2000.0 / 111_320.0, base.longitude),
                    300f,
                ),
            )

        val stats = DayAggregator.aggregate(points)

        assertTrue(
            "expected ~2000m, got ${stats.distanceMeters}",
            stats.distanceMeters in 1950.0..2050.0,
        )
    }

    @Test
    fun `precise points still accumulate small segments`() {
        // 精度 5 米的点，100 米位移应当计入
        val points =
            listOf(
                TrackPointSample(1_000_000L, base, accuracyMeters = 5f),
                TrackPointSample(
                    1_060_000L,
                    GeoPoint(base.latitude + 100.0 / 111_320.0, base.longitude),
                    5f,
                ),
            )

        val stats = DayAggregator.aggregate(points)

        assertTrue(stats.distanceMeters > 90.0)
    }
}
