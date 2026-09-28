package com.wajiha.android.display

/**
 * Whether a bottom-screen foreign app should keep ownership.
 *
 * Inside the grace window, task-manager blindness is not proof the app died.
 * A UsageStats stop after arm is. After the window, only a live process or task
 * keeps ownership.
 */
internal object SecondaryOwnerGrace {
    fun keepOwner(
        now: Long,
        armedAt: Long,
        graceMs: Long,
        stoppedSinceArm: Boolean,
        aliveAfterGrace: Boolean,
    ): Boolean {
        val withinGrace = armedAt > 0L && now - armedAt < graceMs
        if (withinGrace) return !stoppedSinceArm
        return aliveAfterGrace
    }
}
