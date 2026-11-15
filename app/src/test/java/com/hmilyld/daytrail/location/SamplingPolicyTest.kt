package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamplingPolicyTest {
    private val base = GeoPoint(31.2304, 121.4737)
    private val policy = SamplingPolicy(SamplingPreset.POWER_SAVER)

    private fun sample(
        metersNorth: Double = 0.0,
        secondsLater: Long = 0,
        speedMps: Float? = null,
    ): MovementSample {
        // 1 度纬度约 111 320 米
        val point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude)
        return MovementSample(
            point = point,
            timestampMs = 1_000_000L + secondsLater * 1000L,
            speedMps = speedMps,
        )
    }

    @Test
    fun `first sample without previous keeps current state`() {
        val state =
            policy.nextState(
                previous = null,
                sample = sample(),
                currentState = MovementState.STILL,
            )
        assertEquals(MovementState.STILL, state)
    }

    @Test
    fun `repeated small movement becomes still after two samples`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val current = sample(metersNorth = 10.0, secondsLater = 600)

        val first = policy.nextState(previous, current, MovementState.WALKING)
        val second =
            policy.nextState(
                current,
                sample(metersNorth = 12.0, secondsLater = 1200),
                first,
            )

        assertEquals(MovementState.STILL, second)
    }

    @Test
    fun `still beyond sleep threshold enters sleeping`() {
        val start = sample(metersNorth = 0.0, secondsLater = 0)
        val afterThirtyOneMinutes = sample(metersNorth = 5.0, secondsLater = 31 * 60)

        val state =
            policy.nextState(
                previous = start,
                sample = afterThirtyOneMinutes,
                currentState = MovementState.STILL,
            )

        assertEquals(MovementState.SLEEPING, state)
    }

    @Test
    fun `high speed enters driving`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        // 60 秒移动 1200 米 = 20 m/s = 72 km/h
        val current = sample(metersNorth = 1200.0, secondsLater = 60, speedMps = 20f)

        val state = policy.nextState(previous, current, MovementState.WALKING)

        assertEquals(MovementState.DRIVING, state)
    }

    @Test
    fun `driving keeps driving when speed stays above exit threshold`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        // 14 km/h，低于进入阈值 15，但高于退出阈值 12
        val current = sample(metersNorth = 230.0, secondsLater = 60, speedMps = 3.9f)

        val state = policy.nextState(previous, current, MovementState.DRIVING)

        assertEquals(MovementState.DRIVING, state)
    }

    @Test
    fun `sleeping stays sleeping without real displacement`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val current = sample(metersNorth = 30.0, secondsLater = 3600)

        val state = policy.nextState(previous, current, MovementState.SLEEPING)

        assertEquals(MovementState.SLEEPING, state)
    }

    @Test
    fun `sleeping wakes on real displacement`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val current = sample(metersNorth = 500.0, secondsLater = 3600)

        val state = policy.nextState(previous, current, MovementState.SLEEPING)

        assertEquals(MovementState.WALKING, state)
    }

    @Test
    fun `sleeping has no sampling interval`() {
        assertNull(policy.intervalFor(MovementState.SLEEPING))
    }

    @Test
    fun `power saver intervals match product decision`() {
        assertEquals(600_000L, policy.intervalFor(MovementState.STILL))
        assertEquals(180_000L, policy.intervalFor(MovementState.WALKING))
        assertEquals(60_000L, policy.intervalFor(MovementState.DRIVING))
    }

    @Test
    fun `reset clears counters so next still sample does not immediately sleep`() {
        val previous = sample(metersNorth = 0.0, secondsLater = 0)
        val current = sample(metersNorth = 5.0, secondsLater = 31 * 60)

        policy.nextState(previous, current, MovementState.STILL)
        policy.reset()

        val afterReset =
            policy.nextState(
                current,
                sample(metersNorth = 6.0, secondsLater = 32 * 60),
                MovementState.STILL,
            )

        assertEquals(MovementState.STILL, afterReset)
    }
}
