package com.hmilyld.daytrail.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            val ordinal = prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.ordinal
            ThemeMode.entries.getOrElse(ordinal) { ThemeMode.SYSTEM }
        }

    suspend fun setSamplingTier(tier: SamplingTier) {
        context.dataStore.edit { prefs -> prefs[KEY_SAMPLING_TIER] = tier.ordinal }
    }

    suspend fun setLearningEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LEARNING_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.ordinal }
    }

    private companion object {
        val KEY_SAMPLING_TIER = intPreferencesKey("sampling_tier")
        val KEY_LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    }
}
