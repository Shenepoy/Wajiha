package com.wajiha.data.scraper.sources

import com.wajiha.data.WajihaJson
import com.wajiha.data.ra.RaMediaUrls
import com.wajiha.data.ra.parseRaGameIdFromHashResponse
import com.wajiha.data.scraper.HttpJsonResult
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeFailure
import com.wajiha.data.scraper.ScrapeFailureKind
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScrapedMetadata
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.data.scraper.SourceLookupOutcome
import com.wajiha.data.scraper.getStringResult
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RetroAchievements as a metadata source: hash → game match plus icon media.
 * Achievements themselves are handled by the RA feature module; this source
 * only contributes the game link (raGameId) and basic metadata.
 */
class RetroAchievementsSource(
    private val http: HttpClient,
) : ScraperSource {
    override val id = "ra"
    override val displayName = "RetroAchievements"

    override fun isConfigured(settings: ScraperSettings): Boolean = settings.raUsername.isNotBlank() && settings.raApiKey.isNotBlank()

    override suspend fun lookupResult(
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): SourceLookupOutcome {
        val md5 =
            query.md5
                ?: return SourceLookupOutcome.Miss
        val result =
            http.getStringResult(
                "https://retroachievements.org/API/API_GetGameInfoByHash.php",
            ) {
                parameter("z", settings.raUsername)
                parameter("y", settings.raApiKey)
                parameter("h", md5)
            }
        val body =
            when (result) {
                is HttpJsonResult.Failed -> return SourceLookupOutcome.Failed(result.failure)
                is HttpJsonResult.Ok -> result.value
            }
        val gameId =
            parseRaGameIdFromHashResponse(body)
                ?: return SourceLookupOutcome.Miss
        val game =
            runCatching {
                WajihaJson.Default.decodeFromString<RaGame>(body)
            }.getOrNull()
                ?: return SourceLookupOutcome.Failed(
                    ScrapeFailure(ScrapeFailureKind.Parse, "Could not parse RA game"),
                )
        return SourceLookupOutcome.Hit(
            ScrapeCandidate(
                sourceId = id,
                sourceGameId = gameId.toString(),
                name = game.title ?: query.displayName,
                metadata =
                    ScrapedMetadata(
                        name = game.title,
                        developer = game.developer,
                        publisher = game.publisher,
                        genre = game.genre,
                        releaseDate = game.released,
                        raGameId = gameId,
                    ),
                media =
                    buildList {
                        if (settings.raFetchIcon) {
                            game.imageIcon?.let {
                                add(MediaCandidate(type = MediaType.Icon, url = RaMediaUrls.media(it)))
                            }
                        }
                        if (settings.raFetchBoxArt) {
                            game.imageBoxArt?.let {
                                add(MediaCandidate(type = MediaType.Boxart, url = RaMediaUrls.media(it)))
                            }
                        }
                        if (settings.raFetchTitle) {
                            game.imageTitle?.let {
                                add(
                                    MediaCandidate(
                                        type = MediaType.Screenshot,
                                        url = RaMediaUrls.media(it),
                                    ),
                                )
                            }
                        }
                    },
            ),
        )
    }

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings,
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
        @SerialName("ImageBoxArt") val imageBoxArt: String? = null,
    )
}
