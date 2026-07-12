package com.wajiha.android.monitor

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.wajiha.android.work.OperationNotificationHelper
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.SystemNotificationKind

/** System tray + in-app warning when the OOM session guard force-stops games. */
object MemoryGuardNotifier {

    private const val CHANNEL_ID = "wajiha_memory"
    private const val NOTIF_ID = 2101

    fun notifyClosed(
        context: Context,
        killedLabels: List<String>,
        reason: String
    ) {
        if (killedLabels.isEmpty()) return
        val title = when (killedLabels.size) {
            1 -> "Closed ${killedLabels[0]} to free memory"
            else -> "Closed ${killedLabels.size} sessions to free memory"
        }
        val body = if (killedLabels.size == 1) {
            reason
        } else {
            "${killedLabels.joinToString(", ")} · $reason"
        }
        postSystem(context, title, body)
        OperationNotificationHelper.postInApp(title, body, SystemNotificationKind.Warning)
    }

    private fun postSystem(context: Context, title: String, body: String) {
        try {
            OperationNotificationHelper.ensureChannel(
                context,
                CHANNEL_ID,
                "Memory guard",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIF_ID, notification)
        } catch (e: Exception) {
            WajihaLog.w(WajihaTags.NOW_PLAYING, "MemoryGuardNotifier: post failed — ${e.message}")
        }
    }
}
