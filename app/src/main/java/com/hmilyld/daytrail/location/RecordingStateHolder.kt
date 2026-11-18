package com.hmilyld.daytrail.location

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.prefs.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 采集无法进行的原因。只在权限或定位开关有问题时出现。 */
enum class LocationFailureReason {
    /** 没有定位权限（精确或大致都没有）。 */
    PERMISSION_DENIED,

    /** 系统定位开关被关掉，或没有任何启用的 provider。 */
    LOCATION_DISABLED,

    /** 权限与开关都正常，但始终取不到位置。现在只用来说明，不停止采集。 */
    NO_FIX,

    /** 未知原因。 */
    UNKNOWN,
}

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
 */
class RecordingStateHolder {
    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _locationIssue = MutableStateFlow<LocationFailureReason?>(null)

    /**
     * 非 null 表示采集无法进行，界面应提示原因并给重试入口。
     *
     * 注意：信号差（室内、地下）**不再**算失败——按间隔的订阅会继续等，
     * 不需要用户操作。这里只放用户必须动手的两种情况：权限、定位开关。
     */
    val locationIssue: StateFlow<LocationFailureReason?> = _locationIssue.asStateFlow()

    private val _lastFix = MutableStateFlow<GeoPoint?>(null)

    /**
     * 最近一个采样点（GCJ-02）。
     *
     * 地图上的位置圆点直接订阅它，不再自己每 15 秒请求一次定位——
     * 两条独立的定位链路既费电，也会让「圆点」和「轨迹末端」对不上。
     */
    val lastFix: StateFlow<GeoPoint?> = _lastFix.asStateFlow()

    private val _fixStale = MutableStateFlow(false)

    /**
     * 记录中，但已经很久没有拿到新点位。
     *
     * 这不是「失败」：室内、地下都会这样，采集仍在按间隔等。界面据此给一句
     * 琥珀色提示，避免用户看着「记录中」和不变的点数，以为一切正常。
     */
    val fixStale: StateFlow<Boolean> = _fixStale.asStateFlow()

    fun update(newState: RecordingState) {
        _state.value = newState
    }

    fun setLocationIssue(reason: LocationFailureReason?) {
        _locationIssue.value = reason
    }

    fun setLastFix(point: GeoPoint?) {
        _lastFix.value = point
    }

    fun setFixStale(stale: Boolean) {
        _fixStale.value = stale
    }
}
