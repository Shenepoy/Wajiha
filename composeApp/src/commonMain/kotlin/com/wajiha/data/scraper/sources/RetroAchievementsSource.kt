package com.wajiha.data.scraper.sources

import com.wajiha.data.ra.RaClient
import com.wajiha.data.ra.RaMediaUrls
import com.wajiha.data.ra.ResolveRaGameId
import com.wajiha.data.scraper.MatchConfidence
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScrapedMetadata
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.data.scraper.SourceLookupOutcome

/**
 * RetroAchievements as a metadata source: console hash library (GetGameList)
 * plus title fallback, then [API_GetGame] for art/metadata.
 *
 * There is no public Web `API_GetGameInfoByHash` — that endpoint 404s.
 */
class RetroAchievementsSource(
    private val raClient: RaClient,
) : ScraperSource {
    override val id = "ra"
    override val displayName = "RetroAchievements"

    override fun isConfigured(settings: ScraperSettings): Boolean = settings.raUsername.isNotBlank() && settings.raApiKey.isNotBlank()

    override suspend fun lookupResult(
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): SourceLookupOutcome {
        if (!isConfigured(settings)) return SourceLookupOutcome.Miss
        val consoleId = query.raConsoleId ?: return SourceLookupOutcome.Miss
        val hit =
            when (
                val resolved =
                    raClient.resolveGameId(
                        username = settings.raUsername,
                        apiKey = settings.raApiKey,
                        md5 = query.md5,
                        displayName = query.displayName.ifBlank { query.fileName },
                        consoleId = consoleId,
                    )
            ) {
                is ResolveRaGameId.Hit -> resolved
                is ResolveRaGameId.Miss -> return SourceLookupOutcome.Miss
            }
        val summary =
            raClient.gameSummary(
                username = settings.raUsername,
                apiKey = settings.raApiKey,
                raGameId = hit.raGameId,
            )
        val title = summary?.displayTitle ?: query.displayName
        return SourceLookupOutcome.Hit(
            ScrapeCandidate(
                sourceId = id,
                sourceGameId = hit.raGameId.toString(),
                name = title,
                matchConfidence =
                    if (hit.via == "hash") {
                        MatchConfidence.Hash
                    } else {
                        MatchConfidence.Name
                    },
                metadata =
                    ScrapedMetadata(
                        name = summary?.displayTitle,
                        developer = summary?.developer,
                        publisher = summary?.publisher,
                        genre = summary?.genre,
                        releaseDate = summary?.released,
                        raGameId = hit.raGameId,
                    ),
                media =
                    buildList {
                        if (settings.raFetchIcon) {
                            summary?.iconPath?.let {
                                add(MediaCandidate(type = MediaType.Icon, url = RaMediaUrls.media(it)))
                            }
                        }
                        if (settings.raFetchBoxArt) {
                            summary?.imageBoxArt?.let {
                                add(MediaCandidate(type = MediaType.Boxart, url = RaMediaUrls.media(it)))
                            }
                        }
                        if (settings.raFetchTitle) {
                            summary?.imageTitle?.let {
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
}
