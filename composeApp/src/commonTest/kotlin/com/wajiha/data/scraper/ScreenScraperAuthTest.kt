package com.wajiha.data.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenScraperAuthTest {
    @Test
    fun resolveDev_omitsWhenNeitherProvided() {
        assertNull(
            resolveScreenScraperDevCredentials(
                settings = ScraperSettings(screenScraperUser = "me", screenScraperPassword = "pw"),
                buildDevId = "",
                buildDevPassword = "",
            ),
        )
    }

    @Test
    fun resolveDev_omitsWhenOnlyHalfProvided() {
        assertNull(
            resolveScreenScraperDevCredentials(
                settings = ScraperSettings(screenScraperDevId = "only-id"),
                buildDevId = "",
                buildDevPassword = "",
            ),
        )
        assertNull(
            resolveScreenScraperDevCredentials(
                settings = ScraperSettings(),
                buildDevId = "build-id",
                buildDevPassword = "",
            ),
        )
    }

    @Test
    fun resolveDev_prefersSettingsOverBuild() {
        val resolved =
            resolveScreenScraperDevCredentials(
                settings =
                    ScraperSettings(
                        screenScraperDevId = " settings-id ",
                        screenScraperDevPassword = "settings-pw",
                    ),
                buildDevId = "build-id",
                buildDevPassword = "build-pw",
            )
        assertEquals("settings-id" to "settings-pw", resolved)
    }

    @Test
    fun resolveDev_fallsBackToBuildWhenSettingsBlank() {
        val resolved =
            resolveScreenScraperDevCredentials(
                settings = ScraperSettings(screenScraperUser = "me"),
                buildDevId = "build-id",
                buildDevPassword = "build-pw",
            )
        assertEquals("build-id" to "build-pw", resolved)
    }
}
