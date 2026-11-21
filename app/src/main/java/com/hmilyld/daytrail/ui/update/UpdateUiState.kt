package com.hmilyld.daytrail.ui.update

import com.hmilyld.daytrail.data.update.UpdateInfo
import java.io.File

/** 更新流程的界面状态。由 [UpdateCoordinator] 单向驱动。 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    /** 已是最新，或用户推迟过的版本，手动检查时给一句确认。 */
    data object UpToDate : UpdateUiState

    /** 没有配置更新源，入口仍然可见但点击只解释原因。 */
    data object NotConfigured : UpdateUiState

    data class Available(
        val info: UpdateInfo,
    ) : UpdateUiState

    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
    ) : UpdateUiState

    data class Ready(
        val info: UpdateInfo,
        val apk: File,
    ) : UpdateUiState

    data class Failed(
        val message: String,
    ) : UpdateUiState
}
