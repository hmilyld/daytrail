package com.hmilyld.daytrail.data.export

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TrackExportTest {
    private val shanghai = GeoPoint(31.228458, 121.478223)

    private fun day(
        dateKey: String = "2026-11-16",
        points: List<ExportPoint> = listOf(point(0.0), point(300.0)),
        stays: List<ExportStay> = emptyList(),
    ): ExportDay = ExportDay(dateKey, points, stays)

    private fun point(
        metersNorth: Double,
        secondsLater: Long = 0,
    ): ExportPoint =
        ExportPoint(
            point = GeoPoint(shanghai.latitude + metersNorth / 111_320.0, shanghai.longitude),
            timestampMs = 1_794_672_000_000L + secondsLater * 1000L,
            accuracyMeters = 10f,
        )

    // ---- GeoJSON ----

    @Test
    fun `geojson has feature collection wrapper`() {
        val json = TrackExport.toGeoJson(listOf(day()))

        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"type\":\"FeatureCollection\""))
        assertTrue(json.contains("\"features\":["))
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun `geojson emits linestring per day`() {
        val json = TrackExport.toGeoJson(listOf(day()))

        assertTrue(json.contains("\"type\":\"LineString\""))
        assertTrue(json.contains("\"kind\":\"track\""))
    }

    @Test
    fun `geojson emits point per stay`() {
        val stay =
            ExportStay(
                point = shanghai,
                startMs = 1_794_672_000_000L,
                endMs = 1_794_672_000_000L + 8 * 60_000L,
                label = "客户处",
            )
        val json = TrackExport.toGeoJson(listOf(day(stays = listOf(stay))))

        assertTrue(json.contains("\"kind\":\"stay\""))
        assertTrue(json.contains("\"label\":\"客户处\""))
        assertTrue(json.contains("\"minutes\":8"))
    }

    @Test
    fun `geojson escapes quotes in labels`() {
        val stay =
            ExportStay(
                point = shanghai,
                startMs = 1_794_672_000_000L,
                endMs = 1_794_672_000_000L + 60_000L,
                label = "他说\"到了\"",
            )
        val json = TrackExport.toGeoJson(listOf(day(stays = listOf(stay))))

        assertTrue("标签里的引号必须转义", json.contains("他说\\\"到了\\\""))
    }

    @Test
    fun `geojson converts gcj02 to wgs84`() {
        // 输入的是 GCJ-02，导出必须是 WGS-84
        val json =
            TrackExport.toGeoJson(
                listOf(day(points = listOf(point(0.0), point(100.0)))),
            )
        val match = Regex("""coordinates":\[\[([\d.-]+),([\d.-]+)""").find(json)!!
        val exported = GeoPoint(match.groupValues[2].toDouble(), match.groupValues[1].toDouble())

        // 已知 WGS-84 原值
        val knownWgs84 = GeoPoint(31.2304, 121.4737)
        val error = distanceMeters(knownWgs84, exported)
        assertTrue("导出坐标应接近 WGS-84 原值，误差=${error}m", error < 2.0)

        // 同时验证确实发生了转换（与 GCJ-02 相差约 500 米）
        val gcjShift = distanceMeters(shanghai, exported)
        assertTrue("应相对 GCJ-02 偏移约 500 米，实际=${gcjShift}m", gcjShift > 300.0)
    }

    @Test
    fun `geojson skips single point day`() {
        val json = TrackExport.toGeoJson(listOf(day(points = listOf(point(0.0)))))

        assertTrue("单点无法构成 LineString", !json.contains("LineString"))
    }

    @Test
    fun `geojson handles empty input`() {
        val json = TrackExport.toGeoJson(emptyList())

        assertTrue(json.contains("\"features\":[]"))
    }

    @Test
    fun `geojson writes local time with offset`() {
        val json = TrackExport.toGeoJson(listOf(day()), ZoneId.of("Asia/Shanghai"))

        assertTrue(
            "时间应带 +08:00 偏移，而不是 UTC 的 Z",
            json.contains("\"2026-11-15T00:00:00+08:00\""),
        )
    }

    @Test
    fun `midnight export keeps date and time on the same day`() {
        val json =
            TrackExport.toGeoJson(
                listOf(
                    day(
                        dateKey = "2026-11-15",
                        points = listOf(point(0.0), point(100.0, 30)),
                    ),
                ),
                ZoneId.of("Asia/Shanghai"),
            )

        // 本地 00:00:30 若写成 UTC 会变成前一天的 16:00:30，日期属性与时间对不上
        assertTrue(json.contains("\"date\":\"2026-11-15\""))
        assertTrue(json.contains("\"2026-11-15T00:00:30+08:00\""))
    }

    @Test
    fun `gpx writes local time with offset`() {
        val gpx = TrackExport.toGpx(listOf(day()), ZoneId.of("Asia/Shanghai"))

        assertTrue(gpx.contains("<time>2026-11-15T00:00:00+08:00</time>"))
    }

    // ---- GPX ----

    @Test
    fun `gpx has valid header`() {
        val gpx = TrackExport.toGpx(listOf(day()))

        assertTrue(gpx.startsWith("<?xml"))
        assertTrue(gpx.contains("version=\"1.1\""))
        assertTrue(gpx.contains("creator=\"DayTrail\""))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun `gpx emits trkpt with time`() {
        val gpx = TrackExport.toGpx(listOf(day()))

        assertTrue(gpx.contains("<trkpt lat="))
        assertTrue(gpx.contains("<time>"))
    }

    @Test
    fun `gpx splits segments on large gaps`() {
        // 两个点相距 10 公里，应切成两个 trkseg
        val far = point(10_000.0, 600)
        val gpx = TrackExport.toGpx(listOf(day(points = listOf(point(0.0), far))))

        val segments = Regex("<trkseg>").findAll(gpx).count()
        assertEquals(2, segments)
    }

    @Test
    fun `gpx keeps single segment for continuous points`() {
        val gpx = TrackExport.toGpx(listOf(day(points = listOf(point(0.0), point(100.0, 60)))))

        assertEquals(1, Regex("<trkseg>").findAll(gpx).count())
    }

    @Test
    fun `gpx emits waypoint for stay`() {
        val stay =
            ExportStay(
                point = shanghai,
                startMs = 1_794_672_000_000L,
                endMs = 1_794_672_000_000L + 15 * 60_000L,
                label = "服务区",
            )
        val gpx = TrackExport.toGpx(listOf(day(stays = listOf(stay))))

        assertTrue(gpx.contains("<wpt lat="))
        assertTrue(gpx.contains("<name>服务区</name>"))
        assertTrue(gpx.contains("停留 15 分钟"))
    }

    @Test
    fun `gpx escapes xml special characters`() {
        val stay =
            ExportStay(
                point = shanghai,
                startMs = 1_794_672_000_000L,
                endMs = 1_794_672_000_000L + 60_000L,
                label = "A & B <测试>",
            )
        val gpx = TrackExport.toGpx(listOf(day(stays = listOf(stay))))

        assertTrue(gpx.contains("A &amp; B &lt;测试&gt;"))
    }

    @Test
    fun `gpx coordinates are wgs84`() {
        val gpx = TrackExport.toGpx(listOf(day(points = listOf(point(0.0), point(100.0)))))
        val match = Regex("""<trkpt lat="([\d.-]+)" lon="([\d.-]+)"""").find(gpx)!!

        val exported = GeoPoint(match.groupValues[1].toDouble(), match.groupValues[2].toDouble())
        val expected =
            CoordinateTransform.gcj02ToWgs84(shanghai)

        // 应该输出 WGS-84，而不是原始 GCJ-02
        assertTrue(
            "lat=${exported.latitude} 应接近 ${expected.latitude}",
            Math.abs(exported.latitude - expected.latitude) < 1e-5,
        )
    }

    @Test
    fun `gpx handles empty day list`() {
        val gpx = TrackExport.toGpx(emptyList())

        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
        assertTrue("没有数据时不应有 trk", !gpx.contains("<trk>"))
    }

    // ---- 文件名 ----

    @Test
    fun `file name uses single date for one day`() {
        val name = TrackExport.suggestedFileName(listOf(day()), "gpx")

        assertEquals("daytrail-2026-11-16.gpx", name)
    }

    @Test
    fun `file name uses range for multiple days`() {
        val name =
            TrackExport.suggestedFileName(
                listOf(day("2026-11-14"), day("2026-11-16")),
                "geojson",
            )

        assertEquals("daytrail-2026-11-14-2026-11-16.geojson", name)
    }

    @Test
    fun `file name falls back when empty`() {
        assertEquals("daytrail.gpx", TrackExport.suggestedFileName(emptyList(), "gpx"))
    }
}
