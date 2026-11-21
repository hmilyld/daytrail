package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.DateLabels
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.formatDuration
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.data.repo.toMarkerStay
import com.hmilyld.daytrail.data.repo.toStaySegment
import com.hmilyld.daytrail.data.route.GrasproadClient
import com.hmilyld.daytrail.domain.RouteOverride
import com.hmilyld.daytrail.domain.StayMarkers
import com.hmilyld.daytrail.domain.TrackCorrection
import com.hmilyld.daytrail.domain.TrackDisplay
import com.hmilyld.daytrail.domain.TrackPointSample
import com.hmilyld.daytrail.ui.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 时间轴节点类型。 */
enum class TimelineKind {
    START,
    STOP,
    GAP,
    END,
}

/** 时间轴的一个节点。 */
data class TimelineEntry(
    val time: String,
    val title: UiText,
    val kind: TimelineKind,
    val detail: UiText? = null,
    /** 排序用的原始时间戳。不显示，只用于正确排序。 */
    val sortKeyMs: Long = 0L,
)

/** 单日详情状态。 */
data class DayDetailUiState(
    val dateKey: String = "",
    val title: String = "",
    val distanceKm: String = "0.0",
    val duration: String = "0m",
    val stopCount: Int = 0,
    val entries: List<TimelineEntry> = emptyList(),
    /** 地图折线用的分段坐标。按「数据缺失」切开，避免把断点连成直线。 */
    val trackSegments: List<List<GeoPoint>> = emptyList(),
    /** 记录时间跨度，如 `13:24 – 14:38`。让用户一眼看到实际起止。 */
    val timeRange: String = "",
    /** 地图停留标记。每天每个地点一个，画在地点锚点上。 */
    val stayMarkers: List<GeoPoint> = emptyList(),
    /** 有标记的停留各自的质心。起终点落在这些位置上时不再单独画图钉。 */
    val stayCentroids: List<GeoPoint> = emptyList(),
    /** 地图的文字版行程，供 TalkBack 使用。 */
    val accessibilitySummary: List<UiText> = emptyList(),
    val loading: Boolean = true,
    /** 有没有可补全的驾车段（且已配置 Key）。 */
    val canCorrectRoute: Boolean = false,
    /** 正在请求纠偏服务。 */
    val correctingRoute: Boolean = false,
    /** 已保存的纠偏结果。非 null 时地图画的是它。 */
    val routeOverride: RouteOverride? = null,
    /** 纠偏失败的原因。 */
    val routeError: UiText? = null,
) {
    val hasTrack: Boolean get() = trackSegments.any { it.size >= 2 }
}

/**
 * 单日详情 ViewModel。
 *
 * 时间轴的四类节点按时间合并排序：出发 / 停留 / 数据缺失 / 结束。
 * 「数据缺失」必须显式呈现——高铁过隧道会频繁出现，
 * 不标出来用户会以为轨迹画错了。
 */
