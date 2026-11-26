package com.hmilyld.daytrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val markdown =
        """
        # 更新日志

        ---

        ## [Unreleased]

        ---

        ## [0.5.3] - 2026-11-26

        ### 修复

        - 修复 A
        - 修复 B
        ---

        ## [0.5.2] - 2026-11-25

        ### 新增

        - 新增 C
        """.trimIndent()

    @Test
    fun `extracts only the requested version`() {
        val section = Changelog.sectionFor(markdown, "0.5.2")
        assertNotNull(section)
        val text = requireNotNull(section)
        assertTrue(text.contains("新增 C"))
        // 下一版本的标题与小节不能混进来
        assertFalse(text.contains("修复 A"))
        assertFalse(text.contains("0.5.3"))
    }

    @Test
    fun `strips debug suffix and v prefix`() {
        assertTrue(requireNotNull(Changelog.sectionFor(markdown, "0.5.3-debug")).contains("修复 A"))
        assertTrue(requireNotNull(Changelog.sectionFor(markdown, "v0.5.3")).contains("修复 A"))
        assertTrue(requireNotNull(Changelog.sectionFor(markdown, " V0.5.3 ")).contains("修复 A"))
    }

    @Test
    fun `unreleased is not matched by a released version`() {
        assertNull(Changelog.sectionFor(markdown, "Unreleased"))
        assertNull(Changelog.sectionFor(markdown, "0.6.0"))
    }

    @Test
    fun `missing section returns null`() {
        assertNull(Changelog.sectionFor(markdown, "9.9.9"))
        assertNull(Changelog.sectionFor("", "0.5.3"))
        assertNull(Changelog.sectionFor(markdown, ""))
    }

    @Test
    fun `empty section returns null`() {
        val empty = "## [1.0.0]\n\n---\n\n## [0.9.0]\n\n- 旧版本"
        assertNull(Changelog.sectionFor(empty, "1.0.0"))
    }

    @Test
    fun `handles crlf line endings`() {
        val crlf = markdown.replace("\n", "\r\n")
        assertEquals(Changelog.sectionFor(markdown, "0.5.3"), Changelog.sectionFor(crlf, "0.5.3"))
    }

    @Test
    fun `normalize version`() {
        assertEquals("0.5.3", Changelog.normalizeVersion("0.5.3-debug"))
        assertEquals("0.5.3", Changelog.normalizeVersion("v0.5.3"))
        assertEquals("", Changelog.normalizeVersion("  "))
    }
}
