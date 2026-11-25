package com.hmilyld.daytrail.data.import

import com.hmilyld.daytrail.core.CoordinateTransform
import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.geocode.NamingResult
import com.hmilyld.daytrail.data.repo.DayImportBatch
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.domain.RouteOverride
import com.hmilyld.daytrail.domain.TrackEdit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入编排的端到端逻辑：坐标系转换、去重、冲突策略、事务参数。
 *
 * 用一个内存版仓库：只实现导入真正用到的三个方法（observeDay / pointsOfDay /
 * importDays），其余一律 `error("not used")`——如果哪天编排层偷偷多调了一个方法，
 * 测试会立刻失败，这比静默返回空值更安全。
 */
class TrackImporterTest {
    private val day = "2026-11-18"

    private fun feature(
        date: String = day,
        coords: String,
        times: String,
        accuracies: String? = null,
    ): String {
        val accuracyField = accuracies?.let { """"accuracies":[$it],""" } ?: ""
        return """{"type":"Feature","properties":{"date":"$date","kind":"track",""" +
            """"pointCount":2,$accuracyField"times":[$times]},""" +
            """"geometry":{"type":"LineString","coordinates":[$coords]}}"""
    }

    private fun geoJson(vararg features: String): String =
        """{"type":"FeatureCollection","schema":2,"features":[${features.joinToString(",")}]}"""

    private fun twoPoints(
        date: String = day,
        firstTime: String = "2026-11-18T08:00:00+08:00",
        secondTime: String = "2026-11-18T08:01:00+08:00",
        secondLon: String = "121.475701",
    ): String =
        feature(
            date = date,
            coords = "[121.473701,31.230416],[$secondLon,31.231416]",
            times = "\"$firstTime\",\"$secondTime\"",
        )

    @Test
    fun `preview converts wgs84 to gcj02 before writing`() =
        runTest {
            val repo = FakeRepository()
            val importer = TrackImporter(repo)

            val preview =
                (
                    importer.preview(
                        "x.geojson",
                        geoJson(twoPoints()),
                    ) as PreviewResult.Ready
                ).preview

            val actual =
                preview.batches
                    .first()
                    .points
                    .first()
            val expected =
                CoordinateTransform.wgs84ToGcj02(GeoPoint(31.230416, 121.473701))
            assertEquals(expected.latitude, actual.latitude, 1e-7)
            assertEquals(expected.longitude, actual.longitude, 1e-7)
            // 上海一带偏移约 500 米，转换不生效会立刻差出一个街区
            assertTrue(kotlin.math.abs(actual.latitude - 31.230416) > 1e-4)
        }

    @Test
    fun `duplicate samples inside the window collapse`() =
        runTest {
            val repo = FakeRepository()
            val importer = TrackImporter(repo)
            val coords =
                listOf(
                    "121.473701,31.230416",
                    "121.473706,31.230417",
                    "121.480000,31.240000",
                ).joinToString(",") { "[$it]" }
            val times =
                listOf(
                    "2026-11-18T08:00:00+08:00",
                    "2026-11-18T08:00:00.500+08:00",
                    "2026-11-18T08:05:00+08:00",
                ).joinToString(",") { "\"$it\"" }
            val raw = geoJson(feature(coords = coords, times = times))

            val preview = (importer.preview("x.geojson", raw) as PreviewResult.Ready).preview

            assertEquals(
                2,
                preview.batches
                    .first()
                    .points.size,
            )
        }

    @Test
    fun `a day with a single point is not importable`() =
        runTest {
            val repo = FakeRepository()
            val importer = TrackImporter(repo)
            val raw =
                geoJson(
                    feature(
                        coords = "[121.473701,31.230416]",
                        times = "\"2026-11-18T08:00:00+08:00\"",
                    ),
                )

            val result = importer.preview("x.geojson", raw)

            assertTrue(result is PreviewResult.Failure)
        }

    @Test
    fun `skip leaves an existing day untouched`() =
        runTest {
            val repo = FakeRepository()
            repo.seed(day, 1000L)
            val importer = TrackImporter(repo)
            val preview =
                (
                    importer.preview(
                        "x.geojson",
                        geoJson(twoPoints()),
                    ) as PreviewResult.Ready
                ).preview

            assertEquals(listOf(day), preview.existingDays)
            assertTrue(preview.newDays.isEmpty())

            val outcome = importer.commit(preview, ConflictStrategy.SKIP)

            assertEquals(0, outcome.importedPoints)
            assertEquals(1, outcome.skippedDays)
            // 没有写入就根本不该调用仓库的写入口
            assertEquals(null, repo.lastReplace)
        }

    @Test
    fun `merge keeps existing timestamps and adds the rest`() =
        runTest {
            val repo = FakeRepository()
            // 库里已经有 08:00 这个点
            repo.seed(day, 1_765_000_000_000L)
            val importer = TrackImporter(repo)
            val existingTime =
                java.time.Instant
                    .ofEpochMilli(1_765_000_000_000L)
                    .toString()
            val raw = geoJson(twoPoints(firstTime = existingTime))
            val preview = (importer.preview("x.geojson", raw) as PreviewResult.Ready).preview

            val outcome = importer.commit(preview, ConflictStrategy.MERGE)

            assertEquals(1, outcome.importedPoints)
            assertEquals(false, repo.lastReplace)
        }

