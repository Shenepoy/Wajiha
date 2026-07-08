package com.wajiha.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.platform.AppActions
import com.wajiha.platform.LaunchableApp
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameTile(
    val game: GameEntity,
    val boxartPath: String? = null,
    val videoPath: String? = null,
    val heroPath: String? = null,
    val logoPath: String? = null,
    val iconPath: String? = null
)

data class HomeUiState(
    val platforms: List<PlatformEntity> = emptyList(),
    val selectedPlatformId: String? = null,
    val tiles: List<GameTile> = emptyList(),
    val recent: List<GameTile> = emptyList(),
    val favorites: List<GameTile> = emptyList(),
    val launchError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val appActions: AppActions,
    val dualScreenStore: DualScreenStore
) : ViewModel() {

    private val selectedPlatformId = MutableStateFlow<String?>(null)
    private val launchError = MutableStateFlow<String?>(null)

    /** Platforms that actually have games, in configured order. */
    private val platformsWithGames = combine(
        platformRepository.observeEnabled(),
        gameRepository.observeAll()
    ) { platforms, games ->
        val counts = games.groupingBy { it.platformId }.eachCount()
        platforms.filter { (counts[it.id] ?: 0) > 0 }
    }

    /** Art maps so scraped media refreshes the grid / hero live. */
    private val mediaByGame = combine(
        gameRepository.observeAllBoxart(),
        gameRepository.observeAllVideos(),
        gameRepository.observeAllHeroes(),
        gameRepository.observeAllLogos(),
        gameRepository.observeAllIcons()
    ) { boxart, videos, heroes, logos, icons ->
        GameMediaMaps(
            boxart = boxart.pathMap(),
            videos = videos.pathMap(),
            heroes = heroes.pathMap(),
            logos = logos.pathMap(),
            icons = icons.pathMap()
        )
    }

    private val gamesForSelected = selectedPlatformId.flatMapLatest { platformId ->
        if (platformId == null) gameRepository.observeAll()
        else gameRepository.observeForPlatform(platformId)
    }

    private val libraryState = combine(
        platformsWithGames,
        selectedPlatformId,
        gamesForSelected,
        gameRepository.observeRecent(12),
        mediaByGame
    ) { platforms, selected, games, recent, media ->
        HomeUiState(
            platforms = platforms,
            selectedPlatformId = selected,
            tiles = games.map { it.toTile(media) },
            recent = recent.map { it.toTile(media) }
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(libraryState, launchError) { state, error ->
        state.copy(launchError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps

    fun selectPlatform(platformId: String?) {
        appActions.playSound(UiSound.Navigate)
        selectedPlatformId.value = platformId
    }

    fun focusGame(gameId: Long?) {
        dualScreenStore.setFocusedGame(gameId)
    }

    fun launchGame(gameId: Long) {
        appActions.playSound(UiSound.Launch)
        viewModelScope.launch {
            launchError.value = appActions.launchGame(gameId)
        }
    }

    fun launchGameOnDisplay(gameId: Long, displayId: Int) {
        appActions.playSound(UiSound.Launch)
        viewModelScope.launch {
            launchError.value = appActions.launchGameOnDisplay(gameId, displayId)
        }
    }

    fun removeFromLibrary(gameId: Long) {
        viewModelScope.launch {
            launchError.value = appActions.removeFromLibrary(gameId)
        }
    }

    fun deleteGameFile(gameId: Long) {
        viewModelScope.launch {
            launchError.value = appActions.deleteGameFile(gameId)
        }
    }

    fun dismissLaunchError() {
        launchError.value = null
    }

    fun loadApps() {
        viewModelScope.launch { _apps.value = appActions.installedApps() }
    }

    fun launchApp(packageName: String) {
        appActions.playSound(UiSound.Open)
        appActions.launchApp(packageName)
    }

    fun toggleFavorite(gameId: Long, favorite: Boolean) {
        viewModelScope.launch { gameRepository.setFavorite(gameId, favorite) }
    }

    fun playNavigate() = appActions.playSound(UiSound.Navigate)
    fun playOpen() = appActions.playSound(UiSound.Open)
    fun playBack() = appActions.playSound(UiSound.Back)
}

private data class GameMediaMaps(
    val boxart: Map<Long, String>,
    val videos: Map<Long, String>,
    val heroes: Map<Long, String>,
    val logos: Map<Long, String>,
    val icons: Map<Long, String>
)

private fun List<GameMediaEntity>.pathMap(): Map<Long, String> =
    filter { it.localPath != null }.associate { it.gameId to it.localPath!! }

private fun GameEntity.toTile(media: GameMediaMaps): GameTile = GameTile(
    game = this,
    boxartPath = media.boxart[id],
    videoPath = media.videos[id],
    heroPath = media.heroes[id],
    logoPath = media.logos[id],
    iconPath = media.icons[id]
)
