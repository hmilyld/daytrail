package com.hmilyld.daytrail.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hmilyld.daytrail.data.db.entity.DiagnosticEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {
    @Insert
    suspend fun insert(event: DiagnosticEventEntity)

    /** 设置页列表用：最近 N 条，新的在前。 */
    @Query("SELECT * FROM diagnostic_event ORDER BY ts DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DiagnosticEventEntity>>

    /** 导出用：某时间点之后的事件，按时间升序。 */
    @Query("SELECT * FROM diagnostic_event WHERE ts >= :fromMs ORDER BY ts ASC")
    suspend fun since(fromMs: Long): List<DiagnosticEventEntity>

    @Query("DELETE FROM diagnostic_event WHERE ts < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)

    @Query("DELETE FROM diagnostic_event")
    suspend fun deleteAll()
}
