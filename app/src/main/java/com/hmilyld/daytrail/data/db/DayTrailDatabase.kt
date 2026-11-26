package com.hmilyld.daytrail.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hmilyld.daytrail.data.db.dao.DayGapDao
import com.hmilyld.daytrail.data.db.dao.DiagnosticDao
import com.hmilyld.daytrail.data.db.dao.PlaceDao
import com.hmilyld.daytrail.data.db.dao.RouteOverrideDao
import com.hmilyld.daytrail.data.db.dao.StaySegmentDao
import com.hmilyld.daytrail.data.db.dao.TrackDayDao
import com.hmilyld.daytrail.data.db.dao.TrackEditDao
import com.hmilyld.daytrail.data.db.dao.TrackPointDao
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.DiagnosticEventEntity
import com.hmilyld.daytrail.data.db.entity.PlaceEntity
import com.hmilyld.daytrail.data.db.entity.RouteOverrideEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackEditEntity
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
 * - v4：新增 diagnostic_event（诊断日志，只记决策不记坐标）
 * - v5：新增 track_edit（手动微调的折线，只影响显示）
 */
@Database(
    entities = [
        TrackDayEntity::class,
        TrackPointEntity::class,
        StaySegmentEntity::class,
        DayGapEntity::class,
        PlaceEntity::class,
        RouteOverrideEntity::class,
        DiagnosticEventEntity::class,
        TrackEditEntity::class,
    ],
    // 必须与 companion 里的 DB_VERSION 保持一致（KSP 不认类内 const 作为注解实参）。
    version = 5,
    exportSchema = true,
)
abstract class DayTrailDatabase : RoomDatabase() {
    abstract fun trackPointDao(): TrackPointDao

    abstract fun trackDayDao(): TrackDayDao

    abstract fun staySegmentDao(): StaySegmentDao

    abstract fun dayGapDao(): DayGapDao

    abstract fun placeDao(): PlaceDao

    abstract fun routeOverrideDao(): RouteOverrideDao

    abstract fun diagnosticDao(): DiagnosticDao

    abstract fun trackEditDao(): TrackEditDao

    /** 8 张表的行数。备份清单要用；传表名进来，避免 data.db 反向依赖 data.backup。 */
    internal fun rowCounts(tables: List<String>): Map<String, Long> {
        val db = openHelper.readableDatabase
        return tables.associateWith { table ->
            val cursor = db.query("SELECT COUNT(*) FROM `$table`")
            cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
        }
    }

    companion object {
        /** 备份模块要知道库文件名与版本才能校验/替换，但这里不能改 schema。 */
        internal const val NAME = "daytrail.db"
        internal const val DB_VERSION = 5

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

        /** v3 → v4：诊断日志表。 */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `diagnostic_event` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`ts` INTEGER NOT NULL, " +
                            "`category` TEXT NOT NULL, " +
                            "`detail` TEXT NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_diagnostic_event_ts` " +
                            "ON `diagnostic_event` (`ts`)",
                    )
                }
            }

        /** v4 → v5：手动微调的折线，单独一张表，不动已有数据。 */
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `track_edit` (" +
                            "`day_key` TEXT NOT NULL, " +
                            "`segments` TEXT NOT NULL, " +
                            "`cutoff_ts` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`day_key`))",
                    )
                }
            }

        fun create(context: Context): DayTrailDatabase =
            Room
                .databaseBuilder(context.applicationContext, DayTrailDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
