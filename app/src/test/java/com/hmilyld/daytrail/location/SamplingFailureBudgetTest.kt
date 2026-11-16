package com.hmilyld.daytrail.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingFailureBudgetTest {
    @Test
    fun `starts with no failures`() {
        val budget = SamplingFailureBudget()

        assertEquals(0, budget.consecutiveFailures)
        assertFalse(budget.exhausted)
    }

    @Test
    fun `does not exhaust before reaching the limit`() {
        val budget = SamplingFailureBudget(maxConsecutiveFailures = 3)

        assertFalse(budget.recordFailure())
        assertFalse(budget.recordFailure())
        assertFalse(budget.exhausted)
        assertEquals(2, budget.consecutiveFailures)
    }

    @Test
    fun `exhausts exactly at the limit`() {
        val budget = SamplingFailureBudget(maxConsecutiveFailures = 3)

        budget.recordFailure()
        budget.recordFailure()
        val last = budget.recordFailure()

        assertTrue("第三次失败应触发放弃", last)
        assertTrue(budget.exhausted)
    }

    @Test
    fun `success resets the counter`() {
        val budget = SamplingFailureBudget(maxConsecutiveFailures = 3)

        budget.recordFailure()
        budget.recordFailure()
        budget.recordSuccess()

        assertEquals(0, budget.consecutiveFailures)
        assertFalse(budget.exhausted)

        // 重置后能重新累计
        assertFalse(budget.recordFailure())
        assertFalse(budget.recordFailure())
    }

    @Test
    fun `reset clears an exhausted budget for manual retry`() {
        val budget = SamplingFailureBudget(maxConsecutiveFailures = 2)

        budget.recordFailure()
        budget.recordFailure()
        assertTrue(budget.exhausted)

        budget.reset()

        assertFalse(budget.exhausted)
        assertEquals(0, budget.consecutiveFailures)
    }

    @Test
    fun `default limit stops retrying within a few minutes`() {
        val budget = SamplingFailureBudget()

        // 默认 5 次：按单次 25 秒 + 重试 15 秒估算约 3 分钟
        repeat(4) { assertFalse(budget.recordFailure()) }
        assertTrue(budget.recordFailure())
    }

    @Test
    fun `intermittent failures do not exhaust`() {
        val budget = SamplingFailureBudget(maxConsecutiveFailures = 3)

        // 失败-成功-失败-成功，不应触发放弃
        repeat(3) {
            budget.recordFailure()
            budget.recordSuccess()
        }

        assertFalse(budget.exhausted)
    }
}
