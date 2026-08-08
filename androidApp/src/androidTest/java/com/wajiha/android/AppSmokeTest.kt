package com.wajiha.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shallow emulator smoke: MainActivity launches and the process stays alive.
 * Deep dual-display / HOME flows are covered locally on Thor (see scripts/thor-e2e.sh).
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesWithoutCrashing() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.wajiha", appContext.packageName)

        composeRule.waitForIdle()
        assertTrue(composeRule.activity.isFinishing.not())
    }
}
