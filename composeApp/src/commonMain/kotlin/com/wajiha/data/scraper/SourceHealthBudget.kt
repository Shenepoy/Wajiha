package com.wajiha.data.scraper

import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.log.logClockMs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-source call budget + short circuit breaker for batch scrapes.
 * Misses do not trip the breaker; auth / rate-limit / 5xx do.
 */
class SourceHealthBudget(
    private val sourceId: String,
    private val maxCallsPerWindow: Int = 40,
    private val cooldownMs: Long = 60_000L,
) {
    private val mutex = Mutex()
    private var callsInWindow = 0
    private var windowStartedAt = 0L
    private var coolUntil = 0L

    suspend fun isAvailable(nowMs: Long = logClockMs()): Boolean =
        mutex.withLock {
            if (nowMs < coolUntil) return false
            maybeResetWindow(nowMs)
            callsInWindow < maxCallsPerWindow
        }

    suspend fun recordCall(nowMs: Long = logClockMs()) {
        mutex.withLock {
            maybeResetWindow(nowMs)
            callsInWindow++
        }
    }

    suspend fun trip(
        reason: String,
        nowMs: Long = logClockMs(),
    ) {
        mutex.withLock {
            coolUntil = nowMs + cooldownMs
            WajihaLog.w(
                WajihaTags.SCRAPE,
                "source=$sourceId circuit open ${cooldownMs}ms — $reason",
            )
        }
    }

    private fun maybeResetWindow(nowMs: Long) {
        if (nowMs - windowStartedAt > cooldownMs) {
            windowStartedAt = nowMs
            callsInWindow = 0
        }
    }
}
