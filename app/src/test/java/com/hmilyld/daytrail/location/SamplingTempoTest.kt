package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SamplingTempoTest {
    private val base = GeoPoint(31.2304, 121.4737)
    private val tempo = SamplingTempo(SamplingPreset.POWER_SAVER)

    /** 1 度纬度约 111 320 米。 */
    private fun sample(
        metersNorth: Double,
        secondsLater: Long,
    ): TempoSample =
        TempoSample(
            timestampMs = 1_000_000L + secondsLater * 1000L,
            point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude),
        )

    private fun observe(
        fromMeters: Double,
        fromSecond: Long,
        toMeters: Double,
        toSecond: Long,
    ) {
        tempo.observe(sample(fromMeters, fromSecond), sample(toMeters, toSecond))
    }

    /** 从驾车一路降到静止档。 */
    private fun driveToStill() {
        observe(0.0, 0, 1200.0, 60) // 20 m/s → 驾车
        observe(1200.0, 60, 1200.0, 120) // 开始降档计时
        observe(1200.0, 120, 1200.0, 420) // 满 300 秒 → 静止
    }

    @Test
    fun `first sample is ignored`() {
        tempo.observe(previous = null, sample = sample(0.0, 0))

        assertEquals(TempoTier.MOVING, tempo.tier)
    }

    @Test
    fun `walking speed stays in moving tier`() {
        // 120 秒走 240 米，约 7 km/h
        observe(0.0, 0, 240.0, 120)

        assertEquals(TempoTier.MOVING, tempo.tier)
    }

    @Test
    fun `fast movement enters driving immediately`() {
        // 60 秒 1200 米 = 72 km/h
        observe(0.0, 0, 1200.0, 60)

        assertEquals(TempoTier.DRIVING, tempo.tier)
    }

    @Test
    fun `stopping downgrades only after the delay`() {
        observe(0.0, 0, 1200.0, 60)
        assertEquals(TempoTier.DRIVING, tempo.tier)

        // 开始等红灯：这一刻只开始计时，不立即降档
        observe(1200.0, 60, 1200.0, 120)
        assertEquals(TempoTier.DRIVING, tempo.tier)

        // 才过了 180 秒，仍然保持
        observe(1200.0, 120, 1200.0, 300)
        assertEquals(TempoTier.DRIVING, tempo.tier)

        // 满 300 秒 → 降档
        observe(1200.0, 120, 1200.0, 420)
        assertEquals(TempoTier.STILL, tempo.tier)
    }

    @Test
    fun `starting to move leaves the still tier immediately`() {
        driveToStill()
        assertEquals(TempoTier.STILL, tempo.tier)

        // 120 秒走 300 米 = 2.5 m/s，属于移动
        observe(1200.0, 420, 1500.0, 540)

        assertEquals(TempoTier.MOVING, tempo.tier)
    }

    @Test
    fun `one drift sample does not change the tempo`() {
        // 室内静止，10 分钟里漂了 200 米 = 0.33 m/s。
        // 早期实现只要位移超过 50 米就算「在走路」，采样会一路提速。
        tempo.observe(sample(0.0, 0), sample(200.0, 600))

        assertEquals(TempoTier.MOVING, tempo.tier)
    }

    @Test
    fun `power saver intervals match the product decision`() {
        assertEquals(60_000L, SamplingPreset.POWER_SAVER.profile.drivingMs)
        assertEquals(120_000L, SamplingPreset.POWER_SAVER.profile.movingMs)
        assertEquals(600_000L, SamplingPreset.POWER_SAVER.profile.stillMs)
    }

    @Test
    fun `still tier uses the still interval`() {
        driveToStill()

        assertEquals(600_000L, tempo.intervalMs)
    }

    @Test
    fun `changing preset takes effect without rebuilding the tempo`() {
        assertEquals(
            SamplingPreset.POWER_SAVER.profile.movingMs,
            tempo.intervalMs,
        )

        tempo.preset = SamplingPreset.HIGH_ACCURACY

        // 移动档立即按新档位算间隔
        assertEquals(30_000L, tempo.intervalMs)
    }

    @Test
    fun `reset returns to the moving tier`() {
        driveToStill()

        tempo.reset()

        assertEquals(TempoTier.MOVING, tempo.tier)
    }
}
