package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定位结果坐标系归一化的测试。
 *
 * 这是「地图上图标画在哪」的正确性保证：
 * 系统定位给 WGS-84，地图底图是 GCJ-02，不转换会偏移 500 米左右。
 */
class LocationFixTransformTest {
    private fun fix(
        point: GeoPoint,
        system: CoordinateSystem,
    ): LocationFix =
        LocationFix(
            timestampMs = 1_794_672_000_000L,
            point = point,
            accuracyMeters = 10f,
            speedMps = null,
            bearingDegrees = null,
            isMock = false,
            coordinateSystem = system,
        )

    @Test
    fun `wgs84 fix is shifted to gcj02`() {
        val wgs84 = GeoPoint(31.2304, 121.4737)

        val result = fix(wgs84, CoordinateSystem.WGS84).toGcj02()

        // 上海处偏移约 548 米
        val shift = distanceMeters(wgs84, result)
        assertTrue("shift=${shift}m", shift in 400.0..700.0)
    }

    @Test
    fun `gcj02 fix is left unchanged`() {
        val gcj02 = GeoPoint(31.228458, 121.478223)

        val result = fix(gcj02, CoordinateSystem.GCJ02).toGcj02()

        assertEquals(gcj02.latitude, result.latitude, 1e-9)
        assertEquals(gcj02.longitude, result.longitude, 1e-9)
    }

    @Test
    fun `shift direction matches known offset for shanghai`() {
        val wgs84 = GeoPoint(31.2304, 121.4737)

        val result = fix(wgs84, CoordinateSystem.WGS84).toGcj02()

        // 上海处 GCJ-02 相对 WGS-84 是「向南偏东」
        assertTrue(result.latitude < wgs84.latitude)
        assertTrue(result.longitude > wgs84.longitude)
    }

    @Test
    fun `conversion is idempotent for gcj02 input`() {
        val gcj02 = GeoPoint(31.228458, 121.478223)

        val once = fix(gcj02, CoordinateSystem.GCJ02).toGcj02()
        val twice = fix(once, CoordinateSystem.GCJ02).toGcj02()

        assertEquals(once, twice)
    }

    @Test
    fun `out of china wgs84 is not shifted`() {
        val tokyo = GeoPoint(35.6762, 139.6503)

        val result = fix(tokyo, CoordinateSystem.WGS84).toGcj02()

        assertEquals(tokyo.latitude, result.latitude, 1e-9)
        assertEquals(tokyo.longitude, result.longitude, 1e-9)
    }
}
