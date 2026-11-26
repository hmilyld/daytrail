package com.hmilyld.daytrail.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupEnumParsingTest {
    @Test
    fun `known sampling tier names resolve`() {
        assertEquals(SamplingTier.POWER_SAVER, samplingTierFromBackup("POWER_SAVER"))
        assertEquals(SamplingTier.BALANCED, samplingTierFromBackup("BALANCED"))
        assertEquals(SamplingTier.HIGH_ACCURACY, samplingTierFromBackup("HIGH_ACCURACY"))
    }

    @Test
    fun `unknown or null sampling tier falls back to default`() {
        assertEquals(SamplingTier.POWER_SAVER, samplingTierFromBackup(null))
        assertEquals(SamplingTier.POWER_SAVER, samplingTierFromBackup(""))
        assertEquals(SamplingTier.POWER_SAVER, samplingTierFromBackup("FUTURE_TIER"))
    }

    @Test
    fun `known theme mode names resolve`() {
        assertEquals(ThemeMode.SYSTEM, themeModeFromBackup("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, themeModeFromBackup("LIGHT"))
        assertEquals(ThemeMode.DARK, themeModeFromBackup("DARK"))
    }

    @Test
    fun `unknown or null theme mode falls back to default`() {
        assertEquals(ThemeMode.SYSTEM, themeModeFromBackup(null))
        assertEquals(ThemeMode.SYSTEM, themeModeFromBackup("AUTO"))
    }
}
