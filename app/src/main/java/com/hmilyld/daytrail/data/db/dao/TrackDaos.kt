package com.hmilyld.daytrail.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    /** 批量写入。冲突忽略，重复点已在过滤器里挡掉，这里是兜底。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<TrackPointEntity>): List<Long>

    /** 按天取全部原始点，按时间升序。渲染轨迹用。 */
    @Query("SELECT * FROM track_point WHERE day_key = :dayKey ORDER BY ts ASC")
    suspend fun pointsOfDay(dayKey: String): List<TrackPointEntity>

    /** 观察某天的点数变化，用于今日页实时刷新。 */
    @Query("SELECT COUNT(*) FROM track_point WHERE day_key = :dayKey")
    fun observePointCount(dayKey: String): Flow<Int>

    /** 某天最后一个点，用于接续采样。 */
    @Query("SELECT * FROM track_point WHERE day_key = :dayKey ORDER BY ts DESC LIMIT 1")
    suspend fun lastPointOfDay(dayKey: String): TrackPointEntity?

    /** 全局最后一个点，跨天接续时用。 */
    @Query("SELECT * FROM track_point ORDER BY ts DESC LIMIT 1")
    suspend fun lastPoint(): TrackPointEntity?

    /** 某天的时间范围，用于断点检测。 */
    @Query("SELECT MIN(ts) FROM track_point WHERE day_key = :dayKey")
    suspend fun firstTimestampOfDay(dayKey: String): Long?

    @Query("SELECT MAX(ts) FROM track_point WHERE day_key = :dayKey")
    suspend fun lastTimestampOfDay(dayKey: String): Long?

    /** 数据占用统计。 */
    @Query("SELECT COUNT(*) FROM track_point")
    suspend fun totalPointCount(): Int

    @Query("DELETE FROM track_point WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM track_point")
    suspend fun deleteAll()
}

@Dao
interface TrackDayDao {
    @Upsert
    suspend fun upsert(day: TrackDayEntity)

    @Query("SELECT * FROM track_day WHERE date_key = :dateKey")
    suspend fun findByKey(dateKey: String): TrackDayEntity?

    @Query("SELECT * FROM track_day WHERE date_key = :dateKey")
    fun observe(dateKey: String): Flow<TrackDayEntity?>

    /** 月历用：取一个日期区间的汇总，按日期升序。 */
    @Query(
        "SELECT * FROM track_day WHERE date_key BETWEEN :fromKey AND :toKey ORDER BY date_key ASC",
    )
    suspend fun range(
        fromKey: String,
        toKey: String,
    ): List<TrackDayEntity>

    @Query(
        "SELECT * FROM track_day WHERE date_key BETWEEN :fromKey AND :toKey ORDER BY date_key ASC",
    )
    fun observeRange(
        fromKey: String,
        toKey: String,
    ): Flow<List<TrackDayEntity>>

    /** 有记录的全部日期，倒序。历史页列表用。 */
    @Query("SELECT * FROM track_day WHERE point_count > 0 ORDER BY date_key DESC")
    fun observeAllWithData(): Flow<List<TrackDayEntity>>

    @Query("DELETE FROM track_day WHERE date_key = :dateKey")
    suspend fun deleteDay(dateKey: String)

    @Query("DELETE FROM track_day")
    suspend fun deleteAll()
}

@Dao
interface StaySegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<StaySegmentEntity>)

    @Query("SELECT * FROM stay_segment WHERE day_key = :dayKey ORDER BY start_ts ASC")
    suspend fun ofDay(dayKey: String): List<StaySegmentEntity>

    @Query("SELECT * FROM stay_segment WHERE day_key = :dayKey ORDER BY start_ts ASC")
    fun observeOfDay(dayKey: String): Flow<List<StaySegmentEntity>>

    @Query("DELETE FROM stay_segment WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM stay_segment")
    suspend fun deleteAll()
}

@Dao
interface DayGapDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gaps: List<DayGapEntity>)

    @Query("SELECT * FROM day_gap WHERE day_key = :dayKey ORDER BY start_ts ASC")
    suspend fun ofDay(dayKey: String): List<DayGapEntity>

    @Query("DELETE FROM day_gap WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM day_gap")
    suspend fun deleteAll()
}
