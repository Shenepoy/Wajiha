package com.wajiha.data.scraper

import com.wajiha.data.scraper.sources.SteamGridDbSource
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.CancellationException

/**
 * Internal match tool: search configured sources, rank media (score + author prefs),
 * and either auto-pick the winner or return the ordered list for Review.
 */
class ScrapeMatchTool(
    private val sources: List<ScraperSource>,
) {
    fun source(id: String): ScraperSource? = sources.firstOrNull { it.id == id }

    fun configuredSources(settings: ScraperSettings): List<ScraperSource> =
        sources.filter { it.id in settings.enabledSources && it.isConfigured(settings) }

    /**
     * Searches all configured sources for [query].
     * [neededTypes] limits which media types get lazy art fetches (SGDB) and
     * which types are ranked into [MatchBundle.mediaByType].
     * Empty [neededTypes] = metadata / game match only (no media art fetch).
     */
    suspend fun searchMatches(
        query: ScrapeQuery,
        settings: ScraperSettings,
        neededTypes: Collection<MediaType> = emptyList(),
        mode: MatchMode = MatchMode.Auto,
        searchName: String? = null,
    ): MatchBundle {
        val active = configuredSources(settings)
        if (active.isEmpty()) {
            return MatchBundle(
                sourceSummaries =
                    listOf(
                        SourceResultSummary(
                            sourceId = "none",
                            status = SourceResultStatus.Failed,
                            kind = ScrapeFailureKind.NotConfigured,
                            message = "No sources configured",
                        ),
                    ),
            )
        }

        val types = neededTypes.distinct()

        val summaries = mutableListOf<SourceResultSummary>()
        val gameMatches = mutableListOf<RankedGameMatch>()
        val metadataCandidates = mutableListOf<ScrapeCandidate>()
        val rawMedia = mutableListOf<RankedMediaOption>()
        var rejectedLowConfidence = 0
        var bestRejectedScore: Float? = null

        val name = searchName?.takeIf { it.isNotBlank() } ?: query.displayName

        for (source in active) {
            val outcome =
                try {
                    when (source) {
                        is SteamGridDbSource -> {
                            source.searchGamesOnly(name, query, settings)
                        }

                        else -> {
                            source.lookupResult(query, settings)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    WajihaLog.w(
                        WajihaTags.SCRAPE,
                        "matchTool source=${source.id}: ${e.message}",
                    )
                    SourceLookupOutcome.Failed(classifyThrowable(e))
                }

            summaries += outcome.toSummary(source.id)

            when (outcome) {
                is SourceLookupOutcome.Hit -> {
                    val confidence =
                        outcome.candidate.matchConfidence.takeUnless {
                            it == MatchConfidence.Unknown
                        } ?: confidenceFor(source.id, outcome.candidate)
                    val tagged =
                        if (outcome.candidate.matchConfidence == MatchConfidence.Unknown) {
                            outcome.candidate.copy(matchConfidence = confidence)
                        } else {
                            outcome.candidate
                        }
                    val nameScore = NameMatchScorer.score(name, tagged.name)
                    // Auto: Cocoon-style gate — refuse weak name/autocomplete before art fetch.
                    if (mode == MatchMode.Auto &&
                        !NameMatchScorer.passesAutoGate(confidence, name, tagged.name)
                    ) {
                        rejectedLowConfidence++
                        bestRejectedScore =
                            maxOf(bestRejectedScore ?: 0f, nameScore)
                        WajihaLog.i(
                            WajihaTags.SCRAPE,
                            "No confident ${source.id} match for \"$name\": " +
                                "score ${"%.2f".format(nameScore)} below " +
                                "${"%.2f".format(NameMatchScorer.AutoConfidenceThreshold)} " +
                                "(candidate=\"${tagged.name}\")",
                        )
                        continue
                    }
                    gameMatches +=
                        RankedGameMatch(
                            sourceId = source.id,
                            sourceGameId = tagged.sourceGameId,
                            name = tagged.name,
                            confidence = confidence,
                            candidate = tagged,
                            nameScore = nameScore,
                        )
                    if (tagged.metadata != null) {
                        metadataCandidates += tagged
                    }
                    // Existing media on the candidate (SS full hydrate, etc.)
                    for (media in tagged.media) {
                        if (media.type !in types) continue
                        rawMedia += media.toRankedOption(source.id, confidence)
                    }
                }

                SourceLookupOutcome.Miss -> {
                    Unit
                }

                is SourceLookupOutcome.Failed -> {
                    Unit
                }
            }
        }

        // Lazy SGDB art for the best autocomplete hit when Auto, or first few in Review
        val sgdbHasMoreByType = mutableMapOf<MediaType, Boolean>()
        val sgdb = active.filterIsInstance<SteamGridDbSource>().firstOrNull()
        if (sgdb != null && sgdb.id in settings.enabledSources) {
            val sgdbHits =
                gameMatches.filter { it.sourceId == "steamgriddb" }.let { hits ->
                    when (mode) {
                        MatchMode.Auto -> hits.take(1)
                        MatchMode.Review -> hits.take(3)
                    }
                }
            sgdbHits.forEachIndexed { hitIndex, hit ->
                val gameId = hit.sourceGameId.toIntOrNull() ?: return@forEachIndexed
                val artTypes =
                    types.filter {
                        it in
                            setOf(
                                MediaType.Boxart,
                                MediaType.Square,
                                MediaType.Hero,
                                MediaType.Logo,
                                MediaType.Icon,
                            )
                    }
                for (type in artTypes) {
                    val page =
                        sgdb.fetchMediaTypePage(
                            gameId = gameId,
                            type = type,
                            settings = settings,
                            page = 0,
                            limit = if (mode == MatchMode.Auto) 10 else SteamGridDbSource.PageSize,
                        )
                    // Paging follows the primary (first) SGDB hit only.
                    if (hitIndex == 0) {
                        sgdbHasMoreByType[type] = page.hasMore
                    }
                    for (media in page.media) {
                        rawMedia += media.toRankedOption("steamgriddb", hit.confidence)
                    }
                }
            }
        }

        // Also pull name-search candidates for Review (SS etc.)
        if (mode == MatchMode.Review) {
            for (source in active) {
                if (source.id == "steamgriddb") continue
                try {
                    val searched = source.search(name, query, settings)
                    for (c in searched) {
                        val conf =
                            c.matchConfidence.takeUnless { it == MatchConfidence.Unknown }
                                ?: MatchConfidence.Name
                        val tagged =
                            if (c.matchConfidence == MatchConfidence.Unknown) {
                                c.copy(matchConfidence = conf)
                            } else {
                                c
                            }
                        if (gameMatches.none { it.sourceId == tagged.sourceId && it.sourceGameId == tagged.sourceGameId }) {
                            gameMatches +=
                                RankedGameMatch(
                                    sourceId = tagged.sourceId,
                                    sourceGameId = tagged.sourceGameId,
                                    name = tagged.name,
                                    confidence = conf,
                                    candidate = tagged,
                                    nameScore = NameMatchScorer.score(name, tagged.name),
                                )
                        }
                        if (tagged.metadata != null &&
                            metadataCandidates.none {
                                it.sourceId == tagged.sourceId && it.sourceGameId == tagged.sourceGameId
                            }
                        ) {
                            metadataCandidates += tagged
                        }
                        for (media in tagged.media) {
                            if (media.type in types) {
                                rawMedia += media.toRankedOption(tagged.sourceId, conf)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    WajihaLog.w(WajihaTags.SCRAPE, "matchTool search ${source.id}: ${e.message}")
                }
            }
        }

        val mediaByType =
            types
                .associateWith { type ->
                    val policy = MatchRankPolicy.fromSettings(settings, type)
                    val forType = rawMedia.filter { it.candidate.type == type }.distinctBy { it.url }
                    MediaRanker.rankMediaOptions(forType, policy)
                }.filterValues { it.isNotEmpty() }

        val orderedGames =
            gameMatches.sortedWith(
                compareBy<RankedGameMatch> { it.confidence.ordinal }
                    .thenByDescending { it.nameScore }
                    .thenBy { settings.metadataPriority.indexOf(it.sourceId).let { i -> if (i < 0) 99 else i } },
            )

        return MatchBundle(
            gameMatches = orderedGames.distinctBy { it.sourceId to it.sourceGameId },
            mediaByType = mediaByType,
            metadataCandidates =
                metadataCandidates.distinctBy { it.sourceId to it.sourceGameId }.sortedBy {
                    settings.metadataPriority.indexOf(it.sourceId).let { i -> if (i < 0) 99 else i }
                },
            sourceSummaries = summaries,
            steamGridDbHasMoreByType = sgdbHasMoreByType,
            rejectedLowConfidenceCount = rejectedLowConfidence,
            bestRejectedNameScore = bestRejectedScore,
        )
    }

    /** Builds a [ScrapeSelection] from the ranked bundle (Auto). */
    fun pickBest(
        bundle: MatchBundle,
        settings: ScraperSettings,
        neededTypes: Collection<MediaType>,
    ): ScrapeSelection {
        val meta =
            settings.metadataPriority.firstNotNullOfOrNull { sourceId ->
                bundle.metadataCandidates.firstOrNull { it.sourceId == sourceId && it.metadata != null }
            } ?: bundle.metadataCandidates.firstOrNull { it.metadata != null }

        val media = mutableMapOf<MediaType, StagedMediaPick?>()
        for (type in neededTypes) {
            val policy = MatchRankPolicy.fromSettings(settings, type)
            val ranked = bundle.mediaByType[type].orEmpty()
            val best = MediaRanker.pickBestOption(ranked, policy) ?: continue
            media[type] = StagedMediaPick(best.sourceId, best.candidate)
        }
        return ScrapeSelection(metadataFrom = meta, media = media)
    }

    private fun confidenceFor(
        sourceId: String,
        candidate: ScrapeCandidate,
    ): MatchConfidence {
        if (candidate.matchConfidence != MatchConfidence.Unknown) {
            return candidate.matchConfidence
        }
        return when (sourceId) {
            "steamgriddb" -> MatchConfidence.Autocomplete
            "ra" -> MatchConfidence.Hash
            "screenscraper" -> MatchConfidence.Name
            else -> MatchConfidence.Unknown
        }
    }

    private fun MediaCandidate.toRankedOption(
        sourceId: String,
        confidence: MatchConfidence,
    ): RankedMediaOption =
        RankedMediaOption(
            sourceId = sourceId,
            candidate = this,
            score = score ?: 0,
            authorKey = authorKey,
            authorDisplay = authorName,
            rankReason = "",
            confidence = confidence,
        )
}
