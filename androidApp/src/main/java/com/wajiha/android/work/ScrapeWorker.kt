package com.wajiha.android.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wajiha.data.scraper.BatchScraper
import com.wajiha.data.scraper.ScraperSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground batch-scrape job. Progress lives in [BatchScraper.progress]
 * (observed by the UI); the notification mirrors it coarsely.
 */
class ScrapeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val batchScraper: BatchScraper by inject()

    override suspend fun doWork(): Result {
        setForeground(foregroundInfo(0, 0))

        val platformId = inputData.getString(KEY_PLATFORM_ID)
        val notifJob = startNotificationUpdates()
        return try {
            batchScraper.runForPlatform(platformId)
            val progress = batchScraper.progress.value
            Result.success(
                workDataOf(
                    "matched" to progress.matched,
                    "failed" to progress.failed
                )
            )
        } finally {
            notifJob.cancel()
        }
    }

    private suspend fun startNotificationUpdates(): Job {
        val scope = CoroutineScope(kotlin.coroutines.coroutineContext + Job())
        return scope.launch {
            batchScraper.progress.collect { p ->
                if (p.running && p.total > 0) {
                    runCatching { setForeground(foregroundInfo(p.done, p.total)) }
                }
            }
        }
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val text = if (total > 0) "Scraping $done / $total" else "Scraping library"
        val notification: Notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Wajiha")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Scraping", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object : KoinComponent {
        private const val KEY_PLATFORM_ID = "platformId"
        private const val UNIQUE_NAME = "wajiha-scrape"
        private const val CHANNEL_ID = "wajiha_scrape"
        private const val NOTIFICATION_ID = 2001

        suspend fun enqueue(context: Context, platformId: String? = null) {
            val settings = getKoin().get<ScraperSettingsRepository>().current()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()
            val request = OneTimeWorkRequestBuilder<ScrapeWorker>()
                .setInputData(workDataOf(KEY_PLATFORM_ID to platformId))
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            // Mark as user-cancelled first so the persisted checkpoint isn't
            // kept in a resumable state when the coroutine gets cancelled.
            getKoin().get<BatchScraper>().requestCancel()
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
