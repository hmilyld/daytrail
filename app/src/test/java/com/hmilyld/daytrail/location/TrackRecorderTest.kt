package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.DayKey
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 记录写入与重算调用的假仓库。 */
private class FakeTrackRepository : TrackRepository {
    val inserted = mutableListOf<TrackPointEntity>()
    val recomputed = mutableListOf<DayKey>()
    var lastPointEntity: TrackPointEntity? = null

    /** 置 true 时下一次写入抛异常，用于验证缓冲不会被提前清空。 */
    var failNextAppend = false

    override suspend fun appendPoints(points: List<TrackPointEntity>): Int {
        if (failNextAppend) {
            failNextAppend = false
            throw IllegalStateException("模拟写库失败")
        }
        inserted += points
        return points.size
    }

    override suspend fun recomputeDay(dayKey: DayKey) {
        recomputed += dayKey
    }

    override fun observeDay(dayKey: DayKey): Flow<TrackDayEntity?> = flowOf(null)

    override suspend fun pointsOfDay(dayKey: DayKey): List<TrackPointEntity> = emptyList()

    override fun observePoints(dayKey: DayKey): Flow<List<TrackPointEntity>> = flowOf(emptyList())

    override suspend fun staysOfDay(dayKey: DayKey): List<StaySegmentEntity> = emptyList()

    override fun observeStays(dayKey: DayKey): Flow<List<StaySegmentEntity>> = flowOf(emptyList())

    override suspend fun gapsOfDay(dayKey: DayKey): List<DayGapEntity> = emptyList()

    override fun observeGaps(dayKey: DayKey): Flow<List<DayGapEntity>> = flowOf(emptyList())

    override fun observeRange(
        from: DayKey,
        to: DayKey,
    ): Flow<List<TrackDayEntity>> = flowOf(emptyList())

    override fun observeDaysWithData(): Flow<List<TrackDayEntity>> = flowOf(emptyList())

    override fun observePointCount(dayKey: DayKey): Flow<Int> = flowOf(0)

    override suspend fun lastPoint(): TrackPointEntity? = lastPointEntity

    override suspend fun deleteDay(dayKey: DayKey) = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun totalPointCount(): Int = inserted.size
}

class TrackRecorderTest {
    private val base = GeoPoint(31.2304, 121.4737)

    private fun fix(
        metersNorth: Double,
        secondsLater: Long,
    ): LocationFix =
        LocationFix(
            timestampMs = 1_794_672_000_000L + secondsLater * 1000L,
            point = GeoPoint(base.latitude + metersNorth / 111_320.0, base.longitude),
            accuracyMeters = 10f,
            speedMps = null,
            bearingDegrees = null,
            isMock = false,
        )

    @Test
    fun `accepted fix is buffered not written immediately`() =
        runTest {
            val repo = FakeTrackRepository()
            val recorder = TrackRecorder(repo, TestScope(StandardTestDispatcher(testScheduler)))

            val accepted = recorder.offer(fix(0.0, 0))

            assertTrue(accepted)
            assertEquals(1, recorder.bufferedCount)
            assertTrue(repo.inserted.isEmpty())
        }

    @Test
    fun `flush writes buffer and recomputes day`() =
        runTest {
            val repo = FakeTrackRepository()
            val recorder = TrackRecorder(repo, this)

            recorder.offer(fix(0.0, 0))
            recorder.flush()

            assertEquals(1, repo.inserted.size)
            assertEquals(1, repo.recomputed.size)
            assertEquals(0, recorder.bufferedCount)
        }

    @Test
    fun `error fix is rejected`() =
        runTest {
            val repo = FakeTrackRepository()
            val recorder = TrackRecorder(repo, this)

            val accepted =
                recorder.offer(
                    LocationFix(
                        timestampMs = 1_794_672_000_000L,
                        point = base,
                        accuracyMeters = 10f,
                        speedMps = null,
                        bearingDegrees = null,
                        isMock = false,
                        errorCode = 7,
                    ),
                )

            assertEquals(false, accepted)
            assertEquals(0, recorder.bufferedCount)
        }

