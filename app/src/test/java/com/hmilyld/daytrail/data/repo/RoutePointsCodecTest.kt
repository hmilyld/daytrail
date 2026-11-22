package com.hmilyld.daytrail.data.repo

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 折线 JSON 编解码（纠偏结果与手动微调共用同一套）。
 *
 * 存在的意义：这个字段是 TEXT，格式一旦写错不会有人立刻发现——
 * 直到某天用户打开详情页，发现修正过的轨迹全没了。
 */
class RoutePointsCodecTest {
    @Test
    fun `round trip keeps segment order and coordinate order`() {
        val segments =
            listOf(
                listOf(GeoPoint(31.2304, 121.4737), GeoPoint(31.2310, 121.4750)),
                listOf(GeoPoint(31.2400, 121.4800), GeoPoint(31.2410, 121.4810)),
            )

        val decoded = decodePoints(encodePoints(segments))

        assertEquals(segments, decoded)
    }

    @Test
    fun `coordinates are stored as longitude then latitude`() {
        // 与 GeoJSON 一致。写反了轨迹会在地图上镜像到地球另一头。
        val text = encodePoints(listOf(listOf(GeoPoint(31.5, 121.5), GeoPoint(32.5, 122.5))))

        assertTrue(text.contains("[121.5,31.5]"))
        assertTrue(text.contains("[122.5,32.5]"))
    }

    @Test
    fun `segments with fewer than two points are dropped`() {
        val text = """[[[121.0,31.0],[121.1,31.1]],[[121.2,31.2]]]"""

        val decoded = decodePoints(text)

        assertEquals(1, decoded.size)
        assertEquals(2, decoded.first().size)
    }

    @Test
    fun `broken text decodes to nothing instead of throwing`() {
        // 老版本或手工改坏的数据不能让详情页崩溃
        assertEquals(emptyList<List<GeoPoint>>(), decodePoints("not json"))
        assertEquals(emptyList<List<GeoPoint>>(), decodePoints(""))
    }
}
