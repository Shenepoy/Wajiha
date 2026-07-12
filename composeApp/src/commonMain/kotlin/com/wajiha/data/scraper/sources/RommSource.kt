package com.wajiha.data.scraper.sources

import com.wajiha.data.WajihaJson
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
import com.wajiha.data.scraper.SourceSearchBundle
import com.wajiha.data.scraper.getStringResult
import com.wajiha.data.scraper.rommAuth
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** RomM server (self-hosted library manager) — remote metadata/media sync. */
class RommSource(private val http: HttpClient) : ScraperSource {

    override val id = "romm"
    override val displayName = "RomM"

    override fun isConfigured(settings: ScraperSettings): Boolean =
        settings.rommUrl.isNotBlank()

    override suspend fun lookupResult(
        query: ScrapeQuery,
        settings: ScraperSettings
    ): SourceLookupOutcome = searchWithOutcome(query.fileName, query, settings).toLookupOutcome()

    override suspend fun search(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): List<ScrapeCandidate> = searchWithOutcome(name, query, settings).candidates

    private suspend fun searchWithOutcome(
        name: String,
        query: ScrapeQuery,
        settings: ScraperSettings
    ): SourceSearchBundle {
        val base = settings.rommUrl.trimEnd('/')
        val result = http.getStringResult("$base/api/roms") {
            rommAuth(settings)
            parameter("search_term", name)
            parameter("limit", 10)
        }
        val body = when (result) {
            is HttpJsonResult.Failed -> return SourceSearchBundle(emptyList(), result.failure)
            is HttpJsonResult.Ok -> result.value
        }
        val roms = try {
            try {
                WajihaJson.Lenient.decodeFromString<RommPage>(body).items.orEmpty()
            } catch (_: Exception) {
                WajihaJson.Lenient.decodeFromString<List<RommRom>>(body)
            }
        } catch (e: Exception) {
            return SourceSearchBundle(
                emptyList(),
                ScrapeFailure(ScrapeFailureKind.Parse, e.message ?: "Parse failed")
            )
        }
        return SourceSearchBundle(
            roms.map { rom ->
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
                            add(
                                MediaCandidate(
                                    type = MediaType.Screenshot,
                                    url = absolutize(base, it)
                                )
                            )
                        }
                    },
                    thumbnailUrl = cover
                )
            }
        )
    }

    private fun absolutize(base: String, path: String): String =
        if (path.startsWith("http")) path else "$base/${path.trimStart('/')}"

    @Serializable
    private data class RommPage(val items: List<RommRom>? = null)

    @Serializable
    private data class RommRom(
        val id: Long,
        val name: String? = null,
        @SerialName("fs_name") val fileName: String? = null,
        val summary: String? = null,
        val genres: List<String>? = null,
        val rating: String? = null,
        @SerialName("url_cover") val urlCover: String? = null,
        @SerialName("url_screenshots") val urlScreenshots: List<String>? = null
    )
}
