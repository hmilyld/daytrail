package com.hmilyld.daytrail.data.repo

import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.data.db.DayTrailDatabase
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.domain.DayAggregator
import com.hmilyld.daytrail.domain.GapDetector
import com.hmilyld.daytrail.domain.StayDetector
import com.hmilyld.daytrail.domain.TrackPointSample
import kotlinx.coroutines.flow.Flow

/**
 * 纯本地的 [TrackRepository] 实现。
 *
 * 所有写操作都在这里收口，上层不直接碰 DAO。
 * `recomputeDay` 是幂等的：重复调用只会得到相同结果，
 * 因此可以在每次批量写入后无条件调用，不需要维护增量状态。
 */
class LocalTrackRepository(
    private val database: DayTrailDatabase,
    private val stayDetector: StayDetector = StayDetector(),
    private val gapDetector: GapDetector = GapDetector(),
) : TrackRepository {
    private val pointDao = database.trackPointDao()
    private val dayDao = database.trackDayDao()
    private val stayDao = database.staySegmentDao()
    private val gapDao = database.dayGapDao()

    override suspend fun appendPoints(points: List<TrackPointEntity>): Int {
        if (points.isEmpty()) return 0
        val ids = pointDao.insertAll(points)
        return ids.count { it > 0 }
    }

    override suspend fun recomputeDay(dayKey: DayKey) {
        val entities = pointDao.pointsOfDay(dayKey.value)
        val samples = entities.map { TrackPointSample(it.timestampMs, it.toGeoPoint()) }

        val stats = DayAggregator.aggregate(samples)
        dayDao.upsert(
            TrackDayEntity(
                dateKey = dayKey.value,
                distanceMeters = stats.distanceMeters,
                movingMillis = stats.movingMillis,
                stoppedMillis = stats.stoppedMillis,
                pointCount = stats.pointCount,
                startedAt = stats.startedAt,
                endedAt = stats.endedAt,
            ),
        )

        // 停留段与断点都是「按天全量重算」，先清后写，避免累积脏数据
        stayDao.deleteDay(dayKey.value)
        val stays =
            stayDetector
                .detect(samples.map { StayDetector.TimedPoint(it.timestampMs, it.point) })
                .map { stay ->
                    StaySegmentEntity(
                        dayKey = dayKey.value,
                        startMs = stay.startMs,
                        endMs = stay.endMs,
                        latitude = stay.point.latitude,
                        longitude = stay.point.longitude,
                        radiusMeters = stay.radiusMeters,
                    )
                }
        if (stays.isNotEmpty()) {
            stayDao.insertAll(stays)
        }

        gapDao.deleteDay(dayKey.value)
        val gaps =
            gapDetector.detect(samples).map { gap ->
                DayGapEntity(
                    dayKey = dayKey.value,
                    startMs = gap.startMs,
                    endMs = gap.endMs,
                    reason = gap.reason,
                )
            }
        if (gaps.isNotEmpty()) {
            gapDao.insertAll(gaps)
        }
    }

    override fun observeDay(dayKey: DayKey): Flow<TrackDayEntity?> = dayDao.observe(dayKey.value)

    override suspend fun pointsOfDay(dayKey: DayKey): List<TrackPointEntity> =
        pointDao.pointsOfDay(dayKey.value)

    override suspend fun staysOfDay(dayKey: DayKey): List<StaySegmentEntity> =
        stayDao.ofDay(dayKey.value)

    override fun observeStays(dayKey: DayKey): Flow<List<StaySegmentEntity>> =
        stayDao.observeOfDay(dayKey.value)

    override suspend fun gapsOfDay(dayKey: DayKey): List<DayGapEntity> = gapDao.ofDay(dayKey.value)

    override fun observeRange(
        from: DayKey,
        to: DayKey,
    ): Flow<List<TrackDayEntity>> = dayDao.observeRange(from.value, to.value)

    override fun observeDaysWithData(): Flow<List<TrackDayEntity>> = dayDao.observeAllWithData()

    override fun observePointCount(dayKey: DayKey): Flow<Int> =
        pointDao.observePointCount(dayKey.value)

    override suspend fun lastPoint(): TrackPointEntity? = pointDao.lastPoint()

    override suspend fun deleteDay(dayKey: DayKey) {
        pointDao.deleteDay(dayKey.value)
        stayDao.deleteDay(dayKey.value)
        gapDao.deleteDay(dayKey.value)
        dayDao.deleteDay(dayKey.value)
    }

    override suspend fun deleteAll() {
        pointDao.deleteAll()
        stayDao.deleteAll()
        gapDao.deleteAll()
        dayDao.deleteAll()
    }

    override suspend fun totalPointCount(): Int = pointDao.totalPointCount()
}

/** 实体到领域模型的转换。集中放这里，避免各处重复写。 */
internal fun TrackPointEntity.toGeoPoint() =
    com.hmilyld.daytrail.core
        .GeoPoint(latitude = latitude, longitude = longitude)

/** 今天（本地时区）的日期键。 */
fun todayKey(): DayKey = DayKeys.of(System.currentTimeMillis())
