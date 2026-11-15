package com.hmilyld.daytrail.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.prefs.SettingsStore
import com.hmilyld.daytrail.data.prefs.ThemeMode
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页状态。 */
data class SettingsUiState(
    val samplingTier: SamplingTier = SamplingTier.POWER_SAVER,
    val learningEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pointCount: Int = 0,
)

/**
 * 设置页 ViewModel。
 *
 * 设置项直接写 DataStore，不做本地副本：
 * DataStore 的 Flow 会把新值回传，界面自然刷新。
 */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val repository: TrackRepository,
) : ViewModel() {
    private val pointCount = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsStore.samplingTier,
            settingsStore.learningEnabled,
            settingsStore.themeMode,
            pointCount,
        ) { tier, learning, theme, points ->
            SettingsUiState(
                samplingTier = tier,
                learningEnabled = learning,
                themeMode = theme,
                pointCount = points,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    init {
        refreshPointCount()
    }

    fun setSamplingTier(tier: SamplingTier) {
        viewModelScope.launch { settingsStore.setSamplingTier(tier) }
    }

    fun setLearningEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLearningEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    /** 清除全部数据。调用方必须先做二次确认。 */
    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
            refreshPointCount()
        }
    }

    private fun refreshPointCount() {
        viewModelScope.launch {
            pointCount.value = repository.totalPointCount()
        }
    }
}
