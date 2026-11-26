package com.hmilyld.daytrail.data.backup

import android.content.Context
import android.net.Uri
import com.hmilyld.daytrail.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 备份 / 恢复的界面状态。
 *
 * 放在应用级 scope 里而不是 `rememberCoroutineScope`：备份要读整个数据库，
 * 旋转屏幕会销毁 composition、取消协程，留下半截备份文件。
 */
data class BackupUiState(
    val inspecting: Boolean = false,
    val backupRunning: Boolean = false,
    val restoreRunning: Boolean = false,
    /** 已通过校验、等待用户确认的备份。 */
    val inspection: RestoreInspection? = null,
    val pendingUri: Uri? = null,
    /** 一次性提示，展示后调用 [BackupCoordinator.consumeMessage] 清掉。 */
    val message: String? = null,
    /** 已准备好恢复，等待用户重新打开应用。 */
    val restorePrepared: Boolean = false,
)

/**
 * 备份 / 恢复动作的协调器，与 `UpdateCoordinator` 同构：
 * 应用级 scope + 单一 StateFlow，设置页只负责渲染。
 */
class BackupCoordinator(
    private val context: Context,
    private val manager: BackupManager,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedName(): String = manager.suggestedName()

    fun backup(uri: Uri) {
        if (_state.value.backupRunning) return
        _state.value = _state.value.copy(backupRunning = true, message = null)
        scope.launch {
            val result = manager.backup(uri)
            _state.value =
                _state.value.copy(
                    backupRunning = false,
                    message =
                        when (result) {
                            is BackupResult.Success ->
                                context.getString(
                                    R.string.backup_success,
                                    result.tableRowCounts.size,
                                    result.sizeBytes,
                                )
                            is BackupResult.Failure -> result.message
                        },
                )
        }
    }

    /** 只读校验；成功后 [BackupUiState.inspection] 非空。 */
    fun inspect(uri: Uri) {
        if (_state.value.inspecting) return
        _state.value =
            _state.value.copy(
                inspecting = true,
                inspection = null,
                pendingUri = uri,
                message = null,
            )
        scope.launch {
            when (val result = manager.inspect(uri)) {
                is InspectionResult.Ready ->
                    _state.value =
                        _state.value.copy(
                            inspecting = false,
                            inspection = result.inspection,
                        )
                is InspectionResult.Rejected ->
                    _state.value =
                        _state.value.copy(
                            inspecting = false,
                            pendingUri = null,
                            message = result.reason,
                        )
            }
        }
    }

    fun restore(allowDifferentApplicationId: Boolean) {
        val uri = _state.value.pendingUri ?: return
        if (_state.value.restoreRunning) return
        _state.value = _state.value.copy(restoreRunning = true, message = null)
        scope.launch {
            when (val result = manager.prepareRestore(uri, allowDifferentApplicationId)) {
                is RestorePrepareResult.Success ->
                    _state.value =
                        _state.value.copy(
                            restoreRunning = false,
                            restorePrepared = true,
                            inspection = null,
                            pendingUri = null,
                        )
                is RestorePrepareResult.Failure ->
                    _state.value =
                        _state.value.copy(
                            restoreRunning = false,
                            message = result.message,
                        )
            }
        }
    }

    fun dismissInspection() {
        _state.value = _state.value.copy(inspection = null, pendingUri = null)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun consumeRestorePrepared() {
        _state.value = _state.value.copy(restorePrepared = false)
    }
}
