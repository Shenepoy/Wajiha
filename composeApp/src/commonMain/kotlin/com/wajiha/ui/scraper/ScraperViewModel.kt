package com.wajiha.ui.scraper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.ra.RaRepository
import com.wajiha.data.scraper.BatchScrapeProgress
import com.wajiha.data.scraper.BatchScraper
import com.wajiha.data.scraper.CredentialTestResult
import com.wajiha.data.scraper.PlatformScraperOverride
import com.wajiha.data.scraper.ScrapeApiLog
import com.wajiha.data.scraper.ScrapeApiLogEntry
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeEngine
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.data.scraper.ScraperCredentialValidator
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.data.scraper.needsGapFill
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.platform.LibraryActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the manual-match flow of one selected game. */
data class ManualMatchState(
    val game: GameEntity? = null,
    val media: List<GameMediaEntity> = emptyList(),
    val searching: Boolean = false,
    val candidates: List<ScrapeCandidate> = emptyList(),
    val applying: Boolean = false,
    val message: String? = null,
    /** null = neutral, true = success, false = error */
    val messageSuccess: Boolean? = null,
)

/** Result of a credential test probe for one scraper source. */
data class SourceTestState(
    val loading: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
)

class ScraperViewModel(
    private val settingsRepository: ScraperSettingsRepository,
    private val engine: ScrapeEngine,
    private val batchScraper: BatchScraper,
    private val gameRepository: GameRepository,
    platformRepository: PlatformRepository,
    private val libraryActions: LibraryActions,
    private val credentialValidator: ScraperCredentialValidator,
    private val raRepository: RaRepository,
) : ViewModel() {
    val settings: StateFlow<ScraperSettings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.Eagerly, ScraperSettings())

    val progress: StateFlow<BatchScrapeProgress> = batchScraper.progress

    /** In-memory scraper HTTP exchange log for the API logs viewer. */
    val apiLogs: StateFlow<List<ScrapeApiLogEntry>> = ScrapeApiLog.entries

    fun clearApiLogs() = ScrapeApiLog.clear()

    fun isScreenScraperConfigured(settings: ScraperSettings): Boolean = credentialValidator.isScreenScraperConfigured(settings)

    init {
        // Show the persisted snapshot (last run / interrupted run) after restart
        viewModelScope.launch { batchScraper.restoreIfIdle() }
    }

    val platforms: StateFlow<List<PlatformEntity>> =
        platformRepository
            .observeEnabled()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Platforms with at least one ROM folder — the ones actually in the library. */
    val inUsePlatforms: StateFlow<List<PlatformEntity>> =
        combine(platformRepository.observeEnabled(), gameRepository.observeRomFolders()) { platformList, folderList ->
            val folderPlatformIds = folderList.map { it.platformId }.toSet()
            platformList.filter { it.id in folderPlatformIds }.sortedBy { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _gameResults = MutableStateFlow<List<GameEntity>>(emptyList())
    val gameResults: StateFlow<List<GameEntity>> = _gameResults

    private val _manual = MutableStateFlow(ManualMatchState())
    val manual: StateFlow<ManualMatchState> = _manual

    private val _sourceTests = MutableStateFlow<Map<String, SourceTestState>>(emptyMap())
    val sourceTests: StateFlow<Map<String, SourceTestState>> = _sourceTests

    private val _batchFeedback = MutableStateFlow<String?>(null)

    /** One-shot banner for preflight / already-running / enqueue feedback. */
    val batchFeedback: StateFlow<String?> = _batchFeedback

    fun dismissBatchFeedback() {
        _batchFeedback.value = null
    }

    // ---- settings updates ----

    fun update(transform: (ScraperSettings) -> ScraperSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun toggleSource(
        sourceId: String,
        enabled: Boolean,
    ) = update { s ->
        s.copy(
            enabledSources =
                if (enabled) {
                    (s.enabledSources + sourceId).distinct()
                } else {
                    s.enabledSources - sourceId
                },
        )
    }

    /** Moves a source one step up in the priority chain for a media type. */
    fun promoteMediaSource(
        mediaType: String,
        sourceId: String,
    ) = update { s ->
        val chain = (s.mediaPriority[mediaType] ?: return@update s).toMutableList()
        val index = chain.indexOf(sourceId)
        if (index > 0) {
            chain.removeAt(index)
            chain.add(index - 1, sourceId)
        }
        s.copy(mediaPriority = s.mediaPriority + (mediaType to chain))
    }

    // ---- credential tests ----

    fun testSourceCredentials(sourceId: String) {
        _sourceTests.value = _sourceTests.value + (sourceId to SourceTestState(loading = true))
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val result =
                when (sourceId) {
                    "screenscraper" -> {
                        credentialValidator.testScreenScraper(settings)
                    }

                    "steamgriddb" -> {
                        credentialValidator.testSteamGridDb(settings)
                    }

                    "romm" -> {
                        credentialValidator.testRomm(settings)
                    }

                    "ra" -> {
                        val profile = raRepository.login()
                        if (profile != null) {
                            CredentialTestResult.Success(
                                "Logged in as ${profile.user} (${profile.totalPoints} pts)",
                            )
                        } else {
                            CredentialTestResult.Failure("Login failed — check username/API key")
                        }
                    }

                    else -> {
                        CredentialTestResult.Failure("No test available for this source")
                    }
                }
            _sourceTests.value = _sourceTests.value + (
                sourceId to
                    SourceTestState(
                        loading = false,
                        message =
                            when (result) {
                                is CredentialTestResult.Success -> result.message
                                is CredentialTestResult.Failure -> result.message
                            },
                        success = result is CredentialTestResult.Success,
                    )
            )
        }
    }

    // ---- per-platform overrides ----

    /** Toggles a source for one platform; starts from the effective set. */
    fun togglePlatformSource(
        platformId: String,
        sourceId: String,
        enabled: Boolean,
    ) = update { s ->
        val current = s.platformOverrides[platformId]?.enabledSources ?: s.enabledSources
        val next = if (enabled) (current + sourceId).distinct() else current - sourceId
        val override =
            (s.platformOverrides[platformId] ?: PlatformScraperOverride())
                .copy(enabledSources = next)
        s.copy(platformOverrides = s.platformOverrides + (platformId to override))
    }

    fun setPlatformRegionPriority(
        platformId: String,
        regions: List<String>,
    ) = update { s ->
        val override =
            (s.platformOverrides[platformId] ?: PlatformScraperOverride())
                .copy(regionPriority = regions.ifEmpty { null })
        s.copy(
            platformOverrides =
                if (override.isEmpty) {
                    s.platformOverrides - platformId
                } else {
                    s.platformOverrides + (platformId to override)
                },
        )
    }

    fun clearPlatformOverride(platformId: String) =
        update { s ->
            s.copy(platformOverrides = s.platformOverrides - platformId)
        }

    private fun updatePlatformOverride(
        platformId: String,
        transform: (PlatformScraperOverride?) -> PlatformScraperOverride?,
    ) = update { s ->
        val next = transform(s.platformOverrides[platformId])
        val overrides =
            when {
                next == null || next.isEmpty -> s.platformOverrides - platformId
                else -> s.platformOverrides + (platformId to next)
            }
        s.copy(platformOverrides = overrides)
    }

    fun setPlatformStringOption(
        platformId: String,
        global: String,
        apply: (PlatformScraperOverride, String?) -> PlatformScraperOverride,
        value: String?,
    ) = updatePlatformOverride(platformId) { current ->
        val normalized = value?.takeIf { it != global }
        apply(current ?: PlatformScraperOverride(), normalized)
    }

    fun setPlatformBooleanOption(
        platformId: String,
        global: Boolean,
        apply: (PlatformScraperOverride, Boolean?) -> PlatformScraperOverride,
        value: Boolean?,
    ) = updatePlatformOverride(platformId) { current ->
        val normalized = value?.takeIf { it != global }
        apply(current ?: PlatformScraperOverride(), normalized)
    }

    fun setPlatformIntOption(
        platformId: String,
        global: Int,
        apply: (PlatformScraperOverride, Int?) -> PlatformScraperOverride,
        value: Int?,
    ) = updatePlatformOverride(platformId) { current ->
        val normalized = value?.takeIf { it != global }
        apply(current ?: PlatformScraperOverride(), normalized)
    }

    fun setPlatformGridStyles(
        platformId: String,
        styles: List<String>,
    ) = setPlatformStyleList(
        platformId,
        styles,
        global = { it.steamGridDbGridStyles },
        apply = { o, v -> o.copy(steamGridDbGridStyles = v) },
    )

    fun setPlatformHeroStyles(
        platformId: String,
        styles: List<String>,
    ) = setPlatformStyleList(
        platformId,
        styles,
        global = { it.steamGridDbHeroStyles },
        apply = { o, v -> o.copy(steamGridDbHeroStyles = v) },
    )

    fun setPlatformLogoStyles(
        platformId: String,
        styles: List<String>,
    ) = setPlatformStyleList(
        platformId,
        styles,
        global = { it.steamGridDbLogoStyles },
        apply = { o, v -> o.copy(steamGridDbLogoStyles = v) },
    )

    fun setPlatformIconStyles(
        platformId: String,
        styles: List<String>,
    ) = setPlatformStyleList(
        platformId,
        styles,
        global = { it.steamGridDbIconStyles },
        apply = { o, v -> o.copy(steamGridDbIconStyles = v) },
    )

    private fun setPlatformStyleList(
        platformId: String,
        styles: List<String>,
        global: (ScraperSettings) -> List<String>,
        apply: (PlatformScraperOverride, List<String>?) -> PlatformScraperOverride,
    ) = update { s ->
        val normalized = styles.takeIf { it != global(s) }
        val current = s.platformOverrides[platformId] ?: PlatformScraperOverride()
        val next = apply(current, normalized)
        val overrides =
            if (next.isEmpty) {
                s.platformOverrides - platformId
            } else {
                s.platformOverrides + (platformId to next)
            }
        s.copy(platformOverrides = overrides)
    }

    // ---- batch ----

    fun startBatch(
        platformId: String? = null,
        policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps,
    ) {
        viewModelScope.launch {
            _batchFeedback.value = null
            val reason = libraryActions.startScrape(platformId, policy.wireName())
            if (reason != null) {
                _batchFeedback.value = reason
            }
        }
    }

    /** Games that would be processed for Fill gaps / Force on a platform. */
    suspend fun estimateBatchCount(
        platformId: String?,
        policy: ScrapeRunPolicy,
    ): Int {
        val all = gameRepository.observeAll().first()
        val scoped = if (platformId == null) all else all.filter { it.platformId == platformId }
        return when (policy.mode) {
            com.wajiha.data.scraper.ScrapeRunMode.Force -> {
                scoped.size
            }

            com.wajiha.data.scraper.ScrapeRunMode.FillGaps -> {
                scoped.count { game ->
                    game.needsGapFill(gameRepository.media(game.id))
                }
            }
        }
    }

    suspend fun reviewQueueGames(
        platformId: String,
        includeScraped: Boolean,
    ): List<GameEntity> {
        val all = gameRepository.observeAll().first().filter { it.platformId == platformId }
        if (includeScraped) return all
        return all.filter { game -> game.needsGapFill(gameRepository.media(game.id)) }
    }

    fun retryFailedBatch(platformId: String? = null) {
        viewModelScope.launch {
            _batchFeedback.value = null
            val reason = libraryActions.retryFailedScrape(platformId)
            if (reason != null) {
                _batchFeedback.value = reason
            }
        }
    }

    fun canRetryFailed(platformId: String? = null): Boolean = batchScraper.hasRetryableIssues(platformId)

    fun cancelBatch() = libraryActions.cancelScrape()

    fun pauseBatch() = batchScraper.pause()

    fun resumeBatch() = batchScraper.resume()

    fun hasConfiguredSources(
        platformId: String? = null,
        sourceId: String? = null,
    ): Boolean {
        val s = settings.value
        val platformSettings = if (platformId != null) s.forPlatform(platformId) else s
        val effective =
            sourceId?.let { platformSettings.copy(enabledSources = listOf(it)) }
                ?: platformSettings
        return engine.hasConfiguredSources(effective)
    }

    // ---- manual match ----

    fun searchLibrary(query: String) {
        if (query.isBlank()) {
            _gameResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _gameResults.value = gameRepository.search(query).first()
        }
    }

    fun selectGame(game: GameEntity) {
        _manual.value = ManualMatchState(game = game)
        viewModelScope.launch {
            _manual.value = _manual.value.copy(media = gameRepository.media(game.id))
        }
    }

    fun clearSelection() {
        _manual.value = ManualMatchState()
    }

    fun searchSources(name: String) {
        val game = _manual.value.game ?: return
        _manual.value =
            _manual.value.copy(
                searching = true,
                candidates = emptyList(),
                message = null,
                messageSuccess = null,
            )
        viewModelScope.launch {
            try {
                val candidates = engine.searchAll(name, game, settingsRepository.current())
                _manual.value =
                    _manual.value.copy(
                        searching = false,
                        candidates = candidates,
                        message = if (candidates.isEmpty()) "No matches found" else null,
                        messageSuccess = if (candidates.isEmpty()) false else null,
                    )
            } catch (e: Exception) {
                _manual.value =
                    _manual.value.copy(
                        searching = false,
                        candidates = emptyList(),
                        message = e.message ?: "Search failed",
                        messageSuccess = false,
                    )
            }
        }
    }

    fun applyCandidate(candidate: ScrapeCandidate) {
        val game = _manual.value.game ?: return
        _manual.value = _manual.value.copy(applying = true, message = null, messageSuccess = null)
        viewModelScope.launch {
            try {
                val result = engine.applyManualMatch(game, candidate, settingsRepository.current())
                val refreshedGame = gameRepository.byId(game.id)
                val (msg, ok) = result.userMessage(verb = "Applied")
                _manual.value =
                    _manual.value.copy(
                        game = refreshedGame ?: game,
                        media = gameRepository.media(game.id),
                        applying = false,
                        message = msg,
                        messageSuccess = ok,
                    )
            } catch (e: Exception) {
                _manual.value =
                    _manual.value.copy(
                        applying = false,
                        message = e.message ?: "Apply failed",
                        messageSuccess = false,
                    )
            }
        }
    }

    fun deleteMedia(media: GameMediaEntity) {
        viewModelScope.launch {
            gameRepository.deleteMedia(media.id)
            _manual.value.game?.let {
                _manual.value = _manual.value.copy(media = gameRepository.media(it.id))
            }
        }
    }

    fun rescrapeSelected() {
        val game = _manual.value.game ?: return
        _manual.value = _manual.value.copy(applying = true, message = null, messageSuccess = null)
        viewModelScope.launch {
            try {
                val result = engine.scrapeGame(game, settingsRepository.current())
                val (msg, ok) = result.userMessage(verb = "Scraped")
                _manual.value =
                    _manual.value.copy(
                        game = gameRepository.byId(game.id) ?: game,
                        media = gameRepository.media(game.id),
                        applying = false,
                        message = msg,
                        messageSuccess = ok,
                    )
            } catch (e: Exception) {
                _manual.value =
                    _manual.value.copy(
                        applying = false,
                        message = e.message ?: "Scrape failed",
                        messageSuccess = false,
                    )
            }
        }
    }
}
