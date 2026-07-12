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
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.domain.repository.PlatformRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground batch-scrape job. Progress lives in [BatchScraper.progress]
 * (observed by the UI); the notification mirrors it and posts a summary when finished.
 * Review mode is UI-only and never enqueued here.
 */
class ScrapeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val batchScraper: BatchScraper by inject()
    private val platformRepository: PlatformRepository by inject()

    override suspend fun doWork(): Result {
        val platformId = inputData.getString(KEY_PLATFORM_ID)
        val policy = ScrapeRunPolicy.fromName(inputData.getString(KEY_MODE))
        val platformLabel = platformId?.let { platformRepository.byId(it)?.name }
        setForeground(
            OperationNotificationHelper.scrapeForegroundInfo(
                applicationContext,
                platformLabel,
                done = 0,
                total = 0,
                summary = "Preparing scrape…",
                currentGameName = null,
                paused = false
            )
        )

        val notifJob = launchProgressNotifications(
            progress = batchScraper.progress,
            shouldUpdate = { it.running },
            foregroundInfo = { p ->
                OperationNotificationHelper.scrapeForegroundInfo(
                    context = applicationContext,
                    platformLabel = platformLabel,
                    done = p.done,
                    total = p.total,
                    summary = p.summaryLine(),
                    currentGameName = p.currentGameName,
                    paused = p.paused
                )
            }
        )
        return try {
            batchScraper.runForPlatform(platformId, policy)
            val progress = batchScraper.progress.value
            OperationNotificationHelper.postScrapeComplete(
                context = applicationContext,
                platformLabel = platformLabel,
                progress = progress
            )
            Result.success(
                workDataOf(
                    "matched" to progress.matched,
                    "partial" to progress.partial,
                    "noMatch" to progress.noMatch,
                    "errors" to progress.errorCount
                )
            )
        } finally {
            notifJob.cancel()
        }
    }

    companion object : KoinComponent {
        private const val KEY_PLATFORM_ID = "platformId"
        private const val KEY_MODE = "mode"
        private const val UNIQUE_NAME = "wajiha-scrape"

        /** True when a scrape job is running or waiting on constraints (e.g. Wi‑Fi). */
        suspend fun isWorkActive(context: Context): Boolean {
            if (getKoin().get<BatchScraper>().progress.value.running) return true
            return try {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)
                    .first()
                    .any { !it.state.isFinished }
            } catch (_: Exception) {
                false
            }
        }

        suspend fun enqueue(
            context: Context,
            platformId: String? = null,
            policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps
        ) {
            val settings = getKoin().get<ScraperSettingsRepository>().current()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()
            val request = OneTimeWorkRequestBuilder<ScrapeWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PLATFORM_ID to platformId,
                        KEY_MODE to policy.wireName()
                    )
                )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            getKoin().get<BatchScraper>().requestCancel()
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
