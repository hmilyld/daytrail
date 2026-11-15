package com.hmilyld.daytrail.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hmilyld.daytrail.data.db.dao.DayGapDao
import com.hmilyld.daytrail.data.db.dao.StaySegmentDao
import com.hmilyld.daytrail.data.db.dao.TrackDayDao
import com.hmilyld.daytrail.data.db.dao.TrackPointDao
import com.hmilyld.daytrail.data.db.entity.DayGapEntity
import com.hmilyld.daytrail.data.db.entity.StaySegmentEntity
import com.hmilyld.daytrail.data.db.entity.TrackDayEntity
import com.hmilyld.daytrail.data.db.entity.TrackPointEntity

/**
 * 本地数据库。
 *
 * 只有四张业务表，全部是本机数据，不做加密（导出时会提示风险）。
 * `allowBackup=false` 已在 manifest 里关掉系统备份，避免轨迹被同步到云端。
 *
 * 版本号变更时必须提供 Migration，不允许 destructive fallback：
 * 轨迹是用户长期积累的数据，静默清空不可接受。
 */
@Database(
    entities = [
        TrackDayEntity::class,
        TrackPointEntity::class,
        StaySegmentEntity::class,
        DayGapEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DayTrailDatabase : RoomDatabase() {
    abstract fun trackPointDao(): TrackPointDao

    abstract fun trackDayDao(): TrackDayDao

    abstract fun staySegmentDao(): StaySegmentDao

    abstract fun dayGapDao(): DayGapDao

    companion object {
        private const val NAME = "daytrail.db"

        fun create(context: Context): DayTrailDatabase =
            Room
                .databaseBuilder(context.applicationContext, DayTrailDatabase::class.java, NAME)
                .build()
    }
}
