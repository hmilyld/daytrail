package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSegmenterTest {
    private val base = GeoPoint(34.0409830, 114.3816850)

    private fun sample(
        metersNorth: Double,
        secondsLater: Long,
        accuracy: Float = 20f,
    ): TrackPointSample =
        TrackPointSample(
            timestampMs = 1_794_672_000_000L + secondsLater * 1000L,
            point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude),
            accuracyMeters = accuracy,
        )

    @Test
    fun `empty input yields no segments`() {
        assertTrue(TrackSegmenter.segment(emptyList()).isEmpty())
    }

    @Test
    fun `single point yields one segment`() {
        val segments = TrackSegmenter.segment(listOf(sample(0.0, 0)))

        assertEquals(1, segments.size)
        assertEquals(1, segments.first().size)
    }

    @Test
    fun `sparse but continuous sampling stays one segment`() {
        // 回归用例：省电档 3 分钟一个点、每次移动 3 公里。
        // 这正是用户导出的真实形态（25 点 / 75 公里 / 平均间距 3.1 公里）。
        // 按「距离 > 2km 就切」会碎成十几段，所以必须配合时间判断。
        val points =
            (0..24).map { index ->
                sample(metersNorth = index * 3000.0, secondsLater = index * 180L)
            }

        val segments = TrackSegmenter.segment(points)

        assertEquals("连续驾驶不应被切碎", 1, segments.size)
        assertEquals(25, segments.first().size)
    }

    @Test
    fun `long time gap with displacement splits`() {
        // 30 分钟无数据且移动了 20 公里：真实的数据缺失（隧道、关机）
        val points =
            listOf(
                sample(0.0, 0),
                sample(20_000.0, 30 * 60),
            )

        val segments = TrackSegmenter.segment(points)

        assertEquals(2, segments.size)
    }

    @Test
    fun `long time gap without displacement does not split`() {
        // 手机放桌上静止一晚：时间很长但没移动，不是数据缺失
        val points =
            listOf(
                sample(0.0, 0),
                sample(5.0, 8 * 3600),
            )

        val segments = TrackSegmenter.segment(points)

        assertEquals(1, segments.size)
    }

    @Test
    fun `large displacement within short time does not split`() {
        // 5 分钟跑 4 公里（开车），采样没断，不该切
        val points =
            listOf(
                sample(0.0, 0),
                sample(4_000.0, 5 * 60),
            )

        val segments = TrackSegmenter.segment(points)

        assertEquals(1, segments.size)
    }

    @Test
    fun `multiple gaps produce multiple segments`() {
        val points =
            listOf(
                sample(0.0, 0),
                sample(100.0, 180),
                sample(20_000.0, 40 * 60),
                sample(20_100.0, 43 * 60),
                sample(40_000.0, 80 * 60),
            )

        val segments = TrackSegmenter.segment(points)

        assertEquals(3, segments.size)
        assertEquals(listOf(2, 2, 1), segments.map { it.size })
    }

    @Test
    fun `flatten restores original order`() {
        val points =
            listOf(
                sample(0.0, 0),
                sample(100.0, 180),
                sample(20_000.0, 40 * 60),
            )
        val segments = TrackSegmenter.segment(points)

        val flat = TrackSegmenter.flatten(segments)

        assertEquals(3, flat.size)
        assertEquals(points.first().point, flat.first())
        assertEquals(points.last().point, flat.last())
    }
}
