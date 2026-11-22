package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackDisplayTest {
    private fun sample(
        metersNorth: Double,
        secondsLater: Long,
        accuracy: Float,
    ): TrackPointSample =
        TrackPointSample(
            timestampMs = 1_000_000L + secondsLater * 1000L,
            point = GeoPoint(31.2304 + metersNorth / 111_320.0, 121.4737),
            accuracyMeters = accuracy,
        )

    @Test
    fun `coarse points are excluded from the line`() {
        val points =
            listOf(
                sample(0.0, 0, 10f),
                // 2 公里误差的点，画上去就是一条乱线
                sample(5_000.0, 120, 2_000f),
                sample(10_000.0, 240, 10f),
            )

        val flat = TrackDisplay.segments(points).flatten()

        assertEquals(2, flat.size)
    }

    @Test
    fun `falls back to coarse points when nothing is precise`() {
        // 只给了「大致位置」时一个精确点都没有，也必须能看到轨迹
        val points =
            listOf(
                sample(0.0, 0, 2_000f),
                sample(1_000.0, 120, 2_000f),
                sample(2_000.0, 240, 2_000f),
            )

        val flat = TrackDisplay.segments(points).flatten()

        assertEquals(3, flat.size)
    }

    @Test
    fun `long gap with real displacement splits segments`() {
        val points =
            listOf(
                sample(0.0, 0, 10f),
                sample(1_000.0, 120, 10f),
                // 38 分钟后已经跑出 5 公里：中间确实没有数据
                sample(6_000.0, 2_400, 10f),
            )

        val segments = TrackDisplay.segments(points)

        assertEquals(2, segments.size)
    }

    @Test
    fun `empty input produces no segments`() {
        assertEquals(emptyList<List<GeoPoint>>(), TrackDisplay.segments(emptyList()))
    }

    @Test
    fun `stay window collapses to a single anchored point`() {
        // 停留段内三个点相距 10m：画出来就是一小团来回的线
        val points =
            listOf(
                sample(0.0, 0, 10f),
                sample(5.0, 60, 10f),
                sample(10.0, 120, 10f),
            )
        val anchor = GeoPoint(31.2304 + 8.0 / 111_320.0, 121.4737)
        val stay =
            StaySegment(
                startMs = 1_000_000L,
                endMs = 1_000_000L + 120_000L,
                point = anchor,
                radiusMeters = 20.0,
            )

        val flat = TrackDisplay.segments(points, listOf(stay)).flatten()

        assertEquals(1, flat.size)
        assertEquals(anchor, flat.first())
    }

    @Test
    fun `points after the stay window keep their position`() {
        val points =
            listOf(
                sample(0.0, 0, 10f),
                sample(5.0, 60, 10f),
                // 停留结束后已经开出 600 米
                sample(600.0, 900, 10f),
            )
        val anchor = GeoPoint(31.2304 + 8.0 / 111_320.0, 121.4737)
        val stay =
            StaySegment(
                startMs = 1_000_000L,
                endMs = 1_000_000L + 60_000L,
                point = anchor,
                radiusMeters = 20.0,
            )

        val flat = TrackDisplay.segments(points, listOf(stay)).flatten()

        assertEquals(2, flat.size)
        assertEquals(anchor, flat.first())
        // 8m 处的锚点到 600m 处的点，约 592m
        assertEquals(592.0, distanceMeters(flat.first(), flat.last()), 2.0)
    }

    // ---- 手动微调 ----

    private fun edit(
        segments: List<List<GeoPoint>>,
        cutoffMs: Long,
    ): TrackEdit =
        TrackEdit(
            segments = segments,
            cutoffMs = cutoffMs,
            updatedAtMs = 0L,
        )

    @Test
    fun `manual edit replaces the drawn line entirely`() {
        // 原始点是两段（中间有断点），编辑结果只保留用户画的那一条
        val points =
            listOf(
                sample(0.0, 0, 10f),
                sample(1_000.0, 120, 10f),
                sample(6_000.0, 2_400, 10f),
            )
        val edited = listOf(listOf(sample(0.0, 0, 10f).point, sample(900.0, 0, 10f).point))

        val segments =
            TrackDisplay.segments(points, edit = edit(edited, cutoffMs = 1_000_000L + 2_400_000L))

        assertEquals(edited, segments)
    }

    @Test
    fun `points recorded after the edit are appended as one continuous segment`() {
        val p0 = sample(0.0, 0, 10f)
        val p1 = sample(100.0, 60, 10f)
        val p2 = sample(200.0, 120, 10f)
        val p3 = sample(300.0, 180, 10f)
        val edited = listOf(listOf(p0.point, p1.point))

        val segments =
            TrackDisplay.segments(
                listOf(p0, p1, p2, p3),
                // 编辑基线只到 p1，p2/p3 是保存之后才采到的
                edit = edit(edited, cutoffMs = p1.timestampMs),
            )

        // 只差一分钟、相距一百多米：接上，不能画成两截
        assertEquals(1, segments.size)
        assertEquals(4, segments.first().size)
        assertEquals(edited.first().first(), segments.first().first())
        assertEquals(p3.point, segments.first().last())
    }

    @Test
    fun `a real gap after the edit stays a separate segment`() {
        val p0 = sample(0.0, 0, 10f)
        val p1 = sample(100.0, 60, 10f)
        // 一小时后又离了 6 公里：这是真的没采到点
        val p2 = sample(6_000.0, 3_660, 10f)
        val edited = listOf(listOf(p0.point, p1.point))

        val segments =
            TrackDisplay.segments(
                listOf(p0, p1, p2),
                edit = edit(edited, cutoffMs = p1.timestampMs),
            )

        assertEquals(2, segments.size)
    }
}
