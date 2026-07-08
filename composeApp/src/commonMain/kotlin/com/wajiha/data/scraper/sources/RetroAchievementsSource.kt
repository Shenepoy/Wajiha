package com.wajiha.data.scraper.sources

import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScrapedMetadata
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * RetroAchievements as a metadata source: hash → game match plus icon media.
 * Achievements themselves are handled by the RA feature module; this source
 * only contributes the game link (raGameId) and basic metadata.
 */
class RetroAchievementsSource(private val http: HttpClient) : ScraperSource {

    override val id = "ra"
    override val displayName = "RetroAchievements"

    private val json = Json { ignoreUnknownKeys = true }

    override fun isConfigured(settings: ScraperSettings): Boolean =
        settings.raUsername.isNotBlank() && settings.raApiKey.isNotBlank()

    override suspend fun lookup(query: ScrapeQuery, settings: ScraperSettings): ScrapeCandidate? {
        val md5 = query.md5 ?: return null
        val game = try {
            val body = http.get("https://retroachievements.org/API/API_GetGameInfoByHash.php") {
                parameter("z", settings.raUsername)
                parameter("y", settings.raApiKey)
                parameter("h", md5)
            }.body<String>()
            json.decodeFromString<RaGame>(body)
        } catch (_: Exception) {
            return null
        }
        val gameId = game.id ?: return null
        if (gameId <= 0) return null
        return ScrapeCandidate(
            sourceId = id,
            sourceGameId = gameId.toString(),
            name = game.title ?: query.displayName,
            metadata = ScrapedMetadata(
                name = game.title,
                developer = game.developer,
                publisher = game.publisher,
                genre = game.genre,
                releaseDate = game.released,
                raGameId = gameId.toLong()
            ),
            media = listOfNotNull(
                game.imageIcon?.let {
                    MediaCandidate(type = MediaType.Icon, url = "https://media.retroachievements.org$it")
                },
                game.imageBoxArt?.let {
                    MediaCandidate(type = MediaType.Boxart, url = "https://media.retroachievements.org$it")
                },
                game.imageTitle?.let {
                    MediaCandidate(type = MediaType.Screenshot, url = "https://media.retroachievements.org$it")
                }
            )
        )
    }

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): List<ScrapeCandidate> = emptyList() // RA has no public name-search API

    @Serializable
    private data class RaGame(
        @SerialName("ID") val id: Int? = null,
        @SerialName("Title") val title: String? = null,
        @SerialName("Genre") val genre: String? = null,
        @SerialName("Developer") val developer: String? = null,
        @SerialName("Publisher") val publisher: String? = null,
        @SerialName("Released") val released: String? = null,
        @SerialName("ImageIcon") val imageIcon: String? = null,
        @SerialName("ImageTitle") val imageTitle: String? = null,
        @SerialName("ImageBoxArt") val imageBoxArt: String? = null
    )
}
