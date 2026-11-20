package com.hmilyld.daytrail.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.PlaceEntity
import com.hmilyld.daytrail.data.db.entity.RouteOverrideEntity
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

    /**
     * 观察某天的全部原始点，用于录制过程中实时绘制轨迹。
     *
     * 每次批量写入后 Room 会重新发出整个列表。
     * 一天的典型量级是 150–500 个点，重发成本可接受；
     * 而采样间隔是分钟级，刷新频率很低，不会造成抖动。
     */
    @Query("SELECT * FROM track_point WHERE day_key = :dayKey ORDER BY ts ASC")
    fun observePointsOfDay(dayKey: String): Flow<List<TrackPointEntity>>

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

    /** 有记录的日期键，升序。地点回填用。 */
    @Query("SELECT date_key FROM track_day WHERE point_count > 0 ORDER BY date_key ASC")
    suspend fun keysWithData(): List<String>

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

    /** 观察某天的断点，详情页实时刷新用。 */
    @Query("SELECT * FROM day_gap WHERE day_key = :dayKey ORDER BY start_ts ASC")
    fun observeOfDay(dayKey: String): Flow<List<DayGapEntity>>

    @Query("DELETE FROM day_gap WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM day_gap")
    suspend fun deleteAll()
}

/** 地点 + 去过的天数。地点页列表用。 */
data class PlaceSummary(
    @Embedded val place: PlaceEntity,
    @ColumnInfo(name = "visit_days") val visitDays: Int,
)

@Dao
interface PlaceDao {
    @Insert
    suspend fun insert(place: PlaceEntity): Long

    /** 全部地点，**包含**被删除的：它们还要参与匹配，否则会被重算重新创建。 */
    @Query("SELECT * FROM place")
    suspend fun all(): List<PlaceEntity>

    /** 地点页列表。按去过的天数倒序，家/公司自然排在最前。 */
    @Query(
        "SELECT p.*, (SELECT COUNT(DISTINCT s.day_key) FROM stay_segment s " +
            "WHERE s.place_id = p.id) AS visit_days " +
            "FROM place p WHERE p.suppressed = 0 " +
            "ORDER BY visit_days DESC, p.last_seen_ms DESC",
    )
    fun observeSummaries(): Flow<List<PlaceSummary>>

    /** 单个地点的详情。地图页用。 */
    @Query(
        "SELECT p.*, (SELECT COUNT(DISTINCT s.day_key) FROM stay_segment s " +
            "WHERE s.place_id = p.id) AS visit_days " +
            "FROM place p WHERE p.id = :id",
    )
    fun observeSummary(id: Long): Flow<PlaceSummary?>

    @Query("UPDATE place SET label = :label, label_source = :source WHERE id = :id")
    suspend fun rename(
        id: Long,
        label: String,
        source: String,
    )

    /**
     * 只在「还没有名字」时写入。
     *
     * 逆地理编码是自动触发的，绝不能覆盖用户自己起的名字——把条件写进 SQL 里，
     * 比先查再改更省事，也不会和用户的改名操作打架。
     */
    @Query(
        "UPDATE place SET label = :label, label_source = :source " +
            "WHERE id = :id AND (label IS NULL OR label = '')",
    )
    suspend fun renameIfUnnamed(
        id: Long,
        label: String,
        source: String,
    )

    /** 未命名且未被删除的地点，供「补全名称」使用。 */
    @Query("SELECT * FROM place WHERE suppressed = 0 AND (label IS NULL OR label = '')")
    suspend fun unnamed(): List<PlaceEntity>

    /** 「删除」只是隐藏：行留着参与匹配，见 [PlaceEntity.suppressed]。 */
    @Query("UPDATE place SET suppressed = 1 WHERE id = :id")
    suspend fun suppress(id: Long)

    @Query("UPDATE place SET last_seen_ms = :lastSeenMs WHERE id = :id")
    suspend fun touch(
        id: Long,
        lastSeenMs: Long,
    )

    @Query("SELECT COUNT(*) FROM place")
    suspend fun count(): Int

    @Query("DELETE FROM place")
    suspend fun deleteAll()
}

@Dao
interface RouteOverrideDao {
    @Upsert
    suspend fun upsert(route: RouteOverrideEntity)

    @Query("SELECT * FROM route_override WHERE day_key = :dayKey")
    suspend fun ofDay(dayKey: String): RouteOverrideEntity?

    @Query("SELECT * FROM route_override WHERE day_key = :dayKey")
    fun observeOfDay(dayKey: String): Flow<RouteOverrideEntity?>

    @Query("DELETE FROM route_override WHERE day_key = :dayKey")
    suspend fun deleteDay(dayKey: String)

    @Query("DELETE FROM route_override")
    suspend fun deleteAll()
}
