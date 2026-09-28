package com.wajiha.android.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroUsageOwnershipTest {
    @Test
    fun resumeWithoutStop_stillOwns() {
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "ChromeTabbedActivity", 100),
            )
        assertTrue(usageEventsStillOwnDisplay(events))
    }

    @Test
    fun stopNewerThanResume_doesNotOwn() {
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "ChromeTabbedActivity", 100),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_STOPPED, "ChromeTabbedActivity", 200),
            )
        assertFalse(usageEventsStillOwnDisplay(events))
    }

    @Test
    fun pauseNewerThanResume_doesNotOwn() {
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "ChromeTabbedActivity", 100),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_PAUSED, "ChromeTabbedActivity", 150),
            )
        assertFalse(usageEventsStillOwnDisplay(events))
    }

    @Test
    fun childResumeAfterRootStop_stillOwns() {
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "MainActivity", 100),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "SetupWizardActivity", 120),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_STOPPED, "MainActivity", 130),
            )
        assertTrue(usageEventsStillOwnDisplay(events))
    }
}
