package com.wajiha.android.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo

/**
 * Shared notification builder for library scan and batch scrape workers.
 * Progress uses a low-importance ongoing foreground notification; completion
 * posts a separate summary notification.
 */
object OperationNotificationHelper {

    const val CHANNEL_SCAN = "wajiha_scan"
    const val CHANNEL_SCRAPE = "wajiha_scrape"

    const val NOTIF_ID_SCAN_PROGRESS = 2002
    const val NOTIF_ID_SCAN_COMPLETE = 2003
    const val NOTIF_ID_SCRAPE_PROGRESS = 2001
    const val NOTIF_ID_SCRAPE_COMPLETE = 2004

    private const val MAX_FAILURE_LINES = 3

    fun ensureChannel(context: Context, channelId: String, name: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun scanForegroundInfo(
        context: Context,
        platformLabel: String?,
        foldersDone: Int,
        foldersTotal: Int,
        gamesAdded: Int,
        gamesRemoved: Int,
        detail: String?
    ): ForegroundInfo {
        ensureChannel(context, CHANNEL_SCAN, "Library scan")
        val title = if (platformLabel != null) "Scanning $platformLabel" else "Scanning library"
        val progressText = when {
            foldersTotal > 0 -> "Folder ${foldersDone + 1} / $foldersTotal"
            else -> "Scanning folders…"
        }
        val stats = buildList {
            add(progressText)
            if (gamesAdded > 0 || gamesRemoved > 0) {
                add("+$gamesAdded added, -$gamesRemoved removed")
            }
            detail?.let { add(it) }
        }.joinToString(" · ")
        val indeterminate = foldersTotal == 0
        val notification = Notification.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(stats)
            .setSubText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(foldersTotal.coerceAtLeast(1), foldersDone, indeterminate)
            .build()
        return ForegroundInfo(
            NOTIF_ID_SCAN_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    fun hashingForegroundInfo(context: Context, platformLabel: String?): ForegroundInfo {
        ensureChannel(context, CHANNEL_SCAN, "Library scan")
        val title = if (platformLabel != null) "Scanning $platformLabel" else "Scanning library"
        val notification = Notification.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Computing file hashes…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(
            NOTIF_ID_SCAN_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    fun scrapeForegroundInfo(
        context: Context,
        platformLabel: String?,
        done: Int,
        total: Int,
        matched: Int,
        failed: Int,
        currentGameName: String?
    ): ForegroundInfo {
        ensureChannel(context, CHANNEL_SCRAPE, "Scraping")
        val title = if (platformLabel != null) "Scraping $platformLabel" else "Scraping library"
        val detail = currentGameName?.let { shorten(it, 48) }
        val progressText = when {
            total > 0 -> "$done / $total — $matched matched, $failed failed"
            else -> "Preparing scrape…"
        }
        val notification = Notification.Builder(context, CHANNEL_SCRAPE)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(detail ?: progressText)
            .setSubText(if (detail != null) progressText else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .build()
        return ForegroundInfo(
            NOTIF_ID_SCRAPE_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    fun postScanComplete(
        context: Context,
        platformLabel: String?,
        added: Int,
        removed: Int,
        skipped: Int,
        foldersFailed: Int,
        failureMessages: List<String>
    ) {
        ensureChannel(context, CHANNEL_SCAN, "Library scan")
        val success = foldersFailed == 0
        val title = if (success) "Scan complete" else "Scan finished with errors"
        val summary = buildScanSummary(added, removed, skipped, foldersFailed, failureMessages)
        val notification = Notification.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(title)
            .setContentText(summary.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_SCAN_COMPLETE, notification)
    }

    fun postScrapeComplete(
        context: Context,
        platformLabel: String?,
        matched: Int,
        failed: Int,
        errors: Map<Long, String>
    ) {
        ensureChannel(context, CHANNEL_SCRAPE, "Scraping")
        val success = failed == 0
        val title = if (success) "Scrape complete" else "Scrape finished with errors"
        val summary = buildScrapeSummary(matched, failed, errors)
        val notification = Notification.Builder(context, CHANNEL_SCRAPE)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(title)
            .setContentText(summary.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_SCRAPE_COMPLETE, notification)
    }

    fun shortenFolderUri(uri: String): String {
        val decoded = runCatching { android.net.Uri.parse(uri).lastPathSegment }.getOrNull()
        return shorten(decoded ?: uri, 40)
    }

    private fun buildScanSummary(
        added: Int,
        removed: Int,
        skipped: Int,
        foldersFailed: Int,
        failureMessages: List<String>
    ): String = buildString {
        appendLine("+$added added · $removed removed · $skipped skipped")
        if (foldersFailed > 0) {
            appendLine("$foldersFailed folder failure${if (foldersFailed == 1) "" else "s"}")
            failureMessages.take(MAX_FAILURE_LINES).forEach { appendLine("• ${shorten(it, 80)}") }
            if (failureMessages.size > MAX_FAILURE_LINES) {
                appendLine("• …and ${failureMessages.size - MAX_FAILURE_LINES} more")
            }
        }
        append(
            if (foldersFailed == 0) "Success" else "Completed with errors"
        )
    }

    private fun buildScrapeSummary(
        matched: Int,
        failed: Int,
        errors: Map<Long, String>
    ): String = buildString {
        appendLine("$matched updated · $failed failed")
        if (failed > 0) {
            errors.values.take(MAX_FAILURE_LINES).forEach { appendLine("• ${shorten(it, 80)}") }
            if (errors.size > MAX_FAILURE_LINES) {
                appendLine("• …and ${errors.size - MAX_FAILURE_LINES} more")
            }
        }
        append(if (failed == 0) "Success" else "Completed with errors")
    }

    private fun shorten(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"
}
