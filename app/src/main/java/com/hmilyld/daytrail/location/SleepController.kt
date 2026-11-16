package com.hmilyld.daytrail.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 休眠控制：省电的核心。
 *
 * 进入休眠时停止主动定位，只留两条极省电的唤醒通路：
 *
 * 1. **显著运动传感器**（`TYPE_SIGNIFICANT_MOTION`）：一次性触发传感器，
 *    由硬件在低功耗域监听，触发后自动注销，几乎不耗电。
 *    不需要 `ACTIVITY_RECOGNITION` 权限。
 * 2. **兜底闹钟**：部分 ROM 在深度睡眠下不会触发传感器，
 *    所以每隔 [fallbackIntervalMs] 做一次粗定位确认。
 *
 * 两条通路都会回调 [onWake]，由调用方决定是否真的退出休眠
 * （兜底定位还要判断位移是否超过阈值）。
 */
class SleepController(
    private val context: Context,
    /** 兜底定位间隔。档位切换后由 Service 更新，下一次休眠即按新值计时。 */
    var fallbackIntervalMs: Long,
    private val onWake: (WakeReason) -> Unit,
) {
    /** 唤醒原因。 */
    enum class WakeReason {
        /** 传感器检测到显著运动。 */
        SIGNIFICANT_MOTION,

        /** 兜底闹钟到点。 */
        FALLBACK_TIMER,
    }

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val significantMotion: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private val handler = Handler(Looper.getMainLooper())

    private var armed = false

    private val triggerListener =
        object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                if (!armed) return
                // 一次性传感器，触发后必须重新注册才能再次生效
                armed = false
                cancelFallback()
                onWake(WakeReason.SIGNIFICANT_MOTION)
            }
        }

    private val fallbackRunnable =
        object : Runnable {
            override fun run() {
                if (!armed) return
                onWake(WakeReason.FALLBACK_TIMER)
            }
        }

    /** 是否具备显著运动传感器。没有时只能靠兜底闹钟。 */
    val hasMotionSensor: Boolean get() = significantMotion != null

    /** 进入休眠：注册传感器 + 启动兜底闹钟。 */
    fun sleep() {
        if (armed) return
        armed = true

        val sensor = significantMotion
        if (sensor != null) {
            runCatching {
                sensorManager?.requestTriggerSensor(triggerListener, sensor)
            }.onFailure { error ->
                // 某些设备/模拟器上会抛异常，此时退化为纯定时兜底
                Log.w(TAG, "显著运动传感器注册失败，改用定时兜底", error)
            }
        }

        handler.postDelayed(fallbackRunnable, fallbackIntervalMs)
    }

    /** 退出休眠：注销传感器与闹钟。 */
    fun wake() {
        armed = false
        cancelFallback()
        val sensor = significantMotion
        if (sensor != null) {
            runCatching { sensorManager?.cancelTriggerSensor(triggerListener, sensor) }
        }
    }

    /** 彻底释放。Service 销毁时调用。 */
    fun release() {
        wake()
        handler.removeCallbacksAndMessages(null)
    }

    private fun cancelFallback() {
        handler.removeCallbacks(fallbackRunnable)
    }

    private companion object {
        const val TAG = "SleepController"
    }
}
