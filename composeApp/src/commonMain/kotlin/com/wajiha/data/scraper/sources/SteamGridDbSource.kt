package com.wajiha.data.scraper.sources

import com.wajiha.data.scraper.HttpJsonResult
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.data.scraper.SourceLookupOutcome
import com.wajiha.data.scraper.SourceSearchBundle
import com.wajiha.data.scraper.getJsonResult
import com.wajiha.data.scraper.steamGridDbAuth
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

/** SteamGridDB — grids (boxart), heroes, logos, icons. Needs a user API key. */
class SteamGridDbSource(
    private val http: HttpClient,
) : ScraperSource {
    override val id = "steamgriddb"
    override val displayName = "SteamGridDB"

    private var cachedSearchKey: String? = null
    private var cachedSearchAt = 0L
    private var cachedSearchBundle: SourceSearchBundle? = null

    override fun isConfigured(settings: ScraperSettings): Boolean = settings.steamGridDbApiKey.isNotBlank()

    override suspend fun lookupResult(
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): SourceLookupOutcome = searchWithOutcome(query.displayName, query, settings).toLookupOutcome()

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): List<ScrapeCandidate> = searchWithOutcome(name, query, settings).candidates

    private suspend fun searchWithOutcome(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): SourceSearchBundle {
        val cacheKey = "${settings.steamGridDbApiKey}|$name"
        val now = System.currentTimeMillis()
        cachedSearchBundle?.let { bundle ->
            if (cachedSearchKey == cacheKey && now - cachedSearchAt < SEARCH_CACHE_MS) {
                return bundle
            }
        }
        val result =
            http.getJsonResult<SgdbEnvelope<SgdbGame>>(
                "https://www.steamgriddb.com/api/v2/search/autocomplete/${name.encodeURLPathPart()}",
            ) {
                steamGridDbAuth(settings)
            }
        val games =
            when (result) {
                is HttpJsonResult.Failed -> {
                    val bundle = SourceSearchBundle(emptyList(), result.failure)
                    cacheSearch(cacheKey, bundle)
                    return bundle
                }

                is HttpJsonResult.Ok -> {
                    result.value.data.orEmpty()
                }
            }
        if (games.isEmpty()) {
            val bundle = SourceSearchBundle(emptyList())
            cacheSearch(cacheKey, bundle)
            return bundle
        }
        // First page only during search/lookup — review loads more on demand.
        val candidates =
            games.take(5).map { game ->
                ScrapeCandidate(
                    sourceId = id,
                    sourceGameId = game.id.toString(),
                    name = game.name,
                    media = fetchMediaPreview(game.id, settings),
                )
            }
        val bundle = SourceSearchBundle(candidates)
        cacheSearch(cacheKey, bundle)
        return bundle
    }

    private fun cacheSearch(
        key: String,
        bundle: SourceSearchBundle,
    ) {
        cachedSearchKey = key
        cachedSearchAt = System.currentTimeMillis()
        cachedSearchBundle = bundle
    }

    /**
     * Preview media for match lists / auto-scrape: one page per type (limit [PageSize]).
     * Review UI calls [fetchMediaTypePage] to page through the rest.
     */
    private suspend fun fetchMediaPreview(
        gameId: Int,
        settings: ScraperSettings,
    ): List<MediaCandidate> {
        val result = mutableListOf<MediaCandidate>()
        for ((_, type) in Endpoints) {
            val page = fetchMediaTypePage(gameId, type, settings, page = 0)
            result += page.media
        }
        return result
    }

    /**
     * Loads one page of assets for [type]. Used by the review picker for lazy loading.
     */
    suspend fun fetchMediaTypePage(
        gameId: Int,
        type: MediaType,
        settings: ScraperSettings,
        page: Int,
        limit: Int = PageSize,
    ): SteamGridDbMediaPage {
        val endpoint =
            Endpoints.entries.firstOrNull { it.value == type }?.key
                ?: return SteamGridDbMediaPage(emptyList(), page, hasMore = false)
        val fetch =
            http.getJsonResult<SgdbEnvelope<SgdbAsset>>(
                "https://www.steamgriddb.com/api/v2/$endpoint/game/$gameId",
            ) {
                steamGridDbAuth(settings)
                applyGridFilters(endpoint, settings)
                parameter("limit", limit)
                parameter("page", page)
            }
        val envelope =
            when (fetch) {
                is HttpJsonResult.Failed -> return SteamGridDbMediaPage(emptyList(), page, false)
                is HttpJsonResult.Ok -> fetch.value
            }
        val assets = envelope.data.orEmpty()
        val styles = stylesForEndpoint(endpoint, settings)
        val sorted =
            if (styles.isNotEmpty()) {
                assets.sortedByStylePreference(styles)
            } else {
                assets
            }
        val media =
            sorted.map { asset ->
                MediaCandidate(
                    type = type,
                    url = asset.url,
                    width = asset.width,
                    height = asset.height,
                    sourceVariant = asset.style,
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
        parameter("nsfw", settings.steamGridDbIncludeNsfw)
        parameter("humor", settings.steamGridDbIncludeHumor)
    }

    private fun List<SgdbAsset>.sortedByStylePreference(styles: List<String>): List<SgdbAsset> =
        sortedBy { asset ->
            val idx = styles.indexOf(asset.style?.lowercase())
            if (idx == -1) styles.size else idx
        }

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
    )

    companion object {
        const val PageSize = 50
        private const val SEARCH_CACHE_MS = 5_000L
        private val Endpoints =
            mapOf(
                "grids" to MediaType.Boxart,
                "heroes" to MediaType.Hero,
                "logos" to MediaType.Logo,
                "icons" to MediaType.Icon,
            )
    }
}
