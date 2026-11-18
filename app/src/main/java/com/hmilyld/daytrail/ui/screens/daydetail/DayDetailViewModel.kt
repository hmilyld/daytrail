package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.formatDuration
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.domain.TrackDisplay
import com.hmilyld.daytrail.domain.TrackPointSample
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
    val title: String,
    val detail: String,
    val kind: TimelineKind,
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
    /** 地图停留标记。 */
    val stayMarkers: List<GeoPoint> = emptyList(),
    /** 地图的文字版行程，供 TalkBack 使用。 */
    val accessibilitySummary: String = "",
    val loading: Boolean = true,
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
            ) { points, stays, gaps, day ->
                buildState(dateKey, points, stays, gaps, day)
            }.collect { state -> _uiState.value = state }
        }
    }

    private fun buildState(
        dateKey: String,
        points: List<TrackPointEntity>,
        stays: List<StaySegmentEntity>,
        gaps: List<DayGapEntity>,
        day: TrackDayEntity?,
    ): DayDetailUiState {
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
                    title = "开始记录",
                    detail = "",
                    kind = TimelineKind.START,
                    sortKeyMs = start,
                )
        }

        stays.forEach { stay ->
            entries +=
                TimelineEntry(
                    time = formatTime(stay.startMs, zone),
                    title = stay.displayTitle(),
                    detail = "停留 ${(stay.endMs - stay.startMs) / 60_000} 分钟",
                    kind = TimelineKind.STOP,
                    sortKeyMs = stay.startMs,
                )
        }

        gaps.forEach { gap ->
            entries +=
                TimelineEntry(
                    time = formatTime(gap.startMs, zone),
                    title = "这段时间没有记录",
                    detail = "约 ${(gap.endMs - gap.startMs) / 60_000} 分钟没有采样点",
                    kind = TimelineKind.GAP,
                    sortKeyMs = gap.startMs,
                )
        }

        endMs?.let { end ->
            entries +=
                TimelineEntry(
                    time = formatTime(end, zone),
                    title = "结束记录",
                    detail = "",
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
        // 精度筛、毛刺修复、切段都在 TrackDisplay 里做，
        // 与今日页共用同一套，避免两处画出来的线不一样。
        val segments = TrackDisplay.segments(samples)
        val geoPoints = segments.flatten()
        val markers =
            stays
                .filter { it.endMs - it.startMs >= TrackDisplay.MIN_STAY_MARKER_MS }
                .map { stay -> GeoPoint(stay.latitude, stay.longitude) }

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
            stayMarkers = markers,
            accessibilitySummary = buildAccessibilitySummary(geoPoints, stays, segments.size),
            loading = false,
        )
    }

    /** 停留点的显示标题。M2-B 接入地点命名后改为返回真实名称。 */
    private fun StaySegmentEntity.displayTitle(): String = "停留"

    /**
     * 地图的文字版行程。
     *
     * 折线对读屏完全不可用，必须提供等价信息。
     */
    private fun buildAccessibilitySummary(
        points: List<GeoPoint>,
        stays: List<StaySegmentEntity>,
        segmentCount: Int,
    ): String {
        if (points.isEmpty()) return ""
        val parts = mutableListOf("轨迹地图")
        parts += "${stays.size} 处停留"
        parts += "${points.size} 个采样点"
        if (segmentCount > 1) {
            parts += "分为 $segmentCount 段"
        }
        return parts.joinToString("，")
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
        val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return "${date.monthValue}月${date.dayOfMonth}日 ${weekdays[date.dayOfWeek.value - 1]}"
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
