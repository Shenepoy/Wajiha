package com.wajiha.ui.scraper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.scraper.BatchScrapeProgress
import com.wajiha.data.scraper.BatchScraper
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.PlatformScraperOverride
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeEngine
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.platform.LibraryActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** State for the manual-match flow of one selected game. */
data class ManualMatchState(
    val game: GameEntity? = null,
    val media: List<GameMediaEntity> = emptyList(),
    val searching: Boolean = false,
    val candidates: List<ScrapeCandidate> = emptyList(),
    val applying: Boolean = false,
    val message: String? = null
)

class ScraperViewModel(
    private val settingsRepository: ScraperSettingsRepository,
    private val engine: ScrapeEngine,
    private val batchScraper: BatchScraper,
    private val gameRepository: GameRepository,
    platformRepository: PlatformRepository,
    private val libraryActions: LibraryActions
) : ViewModel() {

    val settings: StateFlow<ScraperSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScraperSettings())

    val progress: StateFlow<BatchScrapeProgress> = batchScraper.progress

    init {
        // Show the persisted snapshot (last run / interrupted run) after restart
        viewModelScope.launch { batchScraper.restoreIfIdle() }
    }

    val platforms: StateFlow<List<PlatformEntity>> = platformRepository.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _gameResults = MutableStateFlow<List<GameEntity>>(emptyList())
    val gameResults: StateFlow<List<GameEntity>> = _gameResults

    private val _manual = MutableStateFlow(ManualMatchState())
    val manual: StateFlow<ManualMatchState> = _manual

    // ---- settings updates ----

    fun update(transform: (ScraperSettings) -> ScraperSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun toggleSource(sourceId: String, enabled: Boolean) = update { s ->
        s.copy(
            enabledSources = if (enabled) {
                (s.enabledSources + sourceId).distinct()
            } else {
                s.enabledSources - sourceId
            }
        )
    }

    /** Moves a source one step up in the priority chain for a media type. */
    fun promoteMediaSource(mediaType: String, sourceId: String) = update { s ->
        val chain = (s.mediaPriority[mediaType] ?: return@update s).toMutableList()
        val index = chain.indexOf(sourceId)
        if (index > 0) {
            chain.removeAt(index)
            chain.add(index - 1, sourceId)
        }
        s.copy(mediaPriority = s.mediaPriority + (mediaType to chain))
    }

    // ---- per-platform overrides ----

    /** Toggles a source for one platform; starts from the effective set. */
    fun togglePlatformSource(platformId: String, sourceId: String, enabled: Boolean) = update { s ->
        val current = s.platformOverrides[platformId]?.enabledSources ?: s.enabledSources
        val next = if (enabled) (current + sourceId).distinct() else current - sourceId
        val override = (s.platformOverrides[platformId] ?: PlatformScraperOverride())
            .copy(enabledSources = next)
        s.copy(platformOverrides = s.platformOverrides + (platformId to override))
    }

    fun setPlatformRegionPriority(platformId: String, regions: List<String>) = update { s ->
        val override = (s.platformOverrides[platformId] ?: PlatformScraperOverride())
            .copy(regionPriority = regions.ifEmpty { null })
        s.copy(
            platformOverrides = if (override.isEmpty) {
                s.platformOverrides - platformId
            } else {
                s.platformOverrides + (platformId to override)
            }
        )
    }

    fun clearPlatformOverride(platformId: String) = update { s ->
        s.copy(platformOverrides = s.platformOverrides - platformId)
    }

    // ---- batch ----

    fun startBatch(platformId: String? = null) = libraryActions.startScrape(platformId)
    fun cancelBatch() = libraryActions.cancelScrape()
    fun pauseBatch() = batchScraper.pause()
    fun resumeBatch() = batchScraper.resume()

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
        _manual.value = _manual.value.copy(searching = true, candidates = emptyList(), message = null)
        viewModelScope.launch {
            val candidates = engine.searchAll(name, game, settingsRepository.current())
            _manual.value = _manual.value.copy(
                searching = false,
                candidates = candidates,
                message = if (candidates.isEmpty()) "No matches found" else null
            )
        }
    }

    fun applyCandidate(candidate: ScrapeCandidate) {
        val game = _manual.value.game ?: return
        _manual.value = _manual.value.copy(applying = true, message = null)
        viewModelScope.launch {
            val result = engine.applyManualMatch(game, candidate, settingsRepository.current())
            val refreshedGame = gameRepository.byId(game.id)
            _manual.value = _manual.value.copy(
                game = refreshedGame ?: game,
                media = gameRepository.media(game.id),
                applying = false,
                message = "Applied: ${result.mediaSaved} media saved"
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun replaceMedia(mediaType: String, candidate: MediaCandidate) {
        val game = _manual.value.game ?: return
        viewModelScope.launch {
            val path = engine.download(game.id, candidate, settingsRepository.current()) ?: run {
                _manual.value = _manual.value.copy(message = "Download failed")
                return@launch
            }
            gameRepository.saveMedia(
                GameMediaEntity(
                    gameId = game.id,
                    type = mediaType,
                    source = "manual",
                    localPath = path,
                    remoteUrl = candidate.url,
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
            )
            _manual.value = _manual.value.copy(media = gameRepository.media(game.id))
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
        _manual.value = _manual.value.copy(applying = true, message = null)
        viewModelScope.launch {
            val result = engine.scrapeGame(game, settingsRepository.current())
            _manual.value = _manual.value.copy(
                game = gameRepository.byId(game.id) ?: game,
                media = gameRepository.media(game.id),
                applying = false,
                message = if (result.matched) {
                    "Scraped from ${result.metadataSource ?: "sources"}: ${result.mediaSaved} media"
                } else {
                    "No match found"
                }
            )
        }
    }
}
