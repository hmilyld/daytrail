package com.hmilyld.daytrail.core

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 里程格式化的唯一入口。
 *
 * 为什么集中在一处：这个数字出现在今日页、历史卡片、单日详情三个地方，
 * 分散实现很容易出现「8.5」与「8.50」不一致的粗糙感。
 *
 * 规则：10 公里以内保留一位小数（步行、市内通勤的精度有意义），
 * 10 公里以上取整（长途不需要小数，取整更易读）。
 */
object DistanceFormat {
    /** 切换到取整显示的阈值（公里）。 */
    private const val INTEGER_THRESHOLD_KM = 10.0

    /**
     * 输入米，输出公里字符串。
     *
     * 先四舍五入到一位小数再判断阈值：否则 9999 米会显示成 `10.0`，
     * 而 10000 米显示成 `10`，同一个数字出现两种写法。
     */
    fun kilometers(meters: Double): String {
        val km = (meters / 1000.0).coerceAtLeast(0.0)
        val rounded = Math.round(km * 10.0) / 10.0
        return if (rounded >= INTEGER_THRESHOLD_KM) {
            String.format(Locale.US, "%.0f", rounded)
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    /** 输入米，输出带单位的字符串，例如 `8.5 km`。 */
    fun withUnit(meters: Double): String = "${kilometers(meters)} km"

    /**
     * 输入米，输出取整到 10 米的米数。
     *
     * 只用于「距此 260 米」这类近距离描述：地点之间的距离是 GPS 质心差，
     * 个位数上的精度没有意义，取整到 10 米反而更诚实。
     */
    fun roundedMeters(meters: Double): Int = (meters / 10.0).roundToInt() * 10
}
