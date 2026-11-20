package com.hmilyld.daytrail.data.repo

import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.DayKeys
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.DayTrailDatabase
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.LabelSource
import com.hmilyld.daytrail.data.db.entity.PlaceEntity
import com.hmilyld.daytrail.data.db.entity.RouteOverrideEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.geocode.PlaceAutoNamer
import com.hmilyld.daytrail.domain.DayAggregator
import com.hmilyld.daytrail.domain.GapDetector
import com.hmilyld.daytrail.domain.PlaceResolver
import com.hmilyld.daytrail.domain.RouteOverride
import com.hmilyld.daytrail.domain.StayDetector
import com.hmilyld.daytrail.domain.StaySegment
import com.hmilyld.daytrail.domain.TrackPointSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    /** 新建地点时顺手补个名字。为空表示不做逆地理编码（单测里就是空的）。 */
    private val autoNamer: PlaceAutoNamer? = null,
) : TrackRepository {
    private val pointDao = database.trackPointDao()
    private val dayDao = database.trackDayDao()
    private val stayDao = database.staySegmentDao()
    private val gapDao = database.dayGapDao()
    private val placeDao = database.placeDao()
    private val routeDao = database.routeOverrideDao()

    override suspend fun appendPoints(points: List<TrackPointEntity>): Int {
        if (points.isEmpty()) return 0
        val ids = pointDao.insertAll(points)
        return ids.count { it > 0 }
    }

    override suspend fun recomputeDay(dayKey: DayKey) {
        val entities = pointDao.pointsOfDay(dayKey.value)
        val samples =
            entities.map {
                TrackPointSample(
                    timestampMs = it.timestampMs,
                    point = it.toGeoPoint(),
                    accuracyMeters = it.accuracyMeters,
                )
            }

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
        val detected =
            stayDetector.detect(samples.map { StayDetector.TimedPoint(it.timestampMs, it.point) })
        val knownPlaces = placeDao.all().toMutableList()
        val stays =
            detected.map { stay ->
                val placeId = resolvePlace(stay, knownPlaces)
                StaySegmentEntity(
                    dayKey = dayKey.value,
                    startMs = stay.startMs,
                    endMs = stay.endMs,
                    latitude = stay.point.latitude,
                    longitude = stay.point.longitude,
                    radiusMeters = stay.radiusMeters,
                    // 被用户删掉的地点不显示名字；行还留着，下一次重算不会再建一个
                    placeId =
                        placeId.takeIf { id ->
                            knownPlaces.none { it.id == id && it.suppressed }
                        },
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

    override fun observePoints(dayKey: DayKey): Flow<List<TrackPointEntity>> =
        pointDao.observePointsOfDay(dayKey.value)

    override suspend fun staysOfDay(dayKey: DayKey): List<StaySegmentEntity> =
        stayDao.ofDay(dayKey.value)

    override fun observeStays(dayKey: DayKey): Flow<List<StaySegmentEntity>> =
        stayDao.observeOfDay(dayKey.value)

    override suspend fun gapsOfDay(dayKey: DayKey): List<DayGapEntity> = gapDao.ofDay(dayKey.value)

    override fun observeGaps(dayKey: DayKey): Flow<List<DayGapEntity>> =
        gapDao.observeOfDay(dayKey.value)

    override fun observeRange(
        from: DayKey,
        to: DayKey,
    ): Flow<List<TrackDayEntity>> = dayDao.observeRange(from.value, to.value)

    override fun observeDaysWithData(): Flow<List<TrackDayEntity>> = dayDao.observeAllWithData()

    override fun observePointCount(dayKey: DayKey): Flow<Int> =
        pointDao.observePointCount(dayKey.value)

    override suspend fun lastPoint(): TrackPointEntity? = pointDao.lastPoint()

    override fun observePlaces(): Flow<List<PlaceSummary>> = placeDao.observeSummaries()

    override suspend fun renamePlace(
        id: Long,
        label: String,
    ) {
        placeDao.rename(id, label.trim(), LabelSource.MANUAL)
    }

    override suspend fun suppressPlace(id: Long) {
        placeDao.suppress(id)
    }

    override fun observePlace(id: Long): Flow<PlaceSummary?> = placeDao.observeSummary(id)

    override suspend fun addPlace(
        point: GeoPoint,
        label: String,
    ): Long {
        val now = System.currentTimeMillis()
        return placeDao.insert(
            PlaceEntity(
                latitude = point.latitude,
                longitude = point.longitude,
                label = label.trim(),
                labelSource = LabelSource.MANUAL,
                firstSeenMs = now,
                lastSeenMs = now,
            ),
        )
    }

    override fun observeRoute(dayKey: DayKey): Flow<RouteOverride?> =
        routeDao.observeOfDay(dayKey.value).map { entity -> entity?.toRouteOverride() }

    override suspend fun saveRoute(
        dayKey: DayKey,
        route: RouteOverride,
    ) {
        routeDao.upsert(
            RouteOverrideEntity(
                dayKey = dayKey.value,
                startMs = route.startMs,
                endMs = route.endMs,
                points = encodePoints(route.segments),
                distanceMeters = route.distanceMeters,
                createdAtMs = route.createdAtMs,
            ),
        )
    }

    override suspend fun clearRoute(dayKey: DayKey) {
        routeDao.deleteDay(dayKey.value)
    }

    override suspend fun backfillPlaces() {
        // 已经有地点说明回填过了。用户把所有地点都删掉的情况也走这里：
        // suppress 只置标志、行还在，count 依然 > 0，不会把删掉的地点重建回来。
        if (placeDao.count() > 0) return

        dayDao.keysWithData().forEach { key ->
            DayKeys.parse(key)?.let { recomputeDay(it) }
        }
    }

    override suspend fun deleteDay(dayKey: DayKey) {
        pointDao.deleteDay(dayKey.value)
        stayDao.deleteDay(dayKey.value)
        gapDao.deleteDay(dayKey.value)
        dayDao.deleteDay(dayKey.value)
        routeDao.deleteDay(dayKey.value)
    }

    override suspend fun deleteAll() {
        pointDao.deleteAll()
        stayDao.deleteAll()
        gapDao.deleteAll()
        dayDao.deleteAll()
        placeDao.deleteAll()
        routeDao.deleteAll()
    }

    override suspend fun totalPointCount(): Int = pointDao.totalPointCount()

    /**
     * 把一段停留归到最近的地点，没有匹配就新建。
     *
     * 幂等：同一段停留被重复重算时，第二次会匹配到上一次建的地点，不会重复创建。
     * 新建的地点要立刻加进 [knownPlaces]，否则同一天的第二次停留会再建一个。
     */
    private suspend fun resolvePlace(
        stay: StaySegment,
        knownPlaces: MutableList<PlaceEntity>,
    ): Long {
        val match =
            PlaceResolver.nearest(
                knownPlaces.map { PlaceResolver.Candidate(it.id, it.toGeoPoint()) },
                stay.point,
            )
        if (match != null) {
            placeDao.touch(match.id, stay.endMs)
            return match.id
        }

        val created =
            PlaceEntity(
                latitude = stay.point.latitude,
                longitude = stay.point.longitude,
                firstSeenMs = stay.startMs,
                lastSeenMs = stay.endMs,
            )
        val id = placeDao.insert(created)
        knownPlaces += created.copy(id = id)
        // 顺手取个名字。fire-and-forget，失败就保持「未命名」，不阻塞重算。
        autoNamer?.enrich(id, stay.point)
        return id
    }

    override suspend fun backfillPlaceNames(): Int = autoNamer?.backfill() ?: 0
}

/** 实体到领域模型的转换。集中放这里，避免各处重复写。 */
internal fun TrackPointEntity.toGeoPoint() =
    com.hmilyld.daytrail.core
        .GeoPoint(latitude = latitude, longitude = longitude)

/** 地点实体到坐标。 */
internal fun PlaceEntity.toGeoPoint() =
    com.hmilyld.daytrail.core
        .GeoPoint(latitude = latitude, longitude = longitude)

/** 纠偏折线的 JSON 编解码。用 [lon, lat] 顺序，与 GeoJSON 一致。 */
private val routeJson = Json { ignoreUnknownKeys = true }

private fun encodePoints(segments: List<List<GeoPoint>>): String =
    routeJson.encodeToString(
        segments.map { segment -> segment.map { listOf(it.longitude, it.latitude) } },
    )

private fun decodePoints(text: String): List<List<GeoPoint>> =
    runCatching { routeJson.decodeFromString<List<List<List<Double>>>>(text) }
        .getOrDefault(emptyList())
        .map { segment ->
            segment.mapNotNull { pair ->
                if (pair.size >= 2) GeoPoint(pair[1], pair[0]) else null
            }
        }.filter { it.size >= 2 }

private fun RouteOverrideEntity.toRouteOverride(): RouteOverride =
    RouteOverride(
        startMs = startMs,
        endMs = endMs,
        segments = decodePoints(points),
        distanceMeters = distanceMeters,
        createdAtMs = createdAtMs,
    )

/** 停留段实体到领域模型的转换。渲染吸附与地图标记共用同一份锚点。 */
internal fun StaySegmentEntity.toStaySegment() =
    com.hmilyld.daytrail.domain.StaySegment(
        startMs = startMs,
        endMs = endMs,
        point =
            com.hmilyld.daytrail.core
                .GeoPoint(latitude = latitude, longitude = longitude),
        radiusMeters = radiusMeters,
    )

/** 今天（本地时区）的日期键。 */
fun todayKey(): DayKey = DayKeys.of(System.currentTimeMillis())
