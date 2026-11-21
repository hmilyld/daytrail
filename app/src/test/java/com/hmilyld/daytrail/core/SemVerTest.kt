package com.hmilyld.daytrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun `parses plain and prefixed versions`() {
        assertEquals("1.2.3", SemVer.parse("1.2.3").toString())
        assertEquals("1.2.3", SemVer.parse("v1.2.3").toString())
        assertEquals("1.2.3", SemVer.parse("V1.2.3").toString())
        assertEquals("1.2.3", SemVer.parse(" 1.2.3 ").toString())
    }

    @Test
    fun `ignores pre-release suffix and build metadata`() {
        assertEquals("1.2.3", SemVer.parse("1.2.3-debug").toString())
        assertEquals("1.2.3", SemVer.parse("1.2.3+4").toString())
        assertEquals("1.2.3", SemVer.parse("1.2.3-rc1+7").toString())
    }

    @Test
    fun `missing components default to zero`() {
        assertEquals("1.2.0", SemVer.parse("1.2").toString())
        assertEquals("1.0.0", SemVer.parse("1").toString())
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("v"))
        assertNull(SemVer.parse("1.2.3.4"))
        assertNull(SemVer.parse("1.x.3"))
        assertNull(SemVer.parse("release"))
    }

    @Test
    fun `numeric comparison not string comparison`() {
        // 字符串比较会认为 "0.10.0" < "0.9.0"，这是最典型的更新逻辑 bug
        assertTrue(SemVer.isNewer("0.10.0", "0.9.0"))
        assertTrue(SemVer.isNewer("1.0.0", "0.99.99"))
        assertTrue(SemVer.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `same or older is not newer`() {
        assertFalse(SemVer.isNewer("1.2.3", "1.2.3"))
        assertFalse(SemVer.isNewer("1.2.2", "1.2.3"))
        assertFalse(SemVer.isNewer(null, "1.2.3"))
        assertFalse(SemVer.isNewer("1.2.3", null))
    }

    @Test
    fun `ordering is lexicographic by component`() {
        val versions = listOf("0.9.0", "0.10.0", "1.0.0").map { SemVer.parse(it)!! }
        assertEquals(listOf("0.9.0", "0.10.0", "1.0.0"), versions.sorted().map { it.toString() })
    }
}
