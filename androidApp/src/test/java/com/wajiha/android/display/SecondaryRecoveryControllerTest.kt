package com.wajiha.android.display

import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryRecoveryControllerTest {
    @Test
    fun duplicateSignalsCoalesceIntoOneBoundedSequence() {
        val fixture = Fixture()

        fixture.controller.request(DISPLAY_ID, "leave")
        fixture.controller.request(DISPLAY_ID, "pause")
        fixture.controller.request(DISPLAY_ID, "focus")
        fixture.scheduler.runAll()

        assertEquals(listOf(false, true, true, true), fixture.effects.allowLaunchAttempts)
        assertFalse(fixture.controller.isActive)
        assertTrue(fixture.effects.logs.any { it.startsWith("coalesced") })
        assertTrue(fixture.effects.logs.any { it.startsWith("exhausted") })
        assertEquals(0, fixture.effects.recoveredCount)
        assertEquals(1, fixture.effects.exhaustedCount)
    }

    @Test
    fun resumeCancelsRetriesAndPublishesOneRecovery() {
        val fixture = Fixture()

        fixture.controller.request(DISPLAY_ID, "pause")
        fixture.effects.healthy = true
        fixture.controller.onResumed(DISPLAY_ID)
        fixture.scheduler.runAll()

        assertEquals(listOf(false), fixture.effects.allowLaunchAttempts)
        assertEquals(1, fixture.effects.recoveredCount)
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun suppressionStopsWithoutAttemptingRecovery() {
        val fixture = Fixture()
        fixture.effects.gate = SecondaryRecoveryGate(suppressReason = "single-screen")

        fixture.controller.request(DISPLAY_ID, "probe")

        assertTrue(fixture.effects.allowLaunchAttempts.isEmpty())
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun deferWaitsBeforeFirstAttempt() {
        val fixture = Fixture()
        fixture.effects.gate = SecondaryRecoveryGate(deferMs = 800L)

        fixture.controller.request(DISPLAY_ID, "game-launch")
        assertTrue(fixture.effects.allowLaunchAttempts.isEmpty())

        fixture.effects.gate = SecondaryRecoveryGate()
        fixture.scheduler.runNext()

        assertEquals(listOf(false), fixture.effects.allowLaunchAttempts)
    }

    @Test
    fun healthyProbeDoesNotStartRecovery() {
        val fixture = Fixture()
        fixture.effects.healthy = true

        fixture.controller.probe(DISPLAY_ID, "health")

        assertFalse(fixture.controller.isActive)
        assertTrue(fixture.effects.allowLaunchAttempts.isEmpty())
    }

    @Test
    fun primaryTaskPresenceChoosesReorderInsteadOfRecreate() {
        assertEquals(PrimaryLaunchStrategy.Reorder, primaryLaunchStrategy(true))
        assertEquals(PrimaryLaunchStrategy.Recreate, primaryLaunchStrategy(false))
    }

    @Test
    fun physicalSecondaryFilterRejectsEmulatorVirtualDisplays() {
        assertTrue(isPhysicalSecondaryDisplay(4, displayFlags = 0, isValid = true))
        assertFalse(
            isPhysicalSecondaryDisplay(6, displayFlags = Display.FLAG_PRIVATE, isValid = true),
        )
        assertFalse(
            isPhysicalSecondaryDisplay(
                Display.DEFAULT_DISPLAY,
                displayFlags = 0,
                isValid = true,
            ),
        )
        assertFalse(isPhysicalSecondaryDisplay(4, displayFlags = 0, isValid = false))
    }

    private class Fixture {
        val scheduler = FakeScheduler()
        val effects = FakeEffects()
        val controller =
            SecondaryRecoveryController(
                scheduler = scheduler,
                effects = effects,
            )
    }

    private class FakeScheduler : SecondaryRecoveryScheduler {
        private val actions = ArrayDeque<() -> Unit>()

        override fun post(
            delayMs: Long,
            action: () -> Unit,
        ) {
            actions.addLast(action)
        }

        fun runNext() {
            actions.removeFirstOrNull()?.invoke()
        }

        fun runAll() {
            while (actions.isNotEmpty()) {
                runNext()
            }
        }
    }

    private class FakeEffects : SecondaryRecoveryEffects {
        var healthy = false
        var gate = SecondaryRecoveryGate()
        val allowLaunchAttempts = mutableListOf<Boolean>()
        val logs = mutableListOf<String>()
        var recoveredCount = 0
        var exhaustedCount = 0

        override fun gate(): SecondaryRecoveryGate = gate

        override fun isHealthy(displayId: Int): Boolean = healthy

        override fun recover(
            displayId: Int,
            allowLaunch: Boolean,
        ) {
            allowLaunchAttempts += allowLaunch
        }

        override fun onRecovered(displayId: Int) {
            recoveredCount += 1
        }

        override fun onExhausted(displayId: Int) {
            exhaustedCount += 1
        }

        override fun log(message: String) {
            logs += message
        }
    }

    private companion object {
        const val DISPLAY_ID = 4
    }
}
