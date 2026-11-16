package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.data.prefs.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 记录状态的内存持有者。
 *
 * 为什么不做持久化：
 * 状态反映的是「服务此刻是否在采集」。进程被杀后服务随之消失，
 * 如果把状态存盘，重开应用会显示「记录中」而实际没有记录——
 * 这比状态归零更糟，用户会以为数据在积累。
 *
 * 因此这里只在内存里保存，进程重启即回到 IDLE，与实际状态一致。
 * 服务在 onCreate/onDestroy 里更新它，界面订阅它。
 *
 * 还承载「定位异常」信息：连续失败后采集会停止，界面据此显示原因与重试入口。
 */
class RecordingStateHolder {
    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _locationIssue = MutableStateFlow<LocationFailureReason?>(null)

    /** 非 null 表示采集已因定位失败而停止，界面应提示原因并给重试入口。 */
    val locationIssue: StateFlow<LocationFailureReason?> = _locationIssue.asStateFlow()

    fun update(newState: RecordingState) {
        _state.value = newState
    }

    fun setLocationIssue(reason: LocationFailureReason?) {
        _locationIssue.value = reason
    }
}
