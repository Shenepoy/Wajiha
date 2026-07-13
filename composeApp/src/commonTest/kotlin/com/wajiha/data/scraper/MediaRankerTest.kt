package com.wajiha.data.scraper

import com.wajiha.data.scraper.sources.cleanRomSearchName
import com.wajiha.data.scraper.sources.romTypeFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaRankerTest {
    private fun opt(
        sourceId: String,
        url: String,
        score: Int,
        authorKey: String? = null,
        authorName: String? = null,
        region: String? = null,
        confidence: MatchConfidence = MatchConfidence.Unknown,
    ) = RankedMediaOption(
        sourceId = sourceId,
        candidate =
            MediaCandidate(
                type = MediaType.Boxart,
                url = url,
                region = region,
                score = score,
                authorKey = authorKey,
                authorName = authorName,
            ),
        score = score,
        authorKey = authorKey,
        authorDisplay = authorName,
        rankReason = "",
        confidence = confidence,
    )

    @Test
    fun blacklistSkipsTopScoreAndPicksNext() {
        val options =
            listOf(
                opt("steamgriddb", "a", score = 100, authorKey = "bad64", authorName = "BadAuthor"),
                opt("steamgriddb", "b", score = 50, authorKey = "good64", authorName = "Good"),
                opt("steamgriddb", "c", score = 40, authorKey = "other", authorName = "Other"),
            )
        val policy =
            MatchRankPolicy(
                blacklistedAuthors = listOf("bad64"),
                sourcePriority = listOf("steamgriddb"),
            )
        val ranked = MediaRanker.rankMediaOptions(options, policy)
        assertEquals("b", ranked.first().url)
        assertEquals(50, ranked.first().score)
        val best = MediaRanker.pickBestOption(ranked, policy)
        assertEquals("b", best?.url)
    }

    @Test
    fun preferredAuthorFloatsAboveHigherScore() {
        val options =
            listOf(
                opt("steamgriddb", "high", score = 99, authorName = "Popular"),
                opt("steamgriddb", "pref", score = 10, authorName = "MyArtist"),
            )
        val policy =
            MatchRankPolicy(
                preferredAuthors = listOf("MyArtist"),
                sourcePriority = listOf("steamgriddb"),
            )
        val ranked = MediaRanker.rankMediaOptions(options, policy)
        assertEquals("pref", ranked.first().url)
        assertTrue(ranked.first().rankReason.contains("preferred-author"))
    }

    @Test
    fun scoreThenSourcePriority() {
        val options =
            listOf(
                opt("libretro", "lib", score = 5),
                opt("steamgriddb", "sg", score = 5),
                opt("screenscraper", "ss", score = 8),
            )
        val policy =
            MatchRankPolicy(
                sourcePriority = listOf("steamgriddb", "screenscraper", "libretro"),
            )
        val ranked = MediaRanker.rankMediaOptions(options, policy)
        assertEquals("ss", ranked.first().url)
        assertEquals("sg", ranked[1].url)
    }

    @Test
    fun variantIndexPicksNth() {
        val options =
            listOf(
                opt("steamgriddb", "first", score = 30),
                opt("steamgriddb", "second", score = 20),
                opt("steamgriddb", "third", score = 10),
            )
        val policy = MatchRankPolicy(variantIndex = 2)
        val ranked = MediaRanker.rankMediaOptions(options, policy)
        assertEquals("third", MediaRanker.pickBestOption(ranked, policy)?.url)
    }

    @Test
    fun hashConfidenceBeatsHigherScoreAutocomplete() {
        val options =
            listOf(
                opt(
                    "steamgriddb",
                    "sgdb",
                    score = 99,
                    confidence = MatchConfidence.Autocomplete,
                ),
                opt(
                    "screenscraper",
                    "ss",
                    score = 10,
                    confidence = MatchConfidence.Hash,
                ),
            )
        val policy =
            MatchRankPolicy(
                sourcePriority = listOf("steamgriddb", "screenscraper"),
            )
        val ranked = MediaRanker.rankMediaOptions(options, policy)
        assertEquals("ss", ranked.first().url)
        assertTrue(ranked.first().rankReason.contains("hash"))
    }

    @Test
    fun preferredAuthorStillBeatsHash() {
        val options =
            listOf(
                opt(
                    "screenscraper",
                    "ss",
                    score = 10,
                    confidence = MatchConfidence.Hash,
                ),
                opt(
                    "steamgriddb",
                    "pref",
                    score = 5,
                    authorName = "MyArtist",
                    confidence = MatchConfidence.Autocomplete,
                ),
            )
        val policy =
            MatchRankPolicy(
                preferredAuthors = listOf("MyArtist"),
                sourcePriority = listOf("screenscraper", "steamgriddb"),
            )
        val ranked = MediaRanker.rankMediaOptions(options, policy)
        assertEquals("pref", ranked.first().url)
    }

    @Test
    fun cleanRomSearchNameStripsTags() {
        assertEquals(
            "Bayonetta 2",
            cleanRomSearchName("Bayonetta 2 (World) (En,Ja).xci"),
        )
    }

    @Test
    fun romTypeForDiscImages() {
        assertEquals("iso", romTypeFor("game.chd"))
        assertEquals("rom", romTypeFor("game.xci"))
    }
}
