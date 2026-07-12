package com.wajiha.ui.secondary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wajiha.state.NowPlayingState
import kotlinx.coroutines.delay
import kotlin.time.Clock

fun formatSessionElapsed(elapsedMs: Long): String {
    val totalSec = (elapsedMs / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
}

@Composable
fun rememberSessionElapsedMs(session: NowPlayingState): Long {
    var elapsed by remember(
        session.packageName,
        session.sessionElapsedMs,
        session.sessionResumedAt,
    ) {
        mutableLongStateOf(session.activeElapsedMs())
    }
    LaunchedEffect(session.packageName, session.sessionElapsedMs, session.sessionResumedAt) {
        while (true) {
            elapsed = session.activeElapsedMs()
            delay(if (session.sessionResumedAt > 0L) 1000 else 2000)
        }
    }
    return elapsed
}
