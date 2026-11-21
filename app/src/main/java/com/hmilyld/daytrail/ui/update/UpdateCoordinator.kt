package com.hmilyld.daytrail.ui.update

import android.content.Context
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.data.update.UpdateCheckResult
import com.hmilyld.daytrail.data.update.UpdateChecker
import com.hmilyld.daytrail.data.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 更新流程的唯一驱动者。
 *
 * 状态放在容器里而不是某个页面：自动检查发生在启动时（没有页面），
 * 而手动检查发生在设置页，两者必须共享同一个状态，否则会出现
 * 「启动时弹了弹窗，进设置页又看到在检查」这种两份进度。
 */
class UpdateCoordinator(
    private val context: Context,
    private val checker: UpdateChecker,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 没有配置更新源时，入口仍然显示但点了只会解释原因。 */
    val configured: Boolean get() = checker.configured

    private var job: Job? = null

    /**
     * 启动时的静默检查。
     *
     * 只有「发现新版本」才会弹窗；已是最新、被跳过、网络失败都不打扰用户。
     */
    fun autoCheck() {
        if (!checker.configured) return
        if (_state.value !is UpdateUiState.Idle) return
        check(force = false)
    }

    fun check(force: Boolean = true) {
        if (!checker.configured) {
            _state.value = UpdateUiState.NotConfigured
            return
        }
        // 已经在检查或下载时忽略重复点击
        if (job?.isActive == true) return

        _state.value = UpdateUiState.Checking
        job =
            scope.launch {
                _state.value =
                    when (val result = checker.check(force)) {
                        // 自动检查在「已是最新」或「被跳过」时必须保持安静：
                        // 启动时弹一个「已是最新」的对话框属于打扰。
                        UpdateCheckResult.UpToDate,
                        UpdateCheckResult.Skipped,
                        ->
                            if (force) UpdateUiState.UpToDate else UpdateUiState.Idle

                        UpdateCheckResult.NotConfigured -> UpdateUiState.NotConfigured
                        is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                        is UpdateCheckResult.Failed ->
                            // 自动检查失败同样静默，只有手动检查才提示原因
                            if (force) {
                                UpdateUiState.Failed(result.message)
                            } else {
                                UpdateUiState.Idle
                            }
                    }
            }
    }

    fun download() {
        val available = _state.value as? UpdateUiState.Available ?: return
        if (job?.isActive == true) return

        _state.value = UpdateUiState.Downloading(available.info, 0f)
        job =
            scope.launch {
                try {
                    // 只留本次要用的包，历史包先清掉
                    UpdateInstaller.clearCacheExcept(context, null)
                    val apk =
                        UpdateInstaller.download(context, available.info) { progress ->
                            if (progress >= 0f) {
                                _state.value = UpdateUiState.Downloading(available.info, progress)
                            }
                        }
                    val rejection = UpdateInstaller.verify(context, apk)
                    if (rejection != null) {
                        apk.delete()
                        _state.value = UpdateUiState.Failed(messageFor(rejection))
                        return@launch
                    }
                    _state.value = UpdateUiState.Ready(available.info, apk)
                } catch (error: UpdateInstaller.ChecksumMismatchException) {
                    _state.value =
                        UpdateUiState.Failed(context.getString(R.string.update_err_checksum))
                } catch (error: IOException) {
                    _state.value =
                        UpdateUiState.Failed(
                            error.message ?: context.getString(R.string.update_err_save),
                        )
                }
            }
    }

    /**
     * 拉起系统安装器。
     *
     * 没有「安装未知应用」授权时先跳授权页，并保持 [UpdateUiState.Ready]：
     * 用户授权回来还能直接再点一次安装，不用重新下载 39 MB。
     */
    fun install(): Boolean {
        val ready = _state.value as? UpdateUiState.Ready ?: return false
        if (!UpdateInstaller.canInstall(context)) {
            UpdateInstaller.openInstallPermissionSettings(context)
            return false
        }
        return UpdateInstaller.install(context, ready.apk)
    }

    /** 用户点「稍后」：记住这个版本，之后不再自动提示。 */
    fun postpone() {
        val info =
            when (val current = _state.value) {
                is UpdateUiState.Available -> current.info
                is UpdateUiState.Ready -> current.info
                is UpdateUiState.Downloading -> current.info
                else -> null
            }
        if (info != null) {
            scope.launch { checker.markSkipped(info.versionName) }
        }
        dismiss()
    }

    /** 关闭提示。下载中的安装包保留，下次继续用。 */
    fun dismiss() {
        job?.cancel()
        job = null
        _state.value = UpdateUiState.Idle
    }

    val installPermissionRequired: Boolean get() = !UpdateInstaller.canInstall(context)

    private fun messageFor(rejection: UpdateInstaller.Rejection): String =
        context.getString(
            when (rejection) {
                UpdateInstaller.Rejection.UNREADABLE -> R.string.update_err_unreadable
                UpdateInstaller.Rejection.WRONG_PACKAGE -> R.string.update_err_package
                UpdateInstaller.Rejection.WRONG_SIGNER -> R.string.update_err_signer
            },
        )
}
