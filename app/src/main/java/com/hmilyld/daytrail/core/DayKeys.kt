package com.hmilyld.daytrail.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 一天的日期键，格式 `yyyy-MM-dd`，按设备本地时区划分。 */
@JvmInline
value class DayKey(
    val value: String,
) {
    override fun toString(): String = value
}

/**
 * 时间与日期换算。
 *
 * 跨天一律以设备本地时区的 0 点为界：23:58 还在路上会自然分成两天，
 * 不做「跨天归并」的特殊处理，否则日统计和月历都会失真。
 */
object DayKeys {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 从毫秒时间戳得到本地日期键。 */
    fun of(
        timestampMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DayKey {
        val date = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
        return DayKey(FORMATTER.format(date))
    }

    /** 从 `yyyy-MM-dd` 字符串解析，非法输入返回 null。 */
    fun parse(value: String): DayKey? =
        runCatching { LocalDate.parse(value, FORMATTER) }.getOrNull()?.let {
            DayKey(FORMATTER.format(it))
        }

    /** 该日期本地时间的 0 点，毫秒时间戳。 */
    fun startOfDay(
        dayKey: DayKey,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long =
        LocalDate
            .parse(dayKey.value, FORMATTER)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /** 该日期之后一天（不含）的 0 点，用于区间查询的右开边界。 */
    fun startOfNextDay(
        dayKey: DayKey,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long =
        LocalDate
            .parse(dayKey.value, FORMATTER)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /** 月历需要的当月所有日期键，从 1 号到月末。 */
    fun daysInMonth(
        year: Int,
        month: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayKey> {
        val first = LocalDate.of(year, month, 1)
        val length = first.lengthOfMonth()
        return (0 until length).map { offset ->
            DayKey(FORMATTER.format(first.plusDays(offset.toLong())))
        }
    }
}

/**
 * 把毫秒时长格式化成紧凑的中文时长，如 `2h14m`、`43m`、`0m`。
 *
 * 刻意不显示秒：本应用的采样间隔是分钟级，秒数没有意义。
 */
fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}
