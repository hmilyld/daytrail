package com.hmilyld.daytrail.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** 历史页的日卡片。 */
data class DayCard(
    val dateKey: String,
    val title: String,
    val distanceKm: Double,
    val heatLevel: Int,
)

/** 历史页状态。 */
data class HistoryUiState(
    val year: Int = 0,
    val month: Int = 0,
    /** 当月每天的里程，索引 0 对应 1 号。 */
    val monthlyKm: List<Double> = emptyList(),
    /** 当月有记录的日卡片，按日期倒序。 */
    val days: List<DayCard> = emptyList(),
    /** 是否还能往后翻。已经是当月时不能再往后。 */
    val canGoNext: Boolean = false,
    /** 当月总里程，展示用。 */
    val monthTotalKm: Double = 0.0,
)

/**
 * 历史页 ViewModel。
 *
 * 支持按月翻阅：早期版本把年月写成 `val`，月历永远停在当月，
 * 用满一个月之后旧数据只能在列表里翻，月历热力完全失效。
 *
 * 数据按「当前选中月」的日期区间查询，不扫全表；
 * 月历热力与日卡片都从同一份查询结果派生，两者永远一致。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: TrackRepository,
) : ViewModel() {
    private val currentMonth: YearMonth = YearMonth.now()

    /** 当前选中的月份。默认当月。 */
    private val selectedMonth = MutableStateFlow(currentMonth)

    val uiState: StateFlow<HistoryUiState> =
        selectedMonth
            .flatMapLatest { month ->
                val from = DayKey("%04d-%02d-01".format(month.year, month.monthValue))
                val to =
                    DayKey(
                        "%04d-%02d-%02d".format(
                            month.year,
                            month.monthValue,
                            month.lengthOfMonth(),
                        ),
                    )
                combine(
                    repository.observeRange(from, to),
                    selectedMonth,
                ) { rows, monthState ->
                    buildState(rows, monthState)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    HistoryUiState(
                        year = currentMonth.year,
                        month = currentMonth.monthValue,
                        canGoNext = false,
                    ),
            )

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        // 不允许翻到未来
        val next = selectedMonth.value.plusMonths(1)
        if (next <= currentMonth) {
            selectedMonth.value = next
        }
    }

    private fun buildState(
        rows: List<TrackDayEntity>,
        month: YearMonth,
    ): HistoryUiState {
        val byKey = rows.associateBy { it.dateKey }
        val monthKeyList = DayKeys.daysInMonth(month.year, month.monthValue)

        val monthlyKm =
            monthKeyList.map { key -> (byKey[key.value]?.distanceMeters ?: 0.0) / 1000.0 }

        val days =
            rows
                .filter { it.pointCount > 0 }
                .sortedByDescending { it.dateKey }
                .map { row ->
                    val date = runCatching { LocalDate.parse(row.dateKey) }.getOrNull()
                    DayCard(
                        dateKey = row.dateKey,
                        title = date?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: row.dateKey,
                        distanceKm = row.distanceMeters / 1000.0,
                        heatLevel = heatLevelOf(row.distanceMeters / 1000.0),
                    )
                }

        return HistoryUiState(
            year = month.year,
            month = month.monthValue,
            monthlyKm = monthlyKm,
            days = days,
            canGoNext = month < currentMonth,
            monthTotalKm = monthlyKm.sum(),
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

        /**
         * 当月 1 号是周几之前需要留白的格子数（周一为一周起点）。
         *
         * 用 `value - 1`：DayOfWeek.MONDAY=1 … SUNDAY=7。
         */
        fun leadingBlanks(
            year: Int,
            month: Int,
        ): Int {
            val first = LocalDate.of(year, month, 1)
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
    }
}
