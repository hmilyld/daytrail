package com.hmilyld.daytrail.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 诊断事件。
 *
 * 只记**决策**，不记坐标：轨迹数据里已经有坐标了，日志里再存一遍既冗余又敏感。
 * 典型内容：「SLEEP→MOVING 速度 4.2」「WiFi→蜂窝，探针距锚点 35m」「定位开关被关闭」。
 */
@Entity(
    tableName = "diagnostic_event",
    indices = [Index(value = ["ts"])],
)
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "ts")
    val timestampMs: Long,
    /** 分类：tier / motion / network / service / issue / probe。 */
    val category: String,
    val detail: String,
)
