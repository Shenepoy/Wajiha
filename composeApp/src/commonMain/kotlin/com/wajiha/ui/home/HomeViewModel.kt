package com.wajiha.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.domain.repository.CollectionRepository
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.platform.AppActions
import com.wajiha.platform.LaunchableApp
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameTile(
    val game: GameEntity,
    val boxartPath: String? = null,
    val videoPath: String? = null,
    val heroPath: String? = null,
    val logoPath: String? = null,
    val iconPath: String? = null,
    val squarePath: String? = null,
)

data class HomeUiState(
    val platforms: List<PlatformEntity> = emptyList(),
    val selectedPlatformId: String? = null,
    val collections: List<CollectionSummary> = emptyList(),
    val selectedCollectionId: Long? = null,
    val browsingCollections: Boolean = false,
    val tiles: List<GameTile> = emptyList(),
    val recent: List<GameTile> = emptyList(),
    val favorites: List<GameTile> = emptyList(),
    val launchError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val appActions: AppActions,
    val dualScreenStore: DualScreenStore,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val selectedPlatformId = MutableStateFlow<String?>(null)
    private val selectedCollectionId = MutableStateFlow<Long?>(null)
    private val browsingCollections = MutableStateFlow(false)
    private val launchError = MutableStateFlow<String?>(null)
    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps
    private var lastLaunchAtMs = 0L

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.iconPackPackage to it.iconShape }
                .distinctUntilChanged()
                .collect { loadApps() }
        }
        viewModelScope.launch {
            // Install often emits ADDED and REPLACED together; one reload is enough.
            appActions.installedAppsChanges.debounce(300).collect { loadApps() }
        }
    }

    /** Ignore duplicate confirm/tap within the launch window (tap + A double-fire). */
    private fun tryBeginLaunch(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLaunchAtMs < LAUNCH_DEBOUNCE_MS) return false
        lastLaunchAtMs = now
        return true
    }

    /** Platforms that actually have games, in configured order. */
    private val platformsWithGames =
        combine(
            platformRepository.observeEnabled(),
            gameRepository.observeAll(),
        ) { platforms, games ->
            val counts = games.groupingBy { it.platformId }.eachCount()
            platforms.filter { (counts[it.id] ?: 0) > 0 }
        }

    /** Art maps so scraped media refreshes the grid / hero live. */
    private val mediaByGame =
        combine(
            combine(
                gameRepository.observeAllBoxart(),
                gameRepository.observeAllVideos(),
                gameRepository.observeAllHeroes(),
                gameRepository.observeAllLogos(),
                gameRepository.observeAllIcons(),
            ) { boxart, videos, heroes, logos, icons ->
                GameMediaMaps(
                    boxart = boxart.pathMap(),
                    videos = videos.pathMap(),
                    heroes = heroes.pathMap(),
                    logos = logos.pathMap(),
                    icons = icons.pathMap(),
                    squares = emptyMap(),
                )
            },
            gameRepository.observeAllSquares(),
        ) { base, squares ->
            base.copy(squares = squares.pathMap())
        }

    private val librarySelection =
        combine(selectedPlatformId, selectedCollectionId, browsingCollections) { platformId, collectionId, browsing ->
            LibrarySelection(
                platformId = platformId,
                collectionId = collectionId,
                browsingCollections = browsing,
            )
        }

    private val gamesForSelected =
        librarySelection.flatMapLatest { selection ->
            when (selection.gameSource()) {
                LibraryGameSource.All -> gameRepository.observeAll()
                LibraryGameSource.Platform -> gameRepository.observeForPlatform(selection.platformId!!)
                LibraryGameSource.Collection -> collectionRepository.observeGames(selection.collectionId!!)
                LibraryGameSource.None -> flowOf(emptyList())
            }
        }

    private val collectionSummaries =
        combine(collectionRepository.observeAll(), collectionRepository.observeCounts()) { collections, counts ->
            val byId = counts.associate { it.collectionId to it.gameCount.toInt() }
            collections.map { collection ->
                CollectionSummary(
                    id = collection.id,
                    name = collection.name,
                    gameCount = byId[collection.id] ?: 0,
                )
            }
        }

    private val libraryState =
        combine(
            platformsWithGames,
            librarySelection,
            gamesForSelected,
            gameRepository.observeRecent(12),
            mediaByGame,
        ) { platforms, selection, games, recent, media ->
            HomeUiState(
                platforms = platforms,
                selectedPlatformId = selection.platformId,
                selectedCollectionId = selection.collectionId,
                browsingCollections = selection.browsingCollections,
                tiles = games.map { it.toTile(media) },
                recent = recent.map { it.toTile(media) },
            )
        }

    val uiState: StateFlow<HomeUiState> =
        combine(libraryState, collectionSummaries, launchError) { state, collections, error ->
            state.copy(collections = collections, launchError = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectPlatform(platformId: String?) {
        appActions.playSound(UiSound.Navigate)
        applySelection(
            if (platformId == null) {
                LibrarySelection().selectAll()
            } else {
                LibrarySelection().selectPlatform(platformId)
            },
        )
    }

    fun openCollections() {
        appActions.playSound(UiSound.Navigate)
        applySelection(LibrarySelection().openCollections())
    }

    fun selectCollection(collectionId: Long) {
        appActions.playSound(UiSound.Navigate)
        applySelection(LibrarySelection().selectCollection(collectionId))
    }

    fun observeMembership(gameId: Long) = collectionRepository.observeCollectionIds(gameId).map { it.toSet() }

    fun setGameInCollection(
        collectionId: Long,
        gameId: Long,
        member: Boolean,
    ) {
        viewModelScope.launch {
            if (member) {
                collectionRepository.addGame(collectionId, gameId)
            } else {
                collectionRepository.removeGame(collectionId, gameId)
            }
        }
    }

    fun createCollection(
        name: String,
        addGameId: Long? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = collectionRepository.create(trimmed, uiState.value.collections.size)
            if (addGameId != null) {
                collectionRepository.addGame(id, addGameId)
            }
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            collectionRepository.delete(collectionId)
            if (selectedCollectionId.value == collectionId) {
                applySelection(LibrarySelection().openCollections())
            }
        }
    }

    private fun applySelection(selection: LibrarySelection) {
        selectedPlatformId.value = selection.platformId
        selectedCollectionId.value = selection.collectionId
        browsingCollections.value = selection.browsingCollections
    }

    fun focusGame(gameId: Long?) {
        dualScreenStore.setFocusedGame(gameId)
    }

    fun launchGame(gameId: Long) {
        if (!tryBeginLaunch()) return
        appActions.playSound(UiSound.Launch)
        viewModelScope.launch {
            launchError.value = appActions.launchGame(gameId)
        }
    }

    fun launchGameOnDisplay(
        gameId: Long,
        displayId: Int,
    ) {
        if (!tryBeginLaunch()) return
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
        if (!tryBeginLaunch()) return
        appActions.playSound(UiSound.Open)
        appActions.launchApp(packageName)
    }

    /**
     * Home dock pins open on the bottom/secondary display when dual-screen is
     * active; single-screen falls back to the primary display.
     */
    fun launchDockApp(packageName: String) {
        if (!tryBeginLaunch()) return
        appActions.playSound(UiSound.Open)
        val secondaryId = dualScreenStore.secondaryDisplayId.value
        if (secondaryId != null && !dualScreenStore.forceSingleScreen) {
            appActions.launchAppOnDisplay(packageName, secondaryId)
        } else {
            appActions.launchApp(packageName)
        }
    }

    fun launchAppOnDisplay(
        packageName: String,
        displayId: Int,
    ) {
        if (!tryBeginLaunch()) return
        appActions.playSound(UiSound.Launch)
        appActions.launchAppOnDisplay(packageName, displayId)
    }

    fun toggleFavorite(
        gameId: Long,
        favorite: Boolean,
    ) {
        viewModelScope.launch { gameRepository.setFavorite(gameId, favorite) }
    }

    fun playNavigate() = appActions.playSound(UiSound.Navigate)

    fun playOpen() = appActions.playSound(UiSound.Open)

    fun playBack() = appActions.playSound(UiSound.Back)

    private companion object {
        private const val LAUNCH_DEBOUNCE_MS = 800L
    }
}

private data class GameMediaMaps(
    val boxart: Map<Long, String>,
    val videos: Map<Long, String>,
    val heroes: Map<Long, String>,
    val logos: Map<Long, String>,
    val icons: Map<Long, String>,
    val squares: Map<Long, String>,
)

private fun List<GameMediaEntity>.pathMap(): Map<Long, String> = filter { it.localPath != null }.associate { it.gameId to it.localPath!! }

private fun GameEntity.toTile(media: GameMediaMaps): GameTile =
    GameTile(
        game = this,
        boxartPath = media.boxart[id],
        videoPath = media.videos[id],
        heroPath = media.heroes[id],
        logoPath = media.logos[id],
        iconPath = media.icons[id],
        squarePath = media.squares[id],
    )
