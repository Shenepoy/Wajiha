package com.wajiha.data.scraper

/**
 * How confident a source is that a game hit is correct.
 * Auto-scrape prefers Hash over Name over Autocomplete.
 */
enum class MatchConfidence {
    Hash,
    Name,
    Autocomplete,
    Unknown,
}

/** Auto applies the ranked winner; Review returns the full ordered list. */
enum class MatchMode {
    Auto,
    Review,
}

/** Policy for [ScrapeMatchTool.pickBest] / ranking. */
data class MatchRankPolicy(
    val preferredAuthors: List<String> = emptyList(),
    val blacklistedAuthors: List<String> = emptyList(),
    /** Source ids in priority order (from mediaPriority for the type). */
    val sourcePriority: List<String> = emptyList(),
    val regionPriority: List<String> = emptyList(),
    val variantIndex: Int = 0,
) {
    companion object {
        fun fromSettings(
            settings: ScraperSettings,
            mediaType: MediaType,
        ): MatchRankPolicy =
            MatchRankPolicy(
                preferredAuthors = settings.preferredMediaAuthors,
                blacklistedAuthors = settings.blacklistedMediaAuthors,
                sourcePriority = settings.mediaPriority[mediaType.dbName].orEmpty(),
                regionPriority = settings.regionPriority,
                variantIndex = settings.mediaVariantIndex,
            )
    }
}

/** Game-level hit from one source before media is fully loaded. */
data class RankedGameMatch(
    val sourceId: String,
    val sourceGameId: String,
    val name: String,
    val confidence: MatchConfidence,
    val candidate: ScrapeCandidate,
    /** Normalized name similarity vs the search query (`0f..1f`). */
    val nameScore: Float = 1f,
)

/** One media option after cross-source ranking. */
data class RankedMediaOption(
    val sourceId: String,
    val candidate: MediaCandidate,
    val score: Int,
    val authorKey: String?,
    val authorDisplay: String?,
    val rankReason: String,
    /** Game-match confidence for this option's source (Hash beats Autocomplete). */
    val confidence: MatchConfidence = MatchConfidence.Unknown,
) {
    val url: String get() = candidate.url
}

/** Full search result for one game / scrape pass. */
data class MatchBundle(
    val gameMatches: List<RankedGameMatch> = emptyList(),
    /** Ranked options per media type (blacklist already applied). */
    val mediaByType: Map<MediaType, List<RankedMediaOption>> = emptyMap(),
    val metadataCandidates: List<ScrapeCandidate> = emptyList(),
    val sourceSummaries: List<SourceResultSummary> = emptyList(),
    /**
     * Whether SteamGridDB has another page for the primary SGDB hit (page 0).
     * Used by Review infinite scroll.
     */
    val steamGridDbHasMoreByType: Map<MediaType, Boolean> = emptyMap(),
    /**
     * Auto only: hits dropped by [NameMatchScorer.AutoConfidenceThreshold].
     * Review keeps weak matches for manual choice.
     */
    val rejectedLowConfidenceCount: Int = 0,
    /** Best name score among Auto rejects (for diagnostics). */
    val bestRejectedNameScore: Float? = null,
)

/** Result of [com.wajiha.data.scraper.ScrapeEngine.gatherReviewMedia] for one media type. */
data class ReviewMediaGatherResult(
    val options: List<Pair<String, MediaCandidate>>,
    val steamGridDbCandidate: ScrapeCandidate? = null,
    val steamGridDbHasMore: Boolean = false,
)

/**
 * Pure ranking helpers for [ScrapeMatchTool] — unit-testable without HTTP.
 */
object MediaRanker {
    fun authorMatches(
        authorKey: String?,
        authorName: String?,
        needles: List<String>,
    ): Boolean {
        if (needles.isEmpty()) return false
        val keys =
            listOfNotNull(authorKey, authorName)
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
        if (keys.isEmpty()) return false
        return needles.any { needle ->
            val n = needle.trim().lowercase()
            n.isNotEmpty() && keys.any { it == n || it.contains(n) }
        }
    }

    /**
     * Drop blacklisted authors, float preferred authors, then confidence
     * (Hash > Name > Autocomplete), then score / source / region.
     *
     * Auto refuses low-confidence options when a better-confidence source
     * already has media for the same type (via sort order + pickBest #0).
     */
    fun rankMediaOptions(
        options: List<RankedMediaOption>,
        policy: MatchRankPolicy,
    ): List<RankedMediaOption> {
        if (options.isEmpty()) return emptyList()
        val kept =
            options.filterNot { opt ->
                authorMatches(opt.authorKey, opt.authorDisplay, policy.blacklistedAuthors)
            }
        if (kept.isEmpty()) return emptyList()

        fun sourceRank(sourceId: String): Int {
            val idx = policy.sourcePriority.indexOf(sourceId)
            return if (idx == -1) policy.sourcePriority.size else idx
        }

        fun regionRank(region: String?): Int {
            val idx = policy.regionPriority.indexOf(region?.lowercase())
            return if (idx == -1) policy.regionPriority.size else idx
        }

        return kept
            .map { opt ->
                val preferred =
                    authorMatches(opt.authorKey, opt.authorDisplay, policy.preferredAuthors)
                val reason =
                    buildString {
                        if (preferred) append("preferred-author")
                        if (opt.confidence != MatchConfidence.Unknown) {
                            if (isNotEmpty()) append(", ")
                            append(opt.confidence.name.lowercase())
                        }
                        if (opt.score > 0) {
                            if (isNotEmpty()) append(", ")
                            append("score=${opt.score}")
                        }
                        if (isEmpty()) append("source-order")
                    }
                opt.copy(rankReason = reason) to preferred
            }.sortedWith(
                compareByDescending<Pair<RankedMediaOption, Boolean>> { it.second }
                    .thenBy { it.first.confidence.ordinal }
                    .thenByDescending { it.first.score }
                    .thenBy { sourceRank(it.first.sourceId) }
                    .thenBy { regionRank(it.first.candidate.region) },
            ).map { it.first }
    }

    fun pickBestOption(
        ranked: List<RankedMediaOption>,
        policy: MatchRankPolicy,
    ): RankedMediaOption? {
        if (ranked.isEmpty()) return null
        val idx = policy.variantIndex.coerceIn(0, ranked.lastIndex)
        return ranked[idx]
    }
}
