package com.hmilyld.daytrail.data.diagnostics

import com.hmilyld.daytrail.data.db.dao.DiagnosticDao
import com.hmilyld.daytrail.data.db.entity.DiagnosticEventEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 诊断日志。
 *
 * 只在**状态变化**时写一条（档位切换、运动/网络事件、探针结果、服务生命周期），
 * 一天几十条，开销可以忽略。保留 7 天，超了按时间删。
 */
class DiagnosticLog(
    private val dao: DiagnosticDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeRecent(limit: Int = 200): Flow<List<DiagnosticEventEntity>> =
        dao.observeRecent(limit)

    suspend fun record(
        category: String,
        detail: String,
    ) {
        // 诊断日志永远不能影响记录主流程：写失败就算了
        runCatching {
            val now = clock()
            dao.insert(
                DiagnosticEventEntity(
                    timestampMs = now,
                    category = category,
                    detail = detail,
                ),
            )
            dao.deleteBefore(now - RETENTION_MS)
        }
    }

    suspend fun since(fromMs: Long): List<DiagnosticEventEntity> = dao.since(fromMs)

    suspend fun clear() {
        dao.deleteAll()
    }

    companion object {
        /** 只保留最近 7 天。 */
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

        private val FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        /** 导出成 JSON。带时区偏移，和轨迹导出的时间格式一致。 */
        fun toJson(
            events: List<DiagnosticEventEntity>,
            exportedAtMs: Long,
            zone: ZoneId = ZoneId.systemDefault(),
            appVersion: String = "",
        ): String {
            val body =
                events.joinToString(",") { event ->
                    val ts = FORMAT.format(Instant.ofEpochMilli(event.timestampMs).atZone(zone))
                    """{"ts":"$ts","category":"${escape(event.category)}",""" +
                        """"detail":"${escape(event.detail)}"}"""
                }
            val exportedAt = FORMAT.format(Instant.ofEpochMilli(exportedAtMs).atZone(zone))
            return """{"exportedAt":"$exportedAt","appVersion":"${escape(appVersion)}",""" +
                """"events":[$body]}"""
        }

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }
}
