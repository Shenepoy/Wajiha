package com.wajiha.data.scraper

import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Result of scraping a single game. */
data class GameScrapeResult(
    val gameId: Long,
    val matched: Boolean,
    val metadataSource: String? = null,
    val mediaSaved: Int = 0,
    val error: String? = null
)

/**
 * Core scraping orchestrator. Runs the configured source chain for one game:
 * metadata from the first source (in [ScraperSettings.metadataPriority]) that
 * matches, media per type from the per-type priority chain. Side effects
 * (downloads, DB writes) all happen here; sources only return candidates.
 */
class ScrapeEngine(
    private val sources: List<ScraperSource>,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val mediaStorage: MediaStorage,
    private val http: HttpClient,
    private val imageProcessor: ImageProcessor = NoopImageProcessor
) {
    private val downloadSemaphore = Semaphore(4)

    fun source(id: String): ScraperSource? = sources.firstOrNull { it.id == id }

    fun configuredSources(settings: ScraperSettings): List<ScraperSource> =
        sources.filter { it.id in settings.enabledSources && it.isConfigured(settings) }

    suspend fun buildQuery(game: GameEntity): ScrapeQuery {
        val platform = platformRepository.byId(game.platformId)
        return buildQuery(game, platform)
    }

    fun buildQuery(game: GameEntity, platform: PlatformEntity?): ScrapeQuery = ScrapeQuery(
        gameId = game.id,
        displayName = game.displayName,
        fileName = game.fileName,
        fileSize = game.fileSize,
        crc32 = game.crc32,
        md5 = game.md5,
        platformId = game.platformId,
        platformName = platform?.name ?: game.platformId,
        screenScraperId = platform?.screenScraperId,
        raConsoleId = platform?.raConsoleId,
        libretroName = platform?.libretroName
    )

    /**
     * Automatically scrapes one game: queries every configured source once,
     * applies metadata from the priority chain and media per-type priority.
     */
    suspend fun scrapeGame(game: GameEntity, settings: ScraperSettings): GameScrapeResult {
        val effective = settings.forPlatform(game.platformId)
        val query = buildQuery(game)
        val active = configuredSources(effective)
        if (active.isEmpty()) {
            WajihaLog.w(WajihaTags.SCRAPE, "gameId=${game.id}: no sources configured")
            return GameScrapeResult(game.id, matched = false, error = "no sources configured")
        }

        val candidates = mutableMapOf<String, ScrapeCandidate>()
        for (source in active) {
            try {
                source.lookup(query, effective)?.let { candidates[source.id] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "gameId=${game.id} source=${source.id} lookup failed: ${e.message}"
                )
            }
        }
        if (candidates.isEmpty()) {
            WajihaLog.i(WajihaTags.SCRAPE, "gameId=${game.id} name=${game.displayName}: no match")
            return GameScrapeResult(game.id, matched = false)
        }

        return applyCandidates(game, candidates, effective)
    }

    /**
     * Applies a manually chosen candidate from one source. Metadata comes from
     * that candidate; its media wins for every type it offers.
     */
    suspend fun applyManualMatch(
        game: GameEntity,
        candidate: ScrapeCandidate,
        settings: ScraperSettings
    ): GameScrapeResult = applyCandidates(
        game,
        mapOf(candidate.sourceId to candidate),
        settings.forPlatform(game.platformId),
        force = true
    )

    @OptIn(ExperimentalTime::class)
    private suspend fun applyCandidates(
        game: GameEntity,
        candidates: Map<String, ScrapeCandidate>,
        settings: ScraperSettings,
        force: Boolean = false
    ): GameScrapeResult {
        // --- metadata: first source in the chain that has any ---
        var metadataSource: String? = null
        var updated = game
        val chain = if (force) candidates.keys.toList() else settings.metadataPriority
        for (sourceId in chain) {
            val meta = candidates[sourceId]?.metadata ?: continue
            updated = updated.mergeMetadata(meta, overwrite = force)
            metadataSource = sourceId
            break
        }
        // RA game id can come from RA even when it isn't the metadata source
        candidates["ra"]?.metadata?.raGameId?.let { raId ->
            if (updated.raGameId == null) updated = updated.copy(raGameId = raId)
        }
        updated = updated.copy(scrapedAt = Clock.System.now().toEpochMilliseconds())
        gameRepository.update(updated)
        WajihaLog.i(
            WajihaTags.SCRAPE,
            "gameId=${game.id} metaSource=$metadataSource " +
                "name=${updated.displayName} region=${updated.region} " +
                "ageRating=${updated.ageRating}"
        )

        // --- media: per-type source priority, first hit wins ---
        var saved = 0
        val existing = gameRepository.media(game.id).associateBy { it.type }
        for (type in MediaType.entries) {
            val hasExisting = existing[type.dbName]?.localPath != null
            if (hasExisting && settings.existingMediaPolicy == "skip" && !force) continue

            val priority = if (force) {
                candidates.keys.toList()
            } else {
                settings.mediaPriority[type.dbName] ?: continue
            }
            val pick = priority.firstNotNullOfOrNull { sourceId ->
                candidates[sourceId]?.media
                    ?.filter { it.type == type }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { list ->
                        (list.pickMedia(settings.regionPriority, settings.mediaVariantIndex)
                            ?: list.first()) to sourceId
                    }
            } ?: continue

            val (media, sourceId) = pick
            val localPath = download(game.id, media, settings)
            if (localPath == null) {
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "gameId=${game.id} media=${type.dbName} from=$sourceId download failed"
                )
                continue
            }
            gameRepository.saveMedia(
                GameMediaEntity(
                    gameId = game.id,
                    type = type.dbName,
                    source = sourceId,
                    localPath = localPath,
                    remoteUrl = media.url,
                    width = media.width,
                    height = media.height,
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
            )
            saved++
            WajihaLog.d(
                WajihaTags.SCRAPE,
                "gameId=${game.id} saved ${type.dbName} from=$sourceId"
            )
        }
        WajihaLog.i(
            WajihaTags.SCRAPE,
            "gameId=${game.id} done matched=true mediaSaved=$saved"
        )
        return GameScrapeResult(game.id, matched = true, metadataSource = metadataSource, mediaSaved = saved)
    }

    /**
     * Downloads (or copies for file:// urls) one media asset; returns local
     * path. Images larger than [ScraperSettings.maxImageResolution] are
     * downscaled and recompressed before storage (0 = keep originals).
     */
    suspend fun download(
        gameId: Long,
        media: MediaCandidate,
        settings: ScraperSettings? = null
    ): String? = downloadSemaphore.withPermit {
        try {
            var extension = media.format
                ?: media.url.substringAfterLast('.', "").substringBefore('?').take(4)
                    .ifBlank {
                        when (media.type) {
                            MediaType.Video -> "mp4"
                            MediaType.Music -> "mp3"
                            else -> "png"
                        }
                    }
            var bytes = if (media.url.startsWith("file://")) {
                readLocalFile(media.url.removePrefix("file://")) ?: return@withPermit null
            } else {
                val response = http.get(media.url)
                if (!response.status.isSuccess()) return@withPermit null
                response.readRawBytes()
            }
            if (bytes.isEmpty()) return@withPermit null

            val maxEdge = settings?.maxImageResolution ?: 0
            val isRaster = media.type != MediaType.Video && media.type != MediaType.Music
            if (maxEdge > 0 && isRaster) {
                val preferAlpha = media.type == MediaType.Logo || media.type == MediaType.Icon
                imageProcessor.process(bytes, maxEdge, preferAlpha)?.let { processed ->
                    bytes = processed.bytes
                    extension = processed.extension
                }
            }
            mediaStorage.save(gameId, media.type, extension, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WajihaLog.w(
                WajihaTags.SCRAPE,
                "download gameId=$gameId type=${media.type.dbName}: ${e.message}"
            )
            null
        }
    }

    /** Manual search across all configured sources, for the match UI. */
    suspend fun searchAll(
        name: String,
        game: GameEntity,
        settings: ScraperSettings
    ): List<ScrapeCandidate> {
        val effective = settings.forPlatform(game.platformId)
        val query = buildQuery(game)
        val out = mutableListOf<ScrapeCandidate>()
        for (source in configuredSources(effective)) {
            try {
                out += source.search(name, query, effective)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "search source=${source.id}: ${e.message}"
                )
            }
        }
        return out
    }

    private fun GameEntity.mergeMetadata(meta: ScrapedMetadata, overwrite: Boolean): GameEntity {
        // First successful scrape (or forced overwrite) upgrades ROM filename → curated title
        val applyName = !meta.name.isNullOrBlank() &&
            (overwrite || displayName.isBlank() || scrapedAt == null)
        val nextName = if (applyName) meta.name.trim() else null
        return copy(
            displayName = nextName ?: displayName,
            sortName = nextName?.lowercase() ?: sortName,
            description = pick(description, meta.description, overwrite),
            developer = pick(developer, meta.developer, overwrite),
            publisher = pick(publisher, meta.publisher, overwrite),
            releaseDate = pick(releaseDate, meta.releaseDate, overwrite),
            genre = pick(genre, meta.genre, overwrite),
            rating = if (overwrite) meta.rating ?: rating else rating ?: meta.rating,
            ageRating = pick(ageRating, meta.ageRating, overwrite),
            players = pick(players, meta.players, overwrite),
            region = pick(region, meta.region, overwrite),
            raGameId = raGameId ?: meta.raGameId
        )
    }

    private fun pick(current: String?, new: String?, overwrite: Boolean): String? =
        if (overwrite) new ?: current else current ?: new
}

/** Platform bridge for reading local `file://` media. */
internal expect fun readLocalFile(path: String): ByteArray?
