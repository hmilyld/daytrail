package com.hmilyld.daytrail.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hmilyld.daytrail.data.backup.BackupSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 采样档位。省电档是默认值，符合产品的「省电第一」取向。 */
enum class SamplingTier {
    /** 静止 10 分 / 步行 3 分 / 驾车 1 分 */
    POWER_SAVER,

    /** 静止 5 分 / 步行 90 秒 / 驾车 45 秒 */
    BALANCED,

    /** 静止 2 分 / 步行 30 秒 / 驾车 15 秒 */
    HIGH_ACCURACY,
}

/**
 * 主题模式。
 *
 * 默认跟随系统。用户可在设置页强制浅色或深色——
 * 地图类应用在户外强光下，强制浅色往往比跟随系统更易读。
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 一次记录会话的状态。
 *
 * 注意：这个状态**不持久化**。它是进程内的运行状态，由 RecordingService 持有。
 * 早期版本把它写进 DataStore，导致进程被杀后重开时界面仍显示「记录中」，
 * 但服务早已不存在，用户以为在记录其实没有。见 AppContainer.recordingState。
 */
enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
}

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "daytrail_settings")

/**
 * 用户设置的唯一读写入口。全部通过 DataStore，不碰 SharedPreferences。
 *
 * 只存「跨会话仍然有效」的设置；运行状态不放这里。
 */
class SettingsStore(
    private val context: Context,
) {
    val samplingTier: Flow<SamplingTier> =
        context.dataStore.data.map { prefs ->
            val ordinal = prefs[KEY_SAMPLING_TIER] ?: SamplingTier.POWER_SAVER.ordinal
            SamplingTier.entries.getOrElse(ordinal) { SamplingTier.POWER_SAVER }
        }

    val learningEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LEARNING_ENABLED] ?: true }

    /**
     * 长时间静止时自动降到 1 小时一次采样。
     *
     * 默认开启：夜里不动的时候按 10 分钟采一晚上没有任何信息量。
     * 关掉之后采样间隔只由速度决定，回到旧行为。
     */
    val autoSleep: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_AUTO_SLEEP] ?: true }

    /**
     * 历史停留的地点归属是否已经回填过。
     *
     * 用持久标志而不是「地点表是否为空」：一个从来没有 8 分钟停留的用户
     * 地点表永远是空的，每次启动都会把所有日期重算一遍。
     */
    val placesBackfillDone: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PLACES_BACKFILL] ?: false }

    /**
     * 用户上次是否在记录（意图，不是运行状态）。
     *
     * 只用于开机提醒；界面的「记录中」仍然只看内存状态（4.11）。
     */
    val recordingIntent: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_RECORDING_INTENT] ?: false }

    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    /**
     * 用户是否已同意隐私政策，且已单独同意处理行踪轨迹（敏感个人信息）。
     *
     * 这是**记录功能与地图初始化**的前置条件：
     * - `DayTrailApplication` 据此决定给高德 SDK 传 true 还是 false；
     * - `DayTrailApp` 据此决定进引导页还是主界面（升级到引入同意的版本时，
     *   老用户会重新走一遍引导）。
     */
    val privacyAccepted: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PRIVACY_ACCEPTED] ?: false }

    /** 启动时自动检查更新。默认开：开源应用的更新提示越少打扰越好，但不能没有。 */
    val autoUpdateCheck: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_AUTO_UPDATE] ?: true }

    /**
     * 上次「尝试」检查更新的时间（不管成功与否都算）。
     *
     * 记的是尝试而不是成功：限流期间如果只在成功时记录，每次启动都会再撞一次。
     * 网络不通的情况由 [UpdateChecker] 单独处理，不写这个值。
     */
    val lastUpdateCheckAt: Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[KEY_LAST_UPDATE_CHECK] ?: 0L }

    /** 用户点过「稍后」的版本，之后不再自动提示。 */
    val skippedUpdateVersion: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[KEY_SKIPPED_UPDATE] }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            val ordinal = prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.ordinal
            ThemeMode.entries.getOrElse(ordinal) { ThemeMode.SYSTEM }
        }

    /** 上次备份时间（毫秒），0 表示从未备份。只进白名单，不参与 SDK 初始化。 */
    val lastBackupAt: Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[KEY_LAST_BACKUP_AT] ?: 0L }

    suspend fun setSamplingTier(tier: SamplingTier) {
        context.dataStore.edit { prefs -> prefs[KEY_SAMPLING_TIER] = tier.ordinal }
    }

    suspend fun setLearningEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LEARNING_ENABLED] = enabled }
    }

    suspend fun setAutoSleep(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_SLEEP] = enabled }
    }

    suspend fun setPlacesBackfillDone() {
        context.dataStore.edit { prefs -> prefs[KEY_PLACES_BACKFILL] = true }
    }

    suspend fun setRecordingIntent(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RECORDING_INTENT] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_PRIVACY_ACCEPTED] = accepted }
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_UPDATE] = enabled }
    }

    suspend fun setLastUpdateCheckAt(timestampMs: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_UPDATE_CHECK] = timestampMs }
    }

    suspend fun setSkippedUpdateVersion(version: String?) {
        context.dataStore.edit { prefs ->
            if (version == null) {
                prefs.remove(KEY_SKIPPED_UPDATE)
            } else {
                prefs[KEY_SKIPPED_UPDATE] = version
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.ordinal }
    }

    suspend fun setLastBackupAt(timestampMs: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_BACKUP_AT] = timestampMs }
    }

    /** 生成设置白名单快照。枚举写 name，绝不写 ordinal。 */
    suspend fun backupSnapshot(createdAtMs: Long): BackupSettings {
        val prefs = context.dataStore.data.first()
        val tier =
            SamplingTier.entries.getOrElse(prefs[KEY_SAMPLING_TIER] ?: 0) {
                SamplingTier.POWER_SAVER
            }
        val theme =
            ThemeMode.entries.getOrElse(prefs[KEY_THEME_MODE] ?: 0) { ThemeMode.SYSTEM }
        return BackupSettings(
            samplingTier = tier.name,
            learningEnabled = prefs[KEY_LEARNING_ENABLED] ?: true,
            autoSleep = prefs[KEY_AUTO_SLEEP] ?: true,
            autoUpdateCheck = prefs[KEY_AUTO_UPDATE] ?: true,
            themeMode = theme.name,
            lastBackupAt = createdAtMs,
        )
    }

    /** 恢复设置。未知枚举值退回默认，不因为一份老备份把设置写坏。 */
    suspend fun applyBackupSettings(settings: BackupSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAMPLING_TIER] = samplingTierFromBackup(settings.samplingTier).ordinal
            prefs[KEY_LEARNING_ENABLED] = settings.learningEnabled
            prefs[KEY_AUTO_SLEEP] = settings.autoSleep
            prefs[KEY_AUTO_UPDATE] = settings.autoUpdateCheck
            prefs[KEY_THEME_MODE] = themeModeFromBackup(settings.themeMode).ordinal
            prefs[KEY_LAST_BACKUP_AT] = settings.lastBackupAt
        }
    }

    private companion object {
        val KEY_SAMPLING_TIER = intPreferencesKey("sampling_tier")
        val KEY_LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val KEY_AUTO_SLEEP = booleanPreferencesKey("auto_sleep")
        val KEY_PLACES_BACKFILL = booleanPreferencesKey("places_backfill_done")
        val KEY_RECORDING_INTENT = booleanPreferencesKey("recording_intent")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_update_check")
        val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_at")
        val KEY_SKIPPED_UPDATE = stringPreferencesKey("skipped_update_version")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }
}
