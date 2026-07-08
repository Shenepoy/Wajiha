package com.wajiha.data.scraper.sources

import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** SteamGridDB — grids (boxart), heroes, logos, icons. Needs a user API key. */
class SteamGridDbSource(private val http: HttpClient) : ScraperSource {

    override val id = "steamgriddb"
    override val displayName = "SteamGridDB"

    private val json = Json { ignoreUnknownKeys = true }

    override fun isConfigured(settings: ScraperSettings): Boolean =
        settings.steamGridDbApiKey.isNotBlank()

    override suspend fun lookup(query: ScrapeQuery, settings: ScraperSettings): ScrapeCandidate? =
        search(query.displayName, query, settings).firstOrNull()

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): List<ScrapeCandidate> {
        val games = try {
            val body = http.get(
                "https://www.steamgriddb.com/api/v2/search/autocomplete/${name.encodeURLPathPart()}"
            ) {
                header("Authorization", "Bearer ${settings.steamGridDbApiKey}")
            }.body<String>()
            json.decodeFromString<SgdbEnvelope<SgdbGame>>(body).data.orEmpty()
        } catch (_: Exception) {
            return emptyList()
        }
        return games.take(5).map { game ->
            ScrapeCandidate(
                sourceId = id,
                sourceGameId = game.id.toString(),
                name = game.name,
                media = fetchMedia(game.id, settings)
            )
        }
    }

    private suspend fun fetchMedia(gameId: Int, settings: ScraperSettings): List<MediaCandidate> {
        val result = mutableListOf<MediaCandidate>()
        val endpoints = mapOf(
            "grids" to MediaType.Boxart,
            "heroes" to MediaType.Hero,
            "logos" to MediaType.Logo,
            "icons" to MediaType.Icon
        )
        for ((endpoint, type) in endpoints) {
            try {
                val body = http.get(
                    "https://www.steamgriddb.com/api/v2/$endpoint/game/$gameId"
                ) {
                    header("Authorization", "Bearer ${settings.steamGridDbApiKey}")
                }.body<String>()
                json.decodeFromString<SgdbEnvelope<SgdbAsset>>(body).data.orEmpty()
                    .take(3)
                    .forEach { asset ->
                        result += MediaCandidate(
                            type = type,
                            url = asset.url,
                            width = asset.width,
                            height = asset.height
                        )
                    }
            } catch (_: Exception) {
            }
        }
        return result
    }

    @Serializable
    private data class SgdbEnvelope<T>(val success: Boolean = false, val data: List<T>? = null)

    @Serializable
    private data class SgdbGame(val id: Int, val name: String)

    @Serializable
    private data class SgdbAsset(val url: String, val width: Int? = null, val height: Int? = null)
}
