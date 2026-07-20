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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Core scraping orchestrator. Uses [ScrapeMatchTool] to search/rank sources,
 * then downloads and persists media. Side effects live here; sources stay pure.
 */
class ScrapeEngine(
    private val sources: List<ScraperSource>,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val mediaStorage: MediaStorage,
    private val http: HttpClient,
    private val imageProcessor: ImageProcessor = NoopImageProcessor,
    private val matchTool: ScrapeMatchTool = ScrapeMatchTool(sources),
) {
    private val downloadSemaphore = Semaphore(4)

    fun source(id: String): ScraperSource? = sources.firstOrNull { it.id == id }

    fun configuredSources(settings: ScraperSettings): List<ScraperSource> =
        sources.filter { it.id in settings.enabledSources && it.isConfigured(settings) }

    fun hasConfiguredSources(settings: ScraperSettings): Boolean = configuredSources(settings).isNotEmpty()

    suspend fun deleteStoredMedia(path: String) {
        runCatching { mediaStorage.delete(path) }
    }

    suspend fun buildQuery(game: GameEntity): ScrapeQuery {
        val platform = platformRepository.byId(game.platformId)
        return buildQuery(game, platform)
    }

    fun buildQuery(
        game: GameEntity,
        platform: PlatformEntity?,
    ): ScrapeQuery =
        ScrapeQuery(
            gameId = game.id,
            displayName = game.displayName,
            fileName = game.fileName,
            fileSize = game.fileSize,
            crc32 = game.crc32,
            md5 = game.md5,
            platformId = game.platformId,
            platformName = platform?.name ?: game.platformId,
            screenScraperId =
                platform?.screenScraperId
                    ?: ScreenScraperSystemIds.resolve(game.platformId),
            raConsoleId = platform?.raConsoleId,
            libretroName = platform?.libretroName,
        )

    /**
     * Automatically scrapes one game via [ScrapeMatchTool] ranking
     * (score + author prefer/blacklist), then persists.
     */
    suspend fun scrapeGame(
        game: GameEntity,
        settings: ScraperSettings,
        policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps,
    ): GameScrapeResult {
        val platformSettings = settings.forPlatform(game.platformId)
        val effective =
            policy.sourceId?.let { platformSettings.copy(enabledSources = listOf(it)) }
                ?: platformSettings
        val query = buildQuery(game)
        if (!hasConfiguredSources(effective)) {
            WajihaLog.w(WajihaTags.SCRAPE, "gameId=${game.id}: no sources configured")
            return GameScrapeResult.notConfigured(game.id)
        }

        val existing = gameRepository.media(game.id).associateBy { it.type }
        // FillGaps only considers preferred artwork (matches batch needsGapFill + UI copy).
        // Force considers every non-music type and overwrites.
        val typesToConsider =
            if (policy.overwriteMedia) {
                MediaType.entries.filter { it != MediaType.Music }
            } else {
                GapFillMediaTypes
            }
        val neededTypes =
            typesToConsider.filter { type ->
                val hasExisting = !existing[type.dbName]?.localPath.isNullOrBlank()
                when {
                    policy.overwriteMedia -> true
                    hasExisting -> false
                    else -> true
                }
            }

        // Nothing missing and metadata already scraped — skip (do not search).
        val needMetadata = policy.overwriteMetadata || game.scrapedAt == null
        if (neededTypes.isEmpty() && !needMetadata) {
            WajihaLog.i(
                WajihaTags.SCRAPE,
                "gameId=${game.id} name=${game.displayName}: skip — media already present",
            )
            return GameScrapeResult.alreadyComplete(game.id)
        }

        val bundle =
            matchTool.searchMatches(
                query = query,
                settings = effective,
                neededTypes = neededTypes,
                mode = MatchMode.Auto,
            )

        if (bundle.metadataCandidates.isEmpty() &&
            bundle.mediaByType.isEmpty() &&
            bundle.gameMatches.isEmpty()
        ) {
            WajihaLog.i(WajihaTags.SCRAPE, "gameId=${game.id} name=${game.displayName}: no match")
            return GameScrapeResult.noMatch(game.id, bundle.sourceSummaries)
        }

        val selection = matchTool.pickBest(bundle, effective, neededTypes)
        return applyRankedSelection(
            game = game,
            selection = selection,
            settings = effective,
            policy = policy,
            sourceSummaries = bundle.sourceSummaries,
        )
    }

    /**
     * Applies a manually chosen candidate from one source. Metadata comes from
     * that candidate; its media is ranked (score / author prefs) per type.
     */
    suspend fun applyManualMatch(
        game: GameEntity,
        candidate: ScrapeCandidate,
        settings: ScraperSettings,
    ): GameScrapeResult {
        val platformSettings = settings.forPlatform(game.platformId)
        val selection =
            ScrapeSelection(
                metadataFrom = candidate,
                media =
                    MediaType.entries
                        .mapNotNull { type ->
                            val list = candidate.media.filter { it.type == type }
                            if (list.isEmpty()) return@mapNotNull null
                            val options =
                                list.map { media ->
                                    RankedMediaOption(
                                        sourceId = candidate.sourceId,
                                        candidate = media,
                                        score = media.score ?: 0,
                                        authorKey = media.authorKey,
                                        authorDisplay = media.authorName,
                                        rankReason = "",
                                        confidence = candidate.matchConfidence,
                                    )
                                }
                            val policy = MatchRankPolicy.fromSettings(platformSettings, type)
                            val best =
                                MediaRanker.pickBestOption(
                                    MediaRanker.rankMediaOptions(options, policy),
                                    policy,
                                ) ?: return@mapNotNull null
                            type to StagedMediaPick(best.sourceId, best.candidate)
                        }.toMap(),
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
        settings: ScraperSettings,
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
                existing[type.dbName]?.let { row ->
                    row.localPath?.let { path -> runCatching { mediaStorage.delete(path) } }
                    gameRepository.deleteMedia(row.id)
                }
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
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            saved++
        }

        val outcome =
            when {
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
            failureKind =
                if (outcome == GameScrapeOutcome.Partial) {
                    ScrapeFailureKind.Download
                } else {
                    null
                },
            message =
                when (outcome) {
                    GameScrapeOutcome.Partial -> "Partial — $failed media download(s) failed"
                    else -> null
                },
            sourceSummaries =
                selection.metadataFrom
                    ?.let {
                        listOf(SourceResultSummary(it.sourceId, SourceResultStatus.Hit))
                    }.orEmpty(),
        )
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun applyRankedSelection(
        game: GameEntity,
        selection: ScrapeSelection,
        settings: ScraperSettings,
        policy: ScrapeRunPolicy,
        sourceSummaries: List<SourceResultSummary>,
    ): GameScrapeResult {
        val overwriteMeta = policy.overwriteMetadata
        var metadataSource: String? = null
        var updated = game
        selection.metadataFrom?.let { candidate ->
            candidate.metadata?.let { meta ->
                updated = updated.mergeMetadata(meta, overwrite = overwriteMeta)
                metadataSource = candidate.sourceId
            }
            candidate.metadata?.raGameId?.let { raId ->
                if (updated.raGameId == null) updated = updated.copy(raGameId = raId)
            }
        }
        updated = updated.copy(scrapedAt = Clock.System.now().toEpochMilliseconds())
        gameRepository.update(updated)
        WajihaLog.i(
            WajihaTags.SCRAPE,
            "gameId=${game.id} metaSource=$metadataSource " +
                "name=${updated.displayName} region=${updated.region} " +
                "ageRating=${updated.ageRating}",
        )

        var saved = 0
        var failed = 0
        var attempted = 0
        for ((type, staged) in selection.media) {
            val media = staged?.candidate ?: continue
            val sourceId = staged.sourceId
            attempted++
            val localPath = download(game.id, media, settings)
            if (localPath == null) {
                failed++
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "gameId=${game.id} media=${type.dbName} from=$sourceId download failed",
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
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            saved++
            WajihaLog.d(
                WajihaTags.SCRAPE,
                "gameId=${game.id} saved ${type.dbName} from=$sourceId " +
                    "score=${media.score} author=${media.authorName ?: media.authorKey}",
            )
        }

        val offeredMedia = selection.media.values.any { it?.candidate != null }
        val outcome =
            when {
                saved > 0 || (!offeredMedia && metadataSource != null) -> GameScrapeOutcome.Matched
                offeredMedia && attempted > 0 && saved == 0 -> GameScrapeOutcome.Partial
                metadataSource != null -> GameScrapeOutcome.Matched
                else -> GameScrapeOutcome.Partial
            }
        val message =
            when (outcome) {
                GameScrapeOutcome.Partial -> {
                    if (failed > 0) {
                        "Matched but $failed media download(s) failed"
                    } else {
                        "Matched with no media saved"
                    }
                }

                else -> {
                    null
                }
            }
        WajihaLog.i(
            WajihaTags.SCRAPE,
            "gameId=${game.id} done outcome=$outcome mediaSaved=$saved mediaFailed=$failed",
        )
        return GameScrapeResult(
            gameId = game.id,
            outcome = outcome,
            metadataSource = metadataSource,
            mediaSaved = saved,
            mediaFailed = failed,
            failureKind =
                if (outcome == GameScrapeOutcome.Partial) {
                    ScrapeFailureKind.Download
                } else {
                    null
                },
            message = message,
            sourceSummaries = sourceSummaries,
        )
    }

    /**
     * Ranked game matches for the review picker (via [ScrapeMatchTool]).
     */
    suspend fun gatherReviewCandidates(
        game: GameEntity,
        settings: ScraperSettings,
        searchName: String? = null,
    ): List<ScrapeCandidate> {
        val effective = settings.forPlatform(game.platformId)
        val query = buildQuery(game)
        val bundle =
            matchTool.searchMatches(
                query = query,
                settings = effective,
                neededTypes = MediaType.entries.filter { it != MediaType.Music },
                mode = MatchMode.Review,
                searchName = searchName,
            )
        // Attach top ranked media onto each game match for the review UI
        return bundle.gameMatches
            .map { match ->
                val media =
                    bundle.mediaByType.values
                        .flatten()
                        .filter { it.sourceId == match.sourceId }
                        .map { it.candidate }
                        .ifEmpty { match.candidate.media }
                match.candidate.copy(media = media.distinctBy { it.type to it.url })
            }.distinctBy { it.sourceId to it.sourceGameId }
    }

    /**
     * Lazy media options for one review slot — ranked by score / author prefs.
     */

    /**
     * Review media gallery for one type, plus SGDB paging context when available.
     */
    suspend fun gatherReviewMedia(
        game: GameEntity,
        type: MediaType,
        settings: ScraperSettings,
        searchName: String? = null,
    ): ReviewMediaGatherResult {
        val effective = settings.forPlatform(game.platformId)
        val query = buildQuery(game)
        val bundle =
            matchTool.searchMatches(
                query = query,
                settings = effective,
                neededTypes = listOf(type),
                mode = MatchMode.Review,
                searchName = searchName,
            )
        return ReviewMediaGatherResult(
            options =
                bundle.mediaByType[type]
                    .orEmpty()
                    .map { it.sourceId to it.candidate }
                    .distinctBy { it.second.url },
            steamGridDbCandidate =
                bundle.gameMatches.firstOrNull { it.sourceId == "steamgriddb" }?.candidate,
            steamGridDbHasMore = bundle.steamGridDbHasMoreByType[type] == true,
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
        settings: ScraperSettings? = null,
    ): String? =
        downloadSemaphore.withPermit {
            try {
                var extension =
                    media.format
                        ?: media.url
                            .substringAfterLast('.', "")
                            .substringBefore('?')
                            .take(4)
                            .ifBlank {
                                when (media.type) {
                                    MediaType.Video -> "mp4"
                                    MediaType.Music -> "mp3"
                                    else -> "png"
                                }
                            }
                var bytes =
                    if (media.url.startsWith("file://")) {
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
                    "download gameId=$gameId type=${media.type.dbName}: ${e.message}",
                )
                null
            }
        }

    /** Manual search across all configured sources — ranked via [ScrapeMatchTool]. */
    suspend fun searchAll(
        name: String,
        game: GameEntity,
        settings: ScraperSettings,
    ): List<ScrapeCandidate> = gatherReviewCandidates(game, settings, searchName = name)

    /**
     * Loads the next SteamGridDB page for [type] on [candidate] (sourceGameId = SGDB id).
     * Returns empty when the source isn't SteamGridDB or there is no more data.
     */
    suspend fun loadMoreSteamGridDbMedia(
        candidate: ScrapeCandidate,
        type: MediaType,
        settings: ScraperSettings,
        platformId: String,
        page: Int,
    ): com.wajiha.data.scraper.sources.SteamGridDbMediaPage {
        if (candidate.sourceId != "steamgriddb") {
            return com.wajiha.data.scraper.sources.SteamGridDbMediaPage(
                emptyList(),
                page,
                hasMore = false,
            )
        }
        val gameId =
            candidate.sourceGameId.toIntOrNull()
                ?: return com.wajiha.data.scraper.sources.SteamGridDbMediaPage(
                    emptyList(),
                    page,
                    hasMore = false,
                )
        val sgdb =
            source("steamgriddb") as? com.wajiha.data.scraper.sources.SteamGridDbSource
                ?: return com.wajiha.data.scraper.sources.SteamGridDbMediaPage(
                    emptyList(),
                    page,
                    hasMore = false,
                )
        return sgdb.fetchMediaTypePage(
            gameId,
            type,
            settings.forPlatform(platformId),
            page,
        )
    }

    private fun GameEntity.mergeMetadata(
        meta: ScrapedMetadata,
        overwrite: Boolean,
    ): GameEntity {
        // First successful scrape (or forced overwrite) upgrades ROM filename → curated title
        val applyName =
            !meta.name.isNullOrBlank() &&
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
            raGameId = raGameId ?: meta.raGameId,
        )
    }

    private fun pick(
        current: String?,
        new: String?,
        overwrite: Boolean,
    ): String? = if (overwrite) new ?: current else current ?: new
}

/** Platform bridge for reading local `file://` media. */
internal expect fun readLocalFile(path: String): ByteArray?
