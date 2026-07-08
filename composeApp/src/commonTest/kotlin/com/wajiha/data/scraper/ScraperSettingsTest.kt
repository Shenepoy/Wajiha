package com.wajiha.data.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScraperSettingsTest {

    @Test
    fun forPlatform_withoutOverride_returnsSameSettings() {
        val settings = ScraperSettings()
        assertSame(settings, settings.forPlatform("snes"))
    }

    @Test
    fun forPlatform_appliesOverriddenFieldsOnly() {
        val settings = ScraperSettings(
            enabledSources = listOf("screenscraper", "steamgriddb"),
            regionPriority = listOf("us", "eu"),
            platformOverrides = mapOf(
                "psx" to PlatformScraperOverride(
                    enabledSources = listOf("romm"),
                    regionPriority = listOf("jp")
                )
            )
        )
        val effective = settings.forPlatform("psx")
        assertEquals(listOf("romm"), effective.enabledSources)
        assertEquals(listOf("jp"), effective.regionPriority)
        // Unset override fields inherit globals
        assertEquals(settings.metadataPriority, effective.metadataPriority)
        assertEquals(settings.mediaPriority, effective.mediaPriority)
        assertEquals(settings.languagePriority, effective.languagePriority)
        // Other platforms are unaffected
        assertEquals(settings.enabledSources, settings.forPlatform("snes").enabledSources)
    }

    @Test
    fun override_isEmpty_onlyWhenAllFieldsNull() {
        assertTrue(PlatformScraperOverride().isEmpty)
        assertTrue(!PlatformScraperOverride(regionPriority = listOf("us")).isEmpty)
    }

    @Test
    fun settingsRoundTripsThroughJson_withOverrides() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val settings = ScraperSettings(
            maxImageResolution = 512,
            platformOverrides = mapOf("n64" to PlatformScraperOverride(enabledSources = listOf("libretro")))
        )
        val decoded = json.decodeFromString<ScraperSettings>(json.encodeToString(ScraperSettings.serializer(), settings))
        assertEquals(settings, decoded)
    }
}
