package com.hmilyld.daytrail.core

/**
 * 里程分档。
 *
 * 热力日历与日列表右侧的里程条共用同一套分档，两处颜色必须一致，
 * 否则同一个 30 公里在读日历和读列表时会是两种深浅。
 *
 * 阈值按出差日的典型里程设定：10 公里以内是通勤，60 公里以上算长途。
 */
object HeatLevel {
    const val NONE = 0
    const val LOW = 1
    const val MEDIUM = 2
    const val HIGH = 3
    const val MAX = 4

    fun of(km: Double): Int =
        when {
            km <= 0.0 -> NONE
            km < 10.0 -> LOW
            km < 30.0 -> MEDIUM
            km < 60.0 -> HIGH
            else -> MAX
        }

    /** 里程条宽度占比。0 档不画条。 */
    fun fraction(level: Int): Float =
        when (level) {
            NONE -> 0f
            LOW -> 0.25f
            MEDIUM -> 0.5f
            HIGH -> 0.75f
            else -> 1f
        }
}
