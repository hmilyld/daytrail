package com.hmilyld.daytrail.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.data.db.entity.DiagnosticEventEntity
import com.hmilyld.daytrail.data.diagnostics.DiagnosticLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 诊断日志页。 */
class DiagnosticsViewModel(
    private val log: DiagnosticLog,
) : ViewModel() {
    val events: StateFlow<List<DiagnosticEventEntity>> =
        log
            .observeRecent(RECENT_LIMIT)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun clear() {
        viewModelScope.launch { log.clear() }
    }

    /** 导出最近 7 天的日志。 */
    suspend fun buildExport(): String {
        val now = System.currentTimeMillis()
        return DiagnosticLog.toJson(
            events = log.since(now - DiagnosticLog.RETENTION_MS),
            exportedAtMs = now,
        )
    }

    companion object {
        private const val RECENT_LIMIT = 200

        private val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        fun suggestedName(): String {
            val date = Instant.now().atZone(ZoneId.systemDefault()).format(NAME_FORMAT)
            return "daytrail-diagnostics-$date.json"
        }
    }
}
