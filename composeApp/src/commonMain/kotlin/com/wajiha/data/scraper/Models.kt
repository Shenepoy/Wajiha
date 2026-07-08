package com.wajiha.data.scraper

import kotlinx.serialization.Serializable

enum class MediaType(val dbName: String) {
    Boxart("boxart"),
    Logo("logo"),
    Hero("hero"),
    Screenshot("screenshot"),
    Fanart("fanart"),
    Video("video"),
    Icon("icon"),
    Banner("banner"),
    /** Local/manual soundtrack only — Study apps do not scrape remote OSTs. */
    Music("music")
}

/** Everything a source needs to identify a game. */
data class ScrapeQuery(
    val gameId: Long,
    val displayName: String,
    val fileName: String,
    val fileSize: Long,
    val crc32: String?,
    val md5: String?,
    val platformId: String,
    val platformName: String,
    val screenScraperId: Int?,
    val raConsoleId: Int?,
    val libretroName: String?
)

data class ScrapedMetadata(
    val name: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val rating: Float? = null,
    val ageRating: String? = null,
    val players: String? = null,
    val region: String? = null,
    val raGameId: Long? = null
)

data class MediaCandidate(
    val type: MediaType,
    val url: String,
    val region: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null
)

/** A possible match returned by a source (auto or manual search). */
data class ScrapeCandidate(
    val sourceId: String,
    val sourceGameId: String,
    val name: String,
    val metadata: ScrapedMetadata? = null,
    val media: List<MediaCandidate> = emptyList(),
    val thumbnailUrl: String? = null
)

/**
 * Per-platform overrides; null fields fall back to the global setting.
 * Applied via [ScraperSettings.forPlatform].
 */
@Serializable
data class PlatformScraperOverride(
    val enabledSources: List<String>? = null,
    val metadataPriority: List<String>? = null,
    val mediaPriority: Map<String, List<String>>? = null,
    val regionPriority: List<String>? = null,
    val languagePriority: List<String>? = null
) {
    val isEmpty: Boolean
        get() = enabledSources == null && metadataPriority == null &&
            mediaPriority == null && regionPriority == null && languagePriority == null
}

/** All user-tunable scraper options (persisted as JSON in DataStore). */
@Serializable
data class ScraperSettings(
    // Credentials
    val screenScraperUser: String = "",
    val screenScraperPassword: String = "",
    val steamGridDbApiKey: String = "",
    val raUsername: String = "",
    val raApiKey: String = "",
    val rommUrl: String = "",
    val rommUsername: String = "",
    val rommPassword: String = "",
    val localMediaPath: String = "",

    val enabledSources: List<String> = listOf(
        "screenscraper", "steamgriddb", "libretro", "ra", "romm", "local"
    ),

    /** Per-media-type source priority; first hit wins. */
    val mediaPriority: Map<String, List<String>> = defaultMediaPriority,

    /** Metadata source priority. */
    val metadataPriority: List<String> = listOf("screenscraper", "romm", "ra"),

    val regionPriority: List<String> = listOf("us", "wor", "eu", "jp"),
    val languagePriority: List<String> = listOf("en", "fr", "de", "es", "ja"),

    /** Longest image edge; larger images are requested smaller/skipped. 0 = original */
    val maxImageResolution: Int = 1024,
    val wifiOnly: Boolean = false,
    val concurrency: Int = 2,
    /** skip | overwrite */
    val existingMediaPolicy: String = "skip",
    val skipAlreadyScraped: Boolean = true,

    /** platformId → overrides; unset fields inherit the global values. */
    val platformOverrides: Map<String, PlatformScraperOverride> = emptyMap()
) {
    /** Effective settings for one platform with its overrides applied. */
    fun forPlatform(platformId: String): ScraperSettings {
        val override = platformOverrides[platformId] ?: return this
        return copy(
            enabledSources = override.enabledSources ?: enabledSources,
            metadataPriority = override.metadataPriority ?: metadataPriority,
            mediaPriority = override.mediaPriority ?: mediaPriority,
            regionPriority = override.regionPriority ?: regionPriority,
            languagePriority = override.languagePriority ?: languagePriority
        )
    }

    companion object {
        val defaultMediaPriority: Map<String, List<String>> = mapOf(
            MediaType.Boxart.dbName to listOf("screenscraper", "steamgriddb", "libretro", "romm", "local"),
            MediaType.Logo.dbName to listOf("steamgriddb", "screenscraper", "local"),
            MediaType.Hero.dbName to listOf("steamgriddb", "screenscraper", "local"),
            MediaType.Screenshot.dbName to listOf("screenscraper", "libretro", "local"),
            MediaType.Fanart.dbName to listOf("screenscraper", "steamgriddb", "local"),
            MediaType.Video.dbName to listOf("screenscraper", "local"),
            MediaType.Icon.dbName to listOf("steamgriddb", "ra", "local"),
            MediaType.Banner.dbName to listOf("screenscraper", "local"),
            MediaType.Music.dbName to listOf("local")
        )
    }
}

/** Sorts candidate media by the user's region priority. */
fun List<MediaCandidate>.bestByRegion(regionPriority: List<String>): MediaCandidate? =
    minByOrNull { candidate ->
        val idx = regionPriority.indexOf(candidate.region?.lowercase())
        if (idx == -1) regionPriority.size else idx
    }
