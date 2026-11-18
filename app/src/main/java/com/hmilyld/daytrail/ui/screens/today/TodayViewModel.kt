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
import com.hmilyld.daytrail.domain.TrackDisplay
import com.hmilyld.daytrail.domain.TrackPointSample
import com.hmilyld.daytrail.location.LocationFailureReason
import com.hmilyld.daytrail.location.RecordingStateHolder
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
    val trackSegments: List<List<GeoPoint>> = emptyList(),
    /** 地图停留标记。 */
    val stayMarkers: List<GeoPoint> = emptyList(),
    /** 地图的文字版概要，供 TalkBack 使用。 */
    val accessibilitySummary: String = "",
    /** 定位异常原因。非 null 表示采集已停止，界面应提示并给重试入口。 */
    val locationIssue: LocationFailureReason? = null,
    /**
     * 最近一个采样点（GCJ-02）。
     *
     * 记录中地图圆点跟着它走，不再自己每 15 秒请求一次定位——
     * 那样既费电，也会让圆点和轨迹末端对不上。
     */
    val lastFix: GeoPoint? = null,
    /**
     * 记录中但很久没有新点位。
     *
     * 与 [locationIssue] 不同：这不是失败，采集还在按间隔等，
     * 只是要让用户知道「现在没有新数据」，而不是看着不变的数字以为一切正常。
     */
    val fixStale: Boolean = false,
)

/**
 * 今日页 ViewModel。
 *
 * 数据流：Room 的 Flow → 界面状态。用 Flow 而不是一次性读取，
 * 这样采集服务写入后界面会自动刷新，不需要手动触发。
 *
 * 轨迹点直接订阅 Room：采样间隔是分钟级、刷盘是 60 秒一次，
 * 刷新频率很低，一天的几百个点重发一次成本可接受。
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
            recordingState.lastFix,
            repository.observeDay(dayKey),
            repository.observePoints(dayKey),
            repository.observeStays(dayKey),
        ) { state, lastFix, day, points, stays ->
            TodayUiState(
                dayKey = dayKey,
                recordingState = state,
                lastFix = lastFix,
                distanceKm = formatKm(day?.distanceMeters ?: 0.0),
                duration =
                    formatDuration(
                        ((day?.movingMillis ?: 0L) + (day?.stoppedMillis ?: 0L)) / 1000,
                    ),
                stopCount = stays.size,
                pointCount = points.size,
                // 关键：把真实坐标传给地图，录制过程中才能画出轨迹。
                // 精度过滤、毛刺修复、按「时间 + 位移」切段都在 TrackDisplay 里，
                // 今日页和单日详情共用同一套，避免两处画出来的线不一样。
                trackSegments =
                    TrackDisplay.segments(
                        points.map { entity ->
                            TrackPointSample(
                                timestampMs = entity.timestampMs,
                                point = GeoPoint(entity.latitude, entity.longitude),
                                accuracyMeters = entity.accuracyMeters,
                            )
                        },
                    ),
                stayMarkers =
                    stays
                        .filter { it.endMs - it.startMs >= TrackDisplay.MIN_STAY_MARKER_MS }
                        .map { stay -> GeoPoint(stay.latitude, stay.longitude) },
                accessibilitySummary =
                    if (points.isEmpty()) {
                        ""
                    } else if (stays.isEmpty()) {
                        "轨迹地图，今日共 ${points.size} 个采样点"
                    } else {
                        "轨迹地图，今日 ${stays.size} 处停留，共 ${points.size} 个采样点"
                    },
            )
        }.combine(recordingState.locationIssue) { state, issue ->
            state.copy(locationIssue = issue)
        }.combine(recordingState.fixStale) { state, stale ->
            state.copy(fixStale = stale)
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
