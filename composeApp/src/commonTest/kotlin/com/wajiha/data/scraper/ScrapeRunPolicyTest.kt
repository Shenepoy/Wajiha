package com.wajiha.data.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrapeRunPolicyTest {
    @Test
    fun sourceOverride_roundTripsThroughWorkerWireValue() {
        val policy = ScrapeRunPolicy.Force.copy(sourceId = "screenscraper")

        val restored = ScrapeRunPolicy.fromName(policy.wireName())

        assertEquals(ScrapeRunMode.Force, restored.mode)
        assertEquals("screenscraper", restored.sourceId)
        assertEquals("force:screenscraper", policy.wireName())
    }

    @Test
    fun legacyWireValuesStillWork() {
        assertEquals(ScrapeRunMode.FillGaps, ScrapeRunPolicy.fromName("fill_gaps").mode)
        assertEquals(ScrapeRunMode.Force, ScrapeRunPolicy.fromName("force").mode)
        assertNull(ScrapeRunPolicy.fromName("force").sourceId)
    }
}
