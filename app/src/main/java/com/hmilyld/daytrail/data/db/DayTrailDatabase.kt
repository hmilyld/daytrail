package com.hmilyld.daytrail.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hmilyld.daytrail.data.db.dao.DayGapDao
import com.hmilyld.daytrail.data.db.dao.PlaceDao
import com.hmilyld.daytrail.data.db.dao.RouteOverrideDao
import com.hmilyld.daytrail.data.db.dao.StaySegmentDao
import com.hmilyld.daytrail.data.db.dao.TrackDayDao
import com.hmilyld.daytrail.data.db.dao.TrackPointDao
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.PlaceEntity
import com.hmilyld.daytrail.data.db.entity.RouteOverrideEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity

/**
 * 本地数据库。
 *
 * 业务表全部是本机数据，不做加密（导出时会提示风险）。
 * `allowBackup=false` 已在 manifest 里关掉系统备份，避免轨迹被同步到云端。
 *
 * 版本号变更时必须提供 Migration，不允许 destructive fallback：
 * 轨迹是用户长期积累的数据，静默清空不可接受。
 *
 * 版本历史：
 * - v1：track_day / track_point / stay_segment / day_gap
 * - v2：新增 place（把多天的停留聚成「家」「公司」这类稳定地点）
 * - v3：新增 route_override（按需纠偏后的路线，派生数据）
 */
@Database(
    entities = [
        TrackDayEntity::class,
        TrackPointEntity::class,
        StaySegmentEntity::class,
        DayGapEntity::class,
        PlaceEntity::class,
        RouteOverrideEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class DayTrailDatabase : RoomDatabase() {
    abstract fun trackPointDao(): TrackPointDao

    abstract fun trackDayDao(): TrackDayDao

    abstract fun staySegmentDao(): StaySegmentDao

    abstract fun dayGapDao(): DayGapDao

    abstract fun placeDao(): PlaceDao

    abstract fun routeOverrideDao(): RouteOverrideDao

    companion object {
        private const val NAME = "daytrail.db"

        /** v1 → v2：只加一张表，不动已有数据。 */
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `place` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`latitude` REAL NOT NULL, " +
                            "`longitude` REAL NOT NULL, " +
                            "`radius_m` REAL NOT NULL, " +
                            "`label` TEXT, " +
                            "`label_source` TEXT NOT NULL, " +
                            "`first_seen_ms` INTEGER NOT NULL, " +
                            "`last_seen_ms` INTEGER NOT NULL, " +
                            "`suppressed` INTEGER NOT NULL)",
                    )
                }
            }

        /** v2 → v3：纠偏结果单独一张表，不动已有数据。 */
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `route_override` (" +
                            "`day_key` TEXT NOT NULL, " +
                            "`start_ts` INTEGER NOT NULL, " +
                            "`end_ts` INTEGER NOT NULL, " +
                            "`points` TEXT NOT NULL, " +
                            "`distance_m` REAL NOT NULL, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`day_key`))",
                    )
                }
            }

        fun create(context: Context): DayTrailDatabase =
            Room
                .databaseBuilder(context.applicationContext, DayTrailDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
