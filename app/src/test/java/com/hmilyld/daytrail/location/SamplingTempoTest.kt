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

    /** 原地不动地喂样本，每 10 分钟一个点。 */
    private fun stayStill(
        fromMinute: Int,
        toMinute: Int,
        insideKnownPlace: Boolean = false,
    ): Int {
        var previous = sample(0.0, fromMinute * 60L)
        var minute = fromMinute
        while (minute < toMinute) {
            minute += 10
            val next = sample(0.0, minute * 60L)
            tempo.observe(previous, next, insideKnownPlace)
            previous = next
        }
        return minute
    }

    @Test
    fun `staying still for an hour enters sleep`() {
        stayStill(fromMinute = 0, toMinute = 70)

        assertEquals(TempoTier.SLEEP, tempo.tier)
        assertEquals(SamplingTempo.SLEEP_INTERVAL_MS, tempo.intervalMs)
    }

    @Test
    fun `inside a known place sleep starts after fifteen minutes`() {
        stayStill(fromMinute = 0, toMinute = 20, insideKnownPlace = true)

        assertEquals(TempoTier.SLEEP, tempo.tier)
    }

    @Test
    fun `movement resets the still timer`() {
        val minute = stayStill(fromMinute = 0, toMinute = 50)
        assertEquals(TempoTier.STILL, tempo.tier)

        // 10 分钟走 600 米 = 1 m/s，属于移动
        tempo.observe(sample(0.0, minute * 60L), sample(600.0, (minute + 10) * 60L))
        assertEquals(TempoTier.MOVING, tempo.tier)

        // 再静止 20 分钟不该休眠：计时已经清零
        var previous = sample(600.0, (minute + 10) * 60L)
        for (next in minute + 20..minute + 30 step 10) {
            val point = sample(600.0, next * 60L)
            tempo.observe(previous, point)
            previous = point
        }

        assertEquals(TempoTier.STILL, tempo.tier)
    }

    @Test
    fun `wake leaves sleep immediately`() {
        stayStill(fromMinute = 0, toMinute = 70)
        assertEquals(TempoTier.SLEEP, tempo.tier)

        tempo.wake()

        assertEquals(TempoTier.MOVING, tempo.tier)
        assertEquals(SamplingPreset.POWER_SAVER.profile.movingMs, tempo.intervalMs)
    }

    @Test
    fun `auto sleep off never enters sleep`() {
        tempo.autoSleepEnabled = false

        stayStill(fromMinute = 0, toMinute = 120)

        assertEquals(TempoTier.STILL, tempo.tier)
    }

    @Test
    fun `a duplicate delivery does not reset the still timer`() {
        // 静止 50 分钟
        stayStill(fromMinute = 0, toMinute = 50)
        assertEquals(TempoTier.STILL, tempo.tier)

        // 同一轮采样的重复投递：4 秒、45 米（实测数据里出现过）。
        // 用它算速度会得到 11 m/s，把静止档打成驾车档并清空静止计时。
        tempo.observe(sample(0.0, 3_000), sample(45.0, 3_004))
        assertEquals(TempoTier.STILL, tempo.tier)

        // 再静止 10 分钟，累计满 60 分钟应该进休眠
        tempo.observe(sample(45.0, 3_004), sample(45.0, 3_600))
        assertEquals(TempoTier.SLEEP, tempo.tier)
    }

    @Test
    fun `a normal interval still updates the tier`() {
        // 30 秒是最高精度档的驾车间隔，不能被最短窗口挡掉
        tempo.observe(sample(0.0, 0), sample(300.0, 30))

        assertEquals(TempoTier.DRIVING, tempo.tier)
    }
}
