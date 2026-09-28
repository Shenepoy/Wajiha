package com.wajiha.data.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrapeRunPolicyTest {
    @Test
    fun sourceOverride_roundTripsThroughWorkerWireValue() {
        val policy = ScrapeRunPolicy.Force.copy(sourceIds = listOf("screenscraper"))

        val restored = ScrapeRunPolicy.fromName(policy.wireName())

        assertEquals(ScrapeRunMode.Force, restored.mode)
        assertEquals("screenscraper", restored.sourceId)
        assertEquals(listOf("screenscraper"), restored.sourceIds)
        assertEquals("force:screenscraper", policy.wireName())
    }

    @Test
    fun multiSourceOverride_roundTripsThroughWorkerWireValue() {
        val policy =
            ScrapeRunPolicy.FillGaps.copy(sourceIds = listOf("screenscraper", "steamgriddb"))

        val restored = ScrapeRunPolicy.fromName(policy.wireName())

        assertEquals(listOf("screenscraper", "steamgriddb"), restored.sourceIds)
        assertEquals("fill_gaps:screenscraper,steamgriddb", policy.wireName())
        assertNull(restored.sourceId)
    }

    @Test
    fun legacyWireValuesStillWork() {
        assertEquals(ScrapeRunMode.FillGaps, ScrapeRunPolicy.fromName("fill_gaps").mode)
        assertEquals(ScrapeRunMode.Force, ScrapeRunPolicy.fromName("force").mode)
        assertNull(ScrapeRunPolicy.fromName("force").sourceId)
    }
}
