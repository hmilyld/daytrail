package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手动微调的几何操作。
 *
 * 这些都是纯函数：不可变、无 Android 依赖，最容易在模拟器上验证不了的地方
 * （x86_64 上地图不显示）靠它们兜住正确性。
 */
class TrackEditOpsTest {
    private val base = GeoPoint(31.2304, 121.4737)

    private fun north(meters: Double) = GeoPoint(base.latitude + meters / 111_320.0, base.longitude)

    private fun east(meters: Double) = GeoPoint(base.latitude, base.longitude + meters / 95_000.0)

    @Test
    fun `baseline drops collinear vertices`() {
        // 三个点在同一条南北线上：抽稀后只剩首尾
        val segments = listOf(listOf(north(0.0), north(1.0), north(2.0)))

        val baseline = TrackEditOps.baseline(segments)

        assertEquals(1, baseline.size)
        assertEquals(2, baseline.first().size)
    }

    @Test
    fun `baseline keeps corners`() {
        val segments = listOf(listOf(north(0.0), north(100.0), east(100.0)))

        val baseline = TrackEditOps.baseline(segments)

        assertEquals(3, baseline.first().size)
    }

    @Test
    fun `move changes only the target vertex`() {
        val segments = listOf(listOf(north(0.0), north(100.0), north(200.0)))

        val moved = TrackEditOps.move(segments, TrackVertex(0, 1), east(50.0))

        assertEquals(east(50.0), moved.first()[1])
        assertEquals(north(0.0), moved.first()[0])
        assertEquals(north(200.0), moved.first()[2])
        // 原数据不被改动：撤销栈靠的就是这个
        assertEquals(north(100.0), segments.first()[1])
    }

    @Test
    fun `move with out of range indices returns the same list`() {
        val segments = listOf(listOf(north(0.0), north(100.0)))

        assertSame(segments, TrackEditOps.move(segments, TrackVertex(3, 0), east(10.0)))
        assertSame(segments, TrackEditOps.move(segments, TrackVertex(0, 9), east(10.0)))
    }

    @Test
    fun `insert puts the point before the given index`() {
        val segments = listOf(listOf(north(0.0), north(200.0)))
        val insertion =
            TrackInsertion(
                segmentIndex = 0,
                insertIndex = 1,
                point = east(10.0),
                distanceMeters = 0.0,
            )

        val inserted = TrackEditOps.insert(segments, insertion)

        assertEquals(3, inserted.first().size)
        assertEquals(east(10.0), inserted.first()[1])
        assertEquals(north(200.0), inserted.first()[2])
    }

    @Test
    fun `delete refuses to leave a segment with a single point`() {
        val twoPoints = listOf(listOf(north(0.0), north(100.0)))

        assertFalse(TrackEditOps.canDelete(twoPoints, TrackVertex(0, 0)))
        assertSame(twoPoints, TrackEditOps.delete(twoPoints, TrackVertex(0, 0)))

        val threePoints = listOf(listOf(north(0.0), north(100.0), north(200.0)))
        assertTrue(TrackEditOps.canDelete(threePoints, TrackVertex(0, 1)))
        assertEquals(2, TrackEditOps.delete(threePoints, TrackVertex(0, 1)).first().size)
    }

    @Test
    fun `nearest projects the tap onto the line instead of using the tap`() {
        // 点击位置在线的东侧 30 米，插入点必须落在线上
        val segments = listOf(listOf(north(0.0), north(200.0)))
        val tap = north(100.0).let { GeoPoint(it.latitude, it.longitude + 30.0 / 95_000.0) }

        val insertion = requireNotNull(TrackEditOps.nearest(segments, tap))
        val point = insertion.point

        // 落在线上：经度回到线本身，到 north(100) 的距离约等于 0
        assertEquals(base.longitude, point.longitude, 1e-9)
        assertEquals(0.0, distanceMeters(point, north(100.0)), 1.0)
        assertEquals(1, insertion.insertIndex)
    }

    @Test
    fun `nearest ignores taps far away from the line`() {
        val segments = listOf(listOf(north(0.0), north(200.0)))

        assertNull(TrackEditOps.nearest(segments, east(500.0), maxDistanceMeters = 50.0))
    }

    @Test
    fun `nearest picks the closest of several segments`() {
        val segments =
            listOf(
                listOf(north(0.0), north(200.0)),
                listOf(east(0.0), east(200.0)),
            )
        // 距离第二段只有 10 米，距离第一段一百多米
        val tap = east(150.0).let { GeoPoint(it.latitude + 10.0 / 111_320.0, it.longitude) }

        val insertion = requireNotNull(TrackEditOps.nearest(segments, tap))

        assertEquals(1, insertion.segmentIndex)
        assertEquals(1, insertion.insertIndex)
    }

    @Test
    fun `nearest never creates a point on top of an existing vertex`() {
        val segments = listOf(listOf(north(0.0), north(200.0)))

        // 点在最后一个顶点之外：投影会退化成端点，插进去就成了重合顶点
        assertNull(TrackEditOps.nearest(segments, north(240.0), maxDistanceMeters = 100.0))
    }

    @Test
    fun `empty segments are not editable`() {
        assertFalse(TrackEditOps.isEditable(emptyList()))
        assertFalse(TrackEditOps.isEditable(listOf(listOf(north(0.0)))))
        assertTrue(TrackEditOps.isEditable(listOf(listOf(north(0.0), north(1.0)))))
    }
}
