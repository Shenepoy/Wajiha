package com.wajiha.android.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryOwnerGraceTest {
    private val graceMs = 15_000L

    @Test
    fun withinGraceWithoutStop_keepsOwner() {
        assertTrue(
            SecondaryOwnerGrace.keepOwner(
                now = 5_000L,
                armedAt = 1_000L,
                graceMs = graceMs,
                stoppedSinceArm = false,
                aliveAfterGrace = false,
            ),
        )
    }

    @Test
    fun stopDuringGrace_releasesOwner() {
        assertFalse(
            SecondaryOwnerGrace.keepOwner(
                now = 5_000L,
                armedAt = 1_000L,
                graceMs = graceMs,
                stoppedSinceArm = true,
                aliveAfterGrace = true,
            ),
        )
    }

    @Test
    fun afterGrace_followsLiveness() {
        assertTrue(
            SecondaryOwnerGrace.keepOwner(
                now = 20_000L,
                armedAt = 1_000L,
                graceMs = graceMs,
                stoppedSinceArm = false,
                aliveAfterGrace = true,
            ),
        )
        assertFalse(
            SecondaryOwnerGrace.keepOwner(
                now = 20_000L,
                armedAt = 1_000L,
                graceMs = graceMs,
                stoppedSinceArm = false,
                aliveAfterGrace = false,
            ),
        )
    }
}
