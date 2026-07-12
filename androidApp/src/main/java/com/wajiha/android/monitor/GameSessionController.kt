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
 *   │ poll: process/task/usage signals all miss                 │
 *   │ → deadStreak++                                            │
 *   │                                                          │
 *   │ poll: deadStreak >= END_CONFIRM_POLLS                     │
 *   ▼                                                          ▼
 * (still ACTIVE until confirmed)                         NO_SESSION
 * ```
 *
 * Launch intent sets the session immediately — polling only confirms end.
 * A cached background process still counts as alive (Thor top-display emulators).
 * UsageStats alone never ends a session.
 */
internal class GameSessionController {
    private val deadStreaks = mutableMapOf<String, Int>()

    /** Brief grace when another session launches and background polls lie. */
    private val siblingLaunchGraceUntil = mutableMapOf<String, Long>()

    fun onSessionStarted(packageName: String) {
        deadStreaks.remove(packageName)
        siblingLaunchGraceUntil.remove(packageName)
    }

    fun onSessionEnded(packageName: String) {
        deadStreaks.remove(packageName)
        siblingLaunchGraceUntil.remove(packageName)
    }

    /** Existing sessions get extra grace when a sibling game takes the top display. */
    fun markSiblingLaunchGrace(
        vararg packageNames: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val until = now + SIBLING_LAUNCH_GRACE_MS
        packageNames.forEach { siblingLaunchGraceUntil[it] = until }
    }

    fun isWithinLaunchGrace(
        sessionStartedAt: Long,
        packageName: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (packageName != null && (siblingLaunchGraceUntil[packageName] ?: 0L) > now) return true
        return sessionStartedAt > 0L && now - sessionStartedAt < LAUNCH_GRACE_MS
    }

    /**
     * @return true when [END_CONFIRM_POLLS] consecutive dead checks have elapsed.
     */
    fun recordAliveCheck(
        packageName: String,
        processAlive: Boolean,
        multiSession: Boolean = false,
    ): Boolean {
        if (processAlive) {
            deadStreaks.remove(packageName)
            return false
        }
        val streak = (deadStreaks[packageName] ?: 0) + 1
        deadStreaks[packageName] = streak
        val required = if (multiSession) MULTI_SESSION_END_CONFIRM_POLLS else END_CONFIRM_POLLS
        return streak >= required
    }

    companion object {
        /** Emulator process may lag behind launch intent / usage events. */
        const val LAUNCH_GRACE_MS = 8_000L

        /** Resist one flaky process poll during alt-tab; ~1.5s at 750ms active poll. */
        const val END_CONFIRM_POLLS = 2

        /** Background session while another game is on top — process polls are noisier. */
        const val MULTI_SESSION_END_CONFIRM_POLLS = 4

        /** Grace for cached siblings when a new game launches on display 0. */
        const val SIBLING_LAUNCH_GRACE_MS = 12_000L
    }
}