    @Test
    fun `replace asks the repository to clear the day first`() =
        runTest {
            val repo = FakeRepository()
            repo.seed(day, 1_765_000_000_000L)
            val importer = TrackImporter(repo)
            val preview =
                (
                    importer.preview(
                        "x.geojson",
                        geoJson(twoPoints()),
                    ) as PreviewResult.Ready
                ).preview

            importer.commit(preview, ConflictStrategy.REPLACE)

            assertEquals(true, repo.lastReplace)
        }

    @Test
    fun `invalid points inside a kept day are reported as dropped`() =
        runTest {
            val repo = FakeRepository()
            val importer = TrackImporter(repo)
            val raw =
                geoJson(
                    feature(
                        coords = "[121.473701,31.230416],[999.0,31.231416],[121.480000,31.240000]",
                        times =
                            listOf(
                                "2026-11-18T08:00:00+08:00",
                                "2026-11-18T08:01:00+08:00",
                                "2026-11-18T08:05:00+08:00",
                            ).joinToString(",") { "\"$it\"" },
                    ),
                )

            val preview = (importer.preview("x.geojson", raw) as PreviewResult.Ready).preview

            assertEquals(
                2,
                preview.batches
                    .first()
                    .points.size,
            )
            // 坐标越界的那个点必须出现在「已丢弃」里，否则用户会以为文件很干净
            assertEquals(1, preview.droppedPoints)
        }

    @Test
    fun `unrecognised content fails instead of guessing`() =
        runTest {
            val importer = TrackImporter(FakeRepository())

            val result = importer.preview("x.txt", "hello world")

            assertTrue(result is PreviewResult.Failure)
        }

    @Test
    fun `blank file fails`() =
        runTest {
            val result = TrackImporter(FakeRepository()).preview("x.geojson", "   ")

            assertTrue(result is PreviewResult.Failure)
        }

    private class FakeRepository : TrackRepository {
        private val store = mutableMapOf<String, MutableList<TrackPointEntity>>()
        var lastReplace: Boolean? = null

        fun seed(
            dayKey: String,
            timestampMs: Long,
        ) {
            store.getOrPut(dayKey) { mutableListOf() } +=
                TrackPointEntity(
                    timestampMs = timestampMs,
                    latitude = 31.23,
                    longitude = 121.47,
                    accuracyMeters = 10f,
                    speedMps = null,
                    bearing = null,
                    dayKey = dayKey,
                )
        }

        override suspend fun importDays(
            batches: List<DayImportBatch>,
            replaceExisting: Boolean,
        ): Int {
            lastReplace = replaceExisting
            var inserted = 0
            batches.forEach { batch ->
                val target = store.getOrPut(batch.dayKey.value) { mutableListOf() }
                if (replaceExisting) target.clear()
                target += batch.points
                inserted += batch.points.size
            }
            return inserted
        }

        override fun observeDay(dayKey: DayKey): Flow<TrackDayEntity?> =
            flowOf(store[dayKey.value]?.let { TrackDayEntity(dayKey.value) })

        override suspend fun pointsOfDay(dayKey: DayKey): List<TrackPointEntity> =
            store[dayKey.value].orEmpty()

        // ---- 以下方法与导入无关 ----

        override suspend fun appendPoints(points: List<TrackPointEntity>): Int = error("not used")

        override suspend fun recomputeDay(dayKey: DayKey) = error("not used")

        override fun observePoints(dayKey: DayKey): Flow<List<TrackPointEntity>> = error("not used")

        override suspend fun staysOfDay(dayKey: DayKey): List<StaySegmentEntity> = error("not used")

        override fun observeStays(dayKey: DayKey): Flow<List<StaySegmentEntity>> = error("not used")

        override suspend fun gapsOfDay(dayKey: DayKey): List<DayGapEntity> = error("not used")

        override fun observeGaps(dayKey: DayKey): Flow<List<DayGapEntity>> = error("not used")

        override fun observeRange(
            from: DayKey,
            to: DayKey,
        ): Flow<List<TrackDayEntity>> = error("not used")

        override fun observeDaysWithData(): Flow<List<TrackDayEntity>> = error("not used")

        override fun observePointCount(dayKey: DayKey): Flow<Int> = error("not used")

        override suspend fun lastPoint(): TrackPointEntity? = error("not used")

        override fun observePlaces(): Flow<List<PlaceSummary>> = error("not used")

        override suspend fun renamePlace(
            id: Long,
            label: String,
        ) = error("not used")

        override suspend fun suppressPlace(id: Long) = error("not used")

        override suspend fun mergePlaces(
            fromId: Long,
            toId: Long,
        ): Boolean = error("not used")

        override suspend fun backfillPlaceNames(): NamingResult = error("not used")

        override fun observePlace(id: Long): Flow<PlaceSummary?> = error("not used")

        override suspend fun addPlace(
            point: GeoPoint,
            label: String,
        ): Long = error("not used")

        override suspend fun backfillPlaces() = error("not used")

        override fun observeRoute(dayKey: DayKey): Flow<RouteOverride?> = error("not used")

        override suspend fun saveRoute(
            dayKey: DayKey,
            route: RouteOverride,
        ) = error("not used")

        override suspend fun clearRoute(dayKey: DayKey) = error("not used")

        override fun observeEdit(dayKey: DayKey): Flow<TrackEdit?> = error("not used")

        override suspend fun saveEdit(
            dayKey: DayKey,
            edit: TrackEdit,
        ) = error("not used")

        override suspend fun clearEdit(dayKey: DayKey) = error("not used")

        override suspend fun deleteDay(dayKey: DayKey) = error("not used")

        override suspend fun deleteAll() = error("not used")

        override suspend fun totalPointCount(): Int = error("not used")
    }
}
