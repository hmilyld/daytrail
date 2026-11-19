package com.hmilyld.daytrail.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 显著运动触发器。
 *
 * 用 `TYPE_SIGNIFICANT_MOTION` 而不是轮询加速度计：
 * - 它是**一次性触发器**，由传感器中枢（低功耗 DSP）判定，CPU 可以完全睡下去
 * - 不需要运行时权限
 * - 是 wake-up 传感器，Doze 下也能唤醒应用
 *
 * 代价：触发后必须重新挂载（见 [TriggerEventListener.onTrigger]），
 * 而且部分 ROM 会把它阉割掉。所以调用方必须保留「每小时心跳」作为兜底，
 * 不能只靠它。
 */
class MotionGate(
    context: Context,
) {
    private val manager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    /** 设备是否提供该传感器。没有时只能靠心跳。 */
    val available: Boolean get() = sensor != null

    /** 运动事件流。每次「手机被拿起来 / 开始走动」触发一次。 */
    fun events(): Flow<Unit> =
        callbackFlow {
            val sensorManager = manager
            val motionSensor = sensor
            if (sensorManager == null || motionSensor == null) {
                close()
                return@callbackFlow
            }

            lateinit var listener: TriggerEventListener
            listener =
                object : TriggerEventListener() {
                    override fun onTrigger(event: TriggerEvent) {
                        trySend(Unit)
                        // 一次性传感器：触发后传感器自己关掉了，必须重新挂上，
                        // 否则下一次运动不会再通知。
                        sensorManager.requestTriggerSensor(listener, motionSensor)
                    }
                }

            if (!sensorManager.requestTriggerSensor(listener, motionSensor)) {
                close()
                return@callbackFlow
            }

            awaitClose { sensorManager.cancelTriggerSensor(listener, motionSensor) }
        }
}
