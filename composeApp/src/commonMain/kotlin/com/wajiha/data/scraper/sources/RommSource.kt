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
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.util.encodeBase64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** RomM server (self-hosted library manager) — remote metadata/media sync. */
class RommSource(private val http: HttpClient) : ScraperSource {

    override val id = "romm"
    override val displayName = "RomM"

    // Lenient: RomM returns numbers for some fields we read as strings
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun isConfigured(settings: ScraperSettings): Boolean =
        settings.rommUrl.isNotBlank()

    override suspend fun lookup(query: ScrapeQuery, settings: ScraperSettings): ScrapeCandidate? =
        search(query.fileName, query, settings).firstOrNull()

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): List<ScrapeCandidate> {
        val base = settings.rommUrl.trimEnd('/')
        val roms = try {
            val body = http.get("$base/api/roms") {
                auth(settings)
                parameter("search_term", name)
                parameter("limit", 10)
            }.body<String>()
            // RomM returns either a bare list or a paginated {items:[...]}
            try {
                json.decodeFromString<RommPage>(body).items.orEmpty()
            } catch (_: Exception) {
                json.decodeFromString<List<RommRom>>(body)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return roms.map { rom ->
            val cover = rom.urlCover?.let { absolutize(base, it) }
            ScrapeCandidate(
                sourceId = id,
                sourceGameId = rom.id.toString(),
                name = rom.name ?: rom.fileName ?: "?",
                metadata = ScrapedMetadata(
                    name = rom.name,
                    description = rom.summary,
                    genre = rom.genres?.joinToString(),
                    rating = rom.rating?.toFloatOrNull()
                ),
                media = buildList {
                    cover?.let { add(MediaCandidate(type = MediaType.Boxart, url = it)) }
                    rom.urlScreenshots.orEmpty().forEach {
                        add(MediaCandidate(type = MediaType.Screenshot, url = absolutize(base, it)))
                    }
                },
                thumbnailUrl = cover
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(settings: ScraperSettings) {
        if (settings.rommUsername.isNotBlank()) {
            val token = "${settings.rommUsername}:${settings.rommPassword}".encodeBase64()
            header("Authorization", "Basic $token")
        }
    }

    private fun absolutize(base: String, path: String): String =
        if (path.startsWith("http")) path else "$base/${path.trimStart('/')}"

    @Serializable
    private data class RommPage(val items: List<RommRom>? = null)

    @Serializable
    private data class RommRom(
        val id: Long,
        val name: String? = null,
        @kotlinx.serialization.SerialName("fs_name") val fileName: String? = null,
        val summary: String? = null,
        val genres: List<String>? = null,
        val rating: String? = null,
        @kotlinx.serialization.SerialName("url_cover") val urlCover: String? = null,
        @kotlinx.serialization.SerialName("url_screenshots") val urlScreenshots: List<String>? = null
    )
}
