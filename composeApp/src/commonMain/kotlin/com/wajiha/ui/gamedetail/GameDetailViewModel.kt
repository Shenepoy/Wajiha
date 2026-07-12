package com.wajiha.ui.gamedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeEngine
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.domain.repository.SessionRepository
import com.wajiha.platform.AppActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameDetailUiState(
    val game: GameEntity? = null,
    val platform: PlatformEntity? = null,
    val emulators: List<EmulatorEntity> = emptyList(),
    val media: List<GameMediaEntity> = emptyList(),
    val totalPlaytimeSec: Long = 0,
    val scraperSources: List<String> = emptyList(),
    val scraping: Boolean = false,
    val scrapeMessage: String? = null,
    /** null = neutral, true = success, false = error/partial */
    val scrapeMessageSuccess: Boolean? = null,
    val actionError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModel(
    private val gameRepository: GameRepository,
    private val sessionRepository: SessionRepository,
    private val platformRepository: PlatformRepository,
    private val scrapeEngine: ScrapeEngine,
    private val scraperSettings: ScraperSettingsRepository,
    private val appActions: AppActions
) : ViewModel() {

    private val gameId = MutableStateFlow<Long?>(null)
    private val scraping = MutableStateFlow(false)
    private val scrapeMessage = MutableStateFlow<String?>(null)
    private val scrapeMessageSuccess = MutableStateFlow<Boolean?>(null)
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GameDetailUiState> = gameId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(GameDetailUiState())
            } else {
                gameRepository.observeById(id).flatMapLatest { game ->
                    if (game == null) {
                        flowOf(GameDetailUiState())
                    } else {
                        val core = combine(
                            flowOf(game),
                            platformRepository.observeById(game.platformId),
                            platformRepository.observeEmulators(game.platformId),
                            gameRepository.observeMedia(id),
                            sessionRepository.observeTotalPlaytime(id)
                        ) { g, platform, emulators, media, playtime ->
                            CoreDetail(g, platform, emulators, media, playtime)
                        }
                        val scrapeUi = combine(
                            scraping,
                            scrapeMessage,
                            scrapeMessageSuccess,
                            actionError
                        ) { isScraping, message, messageOk, error ->
                            ScrapeUi(isScraping, message, messageOk, error)
                        }
                        combine(
                            core,
                            scraperSettings.settings,
                            scrapeUi
                        ) { base, scraperCfg, scrape ->
                            val sources = scrapeEngine.configuredSources(
                                scraperCfg.forPlatform(base.game.platformId)
                            ).map { it.id }
                            GameDetailUiState(
                                game = base.game,
                                platform = base.platform,
                                emulators = base.emulators,
                                media = base.media,
                                totalPlaytimeSec = base.playtime,
                                scraperSources = sources,
                                scraping = scrape.scraping,
                                scrapeMessage = scrape.message,
                                scrapeMessageSuccess = scrape.messageSuccess,
                                actionError = scrape.error
                            )
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GameDetailUiState())

    fun open(id: Long) {
        gameId.value = id
        scrapeMessage.value = null
        scrapeMessageSuccess.value = null
        actionError.value = null
    }

    fun setFavorite(favorite: Boolean) {
        val id = gameId.value ?: return
        viewModelScope.launch { gameRepository.setFavorite(id, favorite) }
    }

    fun setEmulatorOverride(emulatorId: String?) {
        val id = gameId.value ?: return
        viewModelScope.launch { gameRepository.setEmulatorOverride(id, emulatorId) }
    }

    fun setLaunchOnDisplay(displayId: Int?) {
        val id = gameId.value ?: return
        viewModelScope.launch { gameRepository.setLaunchOnDisplay(id, displayId) }
    }

    fun rescrape(
        sourceId: String? = null,
        policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps
    ) {
        val game = uiState.value.game ?: return
        viewModelScope.launch {
            scraping.value = true
            scrapeMessage.value = null
            scrapeMessageSuccess.value = null
            actionError.value = null
            try {
                val settings = scraperSettings.current().forPlatform(game.platformId)
                val result = if (sourceId == null) {
                    scrapeEngine.scrapeGame(game, settings, policy)
                } else {
                    val candidates = scrapeEngine.searchAll(game.displayName, game, settings)
                    val pick = candidates.firstOrNull { it.sourceId == sourceId }
                    if (pick != null) {
                        scrapeEngine.applyManualMatch(game, pick, settings)
                    } else {
                        com.wajiha.data.scraper.GameScrapeResult(
                            gameId = game.id,
                            outcome = com.wajiha.data.scraper.GameScrapeOutcome.NoMatch,
                            failureKind = com.wajiha.data.scraper.ScrapeFailureKind.NoMatch,
                            message = "No match from $sourceId"
                        )
                    }
                }
                val (msg, ok) = result.userMessage(verb = "Scraped", sourceId = sourceId)
                scrapeMessage.value = msg
                scrapeMessageSuccess.value = ok
            } catch (e: Exception) {
                scrapeMessage.value = e.message ?: "Scrape failed"
                scrapeMessageSuccess.value = false
            } finally {
                scraping.value = false
            }
        }
    }

    fun scrapeMediaOnly(mediaType: MediaType, sourceId: String) {
        val game = uiState.value.game ?: return
        viewModelScope.launch {
            scraping.value = true
            scrapeMessage.value = null
            scrapeMessageSuccess.value = null
            try {
                val settings = scraperSettings.current().forPlatform(game.platformId)
                val candidates = scrapeEngine.searchAll(game.displayName, game, settings)
                val pick = candidates.firstOrNull { it.sourceId == sourceId }
                if (pick == null) {
                    scrapeMessage.value = "No match from $sourceId"
                    scrapeMessageSuccess.value = false
                    return@launch
                }
                val media = pick.media.firstOrNull { it.type == mediaType }
                if (media == null) {
                    scrapeMessage.value = "No ${mediaType.dbName} from $sourceId"
                    scrapeMessageSuccess.value = false
                    return@launch
                }
                val path = scrapeEngine.download(game.id, media, settings)
                if (path == null) {
                    scrapeMessage.value = "Download failed"
                    scrapeMessageSuccess.value = false
                    return@launch
                }
                gameRepository.saveMedia(
                    GameMediaEntity(
                        gameId = game.id,
                        type = mediaType.dbName,
                        source = sourceId,
                        localPath = path,
                        remoteUrl = media.url
                    )
                )
                scrapeMessage.value = "Saved ${mediaType.dbName} from $sourceId"
                scrapeMessageSuccess.value = true
            } catch (e: Exception) {
                scrapeMessage.value = e.message ?: "Scrape failed"
                scrapeMessageSuccess.value = false
            } finally {
                scraping.value = false
            }
        }
    }

    fun deleteMedia(type: MediaType) {
        val game = uiState.value.game ?: return
        viewModelScope.launch {
            val existing = gameRepository.mediaOfType(game.id, type.dbName) ?: return@launch
            gameRepository.deleteMedia(existing.id)
            scrapeMessage.value = "Removed ${type.dbName}"
            scrapeMessageSuccess.value = true
        }
    }

    fun launchOnDisplay(displayId: Int) {
        val id = gameId.value ?: return
        viewModelScope.launch {
            actionError.value = appActions.launchGameOnDisplay(id, displayId)
        }
    }

    fun dismissActionError() {
        actionError.value = null
    }

    fun clearScrapeMessage() {
        scrapeMessage.value = null
        scrapeMessageSuccess.value = null
    }
}

private data class CoreDetail(
    val game: GameEntity,
    val platform: PlatformEntity?,
    val emulators: List<EmulatorEntity>,
    val media: List<GameMediaEntity>,
    val playtime: Long
)

private data class ScrapeUi(
    val scraping: Boolean,
    val message: String?,
    val messageSuccess: Boolean?,
    val error: String?
)
