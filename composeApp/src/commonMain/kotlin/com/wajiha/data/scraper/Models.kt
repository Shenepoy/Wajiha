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
    val format: String? = null,
    /** Raw variant tag from the source API (e.g. ScreenScraper "box-2D", SGDB "alternate"). */
    val sourceVariant: String? = null
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
 * Per-platform scraper overrides. Null fields inherit the global [ScraperSettings] value.
 *
 * Source-specific fields:
 * - ScreenScraper: [screenScraperBoxType], [screenScraperScreenshotType], [screenScraperLogoType]
 * - SteamGridDB: [steamGridDbGridStyles]/[steamGridDbHeroStyles]/[steamGridDbLogoStyles]/
 *   [steamGridDbIconStyles], [steamGridDbAnimation], [steamGridDbIncludeNsfw/Humor]
 * - Libretro: [libretroFetchBoxart/Snaps/Titles]
 * - RetroAchievements: [raFetchIcon/BoxArt/Title]
 */
@Serializable
data class PlatformScraperOverride(
    val enabledSources: List<String>? = null,
    val metadataPriority: List<String>? = null,
    val mediaPriority: Map<String, List<String>>? = null,
    val regionPriority: List<String>? = null,
    val languagePriority: List<String>? = null,

    // ScreenScraper media-type preferences
    val screenScraperBoxType: String? = null,
    val screenScraperScreenshotType: String? = null,
    val screenScraperLogoType: String? = null,
    val screenScraperFanartAsHero: Boolean? = null,

    // SteamGridDB API query filters (per photo type; empty/null = all styles)
    val steamGridDbGridStyles: List<String>? = null,
    val steamGridDbHeroStyles: List<String>? = null,
    val steamGridDbLogoStyles: List<String>? = null,
    val steamGridDbIconStyles: List<String>? = null,
    val steamGridDbAnimation: String? = null,
    val steamGridDbIncludeNsfw: Boolean? = null,
    val steamGridDbIncludeHumor: Boolean? = null,

    // Libretro thumbnail directories
    val libretroFetchBoxart: Boolean? = null,
    val libretroFetchSnaps: Boolean? = null,
    val libretroFetchTitles: Boolean? = null,

    // RetroAchievements image fields
    val raFetchIcon: Boolean? = null,
    val raFetchBoxArt: Boolean? = null,
    val raFetchTitle: Boolean? = null,

    /** 0-based index when multiple assets of the same type exist (e.g. alternate covers). */
    val mediaVariantIndex: Int? = null
) {
    val isEmpty: Boolean
        get() = enabledSources == null && metadataPriority == null &&
            mediaPriority == null && regionPriority == null && languagePriority == null &&
            screenScraperBoxType == null && screenScraperScreenshotType == null &&
            screenScraperLogoType == null && screenScraperFanartAsHero == null &&
            steamGridDbGridStyles == null && steamGridDbHeroStyles == null &&
            steamGridDbLogoStyles == null && steamGridDbIconStyles == null &&
            steamGridDbAnimation == null &&
            steamGridDbIncludeNsfw == null && steamGridDbIncludeHumor == null &&
            libretroFetchBoxart == null && libretroFetchSnaps == null &&
            libretroFetchTitles == null && raFetchIcon == null && raFetchBoxArt == null &&
            raFetchTitle == null && mediaVariantIndex == null
}

/** Known SteamGridDB `styles` query values (hints for settings UI). */
object SteamGridDbStyleHints {
    const val Grid = "alternate, blurred, white_logo, material, no_logo"
    const val Hero = "alternate, blurred, material"
    const val Logo = "official, white, black, custom"
    const val Icon = "official, custom"
    const val EmptyMeansAll = "Leave empty to request all styles."
}

