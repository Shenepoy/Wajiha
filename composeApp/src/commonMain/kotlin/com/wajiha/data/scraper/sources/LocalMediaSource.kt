package com.wajiha.data.scraper.sources

import com.wajiha.data.scraper.LocalMediaFiles
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.data.scraper.SourceLookupOutcome

/**
 * Local media folders in ES-DE layout:
 * `<root>/<platform shortname>/<mediatype dir>/<rom base name>.<ext>`
 * URLs use the `file://` scheme so the engine can copy instead of download.
 */
class LocalMediaSource(
    private val files: LocalMediaFiles,
) : ScraperSource {
    override val id = "local"
    override val displayName = "Local media"

    override fun isConfigured(settings: ScraperSettings): Boolean = settings.localMediaPath.isNotBlank()

    override suspend fun lookupResult(
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): SourceLookupOutcome {
        val root = settings.localMediaPath
        val baseName = query.fileName.substringBeforeLast('.')
        val media =
            MediaType.entries.mapNotNull { type ->
                files.find(root, query.platformId, baseName, type)?.let { path ->
                    MediaCandidate(type = type, url = "file://$path")
                }
            }
        if (media.isEmpty()) return SourceLookupOutcome.Miss
        return SourceLookupOutcome.Hit(
            ScrapeCandidate(
                sourceId = id,
                sourceGameId = baseName,
                name = query.displayName,
                media = media,
            ),
        )
    }

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings,
    ): List<ScrapeCandidate> = emptyList()
}
