package com.hmilyld.daytrail.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 历史页的日卡片。 */
data class DayCard(
    val dateKey: String,
    val title: String,
    val distanceKm: Double,
    val heatLevel: Int,
)

/** 历史页状态。 */
data class HistoryUiState(
    val year: Int,
    val month: Int,
    /** 当月每天的里程，索引 0 对应 1 号。 */
    val monthlyKm: List<Double> = emptyList(),
    val days: List<DayCard> = emptyList(),
)

/**
 * 历史页 ViewModel。
 *
 * 月历热力与日卡片都从同一份 `track_day` 派生，
 * 不额外扫原始点，保证历史页在数据量大时依然流畅。
 */
class HistoryViewModel(
    repository: TrackRepository,
) : ViewModel() {
    private val today = LocalDate.now()
    private val year = today.year
    private val month = today.monthValue

    val uiState: StateFlow<HistoryUiState> =
        repository
            .observeDaysWithData()
            .map { rows -> buildState(rows, year, month) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HistoryUiState(year = year, month = month),
            )

    private fun buildState(
        rows: List<TrackDayEntity>,
        year: Int,
        month: Int,
    ): HistoryUiState {
        val monthKeys = DayKeys.daysInMonth(year, month)
        val byKey = rows.associateBy { it.dateKey }

        val monthlyKm =
            monthKeys.map { key ->
                (byKey[key.value]?.distanceMeters ?: 0.0) / 1000.0
            }

        val days =
            rows.map { row ->
                val date = runCatching { LocalDate.parse(row.dateKey) }.getOrNull()
                DayCard(
                    dateKey = row.dateKey,
                    title = date?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: row.dateKey,
                    distanceKm = row.distanceMeters / 1000.0,
                    heatLevel = heatLevelOf(row.distanceMeters / 1000.0),
                )
            }

        return HistoryUiState(
            year = year,
            month = month,
            monthlyKm = monthlyKm,
            days = days,
        )
    }

    companion object {
        /** 里程分档。阈值按出差日的典型里程设定。 */
        fun heatLevelOf(km: Double): Int =
            when {
                km <= 0.0 -> 0
                km < 10.0 -> 1
                km < 30.0 -> 2
                km < 60.0 -> 3
                else -> 4
            }

        /** 月历首日之前需要留白的格子数（周一为一周起点）。 */
        fun leadingBlanks(
            year: Int,
            month: Int,
        ): Int {
            val first = LocalDate.of(year, month, 1)
            // DayOfWeek: MONDAY=1 ... SUNDAY=7
            return first.dayOfWeek.value - 1
        }

        /** 当月需要显示的总格子数，向上取整到整周。 */
        fun cellCount(
            year: Int,
            month: Int,
        ): Int {
            val total = leadingBlanks(year, month) + LocalDate.of(year, month, 1).lengthOfMonth()
            return ((total + 6) / 7) * 7
        }

        /** 供测试与占位：某天的 DayKey。 */
        fun keyOf(
            year: Int,
            month: Int,
            day: Int,
        ): DayKey = DayKey("%04d-%02d-%02d".format(year, month, day))
    }
}
