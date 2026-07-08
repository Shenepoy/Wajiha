package com.wajiha.android.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wajiha.data.scraper.BatchScraper
import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.domain.repository.PlatformRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground batch-scrape job. Progress lives in [BatchScraper.progress]
 * (observed by the UI); the notification mirrors it and posts a summary when finished.
 */
class ScrapeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val batchScraper: BatchScraper by inject()
    private val platformRepository: PlatformRepository by inject()

    override suspend fun doWork(): Result {
        val platformId = inputData.getString(KEY_PLATFORM_ID)
        val platformLabel = platformId?.let { platformRepository.byId(it)?.name }
        setForeground(
            OperationNotificationHelper.scrapeForegroundInfo(
                applicationContext,
                platformLabel,
                done = 0,
                total = 0,
                matched = 0,
                failed = 0,
                currentGameName = null
            )
        )

        val notifJob = startNotificationUpdates(platformLabel)
        return try {
            batchScraper.runForPlatform(platformId)
            val progress = batchScraper.progress.value
            OperationNotificationHelper.postScrapeComplete(
                context = applicationContext,
                platformLabel = platformLabel,
                matched = progress.matched,
                failed = progress.failed,
                errors = progress.errors
            )
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

    private suspend fun startNotificationUpdates(platformLabel: String?): Job {
        val scope = CoroutineScope(kotlin.coroutines.coroutineContext + Job())
        return scope.launch {
            batchScraper.progress.collect { p ->
                if (!p.running) return@collect
                runCatching {
                    setForeground(
                        OperationNotificationHelper.scrapeForegroundInfo(
                            context = applicationContext,
                            platformLabel = platformLabel,
                            done = p.done,
                            total = p.total,
                            matched = p.matched,
                            failed = p.failed,
                            currentGameName = p.currentGameName
                        )
                    )
                }
            }
        }
    }

    companion object : KoinComponent {
        private const val KEY_PLATFORM_ID = "platformId"
        private const val UNIQUE_NAME = "wajiha-scrape"

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
