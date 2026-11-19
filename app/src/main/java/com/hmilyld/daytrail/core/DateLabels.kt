package com.hmilyld.daytrail.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期文案。
 *
 * 放在一处而不是各页面各写一遍：早期「11月18日」在历史列表、单日详情、
 * 导出文件名里各拼了一次，改格式时要改三处。
 *
 * 月份与星期走 `DateTimeFormatter` 的本地化输出，不在 Kotlin 里拼中文字面量。
 */
object DateLabels {
    private val dayWithWeekday: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)

    private val dayOnly: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)

    private val weekdayOnly: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE", Locale.SIMPLIFIED_CHINESE)

    private val timeOfDay: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** `11月18日 周二`。 */
    fun full(date: LocalDate): String = date.format(dayWithWeekday)

    /** `11月18日`。 */
    fun short(date: LocalDate): String = date.format(dayOnly)

    /** `周二`。 */
    fun weekday(date: LocalDate): String = date.format(weekdayOnly)

    /** `09:41`。时间轴与地图摘要共用，避免一处补零一处不补。 */
    fun time(timestampMs: Long): String =
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).format(timeOfDay)
}
