package com.hmilyld.daytrail.data.import

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class GpxImportTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun gpx(vararg body: String): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"DayTrail\">\n")
            append("  <trk>\n")
            body.forEach { append(it).append('\n') }
            append("  </trk>\n")
            append("</gpx>\n")
        }

    private fun point(
        lat: String = "31.230416",
        lon: String = "121.473701",
        time: String? = "2026-11-18T08:00:00+08:00",
    ): String {
        val clock = time?.let { "<time>$it</time>" } ?: ""
        return """    <trkpt lat="$lat" lon="$lon">$clock</trkpt>"""
    }

    @Test
    fun `parses points into their local day`() {
        val parsed =
            GpxImport.parse(
                gpx(
                    point(time = "2026-11-18T08:00:00+08:00"),
                    point(lat = "31.231416", time = "2026-11-18T08:01:00+08:00"),
                ),
                zone,
            )

        assertEquals(ImportFormat.GPX, parsed.format)
        assertEquals(1, parsed.days.size)
        assertEquals("2026-11-18", parsed.days.first().dateKey)
        assertEquals(
            2,
            parsed.days
                .first()
                .points.size,
        )
        assertEquals(0, parsed.droppedPoints)
    }

    @Test
    fun `day follows the local zone not UTC`() {
        // 2026-11-18T23:30Z 在东八区已经是 11-19
        val parsed = GpxImport.parse(gpx(point(time = "2026-11-18T23:30:00Z")), zone)

        assertEquals("2026-11-19", parsed.days.first().dateKey)
    }

    @Test
    fun `single quoted attributes are accepted`() {
        val raw =
            gpx(
                """    <trkpt lat='31.230416' lon='121.473701'>""" +
                    """<time>2026-11-18T08:00:00+08:00</time></trkpt>""",
            )

        val parsed = GpxImport.parse(raw, zone)

        assertEquals(
            1,
            parsed.days
                .first()
                .points.size,
        )
    }

    @Test
    fun `self closing or timeless points are dropped`() {
        val raw =
            gpx(
                """    <trkpt lat="31.230416" lon="121.473701" />""",
                """    <trkpt lat="31.230416" lon="121.473701"></trkpt>""",
                point(),
            )

        val parsed = GpxImport.parse(raw, zone)

        assertEquals(2, parsed.droppedPoints)
        assertEquals(
            1,
            parsed.days
                .first()
                .points.size,
        )
    }

    @Test
    fun `multiple segments still belong to one day`() {
        val raw =
            gpx(
                "    <trkseg>",
                point(time = "2026-11-18T08:00:00+08:00"),
                "    </trkseg>",
                "    <trkseg>",
                point(lat = "31.240416", time = "2026-11-18T09:30:00+08:00"),
                "    </trkseg>",
            )

        val parsed = GpxImport.parse(raw, zone)

        assertEquals(1, parsed.days.size)
        assertEquals(
            2,
            parsed.days
                .first()
                .points.size,
        )
    }

    @Test
    fun `wpt stays are ignored`() {
        val raw =
            gpx(
                """  <wpt lat="31.230416" lon="121.473701"><name>公司</name></wpt>""",
                point(),
            )

        val parsed = GpxImport.parse(raw, zone)

        assertEquals(1, parsed.days.sumOf { it.points.size })
    }
}
