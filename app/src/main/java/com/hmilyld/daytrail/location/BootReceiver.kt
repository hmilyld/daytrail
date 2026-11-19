package com.hmilyld.daytrail.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hmilyld.daytrail.DayTrailApplication
import com.hmilyld.daytrail.MainActivity
import com.hmilyld.daytrail.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机提醒。
 *
 * **不自动开始记录**：进程被杀后用户看到「记录中」却一个点都没有，是 4.11 的教训；
 * 开机静默启动更糟——用户完全不会想到去检查。
 *
 * 只在「上次确实在记录」时弹一条可点击的提醒，由用户决定是否继续。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as? DayTrailApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (app.container.settingsStore.recordingIntent
                        .first()
                ) {
                    notifyResume(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notifyResume(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val resume =
            PendingIntent.getForegroundService(
                context,
                1,
                Intent(context, RecordingService::class.java)
                    .setAction(RecordingService.ACTION_START),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, RecordingService.CHANNEL_ID)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.boot_resume_body))
                .setSmallIcon(R.drawable.ic_stat_track)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, context.getString(R.string.boot_resume_action), resume)
                .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /** 服务没启动时通道可能还不存在，这里补建一次。 */
    private fun ensureChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(RecordingService.CHANNEL_ID) != null) return

        val channel =
            NotificationChannel(
                RecordingService.CHANNEL_ID,
                context.getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_recording_desc)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val NOTIFICATION_ID = 1002
    }
}
