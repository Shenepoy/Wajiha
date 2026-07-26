package com.wajiha.data.scraper

import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrapeGapFillTest {
    private fun game(scrapedAt: Long? = 1L) =
        GameEntity(
            id = 1,
            platformId = "switch",
            displayName = "Zelda",
            sortName = "zelda",
            fileName = "zelda.xci",
            uri = "file:///zelda.xci",
            scrapedAt = scrapedAt,
        )

    private fun media(type: String) =
        GameMediaEntity(
            id = 1,
            gameId = 1,
            type = type,
            source = "steamgriddb",
            localPath = "/media/1/${type}_1.png",
        )

    @Test
    fun needsGapFill_falseWhenPreferredArtworkPresent() {
        val existing =
            listOf(
                media("boxart"),
                media("square"),
                media("logo"),
                media("hero"),
                media("icon"),
            )
        assertFalse(game().needsGapFill(existing))
    }

    @Test
    fun needsGapFill_trueWhenPreferredSlotMissing() {
        val existing = listOf(media("boxart"), media("logo"))
        assertTrue(game().needsGapFill(existing))
    }

    @Test
    fun needsGapFill_trueWhenOnlySquareMissing() {
        val existing = listOf(media("boxart"), media("logo"), media("hero"))
        assertTrue(game().needsGapFill(existing))
        assertEquals(listOf(MediaType.Square), existing.missingGapTypes())
    }

    @Test
    fun needsGapFill_trueWhenNeverScraped() {
        assertTrue(game(scrapedAt = null).needsGapFill(emptyList()))
    }

    @Test
    fun missingGapTypes_ignoresSecondaryArtwork() {
        val existing =
            listOf(
                media("boxart"),
                media("square"),
                media("logo"),
                media("hero"),
                media("icon"),
                media("screenshot"),
            )
        assertEquals(emptyList(), existing.missingGapTypes())
    }

    @Test
    fun alreadyComplete_userMessageExplainsForceOrManual() {
        val (msg, ok) = GameScrapeResult.alreadyComplete(1).userMessage(verb = "Scraped")
        assertTrue(ok)
        assertTrue(msg.contains("Force") || msg.contains("Manual"))
        assertTrue(msg.contains("already", ignoreCase = true))
    }
}
