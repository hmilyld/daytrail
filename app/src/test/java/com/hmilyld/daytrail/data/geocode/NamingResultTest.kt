package com.hmilyld.daytrail.data.geocode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「补全名称」失败原因的挑选规则。
 *
 * 一次可能补多个地点、每种失败都不同，但界面只弹一条提示：
 * 必须挑最该告诉用户的那条，不能随机取第一个。
 */
class NamingResultTest {
    @Test
    fun `no failures yields no message`() {
        assertNull(emptyList<NamingFailure>().primary())
        assertNull(NamingResult(attempted = 3, named = 3).failures.primary())
    }

    @Test
    fun `key problems outrank everything else`() {
        val all =
            listOf(
                NamingFailure.NO_NAME,
                NamingFailure.INVALID_POINT,
                NamingFailure.NETWORK,
                NamingFailure.API_REJECTED,
                NamingFailure.NO_KEY,
            )
        assertEquals(NamingFailure.NO_KEY, all.primary())

        assertEquals(
            NamingFailure.API_REJECTED,
            listOf(NamingFailure.NO_NAME, NamingFailure.API_REJECTED).primary(),
        )
    }

    @Test
    fun `network outrank coordinate and missing name`() {
        assertEquals(
            NamingFailure.NETWORK,
            listOf(NamingFailure.NO_NAME, NamingFailure.INVALID_POINT, NamingFailure.NETWORK)
                .primary(),
        )
        assertEquals(
            NamingFailure.INVALID_POINT,
            listOf(NamingFailure.NO_NAME, NamingFailure.INVALID_POINT).primary(),
        )
    }

    @Test
    fun `duplicates do not change the choice`() {
        assertEquals(
            NamingFailure.NO_NAME,
            listOf(NamingFailure.NO_NAME, NamingFailure.NO_NAME).primary(),
        )
    }
}
