package com.wajiha.android.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.domain.scan.LibraryScanner
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground library scan. Progress lives in [LibraryScanner.progress];
 * the notification mirrors it and posts a summary when finished.
 */
class LibraryScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val scanner: LibraryScanner by inject()
    private val platformRepository: PlatformRepository by inject()

    override suspend fun doWork(): Result {
        val platformId = inputData.getString(KEY_PLATFORM_ID)
        val platformLabel = platformId?.let { platformRepository.byId(it)?.name }
        setForeground(
            OperationNotificationHelper.scanForegroundInfo(
                applicationContext,
                platformLabel,
                foldersDone = 0,
                foldersTotal = 0,
                gamesAdded = 0,
                gamesRemoved = 0,
                detail = null
            )
        )

        val notifJob = startNotificationUpdates(platformLabel)
        return try {
            val result = if (platformId != null) {
                scanner.scanPlatform(platformId)
            } else {
                scanner.scanAll()
            }

            if (inputData.getBoolean(KEY_COMPUTE_HASHES, true)) {
                runCatching {
                    setForeground(
                        OperationNotificationHelper.hashingForegroundInfo(
                            applicationContext,
                            platformLabel
                        )
                    )
                }
                var rounds = 0
                while (scanner.computeMissingHashes(limit = 25) > 0 && rounds < 40) rounds++
            }

            OperationNotificationHelper.postScanComplete(
                context = applicationContext,
                platformLabel = platformLabel,
                added = result.gamesAdded,
                removed = result.gamesRemoved,
                skipped = result.gamesSkipped,
                foldersFailed = result.foldersFailed,
                failureMessages = result.failureMessages
            )

            if (result.error != null && result.gamesAdded == 0) {
                Result.retry()
            } else {
                Result.success(
                    workDataOf(
                        "added" to result.gamesAdded,
                        "removed" to result.gamesRemoved,
                        "skipped" to result.gamesSkipped,
                        "foldersFailed" to result.foldersFailed
                    )
                )
            }
        } finally {
            notifJob.cancel()
        }
    }

    private suspend fun startNotificationUpdates(platformLabel: String?): Job {
        val scope = CoroutineScope(kotlin.coroutines.coroutineContext + Job())
        return scope.launch {
            scanner.progress.collect { p ->
                if (!p.running) return@collect
                runCatching {
                    setForeground(
                        OperationNotificationHelper.scanForegroundInfo(
                            context = applicationContext,
                            platformLabel = platformLabel,
                            foldersDone = p.foldersDone,
                            foldersTotal = p.foldersTotal,
                            gamesAdded = p.gamesAdded,
                            gamesRemoved = p.gamesRemoved,
                            detail = p.currentFolder?.let(OperationNotificationHelper::shortenFolderUri)
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val KEY_PLATFORM_ID = "platformId"
        private const val KEY_COMPUTE_HASHES = "computeHashes"
        private const val UNIQUE_NAME = "wajiha-library-scan"

        fun enqueue(context: Context, platformId: String? = null, computeHashes: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PLATFORM_ID to platformId,
                        KEY_COMPUTE_HASHES to computeHashes
                    )
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
