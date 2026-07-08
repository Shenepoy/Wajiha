package com.wajiha.android.monitor

/**
 * Conservative end-detection for an active game/emulator session.
 *
 * ```
 * NO_SESSION
 *   │ beginSession()          ← GameLauncher intent or external detection
 *   ▼
 * SESSION_ACTIVE ─────────────────────────────────────────────┐
 *   │                                                          │
 *   │ Dual-display: launcher foreground on bottom is NORMAL     │
 *   │ → UI may show grid; session stays ACTIVE                   │
 *   │                                                          │
 *   │ poll: explicit ACTIVITY_STOPPED since session start       │
 *   │ → deadStreak++ (process list alone is not enough)         │
 *   │                                                          │
 *   │ poll: deadStreak >= END_CONFIRM_POLLS                     │
 *   ▼                                                          ▼
 * (still ACTIVE until confirmed)                         NO_SESSION
 * ```
 *
 * Launch intent sets the session immediately — polling only confirms end.
 * A cached background process still counts as alive (Thor top-display emulators).
 * UsageStats foreground or ACTIVITY_STOPPED alone never ends a session.
 */
internal class GameSessionController {

    private val deadStreaks = mutableMapOf<String, Int>()

    fun onSessionStarted(packageName: String) {
        deadStreaks.remove(packageName)
    }

    fun onSessionEnded(packageName: String) {
        deadStreaks.remove(packageName)
    }

    fun isWithinLaunchGrace(sessionStartedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        sessionStartedAt > 0L && now - sessionStartedAt < LAUNCH_GRACE_MS

    /**
     * @return true when [END_CONFIRM_POLLS] consecutive dead checks have elapsed.
     */
    fun recordAliveCheck(packageName: String, processAlive: Boolean): Boolean {
        if (processAlive) {
            deadStreaks.remove(packageName)
            return false
        }
        val streak = (deadStreaks[packageName] ?: 0) + 1
        deadStreaks[packageName] = streak
        return streak >= END_CONFIRM_POLLS
    }

    companion object {
        /** Emulator process may lag behind launch intent / usage events. */
        const val LAUNCH_GRACE_MS = 8_000L
        /** Resist one flaky process poll during alt-tab; ~1.5s at 750ms active poll. */
        const val END_CONFIRM_POLLS = 2
    }
}
