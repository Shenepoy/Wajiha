package com.wajiha.data.scraper.sources

import com.wajiha.data.scraper.HttpJsonResult
import com.wajiha.data.scraper.MatchConfidence
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeFailureKind
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.data.scraper.SourceHealthBudget
import com.wajiha.data.scraper.SourceLookupOutcome
import com.wajiha.data.scraper.SourceSearchBundle
import com.wajiha.data.scraper.getJsonResult
import com.wajiha.data.scraper.steamGridDbAuth
import com.wajiha.log.logClockMs
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

/** One page of SteamGridDB assets for a media type. */
data class SteamGridDbMediaPage(
    val media: List<MediaCandidate>,
    val page: Int,
    val hasMore: Boolean,
)

/**
 * SteamGridDB — grids (boxart + square), heroes, logos, icons.
 *
 * Auto path: cheap autocomplete only; art is fetched lazily by [ScrapeMatchTool]
 * for the chosen game id / needed types. Parses `score` + `author` for ranking.
 * Square uses the grids endpoint with `dimensions=1024x1024,512x512` (Cocoon-style).
 */
class SteamGridDbSource(
    private val http: HttpClient,
) : ScraperSource {
    override val id = "steamgriddb"
    override val displayName = "SteamGridDB"

    private val health = SourceHealthBudget(id, maxCallsPerWindow = 60)

    private var cachedSearchKey: String? = null
    private var cachedSearchAt = 0L
    private var cachedSearchBundle: SourceSearchBundle? = null

    override fun isConfigured(settings: ScraperSettings): Boolean = settings.steamGridDbApiKey.isNotBlank()

    override suspend fun lookupResult(
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): SourceLookupOutcome = searchGamesOnly(query.displayName, query, settings)

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): List<ScrapeCandidate> =
        when (val outcome = searchGamesOnly(name, query, settings)) {
            is SourceLookupOutcome.Hit -> {
                // Expand cache to full candidate list if available
                cachedSearchBundle
                    ?.takeIf { cachedSearchKey == "${settings.steamGridDbApiKey}|$name" }
                    ?.candidates
                    ?: listOf(outcome.candidate)
            }

            else -> {
                emptyList()
            }
        }

    /**
     * Autocomplete only — no media fetches. Used by [com.wajiha.data.scraper.ScrapeMatchTool].
     */
    suspend fun searchGamesOnly(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): SourceLookupOutcome {
        if (!health.isAvailable()) {
            return SourceLookupOutcome.Failed(
                com.wajiha.data.scraper.ScrapeFailure(
                    ScrapeFailureKind.RateLimited,
                    "SteamGridDB temporarily unavailable (budget/circuit)",
                ),
            )
        }
        val cacheKey = "${settings.steamGridDbApiKey}|$name"
        val now = logClockMs()
        cachedSearchBundle?.let { bundle ->
            if (cachedSearchKey == cacheKey && now - cachedSearchAt < SEARCH_CACHE_MS) {
                return bundle.toLookupOutcome()
            }
        }

        health.recordCall()
        val result =
            http.getJsonResult<SgdbEnvelope<SgdbGame>>(
                "https://www.steamgriddb.com/api/v2/search/autocomplete/${name.encodeURLPathPart()}",
            ) {
                steamGridDbAuth(settings)
            }
        val games =
            when (result) {
                is HttpJsonResult.Failed -> {
                    if (result.failure.kind == ScrapeFailureKind.Auth ||
                        result.failure.kind == ScrapeFailureKind.RateLimited ||
                        result.failure.kind == ScrapeFailureKind.Http ||
                        result.failure.kind == ScrapeFailureKind.Network
                    ) {
                        health.trip(result.failure.message)
                    }
                    val bundle = SourceSearchBundle(emptyList(), result.failure)
                    cacheSearch(cacheKey, bundle)
                    return bundle.toLookupOutcome()
                }

                is HttpJsonResult.Ok -> {
                    result.value.data.orEmpty()
                }
            }
        if (games.isEmpty()) {
            val bundle = SourceSearchBundle(emptyList())
            cacheSearch(cacheKey, bundle)
            return SourceLookupOutcome.Miss
        }
        // Candidates without media — tool fetches art for the winner only.
        val candidates =
            games.take(5).map { game ->
                ScrapeCandidate(
                    sourceId = id,
                    sourceGameId = game.id.toString(),
                    name = game.name,
                    media = emptyList(),
                    thumbnailUrl = null,
                    matchConfidence = MatchConfidence.Autocomplete,
                )
            }
        val bundle = SourceSearchBundle(candidates)
        cacheSearch(cacheKey, bundle)
        return SourceLookupOutcome.Hit(candidates.first())
    }

    private fun cacheSearch(
        key: String,
        bundle: SourceSearchBundle,
    ) {
        cachedSearchKey = key
        cachedSearchAt = logClockMs()
        cachedSearchBundle = bundle
    }

    /**
     * Loads one page of assets for [type]. Used by Review paging and [ScrapeMatchTool] lazy art.
     */
    suspend fun fetchMediaTypePage(
        gameId: Int,
        type: MediaType,
        settings: ScraperSettings,
        page: Int,
        limit: Int = PageSize,
    ): SteamGridDbMediaPage {
        if (!health.isAvailable()) {
            return SteamGridDbMediaPage(emptyList(), page, hasMore = false)
        }
        val endpoint = endpointFor(type) ?: return SteamGridDbMediaPage(emptyList(), page, hasMore = false)
        health.recordCall()
        val fetch =
            http.getJsonResult<SgdbEnvelope<SgdbAsset>>(
                "https://www.steamgriddb.com/api/v2/$endpoint/game/$gameId",
            ) {
                steamGridDbAuth(settings)
                applyGridFilters(endpoint, settings)
                if (type == MediaType.Square) {
                    parameter("dimensions", SquareGridDimensions)
                }
                parameter("limit", limit.coerceAtMost(50))
                parameter("page", page)
            }
        val envelope =
            when (fetch) {
                is HttpJsonResult.Failed -> {
                    if (fetch.failure.kind == ScrapeFailureKind.Auth ||
                        fetch.failure.kind == ScrapeFailureKind.RateLimited ||
                        fetch.failure.kind == ScrapeFailureKind.Network
                    ) {
                        health.trip(fetch.failure.message)
                    }
                    return SteamGridDbMediaPage(emptyList(), page, false)
                }

                is HttpJsonResult.Ok -> {
                    fetch.value
                }
            }
        val assets = envelope.data.orEmpty()
        val styles = stylesForEndpoint(endpoint, settings)
        val sorted =
            if (styles.isNotEmpty()) {
                assets.sortedByStylePreference(styles)
            } else {
                assets.sortedByDescending { it.score ?: 0 }
            }
        val media =
            sorted.map { asset ->
                MediaCandidate(
                    type = type,
                    url = asset.url,
                    width = asset.width,
                    height = asset.height,
                    sourceVariant = asset.style,
                    score = asset.score,
                    authorKey = asset.author?.steam64?.takeIf { it.isNotBlank() },
                    authorName = asset.author?.name?.takeIf { it.isNotBlank() },
                )
            }
        val total = envelope.total
        val hasMore =
            when {
                total != null && total > 0 -> (page + 1) * limit < total
                else -> assets.size >= limit
            }
        return SteamGridDbMediaPage(media = media, page = page, hasMore = hasMore)
    }

    private fun stylesForEndpoint(
        endpoint: String,
        settings: ScraperSettings,
    ): List<String> =
        when (endpoint) {
            "grids" -> settings.steamGridDbGridStyles
            "heroes" -> settings.steamGridDbHeroStyles
            "logos" -> settings.steamGridDbLogoStyles
            "icons" -> settings.steamGridDbIconStyles
            else -> emptyList()
        }

    private fun io.ktor.client.request.HttpRequestBuilder.applyGridFilters(
        endpoint: String,
        settings: ScraperSettings,
    ) {
        val styles = stylesForEndpoint(endpoint, settings)
        if (styles.isNotEmpty()) {
            parameter("styles", styles.joinToString(","))
        }
        when (settings.steamGridDbAnimation) {
            "static" -> parameter("types", "static")
            "animated" -> parameter("types", "animated")
            "both" -> parameter("types", "static,animated")
        }
        // SGDB: false = exclude tag, true = ONLY that tag, any = both.
        // UI "Include X" must map to any, not true.
        parameter("nsfw", if (settings.steamGridDbIncludeNsfw) "any" else "false")
        parameter("humor", if (settings.steamGridDbIncludeHumor) "any" else "false")
    }

    private fun List<SgdbAsset>.sortedByStylePreference(styles: List<String>): List<SgdbAsset> =
        sortedWith(
            compareBy<SgdbAsset> { asset ->
                val idx = styles.indexOf(asset.style?.lowercase())
                if (idx == -1) styles.size else idx
            }.thenByDescending { it.score ?: 0 },
        )

    @Serializable
    private data class SgdbEnvelope<T>(
        val success: Boolean = false,
        val data: List<T>? = null,
        val page: Int? = null,
        val total: Int? = null,
        val limit: Int? = null,
    )

    @Serializable
    private data class SgdbGame(
        val id: Int,
        val name: String,
    )

    @Serializable
    private data class SgdbAsset(
        val url: String,
        val width: Int? = null,
        val height: Int? = null,
        val style: String? = null,
        val score: Int? = null,
        val author: SgdbAuthor? = null,
    )

    @Serializable
    private data class SgdbAuthor(
        val name: String? = null,
        val steam64: String? = null,
        val avatar: String? = null,
    )

    private fun endpointFor(type: MediaType): String? =
        when (type) {
            MediaType.Boxart, MediaType.Square -> "grids"
            MediaType.Hero -> "heroes"
            MediaType.Logo -> "logos"
            MediaType.Icon -> "icons"
            else -> null
        }

    companion object {
        const val PageSize = 50
        private const val SEARCH_CACHE_MS = 5_000L

        /** Cocoon-style square grid dimensions for SteamGridDB. */
        private const val SquareGridDimensions = "1024x1024,512x512"
    }
}
