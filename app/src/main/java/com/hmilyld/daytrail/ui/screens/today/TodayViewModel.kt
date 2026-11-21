package com.hmilyld.daytrail.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.DateLabels
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.formatDuration
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.prefs.RecordingState
import com.hmilyld.daytrail.data.prefs.SamplingTier
import com.hmilyld.daytrail.data.prefs.SettingsStore
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.data.repo.toMarkerStay
import com.hmilyld.daytrail.data.repo.toStaySegment
import com.hmilyld.daytrail.data.repo.todayKey
import com.hmilyld.daytrail.domain.StayMarkers
import com.hmilyld.daytrail.domain.TrackDisplay
import com.hmilyld.daytrail.domain.TrackPointSample
import com.hmilyld.daytrail.location.LocationFailureReason
import com.hmilyld.daytrail.location.RecordingStateHolder
import com.hmilyld.daytrail.ui.UiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 今日行程里的一段停留。 */
data class TodayStay(
    /** 开始时间，`09:41`。 */
    val startTimeLabel: String,
    /** 地点名。没有命名的停留为 null，界面显示「停留」。 */
    val label: String?,
    val minutes: Int,
)

/** 今日页的界面状态。 */
data class TodayUiState(
    val dayKey: DayKey,
    /** `11月19日 星期四`。 */
    val dateLabel: String = "",
    val recordingState: RecordingState = RecordingState.IDLE,
    val distanceKm: String = "0.0",
    val duration: String = "0m",
    val stopCount: Int = 0,
    val pointCount: Int = 0,
    /** 地图折线用的坐标。 */
    val trackSegments: List<List<GeoPoint>> = emptyList(),
    /** 地图停留标记。每天每个地点一个，画在地点锚点上。 */
    val stayMarkers: List<GeoPoint> = emptyList(),
    /** 有标记的停留各自的质心。起终点落在这些位置上时不再单独画图钉。 */
    val stayCentroids: List<GeoPoint> = emptyList(),
    /** 地图的文字版概要，供 TalkBack 使用。 */
    val accessibilitySummary: List<UiText> = emptyList(),
    /** 定位异常原因。非 null 表示采集已停止，界面应提示并给重试入口。 */
    val locationIssue: LocationFailureReason? = null,
    /** 最近一个采样点（GCJ-02）。记录中地图图钉跟着它走。 */
    val lastFix: GeoPoint? = null,
    /** 记录中但很久没有新点位。 */
    val fixStale: Boolean = false,
    /** 已进入休眠：采样间隔降到 1 小时一次。 */
    val sleeping: Boolean = false,
    /** 采样档位。今日页可以直接改，不用跳到设置。 */
    val samplingTier: SamplingTier = SamplingTier.POWER_SAVER,
    /** 当天第一个点的时刻，`08:12`。没有点时为 null。 */
    val startedAtLabel: String? = null,
    /** 当天停留，按时间正序。今日行程卡只展示最后几条。 */
    val stays: List<TodayStay> = emptyList(),
)

/**
 * 今日页 ViewModel。
 *
 * 数据流：Room 的 Flow → 界面状态。用 Flow 而不是一次性读取，
 * 这样采集服务写入后界面会自动刷新，不需要手动触发。
 */
class TodayViewModel(
    private val repository: TrackRepository,
    private val settingsStore: SettingsStore,
    private val recordingState: RecordingStateHolder,
) : ViewModel() {
    private val dayKey = todayKey()

    init {
        // 从 v1 升级上来时历史停留没有地点归属，补一次就够。
        viewModelScope.launch {
            if (!settingsStore.placesBackfillDone.first()) {
                repository.backfillPlaces()
                settingsStore.setPlacesBackfillDone()
            }
        }
    }

    val uiState: StateFlow<TodayUiState> =
        combine(
            recordingState.state,
            recordingState.lastFix,
            repository.observeDay(dayKey),
            repository.observePoints(dayKey),
            repository.observeStays(dayKey),
        ) { state, lastFix, day, points, stays ->
            TodaySource(state = state, lastFix = lastFix, day = day, points = points, stays = stays)
        }.combine(repository.observePlaces()) { source, places ->
            buildState(source, places)
        }.combine(recordingState.locationIssue) { state, issue ->
            state.copy(locationIssue = issue)
        }.combine(recordingState.fixStale) { state, stale ->
            state.copy(fixStale = stale)
        }.combine(recordingState.sleeping) { state, sleeping ->
            state.copy(sleeping = sleeping)
        }.combine(settingsStore.samplingTier) { state, tier ->
            state.copy(samplingTier = tier)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(dayKey = dayKey),
        )

    /** 切采样档位。录制中的服务会立即读到新值，不需要重启服务。 */
    fun setSamplingTier(tier: SamplingTier) {
        viewModelScope.launch { settingsStore.setSamplingTier(tier) }
    }

    private data class TodaySource(
        val state: RecordingState,
        val lastFix: GeoPoint?,
        val day: TrackDayEntity?,
        val points: List<TrackPointEntity>,
        val stays: List<StaySegmentEntity>,
    )

    private fun buildState(
        source: TodaySource,
        places: List<PlaceSummary>,
    ): TodayUiState {
        val day = source.day
        val points = source.points
        val stays = source.stays
        val startedAt = points.minOfOrNull { it.timestampMs }
        // 标记按地点聚合（每天每地点一个点），质心单独留着判断起终点是否已被停留代表
        val stayPlan = StayMarkers.plan(stays.map { it.toMarkerStay(places) })

        return TodayUiState(
            dayKey = dayKey,
            dateLabel =
                runCatching {
                    DateLabels.full(
                        LocalDate.parse(dayKey.value),
                    )
                }.getOrDefault(""),
            recordingState = source.state,
            lastFix = source.lastFix,
            distanceKm = formatKm(day?.distanceMeters ?: 0.0),
            duration =
                formatDuration(
                    ((day?.movingMillis ?: 0L) + (day?.stoppedMillis ?: 0L)) / 1000,
                ),
            stopCount = stays.size,
            pointCount = points.size,
            // 精度过滤、毛刺修复、停留吸附、按「时间 + 位移」切段都在 TrackDisplay 里，
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
                    stays.map { it.toStaySegment() },
                ),
            stayMarkers = stayPlan.markers,
            stayCentroids = stayPlan.centroids,
            accessibilitySummary =
                when {
                    points.isEmpty() -> emptyList()
                    stays.isEmpty() ->
                        listOf(
                            UiText.Res(
                                R.string.map_summary_today_points,
                                listOf(points.size),
                            ),
                        )
                    else ->
                        listOf(
                            UiText.Res(
                                R.string.map_summary_today_both,
                                listOf(stays.size, points.size),
                            ),
                        )
                },
            startedAtLabel = startedAt?.let(DateLabels::time),
            stays =
                stays.map { stay ->
                    TodayStay(
                        startTimeLabel = DateLabels.time(stay.startMs),
                        label = stay.placeLabel(places),
                        minutes = ((stay.endMs - stay.startMs) / 60_000L).toInt(),
                    )
                },
        )
    }

    /** 停留的地点名。没命名过就返回 null，由界面显示「停留」。 */
    private fun StaySegmentEntity.placeLabel(places: List<PlaceSummary>): String? =
        placeId?.let { id -> places.firstOrNull { it.place.id == id }?.place?.label }

    companion object {
        /** 里程格式化。统一走 [DistanceFormat]，避免三处显示不一致。 */
        fun formatKm(meters: Double): String = DistanceFormat.kilometers(meters)
    }
}