    @Test
    fun `poor accuracy fix is rejected`() =
        runTest {
            val repo = FakeTrackRepository()
            val recorder = TrackRecorder(repo, this)

            val accepted =
                recorder.offer(
                    LocationFix(
                        timestampMs = 1_794_672_000_000L,
                        point = base,
                        accuracyMeters = 6000f,
                        speedMps = null,
                        bearingDegrees = null,
                        isMock = false,
                    ),
                )

            assertEquals(false, accepted)
        }

    @Test
    fun `buffer flushes automatically at size threshold`() =
        runTest {
            val repo = FakeTrackRepository()
            val recorder =
                TrackRecorder(
                    repository = repo,
                    scope = this,
                    flushSize = 3,
                )

            // 每点间隔 60 秒、相距 100 米，全部应被接受
            recorder.offer(fix(0.0, 0))
            recorder.offer(fix(100.0, 60))
            recorder.offer(fix(200.0, 120))

            assertEquals(3, repo.inserted.size)
            assertEquals(0, recorder.bufferedCount)
        }

    @Test
    fun `multiple days are all recomputed`() =
        runTest {
            val repo = FakeTrackRepository()
            val recorder = TrackRecorder(repo, this)

            // 第一天 23:59，第二天 00:01
            val dayOne = 1_794_672_000_000L + (23 * 3600 + 59 * 60) * 1000L
            recorder.offer(
                LocationFix(
                    timestampMs = dayOne,
                    point = base,
                    accuracyMeters = 10f,
                    speedMps = null,
                    bearingDegrees = null,
                    isMock = false,
                ),
            )
            recorder.offer(
                LocationFix(
                    timestampMs = dayOne + 2 * 60 * 1000L,
                    point = GeoPoint(base.latitude + 0.001, base.longitude),
                    accuracyMeters = 10f,
                    speedMps = null,
                    bearingDegrees = null,
                    isMock = false,
                ),
            )
            recorder.flush()

            assertEquals(2, repo.inserted.size)
            assertEquals(2, repo.recomputed.size)
        }

    @Test
    fun `restore last point prevents first fix being rejected as teleport`() =
        runTest {
            val repo = FakeTrackRepository()
            val previousTime = 1_794_672_000_000L
            repo.lastPointEntity =
                TrackPointEntity(
                    timestampMs = previousTime,
                    latitude = base.latitude,
                    longitude = base.longitude,
                    accuracyMeters = 10f,
                    speedMps = null,
                    bearing = null,
                    dayKey = "2026-11-15",
                )

            val recorder = TrackRecorder(repo, this)
            recorder.restoreLastPoint()

            // 距离上一点 500 米、间隔 60 秒，正常移动，应被接受
            val accepted =
                recorder.offer(
                    LocationFix(
                        timestampMs = previousTime + 60_000L,
                        point = GeoPoint(base.latitude + 500.0 / 111_320.0, base.longitude),
                        accuracyMeters = 10f,
                        speedMps = null,
                        bearingDegrees = null,
                        isMock = false,
                    ),
                )

            assertTrue(accepted)
        }

    @Test
    fun `finish flushes remaining buffer`() =
        runTest {
            val repo = FakeTrackRepository()
            val recorder = TrackRecorder(repo, this)

            recorder.offer(fix(0.0, 0))
            recorder.finish()

            assertEquals(1, repo.inserted.size)
            assertEquals(0, recorder.bufferedCount)
        }

    @Test
    fun `failed write keeps the points in the buffer`() =
        runTest {
            // 先写库、再清缓冲：写库失败时这些点必须还在，否则就永久丢了
            val repo = FakeTrackRepository().apply { failNextAppend = true }
            val recorder = TrackRecorder(repo, this)

            recorder.offer(fix(0.0, 0))
            runCatching { recorder.flush() }

            assertEquals(1, recorder.bufferedCount)

            recorder.flush()

            assertEquals(1, repo.inserted.size)
            assertEquals(0, recorder.bufferedCount)
        }
}
