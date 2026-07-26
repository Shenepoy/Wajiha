package com.wajiha.android.monitor

/**
 * Package-level usage "still active" from a stream of activity lifecycle events.
 *
 * Multi-activity apps (e.g. NetherSX2 SetupWizard on top of MainActivity) emit
 * ACTIVITY_STOPPED for the root activity *after* the child resumes. A package-wide
 * lastResume > lastStop check then falsely reports dead and ends the session.
 *
 * Track resume/stop per activity class instead: the package is active while any
 * of its activities is still in the resumed set.
 */
internal data class UsageTimelineEvent(
    val eventType: Int,
    val className: String?,
    val timeStamp: Long,
)

internal object UsageTimelineActive {
    // Values match android.app.usage.UsageEvents.Event (RESUMED/MOVE_TO_FOREGROUND=1,
    // PAUSED/MOVE_TO_BACKGROUND=2, STOPPED=23).
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val ACTIVITY_STOPPED = 23

    fun isActive(events: Iterable<UsageTimelineEvent>): Boolean {
        val resumed = linkedSetOf<String>()
        for (event in events.sortedBy { it.timeStamp }) {
            val key = event.className?.takeIf { it.isNotEmpty() } ?: ""
            when (event.eventType) {
                ACTIVITY_RESUMED -> resumed.add(key)

                ACTIVITY_PAUSED,
                ACTIVITY_STOPPED,
                -> resumed.remove(key)
            }
        }
        return resumed.isNotEmpty()
    }
}
