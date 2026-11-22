package com.hmilyld.daytrail.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hmilyld.daytrail.AppContainer
import com.hmilyld.daytrail.ui.screens.daydetail.DayDetailViewModel
import com.hmilyld.daytrail.ui.screens.diagnostics.DiagnosticsViewModel
import com.hmilyld.daytrail.ui.screens.history.HistoryViewModel
import com.hmilyld.daytrail.ui.screens.places.PlaceDetailViewModel
import com.hmilyld.daytrail.ui.screens.places.PlacesViewModel
import com.hmilyld.daytrail.ui.screens.settings.SettingsViewModel
import com.hmilyld.daytrail.ui.screens.today.TodayViewModel

/**
 * ViewModel 工厂。
 *
 * 手动 DI 下没有 Hilt 的自动注入，用 [viewModelFactory] 显式声明构造参数。
 * 集中在这里，避免每个页面各写一套 factory。
 */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            TodayViewModel(
                repository = container.trackRepository,
                settingsStore = container.settingsStore,
                recordingState = container.recordingState,
            )
        }
        initializer {
            HistoryViewModel(repository = container.trackRepository)
        }
        initializer {
            DayDetailViewModel(
                repository = container.trackRepository,
                correctionClient = container.grasproadClient,
                recordingState = container.recordingState,
            )
        }
        initializer {
            PlacesViewModel(repository = container.trackRepository)
        }
        initializer {
            PlaceDetailViewModel(repository = container.trackRepository)
        }
        initializer {
            DiagnosticsViewModel(log = container.diagnosticLog)
        }
        initializer {
            SettingsViewModel(
                settingsStore = container.settingsStore,
                repository = container.trackRepository,
            )
        }
    }

/** 占位，避免 CreationExtras 未使用告警。 */
private val unusedExtras: CreationExtras? = null
