package com.hmilyld.daytrail.data.geocode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 逆地理编码报文解析。
 *
 * 报文形状取自高德 v3 `geocode/regeo` 的真实返回：**缺字段时给的是空数组 `[]`
 * 而不是 `null`**，按 String 反序列化会直接炸。这里就是钉住这条。
 */
class ReverseGeocodeClientTest {
    private val client = ReverseGeocodeClient(apiKey = "test-key")

    @Test
    fun `nearest poi wins`() {
        val json =
            """
            {"status":"1","info":"OK","regeocode":{
              "formatted_address":"河南省郑州市金水区花园路",
              "addressComponent":{"township":"花园路街道","district":"金水区","city":"郑州市"},
              "pois":[{"name":"招银大厦"},{"name":"某某超市"}]
            }}
            """.trimIndent()

        assertEquals("招银大厦", client.parseName(json))
    }

    @Test
    fun `falls back to street and number when no poi`() {
        val json =
            """
            {"status":"1","regeocode":{
              "addressComponent":{
                "streetNumber":{"street":"花园路","number":"100"},
                "township":[],"district":"金水区"
              },
              "pois":[]
            }}
            """.trimIndent()

        assertEquals("花园路100", client.parseName(json))
    }

    @Test
    fun `falls back to township then district, tolerating empty arrays`() {
        val township =
            """
            {"status":"1","regeocode":{
              "addressComponent":{"township":"花园路街道","district":"金水区"},
              "pois":[]
            }}
            """.trimIndent()
        assertEquals("花园路街道", client.parseName(township))

        val district =
            """
            {"status":"1","regeocode":{
              "addressComponent":{"township":[],"district":"金水区","city":"郑州市"},
              "pois":[]
            }}
            """.trimIndent()
        assertEquals("金水区", client.parseName(district))
    }

    @Test
    fun `blank name is not a name`() {
        val json =
            """
            {"status":"1","regeocode":{
              "addressComponent":{"township":"","district":"  "},
              "pois":[{"name":""}]
            }}
            """.trimIndent()

        assertNull(client.parseName(json))
    }

    @Test
    fun `error status yields null`() {
        val json = """{"status":"0","info":"INVALID_USER_KEY","regeocode":{}}"""

        assertNull(client.parseName(json))
    }

    @Test
    fun `malformed body yields null instead of throwing`() {
        assertNull(client.parseName("not json at all"))
        assertNull(client.parseName(""))
    }
}
