package com.hmilyld.daytrail.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonScanTest {
    @Test
    fun `splits top level objects`() {
        val raw = """{"features":[{"a":1},{"b":2}], "other": 1}"""

        val elements = JsonScan.elementsOfArray(raw, "features")

        assertEquals(2, elements?.size)
        assertEquals("""{"a":1}""", elements?.get(0))
        assertEquals("""{"b":2}""", elements?.get(1))
    }

    @Test
    fun `brackets and commas inside strings do not split`() {
        val raw = """{"features":[{"note":"a,b]c[d"}],}"""

        val elements = JsonScan.elementsOfArray(raw, "features")

        assertEquals(1, elements?.size)
        assertTrue(elements!![0].contains("a,b]c[d"))
    }

    @Test
    fun `escaped quotes do not end the string`() {
        val raw = """{"features":[{"note":"他说\"到了\", 然后走了"}]}"""

        val elements = JsonScan.elementsOfArray(raw, "features")

        assertEquals(1, elements?.size)
        assertTrue(elements!![0].contains("""然后走了"""))
    }

    @Test
    fun `empty array gives no elements`() {
        assertEquals(0, JsonScan.elementsOfArray("""{"features":[]}""", "features")?.size)
    }

    @Test
    fun `missing key returns null`() {
        assertNull(JsonScan.elementsOfArray("""{"other":[]}""", "features"))
    }

    @Test
    fun `key appearing as a string value is skipped`() {
        // "notes" 的值里出现了 "features"，不能把它当成数组起点
        val raw = """{"notes":"see \"features\" section","features":[{"a":1}]}"""

        val elements = JsonScan.elementsOfArray(raw, "features")

        assertEquals(1, elements?.size)
    }

    @Test
    fun `reads scalar int`() {
        assertEquals(2, JsonScan.intValueOf("""{"schema":2,"x":1}""", "schema"))
        assertNull(JsonScan.intValueOf("""{"schema":"2"}""", "schema"))
        assertNull(JsonScan.intValueOf("""{"x":1}""", "schema"))
    }
}
