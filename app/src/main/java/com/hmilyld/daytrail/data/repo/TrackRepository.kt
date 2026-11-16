package com.hmilyld.daytrail.data.repo

import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
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

    // ---- 维护 ----

    /** 删除某天全部数据。 */
    suspend fun deleteDay(dayKey: DayKey)

    /** 清空全部数据。 */
    suspend fun deleteAll()

    /** 原始点总数，用于数据占用显示。 */
    suspend fun totalPointCount(): Int
}
