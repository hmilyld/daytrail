package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.DistanceFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 里程格式化的单元测试。
 *
 * 为什么单独测这个：它是用户最常看到的数字，格式不一致会显得粗糙
 * （例如同一个 8.5 公里，一处显示 8.5、一处显示 8.50）。
 */
class DistanceFormatTest {
    @Test
    fun `zero shows one decimal`() {
        assertEquals("0.0", DistanceFormat.kilometers(0.0))
    }

    @Test
    fun `under ten km keeps one decimal`() {
        assertEquals("1.5", DistanceFormat.kilometers(1_500.0))
        assertEquals("8.5", DistanceFormat.kilometers(8_500.0))
        assertEquals("9.9", DistanceFormat.kilometers(9_900.0))
    }

    @Test
    fun `ten km and above rounds to integer`() {
        assertEquals("10", DistanceFormat.kilometers(10_000.0))
        assertEquals("12", DistanceFormat.kilometers(12_400.0))
        assertEquals("86", DistanceFormat.kilometers(86_200.0))
    }

    @Test
    fun `boundary at ten km switches format`() {
        // 9999 米仍是 10.0 → 取整后 10；10000 米也是 10
        assertEquals("10", DistanceFormat.kilometers(9_999.0))
        assertEquals("10", DistanceFormat.kilometers(10_000.0))
    }

    @Test
    fun `negative distance clamps to zero`() {
        assertEquals("0.0", DistanceFormat.kilometers(-100.0))
    }

    @Test
    fun `nearby distances round to ten meters`() {
        assertEquals(260, DistanceFormat.roundedMeters(260.4))
        assertEquals(160, DistanceFormat.roundedMeters(163.0))
        assertEquals(330, DistanceFormat.roundedMeters(334.0))
        assertEquals(0, DistanceFormat.roundedMeters(3.0))
    }
}