/** Parses a comma-separated SteamGridDB styles field. */
fun parseSteamGridDbStyles(raw: String): List<String> =
    raw.split(',').map(String::trim).filter(String::isNotEmpty)

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

    /**
     * ScreenScraper box art preference.
     * Values: prefer_2d (default), prefer_3d, 2d_only, 3d_only.
     */
    val screenScraperBoxType: String = "prefer_2d",

    /**
     * ScreenScraper screenshot preference.
     * Values: screenshot (ss), title (ss-title), both.
     */
    val screenScraperScreenshotType: String = "screenshot",

    /**
     * ScreenScraper logo / marquee preference.
     * Values: wheel, wheel_hd, marquee, screenmarquee.
     */
    val screenScraperLogoType: String = "wheel",

    /** When true, ScreenScraper fanart is also offered as hero art. */
    val screenScraperFanartAsHero: Boolean = true,

    /**
     * SteamGridDB styles per photo type (priority order when sorting).
     * Empty = do not filter; API returns all styles.
     * Grids: [SteamGridDbStyleHints.Grid]
     * Heroes: [SteamGridDbStyleHints.Hero]
     * Logos: [SteamGridDbStyleHints.Logo]
     * Icons: [SteamGridDbStyleHints.Icon]
     */
    val steamGridDbGridStyles: List<String> = emptyList(),
    val steamGridDbHeroStyles: List<String> = emptyList(),
    val steamGridDbLogoStyles: List<String> = emptyList(),
    val steamGridDbIconStyles: List<String> = emptyList(),

    /** SteamGridDB animation filter: static, animated, or both. */
    val steamGridDbAnimation: String = "static",

    val steamGridDbIncludeNsfw: Boolean = false,
    val steamGridDbIncludeHumor: Boolean = false,

    val libretroFetchBoxart: Boolean = true,
    val libretroFetchSnaps: Boolean = true,
    val libretroFetchTitles: Boolean = true,

    val raFetchIcon: Boolean = true,
    val raFetchBoxArt: Boolean = true,
    val raFetchTitle: Boolean = true,

    /** 0-based pick when multiple regional/alternate assets exist for one media type. */
    val mediaVariantIndex: Int = 0,

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
            languagePriority = override.languagePriority ?: languagePriority,
            screenScraperBoxType = override.screenScraperBoxType ?: screenScraperBoxType,
            screenScraperScreenshotType = override.screenScraperScreenshotType
                ?: screenScraperScreenshotType,
            screenScraperLogoType = override.screenScraperLogoType ?: screenScraperLogoType,
            screenScraperFanartAsHero = override.screenScraperFanartAsHero ?: screenScraperFanartAsHero,
            steamGridDbGridStyles = override.steamGridDbGridStyles ?: steamGridDbGridStyles,
            steamGridDbHeroStyles = override.steamGridDbHeroStyles ?: steamGridDbHeroStyles,
            steamGridDbLogoStyles = override.steamGridDbLogoStyles ?: steamGridDbLogoStyles,
            steamGridDbIconStyles = override.steamGridDbIconStyles ?: steamGridDbIconStyles,
            steamGridDbAnimation = override.steamGridDbAnimation ?: steamGridDbAnimation,
            steamGridDbIncludeNsfw = override.steamGridDbIncludeNsfw ?: steamGridDbIncludeNsfw,
            steamGridDbIncludeHumor = override.steamGridDbIncludeHumor ?: steamGridDbIncludeHumor,
            libretroFetchBoxart = override.libretroFetchBoxart ?: libretroFetchBoxart,
            libretroFetchSnaps = override.libretroFetchSnaps ?: libretroFetchSnaps,
            libretroFetchTitles = override.libretroFetchTitles ?: libretroFetchTitles,
            raFetchIcon = override.raFetchIcon ?: raFetchIcon,
            raFetchBoxArt = override.raFetchBoxArt ?: raFetchBoxArt,
            raFetchTitle = override.raFetchTitle ?: raFetchTitle,
            mediaVariantIndex = override.mediaVariantIndex ?: mediaVariantIndex
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

/** Sorts candidate media by region priority, then picks [variantIndex] (0 = best). */
fun List<MediaCandidate>.pickMedia(
    regionPriority: List<String>,
    variantIndex: Int = 0
): MediaCandidate? {
    if (isEmpty()) return null
    val sorted = sortedBy { candidate ->
        val idx = regionPriority.indexOf(candidate.region?.lowercase())
        if (idx == -1) regionPriority.size else idx
    }
    return sorted.getOrNull(variantIndex.coerceIn(0, sorted.lastIndex))
}
