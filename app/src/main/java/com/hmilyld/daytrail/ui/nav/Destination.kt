package com.hmilyld.daytrail.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 导航目的地。全部为 @Serializable，Navigation 3 依赖它做状态保存与恢复。
 *
 * 顶层三页（今日 / 历史 / 我的）用 [TopLevelDestination] 描述，
 * 详情页用独立的 NavKey。
 */
sealed interface Destination : NavKey {
    /** 今日：地图 + 实时统计 */
    @Serializable
    data object Today : Destination

    /** 历史：月历热力 + 日列表 */
    @Serializable
    data object History : Destination

    /** 我的：设置 */
    @Serializable
    data object Settings : Destination

    /** 单日详情 */
    @Serializable
    data class DayDetail(
        val dateKey: String,
    ) : Destination

    /** 地点管理。从设置进入。 */
    @Serializable
    data object Places : Destination

    /** 地点详情：在地图上标出位置，可改名/删除。 */
    @Serializable
    data class PlaceDetail(
        val placeId: Long,
    ) : Destination

    /** 地图选点新增地点。 */
    @Serializable
    data object PlacePicker : Destination

    /** 诊断日志。 */
    @Serializable
    data object Diagnostics : Destination

    /** 行程详情 */
    @Serializable
    data class TripDetail(
        val tripId: Long,
    ) : Destination

    /** 首启引导 */
    @Serializable
    data object Onboarding : Destination

    /** 权限自检。随时可进入，用于排查记录异常。 */
    @Serializable
    data object Permissions : Destination
}

/** 顶层导航项：决定底部导航栏 / 导航栏的条目。 */
enum class TopLevelDestination(
    val destination: Destination,
    val labelRes: Int,
) {
    TODAY(Destination.Today, com.hmilyld.daytrail.R.string.nav_today),
    HISTORY(Destination.History, com.hmilyld.daytrail.R.string.nav_history),

    /**
     * 地点。
     *
     * 从设置里提出来做一级入口：时间轴的「家 / 公司」由它决定，
     * 埋在设置第四层时，用户根本不会去命名地点，时间轴就永远只有「停留」。
     */
    PLACES(Destination.Places, com.hmilyld.daytrail.R.string.places_title),
    ME(Destination.Settings, com.hmilyld.daytrail.R.string.nav_me),
}
