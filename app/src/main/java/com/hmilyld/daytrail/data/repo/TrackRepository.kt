package com.hmilyld.daytrail.data.repo

import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.domain.RouteOverride
import kotlinx.coroutines.flow.Flow

/**
 * 轨迹数据的唯一入口。
 *
 * 这是**云端接缝 ①**：上层（ViewModel / Service）只依赖这个接口，
 * 二期接入云端时只新增一个 `RemoteBackedTrackRepository`，
 * 不需要改任何调用方。
 */
interface TrackRepository {
    // ---- 写入 ----

    /** 批量写入原始点。返回实际插入条数。 */
    suspend fun appendPoints(points: List<TrackPointEntity>): Int

    /** 重算某天的汇总与停留段，并写入断点。 */
    suspend fun recomputeDay(dayKey: DayKey)

    // ---- 读取 ----

    /** 某天的汇总。 */
    fun observeDay(dayKey: DayKey): Flow<TrackDayEntity?>

    /** 某天全部原始点，按时间升序。 */
    suspend fun pointsOfDay(dayKey: DayKey): List<TrackPointEntity>

    /**
     * 观察某天的原始点，录制中实时绘制轨迹用。
     *
     * 这是今日页能看到自己走过的路的前提。
     * 若不提供这个流，ViewModel 只能拿到一次快照，
     * 录制过程中地图上不会出现新的轨迹。
     */
    fun observePoints(dayKey: DayKey): Flow<List<TrackPointEntity>>

    /** 某天的停留段。 */
    suspend fun staysOfDay(dayKey: DayKey): List<StaySegmentEntity>

    /** 某天的停留段，可观察。今日页与单日详情用。 */
    fun observeStays(dayKey: DayKey): Flow<List<StaySegmentEntity>>

    /** 某天的断点。 */
    suspend fun gapsOfDay(dayKey: DayKey): List<DayGapEntity>

    /** 某天的断点，可观察。详情页实时刷新用。 */
    fun observeGaps(dayKey: DayKey): Flow<List<DayGapEntity>>

    /** 日期区间内的汇总，月历用。 */
    fun observeRange(
        from: DayKey,
        to: DayKey,
    ): Flow<List<TrackDayEntity>>

    /** 有记录的全部日期，倒序。 */
    fun observeDaysWithData(): Flow<List<TrackDayEntity>>

    /** 今日页实时点数。 */
    fun observePointCount(dayKey: DayKey): Flow<Int>

    /** 全局最后一个点，跨天接续用。 */
    suspend fun lastPoint(): TrackPointEntity?

    // ---- 地点 ----

    /**
     * 地点列表（含去过的天数），按天数倒序。
     *
     * 地点是**派生物**：由 [`recomputeDay`] 把每天的停留聚到最近的已有地点，
     * 没有匹配的就新建一个。锚点一旦确定就不再漂移，这样「家」在地图上
     * 每天都是同一个坐标。
     */
    fun observePlaces(): Flow<List<PlaceSummary>>

    /** 给地点改名。写入后所有引用这个地点的停留一起显示新名字。 */
    suspend fun renamePlace(
        id: Long,
        label: String,
    )

    /** 删除地点。只隐藏，保留行继续参与匹配，避免被下一次重算重新创建。 */
    suspend fun suppressPlace(id: Long)

    /**
     * 用逆地理编码给所有未命名地点补名字，返回补成功的个数。
     *
     * 这是**联网**操作，只有用户在列表页点「补全名称」时才会调用。
     */
    suspend fun backfillPlaceNames(): Int

    /** 单个地点的详情（含去过天数）。 */
    fun observePlace(id: Long): Flow<PlaceSummary?>

    /**
     * 手动新增地点。
     *
     * 和自动聚出来的地点一样参与停留归并：以后在附近停留会自动挂到它下面。
     * 手动地点没有「去过」记录，`visitDays` 从 0 开始。
     */
    suspend fun addPlace(
        point: GeoPoint,
        label: String,
    ): Long

    /**
     * 老数据回填：数据库还是一个地点都没有、但已经有多天停留时，
     * 把已有日期重算一遍，补上地点归属。
     *
     * 为什么需要它：升级前录的停留 `place_id` 是空的，而 [recomputeDay]
     * 只在新数据写入时触发，不补的话地点页会一直空着。
     * 幂等，已经有地点时立刻返回。
     */
    suspend fun backfillPlaces()

    // ---- 纠偏 ----

    /** 纠偏后的路线。没有就是 null。 */
    fun observeRoute(dayKey: DayKey): Flow<RouteOverride?>

    /** 保存纠偏结果。不覆盖原始点，也不改里程。 */
    suspend fun saveRoute(
        dayKey: DayKey,
        route: RouteOverride,
    )

    /** 清除纠偏结果，回到原始折线。 */
    suspend fun clearRoute(dayKey: DayKey)

    // ---- 维护 ----

    /** 删除某天全部数据。 */
    suspend fun deleteDay(dayKey: DayKey)

    /** 清空全部数据。 */
    suspend fun deleteAll()

    /** 原始点总数，用于数据占用显示。 */
    suspend fun totalPointCount(): Int
}
