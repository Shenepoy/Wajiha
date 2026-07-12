package com.wajiha.android.work

import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal suspend fun <T> CoroutineWorker.launchProgressNotifications(
    progress: Flow<T>,
    shouldUpdate: (T) -> Boolean,
    foregroundInfo: (T) -> ForegroundInfo,
): Job {
    val scope = CoroutineScope(coroutineContext + Job())
    return scope.launch {
        progress.collect { value ->
            if (!shouldUpdate(value)) return@collect
            runCatching { setForeground(foregroundInfo(value)) }
        }
    }
}
