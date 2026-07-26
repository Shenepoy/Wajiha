package com.wajiha.android.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTimelineActiveTest {
    @Test
    fun setupWizardAfterMain_stillActive() {
        // NetherSX2 / AetherSX2: Main resumes, then SetupWizard; Main stops last.
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "MainActivity", 100),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_PAUSED, "MainActivity", 110),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "SetupWizardActivity", 120),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_STOPPED, "MainActivity", 130),
            )
        assertTrue(UsageTimelineActive.isActive(events))
    }

    @Test
    fun packageFullyStopped_notActive() {
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "MainActivity", 100),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "SetupWizardActivity", 120),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_STOPPED, "MainActivity", 130),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_PAUSED, "SetupWizardActivity", 200),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_STOPPED, "SetupWizardActivity", 210),
            )
        assertFalse(UsageTimelineActive.isActive(events))
    }

    @Test
    fun singleActivityResume_active() {
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "EmulationActivity", 50),
            )
        assertTrue(UsageTimelineActive.isActive(events))
    }

    @Test
    fun packageWideLastStopWouldLie_perActivityDoesNot() {
        // Old lastResume(120) < lastStop(130) package-wide check → false dead.
        val events =
            listOf(
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "A", 100),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_RESUMED, "B", 120),
                UsageTimelineEvent(UsageTimelineActive.ACTIVITY_STOPPED, "A", 130),
            )
        assertTrue(UsageTimelineActive.isActive(events))
    }
}
