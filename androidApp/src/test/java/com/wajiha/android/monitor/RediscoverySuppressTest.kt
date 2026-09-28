package com.wajiha.android.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RediscoverySuppressTest {
    @Test
    fun noMarker_notSuppressed() {
        assertFalse(
            RediscoverySuppress.stillSuppressed(
                closedAt = null,
                now = 10_000L,
                latestResumeAt = 9_000L,
                usageAccessGranted = true,
            ),
        )
    }

    @Test
    fun lingeringProcessWithoutNewerResume_staysSuppressed() {
        assertTrue(
            RediscoverySuppress.stillSuppressed(
                closedAt = 1_000L,
                now = 90_000L,
                latestResumeAt = 500L,
                usageAccessGranted = true,
            ),
        )
    }

    @Test
    fun resumeNewerThanClose_clears() {
        assertFalse(
            RediscoverySuppress.stillSuppressed(
                closedAt = 1_000L,
                now = 5_000L,
                latestResumeAt = 1_001L,
                usageAccessGranted = true,
            ),
        )
    }

    @Test
    fun resumeAtCloseInstant_staysSuppressed() {
        assertTrue(
            RediscoverySuppress.stillSuppressed(
                closedAt = 1_000L,
                now = 2_000L,
                latestResumeAt = 1_000L,
                usageAccessGranted = true,
            ),
        )
    }

    @Test
    fun usageDenied_shortCapExpires() {
        val closedAt = 1_000L
        assertTrue(
            RediscoverySuppress.stillSuppressed(
                closedAt = closedAt,
                now = closedAt + RediscoverySuppress.USAGE_DENIED_CAP_MS - 1,
                latestResumeAt = 0L,
                usageAccessGranted = false,
            ),
        )
        assertFalse(
            RediscoverySuppress.stillSuppressed(
                closedAt = closedAt,
                now = closedAt + RediscoverySuppress.USAGE_DENIED_CAP_MS,
                latestResumeAt = 0L,
                usageAccessGranted = false,
            ),
        )
    }

    @Test
    fun usageGranted_doesNotExpireOnTheDeniedCap() {
        val closedAt = 1_000L
        assertTrue(
            RediscoverySuppress.stillSuppressed(
                closedAt = closedAt,
                now = closedAt + RediscoverySuppress.USAGE_DENIED_CAP_MS + 60_000L,
                latestResumeAt = 0L,
                usageAccessGranted = true,
            ),
        )
    }
}
