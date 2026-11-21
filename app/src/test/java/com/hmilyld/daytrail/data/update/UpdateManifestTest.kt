package com.hmilyld.daytrail.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun `parses the published manifest`() {
        val raw =
            """
            {"versionName":"0.4.0","versionCode":7,"tag":"v0.4.0",
             "title":"行迹 0.4.0","notes":"修了几个问题",
             "pageUrl":"https://example.test/r/v0.4.0",
             "apkUrl":"https://example.test/daytrail-0.4.0.apk",
             "apkSize":39000000,
             "apkSha256":"ABCDEF0123"}
            """.trimIndent()

        val info = UpdateManifest.parse(raw)!!

        assertEquals("0.4.0", info.versionName)
        assertEquals(7, info.versionCode)
        assertEquals("v0.4.0", info.tagName)
        assertEquals("行迹 0.4.0", info.title)
        assertEquals("修了几个问题", info.notes)
        assertEquals(39_000_000L, info.apkSize)
        // 大小写归一，便于与下载结果直接比较
        assertEquals("abcdef0123", info.apkSha256)
    }

    @Test
    fun `manifest without a version is unusable`() {
        assertNull(UpdateManifest.parse("""{"versionCode":7}"""))
        assertNull(UpdateManifest.parse("""{"versionName":"nightly"}"""))
        assertNull(UpdateManifest.parse("not json"))
    }

    @Test
    fun `defaults are filled when optional fields are absent`() {
        val info = UpdateManifest.parse("""{"versionName":"v1.2.3"}""")!!

        assertEquals("1.2.3", info.versionName)
        assertEquals(0, info.versionCode)
        assertEquals("v1.2.3", info.tagName)
        assertNull(info.apkUrl)
        assertNull(info.apkSha256)
        assertEquals(0L, info.apkSize)
    }

    @Test
    fun `api parser ignores drafts and pre-releases`() {
        val draft = """{"tag_name":"v0.9.0","draft":true,"assets":[]}"""
        val pre = """{"tag_name":"v0.9.0","prerelease":true,"assets":[]}"""

        assertNull(ReleaseParser.parse(draft))
        assertNull(ReleaseParser.parse(pre))
    }

    @Test
    fun `api parser picks the apk asset`() {
        val raw =
            """
            {"tag_name":"v0.9.0","name":"行迹 0.9.0","body":"说明",
             "html_url":"https://example.test/releases/tag/v0.9.0",
             "assets":[
               {"name":"notes.txt","browser_download_url":"https://example.test/notes.txt","size":"10"},
               {"name":"daytrail-0.9.0.apk","browser_download_url":"https://example.test/a.apk","size":"1234"}
             ]}
            """.trimIndent()

        val info = ReleaseParser.parse(raw)!!

        assertEquals("0.9.0", info.versionName)
        assertEquals("https://example.test/a.apk", info.apkUrl)
        assertEquals(1_234L, info.apkSize)
        // API 报文里没有这两项，必须保持默认值而不是编一个
        assertEquals(0, info.versionCode)
        assertNull(info.apkSha256)
    }

    @Test
    fun `api parser without an apk still yields a release page`() {
        val info = ReleaseParser.parse("""{"tag_name":"v0.9.0","assets":[]}""")!!

        assertNull(info.apkUrl)
        assertEquals("v0.9.0", info.tagName)
    }
}
