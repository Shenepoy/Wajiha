package com.wajiha.android.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wajiha.domain.scan.LibraryScanner
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background library scan. Runs the incremental SAF scan, then a lazy
 * hashing pass for newly added games.
 */
class LibraryScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val scanner: LibraryScanner by inject()

    override suspend fun doWork(): Result {
        val platformId = inputData.getString(KEY_PLATFORM_ID)
        val result = if (platformId != null) {
            scanner.scanPlatform(platformId)
        } else {
            scanner.scanAll()
        }
        if (inputData.getBoolean(KEY_COMPUTE_HASHES, true)) {
            var rounds = 0
            while (scanner.computeMissingHashes(limit = 25) > 0 && rounds < 40) rounds++
        }
        return if (result.error != null && result.gamesAdded == 0) {
            Result.retry()
        } else {
            Result.success(
                workDataOf(
                    "added" to result.gamesAdded,
                    "removed" to result.gamesRemoved
                )
            )
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
