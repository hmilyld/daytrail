package com.hmilyld.daytrail.data.prefs

/**
 * 备份里的枚举用 **name** 存储。未知值或旧版本删掉的档位一律退回默认，
 * 不因为一份备份把设置写坏。纯函数，便于单测。
 */
fun samplingTierFromBackup(name: String?): SamplingTier =
    SamplingTier.entries.firstOrNull { it.name == name } ?: SamplingTier.POWER_SAVER

fun themeModeFromBackup(name: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM
