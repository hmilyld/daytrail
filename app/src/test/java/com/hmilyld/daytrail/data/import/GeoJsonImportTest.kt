package com.hmilyld.daytrail.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonImportTest {
    private val shanghai = "121.473701,31.230416"

    private fun feature(
        date: String = "2026-11-18",
        times: String = "\"2026-11-18T08:00:00+08:00\",\"2026-11-18T08:01:00+08:00\"",
        coordinates: String = "[121.473701,31.230416],[121.474701,31.231416]",
        accuracies: String? = "12.0,30.0",
        kind: String = "track",
    ): String {
        val accuracyField = accuracies?.let { """"accuracies":[$it],""" } ?: ""
        return """{"type":"Feature","properties":{"date":"$date","kind":"$kind",""" +
            """"pointCount":2,$accuracyField"times":[$times]},""" +
            """"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
    }

    private fun collection(
        schema: String = """"schema":2,""",
        features: String,
    ): String = """{"type":"FeatureCollection",$schema"name":"DayTrail","features":[$features]}"""

    @Test
    fun `parses the export shape`() {
        val parsed = GeoJsonImport.parse(collection("", feature()))

        assertEquals(ImportFormat.GEOJSON, parsed.format)
        assertEquals(1, parsed.days.size)
        val day = parsed.days.first()
        assertEquals("2026-11-18", day.dateKey)
        assertEquals(2, day.points.size)
        assertEquals(12.0f, day.points[0].accuracyMeters)
        assertEquals(121.473701, day.points[0].longitude, 1e-9)
        assertEquals(31.230416, day.points[0].latitude, 1e-9)
    }

    @Test
    fun `missing accuracies leaves them null`() {
        val parsed = GeoJsonImport.parse(collection("", feature(accuracies = null)))

        assertEquals(
            null,
            parsed.days
                .first()
                .points
                .first()
                .accuracyMeters,
        )
    }

    @Test
    fun `schema 1 without the field is accepted`() {
        val parsed = GeoJsonImport.parse(collection("", feature(accuracies = null)))

        assertEquals(1, parsed.days.size)
    }

    @Test
    fun `newer schema is rejected instead of guessed`() {
        val raw = collection(""""schema":3,""", feature())

        val error = runCatching { GeoJsonImport.parse(raw) }.exceptionOrNull()

        assertTrue(error is ImportException)
        assertTrue(error?.message?.contains("schema=3") == true)
    }

    @Test
    fun `braces with coordinates count mismatch are dropped`() {
        // 坐标 2 个、时间 1 个：第二个点没有时间，必须丢弃而不是瞎猜
        val raw =
            collection(
                "",
                feature(times = "\"2026-11-18T08:00:00+08:00\""),
            )

        val parsed = GeoJsonImport.parse(raw)

        assertEquals(
            1,
            parsed.days
                .first()
                .points.size,
        )
        assertEquals(1, parsed.droppedPoints)
    }

    @Test
    fun `stay features are ignored because they are derived data`() {
        val stay =
            """{"type":"Feature","properties":{"date":"2026-11-18","kind":"stay",""" +
                """"label":"公司","start":"2026-11-18T09:00:00+08:00",""" +
                """"end":"2026-11-18T18:00:00+08:00","minutes":540},""" +
                """"geometry":{"type":"Point","coordinates":[$shanghai]}}"""

        val parsed = GeoJsonImport.parse(collection("", feature() + "," + stay))

        assertEquals(1, parsed.days.size)
        assertEquals(
            2,
            parsed.days
                .first()
                .points.size,
        )
    }
}
