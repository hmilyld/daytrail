package com.hmilyld.daytrail.data.backup

import kotlinx.serialization.Serializable

/**
 * 进入备份的设置白名单。
 *
 * 枚举一律写 **name 字符串**，不写 ordinal：以后在中间插一个新档位，
 * ordinal 会全体错位，老备份恢复回来就变成了另一个档位。
 *
 * 不进白名单：privacy_accepted（同意状态必须在每台设备重新取得）、
 * onboarding_completed、recording_intent、last_update_check_at、skipped_update_version。
 */
@Serializable
data class BackupSettings(
    val samplingTier: String,
    val learningEnabled: Boolean,
    val autoSleep: Boolean,
    val autoUpdateCheck: Boolean,
    val themeMode: String,
    /** 备份时间。恢复后「上次备份」显示的是这个时间，不是恢复时间。 */
    val lastBackupAt: Long,
)
