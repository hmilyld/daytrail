package com.hmilyld.daytrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateTransformTest {
    @Test
    fun `wgs84 to gcj02 shifts chinese coordinates`() {
        // 上海人民广场附近的 WGS-84 坐标
        val wgs84 = GeoPoint(31.2304, 121.4737)

        val gcj02 = CoordinateTransform.wgs84ToGcj02(wgs84)

        // 国内偏移量通常在 100–700 米之间
        val shift = distanceMeters(wgs84, gcj02)
        assertTrue("shift=${shift}m", shift > 50.0 && shift < 800.0)
    }

    @Test
    fun `shift direction is consistent with known offset`() {
        // 上海处 GCJ-02 相对 WGS-84 是「向南偏东」，约 550 米
        val wgs84 = GeoPoint(31.2304, 121.4737)
        val gcj02 = CoordinateTransform.wgs84ToGcj02(wgs84)

        assertTrue("lat should decrease", gcj02.latitude < wgs84.latitude)
        assertTrue("lon should increase", gcj02.longitude > wgs84.longitude)
    }

    @Test
    fun `shift magnitude matches known value for shanghai`() {
        val wgs84 = GeoPoint(31.2304, 121.4737)

        val gcj02 = CoordinateTransform.wgs84ToGcj02(wgs84)

        // 上海处实测偏移约 548 米
        val shift = distanceMeters(wgs84, gcj02)
        assertTrue("shift=${shift}m", shift in 400.0..700.0)
    }

    @Test
    fun `out of china coordinates are unchanged`() {
        // 东京，不在 GCJ-02 生效范围内
        val tokyo = GeoPoint(35.6762, 139.6503)

        val result = CoordinateTransform.wgs84ToGcj02(tokyo)

        assertEquals(tokyo.latitude, result.latitude, 1e-9)
        assertEquals(tokyo.longitude, result.longitude, 1e-9)
    }

    @Test
    fun `invalid coordinate is returned unchanged`() {
        val invalid = GeoPoint(0.0, 0.0)

        val result = CoordinateTransform.wgs84ToGcj02(invalid)

        assertEquals(invalid, result)
    }

    @Test
    fun `gcj02 to wgs84 reverses the shift`() {
        val original = GeoPoint(31.2304, 121.4737)
        val gcj02 = CoordinateTransform.wgs84ToGcj02(original)

        val roundTrip = CoordinateTransform.gcj02ToWgs84(gcj02)

        // 近似逆变换，误差应在 1 米内
        val error = distanceMeters(original, roundTrip)
        assertTrue("round-trip error=${error}m", error < 1.0)
    }

    @Test
    fun `round trip is stable for multiple cities`() {
        val cities =
            listOf(
                GeoPoint(39.9042, 116.4074), // 北京
                GeoPoint(30.2741, 120.1551), // 杭州
                GeoPoint(23.1291, 113.2644), // 广州
                GeoPoint(45.8038, 126.5349), // 哈尔滨
            )

        cities.forEach { city ->
            val gcj = CoordinateTransform.wgs84ToGcj02(city)
            val back = CoordinateTransform.gcj02ToWgs84(gcj)
            val error = distanceMeters(city, back)
            assertTrue("city=$city error=${error}m", error < 1.0)
        }
    }

    @Test
    fun `same input always produces same output`() {
        val point = GeoPoint(31.2304, 121.4737)

        val first = CoordinateTransform.wgs84ToGcj02(point)
        val second = CoordinateTransform.wgs84ToGcj02(point)

        assertEquals(first, second)
    }
}
