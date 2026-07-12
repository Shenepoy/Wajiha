package com.wajiha.data.scraper.sources

import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeFailure
import com.wajiha.data.scraper.ScrapeFailureKind
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.data.scraper.SourceLookupOutcome
import com.wajiha.data.scraper.classifyThrowable
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

/**
 * libretro-thumbnails — no auth, name-convention based. Uses the platform's
 * libretro system name (imported from Daijishō `LIBRETRO:` scraper source).
 */
class LibretroThumbnailsSource(private val http: HttpClient) : ScraperSource {

    override val id = "libretro"
    override val displayName = "Libretro Thumbnails"

    override fun isConfigured(settings: ScraperSettings): Boolean = true

    override suspend fun lookupResult(
        query: ScrapeQuery,
        settings: ScraperSettings
    ): SourceLookupOutcome {
        val system = query.libretroName
            ?: return SourceLookupOutcome.Failed(
                ScrapeFailure(
                    ScrapeFailureKind.NotConfigured,
                    "No libretro system name for this platform"
                )
            )
        val gameName = sanitize(query.fileName.substringBeforeLast('.'))
        val media = mutableListOf<MediaCandidate>()

        val kinds = buildMap {
            if (settings.libretroFetchBoxart) put("Named_Boxarts", MediaType.Boxart)
            if (settings.libretroFetchSnaps) put("Named_Snaps", MediaType.Screenshot)
            if (settings.libretroFetchTitles) put("Named_Titles", MediaType.Fanart)
        }
        if (kinds.isEmpty()) return SourceLookupOutcome.Miss

        var networkFailure: ScrapeFailure? = null
        for ((dir, type) in kinds) {
            val url = "https://thumbnails.libretro.com/" +
                system.encodeURLPathPart() + "/$dir/" +
                gameName.encodeURLPathPart() + ".png"
            when (val check = existsResult(url)) {
                ExistsResult.Yes -> media += MediaCandidate(type = type, url = url)
                ExistsResult.No -> Unit
                is ExistsResult.Error -> networkFailure = check.failure
            }
        }
        if (media.isEmpty()) {
            return networkFailure?.let { SourceLookupOutcome.Failed(it) }
                ?: SourceLookupOutcome.Miss
        }
        return SourceLookupOutcome.Hit(
            ScrapeCandidate(
                sourceId = id,
                sourceGameId = gameName,
                name = query.displayName,
                media = media,
                thumbnailUrl = media.firstOrNull { it.type == MediaType.Boxart }?.url
            )
        )
    }

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): List<ScrapeCandidate> {
        val candidate = lookup(
            ScrapeQuery(
                gameId = query.gameId,
                displayName = name,
                fileName = "$name.png",
                fileSize = 0,
                crc32 = null,
                md5 = null,
                platformId = query.platformId,
                platformName = query.platformName,
                screenScraperId = query.screenScraperId,
                raConsoleId = query.raConsoleId,
                libretroName = query.libretroName
            ),
            settings
        )
        return listOfNotNull(candidate)
    }

    private sealed class ExistsResult {
        data object Yes : ExistsResult()
        data object No : ExistsResult()
        data class Error(val failure: ScrapeFailure) : ExistsResult()
    }

    private suspend fun existsResult(url: String): ExistsResult = try {
        if (http.head(url).status.isSuccess()) ExistsResult.Yes else ExistsResult.No
    } catch (e: Exception) {
        ExistsResult.Error(classifyThrowable(e))
    }

    private fun sanitize(name: String): String =
        name.map { c -> if (c in "&*/:`<>?\\|\"") '_' else c }.toCharArray().concatToString()
}
