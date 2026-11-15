package com.hmilyld.daytrail.ui.screens.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.core.formatDuration
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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
    val points: List<TrackPointEntity> = emptyList(),
    val loading: Boolean = true,
)

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

            _uiState.value =
                buildState(
                    dateKey = dateKey,
                    day = null,
                    points = points,
                    stays = stays,
                    gaps = gaps,
                ).copy(loading = false)

            // 顺便读一次汇总，用于标题行的里程
            day.collect { trackDay ->
                _uiState.value =
                    _uiState.value.copy(
                        distanceKm = formatKm(trackDay?.distanceMeters ?: 0.0),
                        duration =
                            formatDuration(
                                ((trackDay?.movingMillis ?: 0L) + (trackDay?.stoppedMillis ?: 0L)) /
                                    1000,
                            ),
                    )
            }
        }
    }

    private fun buildState(
        dateKey: String,
        day: TrackDayEntity?,
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
                    title = if (stay.labelSource == "unnamed") "停留" else "停留",
                    detail = "停留 ${stay.durationMinutes()} 分钟",
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

        return DayDetailUiState(
            dateKey = dateKey,
            title = formatTitle(dateKey),
            distanceKm = formatKm(day?.distanceMeters ?: 0.0),
            duration =
                formatDuration(
                    ((day?.movingMillis ?: 0L) + (day?.stoppedMillis ?: 0L)) / 1000,
                ),
            stopCount = stays.size,
            entries = entries,
            points = points,
        )
    }

    private fun StaySegmentEntity.durationMinutes(): Long = (endMs - startMs) / 60_000

    private fun formatTime(
        timestampMs: Long,
        zone: ZoneId,
    ): String {
        val time =
            java.time.Instant
                .ofEpochMilli(timestampMs)
                .atZone(zone)
                .toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun formatTitle(dateKey: String): String {
        val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return dateKey
        val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return "${date.monthValue}月${date.dayOfMonth}日 ${weekdays[date.dayOfWeek.value - 1]}"
    }

    private fun formatKm(meters: Double): String {
        val km = meters / 1000.0
        return if (km >= 10) "%.0f".format(km) else "%.1f".format(km)
    }

    /** 供预览与测试。 */
    internal fun previewDateTime(dateKey: String): LocalDateTime? =
        runCatching { LocalDate.parse(dateKey).atStartOfDay() }.getOrNull()
}