class DayDetailViewModel(
    private val repository: TrackRepository,
    private val correctionClient: GrasproadClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DayDetailUiState())
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    private var loadedKey: String? = null

    /**
     * 加载某天。
     *
     * **所有数据都必须订阅**，不能只订阅汇总。
     * 早期实现里 points / stays / gaps 是一次性读取，只有里程时长是订阅的：
     * 用户刚开录就打开详情页，地图和时间轴会永久停在那一刻（显示 1 个点、
     * 开始与结束同一分钟），而标题栏的数字却在继续增长——看起来像「记录断了」，
     * 实际是页面成了快照。
     */
    fun load(dateKey: String) {
        if (loadedKey == dateKey) return
        loadedKey = dateKey

        viewModelScope.launch {
            _uiState.value = DayDetailUiState(dateKey = dateKey, loading = true)

            val key = DayKeys.parse(dateKey) ?: return@launch

            combine(
                repository.observePoints(key),
                repository.observeStays(key),
                repository.observeGaps(key),
                repository.observeDay(key),
                repository.observePlaces(),
            ) { points, stays, gaps, day, places ->
                DaySource(points, stays, gaps, day, places)
            }.combine(repository.observeRoute(key)) { source, route ->
                buildState(dateKey, source, route)
            }.collect { state ->
                // 构建出的状态是「数据侧」的，瞬时状态（正在补全、错误文案）要带过来，
                // 否则录制中每来一个点就会把「正在补全」刷掉。
                val transient = _uiState.value
                _uiState.value =
                    state.copy(
                        correctingRoute = transient.correctingRoute,
                        routeError = transient.routeError,
                    )
            }
        }
    }

    /**
     * 按需纠偏：只在用户点按钮时联网一次。
     *
     * 只送最长的一段驾车轨迹。一天里多段驾驶要分多次请求，配额与耗时都不划算，
     * 而「补一条回家的路」正是这个功能的用途。
     */
    fun correctRoute() {
        val dateKey = loadedKey ?: return
        if (_uiState.value.correctingRoute) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(correctingRoute = true, routeError = null)

            val key = DayKeys.parse(dateKey) ?: return@launch
            val samples =
                repository.pointsOfDay(key).map { point ->
                    TrackPointSample(
                        timestampMs = point.timestampMs,
                        point = GeoPoint(point.latitude, point.longitude),
                        accuracyMeters = point.accuracyMeters,
                    )
                }

            val run =
                TrackCorrection.drivingRuns(samples).maxByOrNull { it.size }
                    ?: run {
                        _uiState.value =
                            _uiState.value.copy(
                                correctingRoute = false,
                                routeError = UiText.Res(R.string.day_detail_route_no_segments),
                            )
                        return@launch
                    }

            val collected = mutableListOf<List<GeoPoint>>()
            var distance = 0.0

            for (chunk in TrackCorrection.chunks(run)) {
                val corrected =
                    correctionClient
                        .correct(TrackCorrection.requestPoints(chunk))
                        .getOrElse { error ->
                            _uiState.value =
                                _uiState.value.copy(
                                    correctingRoute = false,
                                    routeError =
                                        error.message
                                            ?.let { UiText.Raw(it) }
                                            ?: UiText.Res(R.string.day_detail_route_failed),
                                )
                            return@launch
                        }

                collected += corrected.segments
                distance += corrected.distanceMeters
            }

            if (collected.isEmpty()) {
                _uiState.value =
                    _uiState.value.copy(
                        correctingRoute = false,
                        routeError = UiText.Res(R.string.day_detail_route_no_match),
                    )
                return@launch
            }

            repository.saveRoute(
                key,
                RouteOverride(
                    startMs = run.first().timestampMs,
                    endMs = run.last().timestampMs,
                    segments = collected,
                    distanceMeters = distance,
                    createdAtMs = System.currentTimeMillis(),
                ),
            )
            _uiState.value = _uiState.value.copy(correctingRoute = false, routeError = null)
        }
    }

    /** 还原成原始折线。 */
    fun restoreRoute() {
        val dateKey = loadedKey ?: return
        viewModelScope.launch {
            DayKeys.parse(dateKey)?.let { repository.clearRoute(it) }
        }
    }

    private data class DaySource(
        val points: List<TrackPointEntity>,
        val stays: List<StaySegmentEntity>,
        val gaps: List<DayGapEntity>,
        val day: TrackDayEntity?,
        val places: List<PlaceSummary>,
    )

    private fun buildState(
        dateKey: String,
        source: DaySource,
        route: RouteOverride?,
    ): DayDetailUiState {
        val points = source.points
        val stays = source.stays
        val gaps = source.gaps
        val day = source.day
        val places = source.places
        val zone = ZoneId.systemDefault()
        val entries = mutableListOf<TimelineEntry>()

        // 用真实的最早/最晚时间戳，而不是依赖列表首尾顺序：
        // 时间戳可能撞在同一毫秒，排序后首尾未必是极值。
        val startMs = points.minOfOrNull { it.timestampMs }
        val endMs = points.maxOfOrNull { it.timestampMs }

        startMs?.let { start ->
            entries +=
                TimelineEntry(
                    time = formatTime(start, zone),
                    title = UiText.Res(R.string.timeline_start),
                    kind = TimelineKind.START,
                    sortKeyMs = start,
                )
        }

        stays.forEach { stay ->
            entries +=
                TimelineEntry(
                    time = formatTime(stay.startMs, zone),
                    title = stay.displayTitle(places),
                    detail =
                        UiText.Res(
                            R.string.timeline_stay_minutes,
                            listOf((stay.endMs - stay.startMs) / 60_000),
                        ),
                    kind = TimelineKind.STOP,
                    sortKeyMs = stay.startMs,
                )
        }

        gaps.forEach { gap ->
            entries +=
                TimelineEntry(
                    time = formatTime(gap.startMs, zone),
                    title = UiText.Res(R.string.timeline_gap_title),
                    detail =
                        UiText.Res(
                            R.string.timeline_gap_detail,
                            listOf((gap.endMs - gap.startMs) / 60_000),
                        ),
                    kind = TimelineKind.GAP,
                    sortKeyMs = gap.startMs,
                )
        }

        endMs?.let { end ->
            entries +=
                TimelineEntry(
                    time = formatTime(end, zone),
                    title = UiText.Res(R.string.timeline_end),
                    kind = TimelineKind.END,
                    sortKeyMs = end,
                )
        }

        // 时间轴按时间戳排序，同刻按类型固定顺序（开始在前、结束在后）。
        // 不能按 "HH:mm" 字符串排序：跨天或同分钟多条目时会乱。
        val ordered = entries.sortedWith(compareBy({ it.sortKeyMs }, { kindOrder(it.kind) }))

        val samples =
            points.map { point ->
                TrackPointSample(
                    timestampMs = point.timestampMs,
                    point = GeoPoint(point.latitude, point.longitude),
                    accuracyMeters = point.accuracyMeters,
                )
            }
        // 精度筛、毛刺修复、停留吸附、切段都在 TrackDisplay 里做，
        // 与今日页共用同一套，避免两处画出来的线不一样。
        // 有纠偏结果时只替换驾车时间窗，窗口外仍按原始点画。
        val segments =
            TrackDisplay.segments(
                samples,
                stays.map { it.toStaySegment() },
                route,
            )
        val geoPoints = segments.flatten()
        // 每天每个地点一个标记：同一天里「出门前在家」「回来后还在家」是两段停留，
        // 但它们是同一个地点，不该在地图上出现两个点。
        val stayPlan = StayMarkers.plan(stays.map { it.toMarkerStay(places) })

        return DayDetailUiState(
            dateKey = dateKey,
            title = formatTitle(dateKey),
            distanceKm = formatKm(day?.distanceMeters ?: 0.0),
            duration =
                formatDuration(
                    ((day?.movingMillis ?: 0L) + (day?.stoppedMillis ?: 0L)) / 1000,
                ),
            stopCount = stays.size,
            entries = ordered,
            trackSegments = segments,
            timeRange =
                if (startMs != null && endMs != null) {
                    "${formatTime(startMs, zone)} – ${formatTime(endMs, zone)}"
                } else {
                    ""
                },
            stayMarkers = stayPlan.markers,
            stayCentroids = stayPlan.centroids,
            accessibilitySummary = buildAccessibilitySummary(geoPoints, stays, segments.size),
            loading = false,
            canCorrectRoute =
                correctionClient.configured &&
                    TrackCorrection.drivingRuns(samples).isNotEmpty(),
            routeOverride = route,
        )
    }

    /**
     * 停留标题。有地点名就用地点名，比如「家」「公司」。
     *
     * 名字来自 place 表，跨天复用：同一天里两段停留只要落在同一个地点，
     * 标题就一样。
     */
    private fun StaySegmentEntity.displayTitle(places: List<PlaceSummary>): UiText {
        val label =
            placeId?.let { id -> places.firstOrNull { it.place.id == id }?.place?.label }
        return if (label.isNullOrBlank()) UiText.Res(R.string.timeline_stay) else UiText.Raw(label)
    }

    /**
     * 地图的文字版行程。
     *
     * 折线对读屏完全不可用，必须提供等价信息。
     */
    private fun buildAccessibilitySummary(
        points: List<GeoPoint>,
        stays: List<StaySegmentEntity>,
        segmentCount: Int,
    ): List<UiText> {
        if (points.isEmpty()) return emptyList()
        val parts = mutableListOf<UiText>(UiText.Res(R.string.map_summary_title))
        parts += UiText.Res(R.string.map_summary_stays, listOf(stays.size))
        parts += UiText.Res(R.string.map_summary_points, listOf(points.size))
        if (segmentCount > 1) {
            parts += UiText.Res(R.string.map_summary_segments, listOf(segmentCount))
        }
        return parts
    }

    private fun kindOrder(kind: TimelineKind): Int =
        when (kind) {
            TimelineKind.START -> 0
            TimelineKind.STOP -> 1
            TimelineKind.GAP -> 2
            TimelineKind.END -> 3
        }

    private fun formatTime(
        timestampMs: Long,
        zone: ZoneId,
    ): String {
        val time = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun formatTitle(dateKey: String): String {
        val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return dateKey
        // 走 DateLabels：星期由 DateTimeFormatter 本地化输出，
        // 不再在 Kotlin 里手写「周一…周日」数组（那也做不到换语言）。
        return DateLabels.full(date)
    }

    /** 里程格式化。统一走 [DistanceFormat]。 */
    private fun formatKm(meters: Double): String = DistanceFormat.kilometers(meters)

    /** 供测试：把毫秒时间戳格式化成 `HH:mm`。 */
    internal fun previewTime(
        timestampMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = formatTime(timestampMs, zone)

    /** 供测试：日期键转显示标题。 */
    internal fun previewTitle(dateKey: String): String = formatTitle(dateKey)
}
