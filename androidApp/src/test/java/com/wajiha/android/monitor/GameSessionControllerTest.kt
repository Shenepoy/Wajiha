package com.wajiha.android.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionControllerTest {
    @Test
    fun twoDeadPollsEndSingleSession() {
        val controller = GameSessionController()
        assertFalse(controller.recordAliveCheck("emu", processAlive = false))
        assertTrue(controller.recordAliveCheck("emu", processAlive = false))
    }

    @Test
    fun aliveCheckResetsDeadStreak() {
        val controller = GameSessionController()
        assertFalse(controller.recordAliveCheck("emu", processAlive = false))
        assertFalse(controller.recordAliveCheck("emu", processAlive = true))
        assertFalse(controller.recordAliveCheck("emu", processAlive = false))
    }

    @Test
    fun multiSessionNeedsFourDeadPolls() {
        val controller = GameSessionController()
        repeat(GameSessionController.MULTI_SESSION_END_CONFIRM_POLLS - 1) {
            assertFalse(
                controller.recordAliveCheck(
                    "emu",
                    processAlive = false,
                    multiSession = true,
                ),
            )
        }
        assertTrue(
            controller.recordAliveCheck(
                "emu",
                processAlive = false,
                multiSession = true,
            ),
        )
    }

    @Test
    fun launchGraceLastsEightSeconds() {
        val controller = GameSessionController()
        val startedAt = 1_000L
        assertTrue(
            controller.isWithinLaunchGrace(
                sessionStartedAt = startedAt,
                now = startedAt + GameSessionController.LAUNCH_GRACE_MS - 1,
            ),
        )
        assertFalse(
            controller.isWithinLaunchGrace(
                sessionStartedAt = startedAt,
                now = startedAt + GameSessionController.LAUNCH_GRACE_MS,
            ),
        )
    }

    @Test
    fun siblingLaunchGraceOutlastsOwnLaunchGrace() {
        val controller = GameSessionController()
        controller.markSiblingLaunchGrace("emu", now = 0L)
        assertTrue(
            controller.isWithinLaunchGrace(
                sessionStartedAt = 0L,
                packageName = "emu",
                now = GameSessionController.SIBLING_LAUNCH_GRACE_MS - 1,
            ),
        )
        assertFalse(
            controller.isWithinLaunchGrace(
                sessionStartedAt = 0L,
                packageName = "emu",
                now = GameSessionController.SIBLING_LAUNCH_GRACE_MS,
            ),
        )
    }
}
