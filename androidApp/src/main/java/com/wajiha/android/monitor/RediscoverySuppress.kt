package com.wajiha.android.monitor

/**
 * Y-close rediscovery gate.
 *
 * The marker is the close time, not an expiry. Polls keep ignoring the package
 * while that same process lingers. A newer ACTIVITY_RESUMED (or an explicit
 * Wajiha launch, which clears the marker outside this helper) lifts it.
 * When usage access is denied there is no resume signal, so a short cap is the
 * only backstop.
 */
internal object RediscoverySuppress {
    fun stillSuppressed(
        closedAt: Long?,
        now: Long,
        latestResumeAt: Long,
        usageAccessGranted: Boolean,
        usageDeniedCapMs: Long = USAGE_DENIED_CAP_MS,
    ): Boolean {
        if (closedAt == null) return false
        if (latestResumeAt > closedAt) return false
        if (!usageAccessGranted && now - closedAt >= usageDeniedCapMs) return false
        return true
    }

    const val USAGE_DENIED_CAP_MS = 15_000L
}
