package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.core.DistanceFormat
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.formatDuration
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.ui.components.map.MapStayMarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

/** 单日详情状态。 */
data class DayDetailUiState(
    val dateKey: String = "",
    val title: String = "",
    val distanceKm: String = "0.0",
    val duration: String = "0m",
    val stopCount: Int = 0,
    val entries: List<TimelineEntry> = emptyList(),
    /** 地图折线用的坐标（已按时间排序）。 */
    val trackPoints: List<GeoPoint> = emptyList(),
    /** 地图停留标记。 */
    val stayMarkers: List<MapStayMarker> = emptyList(),
    /** 地图的文字版行程，供 TalkBack 使用。 */
    val accessibilitySummary: String = "",
    val loading: Boolean = true,
) {
    val hasTrack: Boolean get() = trackPoints.isNotEmpty()
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

    /** 加载某天。重复调用同一天不会重复查询。 */
    fun load(dateKey: String) {
        if (loadedKey == dateKey) return
        loadedKey = dateKey

        viewModelScope.launch {
            _uiState.value = DayDetailUiState(dateKey = dateKey, loading = true)

            val key = DayKeys.parse(dateKey) ?: return@launch
            val day = repository.observeDay(key)
            val points = repository.pointsOfDay(key)
            val stays = repository.staysOfDay(key)
            val gaps = repository.gapsOfDay(key)

            _uiState.value = buildState(dateKey, points, stays, gaps)

            // 汇总单独订阅：采集过程中里程会持续变化
            day.collect { trackDay ->
                _uiState.value =
                    _uiState.value.copy(
                        distanceKm = formatKm(trackDay?.distanceMeters ?: 0.0),
                        duration =
                            formatDuration(
                                (
                                    (trackDay?.movingMillis ?: 0L) +
                                        (trackDay?.stoppedMillis ?: 0L)
                                ) / 1000,
                            ),
                    )
            }
        }
    }

    private fun buildState(
        dateKey: String,
        points: List<TrackPointEntity>,
        stays: List<StaySegmentEntity>,
        gaps: List<DayGapEntity>,
    ): DayDetailUiState {
        val zone = ZoneId.systemDefault()
        val entries = mutableListOf<TimelineEntry>()

        points.firstOrNull()?.let { first ->
            entries +=
                TimelineEntry(
                    time = formatTime(first.timestampMs, zone),
                    title = "开始记录",
                    detail = "",
                    kind = TimelineKind.START,
                )
        }

        stays.forEach { stay ->
            entries +=
                TimelineEntry(
                    time = formatTime(stay.startMs, zone),
                    title = stay.displayTitle(),
                    detail = "停留 ${(stay.endMs - stay.startMs) / 60_000} 分钟",
                    kind = TimelineKind.STOP,
                )
        }

        gaps.forEach { gap ->
            entries +=
                TimelineEntry(
                    time = formatTime(gap.startMs, zone),
                    title = "数据缺失",
                    detail = "约 ${(gap.endMs - gap.startMs) / 60_000} 分钟无数据",
                    kind = TimelineKind.GAP,
                )
        }

        points.lastOrNull()?.let { last ->
            entries +=
                TimelineEntry(
                    time = formatTime(last.timestampMs, zone),
                    title = "结束记录",
                    detail = "",
                    kind = TimelineKind.END,
                )
        }

        // 时间轴按时间排序，同刻按类型固定顺序（开始在前、结束在后）
        val ordered = entries.sortedWith(compareBy({ it.time }, { kindOrder(it.kind) }))

        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
        val markers =
            stays.map { stay ->
                MapStayMarker(
                    point = GeoPoint(stay.latitude, stay.longitude),
                    label = null,
                    durationMinutes = (stay.endMs - stay.startMs) / 60_000,
                )
            }

        return DayDetailUiState(
            dateKey = dateKey,
            title = formatTitle(dateKey),
            stopCount = stays.size,
            entries = ordered,
            trackPoints = geoPoints,
            stayMarkers = markers,
            accessibilitySummary = buildAccessibilitySummary(geoPoints, stays),
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
    ): String {
        if (points.isEmpty()) return ""
        return if (stays.isEmpty()) {
            "轨迹地图，共 ${points.size} 个采样点"
        } else {
            "轨迹地图，${stays.size} 处停留，共 ${points.size} 个采样点"
        }
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
