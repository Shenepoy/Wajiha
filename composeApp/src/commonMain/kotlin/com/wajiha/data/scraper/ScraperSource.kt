package com.wajiha.data.scraper

/**
 * Pluggable scraper source. Implementations must be side-effect free —
 * they return candidates/URLs, the engine downloads and persists.
 */
interface ScraperSource {
    val id: String
    val displayName: String

    /** True when the source has the credentials it needs (or needs none). */
    fun isConfigured(settings: ScraperSettings): Boolean

    /** Best automatic match for a query (hit / miss / failed). */
    suspend fun lookupResult(query: ScrapeQuery, settings: ScraperSettings): SourceLookupOutcome

    /** Convenience unwrap of [lookupResult] for callers that only need a candidate. */
    suspend fun lookup(query: ScrapeQuery, settings: ScraperSettings): ScrapeCandidate? =
        (lookupResult(query, settings) as? SourceLookupOutcome.Hit)?.candidate

    /** Manual search by name for the match UI. */
    suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): List<ScrapeCandidate>
}

/** Local media file resolution (ES-DE layout), implemented per platform. */
interface LocalMediaFiles {
    /** Returns an existing local path for the game/type or null. */
    fun find(mediaRoot: String, platformShortName: String, romBaseName: String, type: MediaType): String?
}

/** Persisted media file storage for downloaded assets. */
interface MediaStorage {
    /** Saves bytes, returns the absolute path. */
    suspend fun save(gameId: Long, type: MediaType, extension: String, bytes: ByteArray): String
    suspend fun delete(path: String)
}
