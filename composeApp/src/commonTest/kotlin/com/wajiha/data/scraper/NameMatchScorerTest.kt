package com.wajiha.data.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameMatchScorerTest {
    @Test
    fun exactNormalizedMatchScoresOne() {
        assertEquals(1f, NameMatchScorer.score("The Legend of Zelda", "the legend of zelda"))
        assertEquals(
            1f,
            NameMatchScorer.score("Zelda (USA).n64", "The Legend of Zelda [!].n64"),
        )
    }

    @Test
    fun unrelatedTitlesFailAutoGate() {
        val score = NameMatchScorer.score("Super Mario Bros", "Final Fantasy VII")
        assertTrue(score < NameMatchScorer.AutoConfidenceThreshold)
        assertFalse(
            NameMatchScorer.passesAutoGate(
                MatchConfidence.Autocomplete,
                "Super Mario Bros",
                "Final Fantasy VII",
            ),
        )
    }

    @Test
    fun closeTitlesPassThreshold() {
        val score = NameMatchScorer.score("Sonic the Hedgehog", "Sonic The Hedgehog 1")
        assertTrue(score >= NameMatchScorer.AutoConfidenceThreshold, "score=$score")
        assertTrue(
            NameMatchScorer.passesAutoGate(
                MatchConfidence.Name,
                "Sonic the Hedgehog",
                "Sonic The Hedgehog 1",
            ),
        )
    }

    @Test
    fun hashAlwaysPassesEvenWhenNamesDiffer() {
        assertTrue(
            NameMatchScorer.passesAutoGate(
                MatchConfidence.Hash,
                "completely different",
                "other game entirely",
            ),
        )
    }

    @Test
    fun normalizeStripsTagsAndPath() {
        assertEquals(
            "super mario world",
            NameMatchScorer.normalize("/roms/snes/Super Mario World (USA) [!].sfc"),
        )
    }
}
