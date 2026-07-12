package com.wajiha.android.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import com.wajiha.state.SystemNotificationKind
import com.wajiha.state.SystemNotificationStore
import org.koin.core.context.GlobalContext

/**
 * Shared notification builder for library scan and batch scrape workers.
 * Progress uses a low-importance ongoing foreground notification; completion
 * posts a separate summary notification and mirrors into the in-app status bar.
 */
object OperationNotificationHelper {
    const val CHANNEL_SCAN = "wajiha_scan"
    const val CHANNEL_SCRAPE = "wajiha_scrape"

    const val NOTIF_ID_SCAN_PROGRESS = 2002
    const val NOTIF_ID_SCAN_COMPLETE = 2003
    const val NOTIF_ID_SCRAPE_PROGRESS = 2001
    const val NOTIF_ID_SCRAPE_COMPLETE = 2004

    private const val MAX_FAILURE_LINES = 3

    fun ensureChannel(
        context: Context,
        channelId: String,
        name: String,
        importance: Int = NotificationManager.IMPORTANCE_LOW,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, name, importance),
        )
    }

    fun scanForegroundInfo(
        context: Context,
        platformLabel: String?,
        foldersDone: Int,
        foldersTotal: Int,
        gamesAdded: Int,
        gamesRemoved: Int,
        detail: String?,
    ): ForegroundInfo {
        ensureChannel(context, CHANNEL_SCAN, "Library scan")
        val title = if (platformLabel != null) "Scanning $platformLabel" else "Scanning library"
        val progressText =
            when {
                foldersTotal > 0 -> "Folder ${foldersDone + 1} / $foldersTotal"
                else -> "Scanning folders…"
            }
        val stats =
            buildList {
                add(progressText)
                if (gamesAdded > 0 || gamesRemoved > 0) {
                    add("+$gamesAdded added, -$gamesRemoved removed")
                }
                detail?.let { add(it) }
            }.joinToString(" · ")
        val indeterminate = foldersTotal == 0
        val notification =
            Notification
                .Builder(context, CHANNEL_SCAN)
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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    fun hashingForegroundInfo(
        context: Context,
        platformLabel: String?,
    ): ForegroundInfo {
        ensureChannel(context, CHANNEL_SCAN, "Library scan")
        val title = if (platformLabel != null) "Scanning $platformLabel" else "Scanning library"
        val notification =
            Notification
                .Builder(context, CHANNEL_SCAN)
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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    fun scrapeForegroundInfo(
        context: Context,
        platformLabel: String?,
        done: Int,
        total: Int,
        summary: String,
        currentGameName: String?,
        paused: Boolean = false,
    ): ForegroundInfo {
        ensureChannel(context, CHANNEL_SCRAPE, "Scraping")
        val title =
            when {
                paused && platformLabel != null -> "Paused — $platformLabel"
                paused -> "Scrape paused"
                platformLabel != null -> "Scraping $platformLabel"
                else -> "Scraping library"
            }
        val detail = currentGameName?.let { shorten(it, 48) }
        val progressText =
            when {
                total > 0 -> "$done / $total — $summary"
                else -> "Preparing scrape…"
            }
        val notification =
            Notification
                .Builder(context, CHANNEL_SCRAPE)
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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    fun postScanComplete(
        context: Context,
        platformLabel: String?,
        added: Int,
        removed: Int,
        skipped: Int,
        foldersFailed: Int,
        failureMessages: List<String>,
    ) {
        ensureChannel(context, CHANNEL_SCAN, "Library scan")
        val success = foldersFailed == 0
        val title = if (success) "Scan complete" else "Scan finished with errors"
        val summary = buildScanSummary(added, removed, skipped, foldersFailed, failureMessages)
        val notification =
            Notification
                .Builder(context, CHANNEL_SCAN)
                .setSmallIcon(
                    if (success) {
                        android.R.drawable.stat_sys_download_done
                    } else {
                        android.R.drawable.stat_notify_error
                    },
                ).setContentTitle(title)
                .setContentText(summary.lineSequence().first())
                .setStyle(Notification.BigTextStyle().bigText(summary))
                .setAutoCancel(true)
                .build()
        context
            .getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_SCAN_COMPLETE, notification)
        postInApp(
            title = if (platformLabel != null) "$title · $platformLabel" else title,
            body = summary.lineSequence().first(),
            kind = if (success) SystemNotificationKind.Success else SystemNotificationKind.Error,
        )
    }

    fun postScrapeComplete(
        context: Context,
        platformLabel: String?,
        progress: com.wajiha.data.scraper.BatchScrapeProgress,
    ) {
        ensureChannel(context, CHANNEL_SCRAPE, "Scraping")
        val success = progress.problemCount == 0
        val title = if (success) "Scrape complete" else "Scrape finished with issues"
        val summary = buildScrapeSummary(progress)
        val notification =
            Notification
                .Builder(context, CHANNEL_SCRAPE)
                .setSmallIcon(
                    if (success) {
                        android.R.drawable.stat_sys_download_done
                    } else {
                        android.R.drawable.stat_notify_error
                    },
                ).setContentTitle(title)
                .setContentText(summary.lineSequence().first())
                .setStyle(Notification.BigTextStyle().bigText(summary))
                .setAutoCancel(true)
                .build()
        context
            .getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_SCRAPE_COMPLETE, notification)
        postInApp(
            title = if (platformLabel != null) "$title · $platformLabel" else title,
            body = summary.lineSequence().first(),
            kind = if (success) SystemNotificationKind.Success else SystemNotificationKind.Warning,
        )
    }

    fun shortenFolderUri(uri: String): String {
        val decoded =
            runCatching {
                android.net.Uri
                    .parse(uri)
                    .lastPathSegment
            }.getOrNull()
        return shorten(decoded ?: uri, 40)
    }

    fun postInApp(
        title: String,
        body: String,
        kind: SystemNotificationKind,
    ) {
        runCatching {
            GlobalContext.get().get<SystemNotificationStore>().post(title, body, kind)
        }
    }

    private fun buildScanSummary(
        added: Int,
        removed: Int,
        skipped: Int,
        foldersFailed: Int,
        failureMessages: List<String>,
    ): String =
        buildString {
            appendLine("+$added added · $removed removed · $skipped skipped")
            if (foldersFailed > 0) {
                appendLine("$foldersFailed folder failure${if (foldersFailed == 1) "" else "s"}")
                failureMessages.take(MAX_FAILURE_LINES).forEach { appendLine("• ${shorten(it, 80)}") }
                if (failureMessages.size > MAX_FAILURE_LINES) {
                    appendLine("• …and ${failureMessages.size - MAX_FAILURE_LINES} more")
                }
            }
            append(
                if (foldersFailed == 0) "Success" else "Completed with errors",
            )
        }

    private fun buildScrapeSummary(progress: com.wajiha.data.scraper.BatchScrapeProgress): String =
        buildString {
            appendLine(progress.summaryLine())
            val issues = progress.issues
            if (issues.isNotEmpty()) {
                issues.take(MAX_FAILURE_LINES).forEach { issue ->
                    appendLine("• ${shorten("${issue.gameName}: ${issue.message}", 80)}")
                }
                if (issues.size > MAX_FAILURE_LINES) {
                    appendLine("• …and ${issues.size - MAX_FAILURE_LINES} more")
                }
            }
            append(
                if (progress.problemCount == 0) "Success" else "Completed with issues",
            )
        }

    private fun shorten(
        text: String,
        max: Int,
    ): String = if (text.length <= max) text else text.take(max - 1) + "…"
}
