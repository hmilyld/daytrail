package com.hmilyld.daytrail.data.export

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class PlaceExportTest {
    /** 上海人民广场附近的 WGS-84 原值；库里存的是它对应的 GCJ-02。 */
    private val wgs84 = GeoPoint(31.2304, 121.4737)
    private val gcj02 = CoordinateTransform.wgs84ToGcj02(wgs84)
    private val zone = ZoneId.of("Asia/Shanghai")

    private val texts =
        PlaceExportTexts(
            unnamed = "未命名地点",
            coordinateLine = "纬度 %1\$s  经度 %2\$s（WGS-84）",
            radiusAndVisits = "半径 %1\$d 米 · 去过 %2\$d 天",
        )

    private fun place(
        label: String? = "家",
        point: GeoPoint = gcj02,
        radiusMeters: Double = 120.0,
        visitDays: Int = 8,
        suppressed: Boolean = false,
    ): ExportPlace =
        ExportPlace(
            label = label,
            point = point,
            radiusMeters = radiusMeters,
            visitDays = visitDays,
            firstSeenMs = 1_794_672_000_000L,
            lastSeenMs = 1_794_672_000_000L + 86_400_000L,
            suppressed = suppressed,
        )

    private fun num(value: Double): String = String.format(Locale.US, "%.6f", value)

    // ---- 分享文本 ----

    @Test
    fun `share text uses name coordinates and visit summary`() {
        val text = PlaceExport.shareText(place(), texts)

        val lines = text.lines()
        assertEquals("家", lines[0])
        assertTrue(lines[1].contains("纬度 ${num(wgs84.latitude)}"))
        assertTrue(lines[1].contains("经度 ${num(wgs84.longitude)}"))
        assertEquals("半径 120 米 · 去过 8 天", lines[2])
    }

    @Test
    fun `share text converts gcj02 to wgs84`() {
        val text = PlaceExport.shareText(place(point = gcj02), texts)

        // GCJ-02 与 WGS-84 在上海相差约 500 米；文本里必须出现 WGS-84 原值。
        assertTrue(text.contains(num(wgs84.latitude)))
        assertTrue(text.contains(num(wgs84.longitude)))
        assertFalse(text.contains(num(gcj02.latitude)))
    }

    @Test
    fun `share text uses unnamed label and can omit geo link`() {
        val text = PlaceExport.shareText(place(label = null), texts, includeGeoLink = false)

        assertEquals("未命名地点", text.lines()[0])
        assertFalse(text.contains("geo:"))
    }

    @Test
    fun `geo link uses wgs84 and percent encodes the name`() {
        val text = PlaceExport.shareText(place(label = "我的家"), texts)
        val link = text.lines().last()

        assertTrue(link.startsWith("geo:${num(wgs84.latitude)},${num(wgs84.longitude)}?q="))
        // UTF-8 的「我的家」必须被百分号编码，不能把中文裸塞进 URI
        assertTrue(link.contains("%E6%88%91%E7%9A%84%E5%AE%B6"))
    }

    // ---- GPX ----

    @Test
    fun `gpx emits one waypoint per place in wgs84`() {
        val xml = PlaceExport.toGpx(listOf(place()), texts)
        val match = Regex("""<wpt lat="([\d.-]+)" lon="([\d.-]+)">""").find(xml)!!

        assertEquals(num(wgs84.latitude), match.groupValues[1])
        assertEquals(num(wgs84.longitude), match.groupValues[2])
        assertTrue(xml.contains("<name>家</name>"))
        assertTrue(xml.contains("<desc>半径 120 米 · 去过 8 天</desc>"))
    }

    @Test
    fun `gpx escapes xml in names`() {
        val xml = PlaceExport.toGpx(listOf(place(label = "A&B <家> \"x\"")), texts)

        assertTrue(xml.contains("A&amp;B &lt;家&gt; &quot;x&quot;"))
        assertFalse(xml.contains("A&B <家>"))
    }

    @Test
    fun `gpx skips suppressed places`() {
        val xml =
            PlaceExport.toGpx(
                listOf(place(label = "家"), place(label = "公司", suppressed = true)),
                texts,
            )

        assertEquals(1, Regex("""<wpt """).findAll(xml).count())
        assertTrue(xml.contains("<name>家</name>"))
        assertFalse(xml.contains("<name>公司</name>"))
    }

    @Test
    fun `gpx of empty list is still well formed`() {
        val xml = PlaceExport.toGpx(emptyList(), texts)

        assertTrue(xml.startsWith("<?xml"))
        assertTrue(xml.endsWith("</gpx>\n"))
        assertFalse(xml.contains("<wpt "))
    }

    // ---- GeoJSON ----

    @Test
    fun `geojson is a feature collection with point per place`() {
        val json = PlaceExport.toGeoJson(listOf(place()), texts, zone = zone)

        assertTrue(json.contains("\"type\":\"FeatureCollection\""))
        assertTrue(json.contains("\"daytrail.schemaVersion\":1"))
        assertTrue(json.contains("\"geometry\":{\"type\":\"Point\""))
        assertTrue(json.contains("\"name\":\"家\""))
        assertTrue(json.contains("\"visitDays\":8"))
        assertTrue(json.contains("\"radiusMeters\":120"))
    }

    @Test
    fun `geojson converts gcj02 to wgs84`() {
        val json = PlaceExport.toGeoJson(listOf(place()), texts, zone = zone)
        val match = Regex(""""coordinates":\[([\d.-]+),([\d.-]+)\]""").find(json)!!

        assertEquals(num(wgs84.longitude), match.groupValues[1])
        assertEquals(num(wgs84.latitude), match.groupValues[2])
    }

    @Test
    fun `geojson includes iso times`() {
        val json = PlaceExport.toGeoJson(listOf(place()), texts, zone = zone)

        assertTrue(json.contains("\"firstSeen\":\"2026-11-15T"))
        assertTrue(json.contains("\"lastSeen\":\"2026-11-16T"))
        assertTrue(json.contains("+08:00"))
    }

    @Test
    fun `geojson escapes quotes in names`() {
        val json = PlaceExport.toGeoJson(listOf(place(label = "他说\"到了\"")), texts, zone = zone)

        assertTrue(json.contains("他说\\\"到了\\\""))
    }

    @Test
    fun `geojson skips suppressed places`() {
        val json =
            PlaceExport.toGeoJson(
                listOf(place(label = "家"), place(label = "公司", suppressed = true)),
                texts,
                zone = zone,
            )

        assertTrue(json.contains("\"name\":\"家\""))
        assertFalse(json.contains("\"name\":\"公司\""))
    }

    @Test
    fun `geojson of empty list has no features`() {
        val json = PlaceExport.toGeoJson(emptyList(), texts, zone = zone)

        assertTrue(json.contains("\"features\":[]"))
    }

    // ---- 文件名 ----

    @Test
    fun `file names use today and the format extension`() {
        assertTrue(
            PlaceExport
                .suggestedFileName(ExportFormat.GPX, zone)
                .matches(Regex("""daytrail-places-\d{8}\.gpx""")),
        )
        assertTrue(
            PlaceExport
                .suggestedFileName(ExportFormat.GEOJSON, zone)
                .matches(Regex("""daytrail-places-\d{8}\.geojson""")),
        )
    }
}
