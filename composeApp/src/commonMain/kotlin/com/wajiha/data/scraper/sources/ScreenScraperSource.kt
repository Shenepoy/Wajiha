package com.wajiha.data.scraper.sources

import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeQuery
import com.wajiha.data.scraper.ScrapedMetadata
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSource
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ScreenScraper.fr (api2). Hash-first lookup (crc/md5), name search fallback.
 * User account credentials are the user's; optional developer credentials come
 * from build config (passed in [devId]/[devPassword]).
 */
class ScreenScraperSource(
    private val http: HttpClient,
    private val devId: String = "",
    private val devPassword: String = ""
) : ScraperSource {

    override val id = "screenscraper"
    override val displayName = "ScreenScraper"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun isConfigured(settings: ScraperSettings): Boolean =
        settings.screenScraperUser.isNotBlank() && settings.screenScraperPassword.isNotBlank()

    override suspend fun lookup(query: ScrapeQuery, settings: ScraperSettings): ScrapeCandidate? {
        val response = try {
            http.get("https://api.screenscraper.fr/api2/jeuInfos.php") {
                commonParams(settings)
                parameter("romtype", "rom")
                parameter("romnom", query.fileName)
                if (query.fileSize > 0) parameter("romtaille", query.fileSize)
                query.crc32?.let { parameter("crc", it) }
                query.md5?.let { parameter("md5", it) }
                query.screenScraperId?.let { parameter("systemeid", it) }
            }.body<String>()
        } catch (_: Exception) {
            return null
        }
        return parseJeu(response, settings)
    }

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): List<ScrapeCandidate> {
        val response = try {
            http.get("https://api.screenscraper.fr/api2/jeuRecherche.php") {
                commonParams(settings)
                parameter("recherche", name)
                query.screenScraperId?.let { parameter("systemeid", it) }
            }.body<String>()
        } catch (_: Exception) {
            return emptyList()
        }
        return try {
            val parsed = json.decodeFromString<SsSearchEnvelope>(response)
            parsed.response?.jeux.orEmpty().mapNotNull { it.toCandidate(settings) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.commonParams(settings: ScraperSettings) {
        parameter("devid", devId)
        parameter("devpassword", devPassword)
        parameter("softname", "wajiha")
        parameter("output", "json")
        parameter("ssid", settings.screenScraperUser)
        parameter("sspassword", settings.screenScraperPassword)
    }

    private fun parseJeu(response: String, settings: ScraperSettings): ScrapeCandidate? = try {
        json.decodeFromString<SsGameEnvelope>(response).response?.jeu?.toCandidate(settings)
    } catch (_: Exception) {
        null
    }

    // --- ScreenScraper JSON model (subset) ---

    @Serializable
    private data class SsGameEnvelope(val response: SsGameResponse? = null)

    @Serializable
    private data class SsGameResponse(val jeu: SsJeu? = null)

    @Serializable
    private data class SsSearchEnvelope(val response: SsSearchResponse? = null)

    @Serializable
    private data class SsSearchResponse(val jeux: List<SsJeu>? = null)

    @Serializable
    private data class SsJeu(
        val id: String? = null,
        val noms: List<SsRegionText>? = null,
        val synopsis: List<SsLangText>? = null,
        val editeur: SsNamed? = null,
        val developpeur: SsNamed? = null,
        val joueurs: SsText? = null,
        val note: SsText? = null,
        val dates: List<SsRegionText>? = null,
        val genres: List<SsGenre>? = null,
        val classifications: List<SsClassification>? = null,
        val regionshortnames: List<String>? = null,
        val medias: List<SsMedia>? = null
    ) {
        fun toCandidate(settings: ScraperSettings): ScrapeCandidate? {
            val gameId = id ?: return null
            val nameEntry = pickRegionEntry(noms, settings.regionPriority) ?: return null
            val name = nameEntry.text ?: return null
            val region = nameEntry.region
                ?: regionshortnames?.firstOrNull()
            val ageRating = formatAgeRating(
                classifications,
                settings.regionPriority
            )
            val media = medias.orEmpty().mapNotNull { it.toCandidate() }
            // Prefer steamgrid for hero; also map fanart as hero candidate for UI
            val expandedMedia = media + media.filter { it.type == MediaType.Fanart }.map {
                it.copy(type = MediaType.Hero)
            }
            return ScrapeCandidate(
                sourceId = "screenscraper",
                sourceGameId = gameId,
                name = name,
                metadata = ScrapedMetadata(
                    name = name,
                    description = pickLang(synopsis, settings.languagePriority),
                    developer = developpeur?.text,
                    publisher = editeur?.text,
                    releaseDate = pickRegion(dates, settings.regionPriority),
                    genre = genres.orEmpty().firstNotNullOfOrNull { genre ->
                        pickLang(genre.noms, settings.languagePriority)
                    },
                    rating = note?.text?.substringBefore('/')?.trim()?.toFloatOrNull(),
                    ageRating = ageRating,
                    players = joueurs?.text,
                    region = region
                ),
                media = expandedMedia.distinctBy { it.type to it.url },
                thumbnailUrl = media.firstOrNull { it.type == MediaType.Boxart }?.url
            )
        }

        private fun pickRegion(items: List<SsRegionText>?, priority: List<String>): String? =
            pickRegionEntry(items, priority)?.text

        private fun pickRegionEntry(
            items: List<SsRegionText>?,
            priority: List<String>
        ): SsRegionText? {
            if (items.isNullOrEmpty()) return null
            for (region in priority) {
                items.firstOrNull { it.region?.lowercase() == region }?.let { return it }
            }
            return items.first()
        }

        private fun pickLang(items: List<SsLangText>?, priority: List<String>): String? {
            if (items.isNullOrEmpty()) return null
            for (lang in priority) {
                items.firstOrNull { it.langue?.lowercase() == lang }?.let { return it.text }
            }
            return items.first().text
        }

        private fun formatAgeRating(
            classifications: List<SsClassification>?,
            regionPriority: List<String>
        ): String? {
            if (classifications.isNullOrEmpty()) return null
            val preferredBoards = when {
                regionPriority.any { it.equals("us", true) || it.equals("wor", true) } ->
                    listOf("ESRB") + REGION_RATING_PREF
                regionPriority.any {
                    it.equals("eu", true) || it.equals("fr", true) || it.equals("de", true)
                } ->
                    listOf("PEGI", "USK") + REGION_RATING_PREF
                regionPriority.any { it.equals("jp", true) } ->
                    listOf("CERO") + REGION_RATING_PREF
                else -> REGION_RATING_PREF
            }
            for (board in preferredBoards.distinct()) {
                val hit = classifications.firstOrNull {
                    it.type?.equals(board, ignoreCase = true) == true && !it.text.isNullOrBlank()
                }
                if (hit != null) return "${hit.type!!.uppercase()} ${hit.text!!.trim()}"
            }
            val first = classifications.firstOrNull { !it.type.isNullOrBlank() && !it.text.isNullOrBlank() }
                ?: run {
                    WajihaLog.d(
                        WajihaTags.SCRAPE,
                        "SS classifications present but unparsed: $classifications"
                    )
                    return null
                }
            return "${first.type!!.uppercase()} ${first.text!!.trim()}"
        }

        companion object {
            private val REGION_RATING_PREF =
                listOf("ESRB", "PEGI", "CERO", "USK", "BBFC", "OFLC", "ACB")
        }
    }

    @Serializable
    private data class SsRegionText(val region: String? = null, val text: String? = null)

    @Serializable
    private data class SsLangText(val langue: String? = null, val text: String? = null)

    @Serializable
    private data class SsNamed(val text: String? = null)

    @Serializable
    private data class SsText(val text: String? = null)

    @Serializable
    private data class SsGenre(val noms: List<SsLangText>? = null)

    @Serializable
    private data class SsClassification(
        val type: String? = null,
        val text: String? = null
    )

    @Serializable
    private data class SsMedia(
        val type: String? = null,
        val url: String? = null,
        val region: String? = null,
        val format: String? = null
    ) {
        fun toCandidate(): MediaCandidate? {
            val mediaUrl = url ?: return null
            val mediaType = when (type) {
                "box-2D", "box-3D" -> MediaType.Boxart
                "wheel", "wheel-hd" -> MediaType.Logo
                "ss", "ss-title" -> MediaType.Screenshot
                "fanart" -> MediaType.Fanart
                "steamgrid" -> MediaType.Hero
                "video", "video-normalized" -> MediaType.Video
                "screenmarquee", "marquee" -> MediaType.Banner
                else -> return null
            }
            return MediaCandidate(
                type = mediaType,
                url = mediaUrl,
                region = region,
                format = format
            )
        }
    }
}
