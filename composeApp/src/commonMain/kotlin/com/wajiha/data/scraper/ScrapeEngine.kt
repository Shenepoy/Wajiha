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

    fun hasConfiguredSources(settings: ScraperSettings): Boolean =
        configuredSources(settings).isNotEmpty()

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
    suspend fun scrapeGame(
        game: GameEntity,
        settings: ScraperSettings,
        policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps
    ): GameScrapeResult {
        val effective = settings.forPlatform(game.platformId)
        val query = buildQuery(game)
        val active = configuredSources(effective)
        if (active.isEmpty()) {
            WajihaLog.w(WajihaTags.SCRAPE, "gameId=${game.id}: no sources configured")
            return GameScrapeResult.notConfigured(game.id)
        }

        val sourceOutcomes = linkedMapOf<String, SourceLookupOutcome>()
        val candidates = mutableMapOf<String, ScrapeCandidate>()
        for (source in active) {
            val outcome = try {
                source.lookupResult(query, effective)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "gameId=${game.id} source=${source.id} lookup failed: ${e.message}"
                )
                SourceLookupOutcome.Failed(classifyThrowable(e))
            }
            sourceOutcomes[source.id] = outcome
            if (outcome is SourceLookupOutcome.Hit) {
                candidates[source.id] = outcome.candidate
            }
        }
        val summaries = sourceOutcomes.map { (id, outcome) -> outcome.toSummary(id) }
        if (candidates.isEmpty()) {
            WajihaLog.i(WajihaTags.SCRAPE, "gameId=${game.id} name=${game.displayName}: no match")
            return GameScrapeResult.noMatch(game.id, summaries)
        }

        return applyCandidates(
            game = game,
            candidates = candidates,
            settings = effective,
            policy = policy,
            sourceSummaries = summaries
        )
    }

    /**
     * Applies a manually chosen candidate from one source. Metadata comes from
     * that candidate; its media wins for every type it offers.
     */
    suspend fun applyManualMatch(
        game: GameEntity,
        candidate: ScrapeCandidate,
        settings: ScraperSettings
    ): GameScrapeResult {
        val platformSettings = settings.forPlatform(game.platformId)
        val selection = ScrapeSelection(
            metadataFrom = candidate,
            media = MediaType.entries.mapNotNull { type ->
                val list = candidate.media.filter { it.type == type }
                if (list.isEmpty()) return@mapNotNull null
                val pick = list.pickMedia(
                    platformSettings.regionPriority,
                    platformSettings.mediaVariantIndex
                ) ?: list.first()
                type to StagedMediaPick(candidate.sourceId, pick)
            }.toMap()
        )
        return applySelection(game, selection, settings)
    }

    /**
     * Applies staged review picks. Absent media keys are left untouched;
     * explicit null values clear that media type.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun applySelection(
        game: GameEntity,
        selection: ScrapeSelection,
        settings: ScraperSettings
    ): GameScrapeResult {
        val effective = settings.forPlatform(game.platformId)
        var updated = game
        var metadataSource: String? = null
        selection.metadataFrom?.let { candidate ->
            candidate.metadata?.let { meta ->
                updated = updated.mergeMetadata(meta, overwrite = true)
                metadataSource = candidate.sourceId
            }
            candidate.metadata?.raGameId?.let { raId ->
                if (updated.raGameId == null) updated = updated.copy(raGameId = raId)
            }
        }
        updated = updated.copy(scrapedAt = Clock.System.now().toEpochMilliseconds())
        gameRepository.update(updated)

        var saved = 0
        var failed = 0
        var attempted = 0
        val existing = gameRepository.media(game.id).associateBy { it.type }
        for ((type, stagedOrNull) in selection.media) {
            if (stagedOrNull == null || stagedOrNull.candidate == null) {
                existing[type.dbName]?.let { gameRepository.deleteMedia(it.id) }
                continue
            }
            val media = stagedOrNull.candidate
            attempted++
            val localPath = download(game.id, media, effective)
            if (localPath == null) {
                failed++
                continue
            }
            gameRepository.saveMedia(
                GameMediaEntity(
                    gameId = game.id,
                    type = type.dbName,
                    source = stagedOrNull.sourceId,
                    localPath = localPath,
                    remoteUrl = media.url,
                    width = media.width,
                    height = media.height,
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
            )
            saved++
        }

        val outcome = when {
            saved > 0 || metadataSource != null -> GameScrapeOutcome.Matched
            attempted > 0 && saved == 0 -> GameScrapeOutcome.Partial
            selection.media.isEmpty() && metadataSource == null -> GameScrapeOutcome.Matched
            else -> GameScrapeOutcome.Partial
        }
        return GameScrapeResult(
            gameId = game.id,
            outcome = outcome,
            metadataSource = metadataSource,
            mediaSaved = saved,
            mediaFailed = failed,
            failureKind = if (outcome == GameScrapeOutcome.Partial) {
                ScrapeFailureKind.Download
            } else {
                null
            },
            message = when (outcome) {
                GameScrapeOutcome.Partial -> "Partial — $failed media download(s) failed"
                else -> null
            },
            sourceSummaries = selection.metadataFrom?.let {
                listOf(SourceResultSummary(it.sourceId, SourceResultStatus.Hit))
            }.orEmpty()
        )
    }

    /**
     * Lookup + search candidates for the review picker (search first, then
     * hash/lookup hits for sources that don't search by name).
     */
    suspend fun gatherReviewCandidates(
        game: GameEntity,
        settings: ScraperSettings,
        searchName: String? = null
    ): List<ScrapeCandidate> {
        val effective = settings.forPlatform(game.platformId)
        val name = searchName?.takeIf { it.isNotBlank() } ?: game.displayName
        val searched = searchAll(name, game, effective)

        val query = buildQuery(game)
        val fromLookup = mutableListOf<ScrapeCandidate>()
        for (source in configuredSources(effective)) {
            when (val outcome = source.lookupResult(query, effective)) {
                is SourceLookupOutcome.Hit -> fromLookup += outcome.candidate
                else -> Unit
            }
        }
        return (searched + fromLookup).distinctBy { it.sourceId to it.sourceGameId }
    }

    /**
     * Lazy media options for one review slot. Searches/looks up sources and
     * returns only [type] (client-side filter). SteamGridDB first page is included.
     */
    suspend fun gatherReviewMedia(
        game: GameEntity,
        type: MediaType,
        settings: ScraperSettings,
        searchName: String? = null
    ): List<Pair<String, MediaCandidate>> {
        val candidates = gatherReviewCandidates(game, settings, searchName)
        return candidates
            .flatMap { c ->
                c.media.filter { it.type == type }.map { c.sourceId to it }
            }
            .distinctBy { it.second.url }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun applyCandidates(
        game: GameEntity,
        candidates: Map<String, ScrapeCandidate>,
        settings: ScraperSettings,
        policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps,
        sourceSummaries: List<SourceResultSummary> = emptyList()
    ): GameScrapeResult {
        val overwriteMeta = policy.overwriteMetadata
        val overwriteMedia = policy.overwriteMedia
        // --- metadata: first source in the chain that has any ---
        var metadataSource: String? = null
        var updated = game
        val chain = if (overwriteMeta) candidates.keys.toList() else settings.metadataPriority
        for (sourceId in chain) {
            val meta = candidates[sourceId]?.metadata ?: continue
            updated = updated.mergeMetadata(meta, overwrite = overwriteMeta)
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
        var failed = 0
        var attempted = 0
        val existing = gameRepository.media(game.id).associateBy { it.type }
        for (type in MediaType.entries) {
            val hasExisting = existing[type.dbName]?.localPath != null
            if (hasExisting && !overwriteMedia) continue
            if (policy.onlyMissingMedia && hasExisting) continue

            val priority = if (overwriteMedia) {
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
            attempted++
            val localPath = download(game.id, media, settings)
            if (localPath == null) {
                failed++
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

        val offeredMedia = candidates.values.any { it.media.isNotEmpty() }
        val outcome = when {
            saved > 0 || (!offeredMedia && metadataSource != null) -> GameScrapeOutcome.Matched
            offeredMedia && attempted > 0 && saved == 0 -> GameScrapeOutcome.Partial
            metadataSource != null -> GameScrapeOutcome.Matched
            else -> GameScrapeOutcome.Partial
        }
        val message = when (outcome) {
            GameScrapeOutcome.Partial ->
                if (failed > 0) "Matched but $failed media download(s) failed"
                else "Matched with no media saved"
            else -> null
        }
        WajihaLog.i(
            WajihaTags.SCRAPE,
            "gameId=${game.id} done outcome=$outcome mediaSaved=$saved mediaFailed=$failed"
        )
        return GameScrapeResult(
            gameId = game.id,
            outcome = outcome,
            metadataSource = metadataSource,
            mediaSaved = saved,
            mediaFailed = failed,
            failureKind = if (outcome == GameScrapeOutcome.Partial) {
                ScrapeFailureKind.Download
            } else {
                null
            },
            message = message,
            sourceSummaries = sourceSummaries
        )
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

    /**
     * Loads the next SteamGridDB page for [type] on [candidate] (sourceGameId = SGDB id).
     * Returns empty when the source isn't SteamGridDB or there is no more data.
     */
    suspend fun loadMoreSteamGridDbMedia(
        candidate: ScrapeCandidate,
        type: MediaType,
        settings: ScraperSettings,
        platformId: String,
        page: Int
    ): com.wajiha.data.scraper.sources.SteamGridDbMediaPage {
        if (candidate.sourceId != "steamgriddb") {
            return com.wajiha.data.scraper.sources.SteamGridDbMediaPage(
                emptyList(),
                page,
                hasMore = false
            )
        }
        val gameId = candidate.sourceGameId.toIntOrNull()
            ?: return com.wajiha.data.scraper.sources.SteamGridDbMediaPage(
                emptyList(),
                page,
                hasMore = false
            )
        val sgdb = source("steamgriddb") as? com.wajiha.data.scraper.sources.SteamGridDbSource
            ?: return com.wajiha.data.scraper.sources.SteamGridDbMediaPage(
                emptyList(),
                page,
                hasMore = false
            )
        return sgdb.fetchMediaTypePage(
            gameId,
            type,
            settings.forPlatform(platformId),
            page
        )
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
