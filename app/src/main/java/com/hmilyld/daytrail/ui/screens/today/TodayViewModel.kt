package com.hmilyld.daytrail.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.formatDuration
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.data.prefs.SettingsStore
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.data.repo.todayKey
import com.hmilyld.daytrail.location.RecordingStateHolder
import com.hmilyld.daytrail.ui.components.map.MapStayMarker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 今日页的界面状态。 */
data class TodayUiState(
    val dayKey: DayKey,
    val recordingState: RecordingState = RecordingState.IDLE,
    val distanceKm: String = "0.0",
    val duration: String = "0m",
    val stopCount: Int = 0,
    val pointCount: Int = 0,
    /** 地图折线用的坐标。 */
    val trackPoints: List<GeoPoint> = emptyList(),
    /** 地图停留标记。 */
    val stayMarkers: List<MapStayMarker> = emptyList(),
)

/**
 * 今日页 ViewModel。
 *
 * 数据流：Room 的 Flow → 界面状态。用 Flow 而不是一次性读取，
 * 这样采集服务写入后界面会自动刷新，不需要手动触发。
 *
 * 轨迹点不能直接订阅 Room 的全量查询：每写入一批点都会触发一次查询，
 * 一天几百个点时会造成明显抖动。这里只在点数变化时按需读取。
 */
class TodayViewModel(
    private val repository: TrackRepository,
    private val settingsStore: SettingsStore,
    private val recordingState: RecordingStateHolder,
) : ViewModel() {
    private val dayKey = todayKey()

    val uiState: StateFlow<TodayUiState> =
        combine(
            recordingState.state,
            repository.observeDay(dayKey),
            repository.observePointCount(dayKey),
            repository.observeStays(dayKey),
        ) { state, day, points, stays ->
            TodayUiState(
                dayKey = dayKey,
                recordingState = state,
                distanceKm = formatKm(day?.distanceMeters ?: 0.0),
                duration =
                    formatDuration(
                        ((day?.movingMillis ?: 0L) + (day?.stoppedMillis ?: 0L)) / 1000,
                    ),
                stopCount = stays.size,
                pointCount = points,
                trackPoints = emptyList(),
                stayMarkers =
                    stays.map { stay ->
                        MapStayMarker(
                            point = GeoPoint(stay.latitude, stay.longitude),
                            label = null,
                            durationMinutes = (stay.endMs - stay.startMs) / 60_000,
                        )
                    },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(dayKey = dayKey),
        )

    companion object {
        /** 里程格式化。统一走 [DistanceFormat]，避免三处显示不一致。 */
        fun formatKm(meters: Double): String = DistanceFormat.kilometers(meters)
    }
}
