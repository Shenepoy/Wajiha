package com.wajiha.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.platform.LibraryActions
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingDisplayMode
import com.wajiha.state.SecondaryMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository,
    platformRepository: PlatformRepository,
    private val libraryActions: LibraryActions,
    private val dualScreenStore: DualScreenStore,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** False until DataStore has emitted; avoids flashing onboarding at start. */
    val settingsLoaded: StateFlow<Boolean> =
        settingsRepository.settings
            .map { true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val allPlatforms: StateFlow<List<PlatformEntity>> =
        platformRepository
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<RomFolderEntity>> =
        gameRepository
            .observeRomFolders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Platforms already in the library UI — those with at least one ROM folder.
     * Bundled packs leave most systems enabled; folders are the real "in use" signal.
     */
    val inUsePlatforms: StateFlow<List<PlatformEntity>> =
        kotlinx.coroutines.flow
            .combine(allPlatforms, folders) { platforms, folderList ->
                val folderPlatformIds = folderList.map { it.platformId }.toSet()
                platforms.filter { it.id in folderPlatformIds }.sortedBy { it.name }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Mirror persisted options into the dual-screen store
        settingsRepository.settings
            .onEach { s ->
                dualScreenStore.blackoutOnLaunch = s.blackoutOnLaunch
                dualScreenStore.preferredGameMode =
                    runCatching { SecondaryMode.valueOf(s.gameSecondaryMode) }
                        .getOrDefault(SecondaryMode.NowPlaying)
                dualScreenStore.nowPlayingDisplay = NowPlayingDisplayMode.fromName(s.nowPlayingDisplay)
                dualScreenStore.gameDimEnabled = s.gameDimEnabled
                dualScreenStore.gameDimOnlyOnNowPlaying = s.gameDimOnlyOnNowPlaying
                dualScreenStore.gameDimPercent = s.gameDimPercent
                dualScreenStore.gameplayDimTimeoutSeconds = s.gameplayDimTimeoutSeconds
            }.launchIn(viewModelScope)
    }

    fun setBlackoutOnLaunch(value: Boolean) {
        viewModelScope.launch { settingsRepository.setBlackoutOnLaunch(value) }
    }

    fun setDetectManualLaunches(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDetectManualLaunches(value) }
    }

    fun setMemoryGuardEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setMemoryGuardEnabled(value) }
    }

    fun setRomReconciliationEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setRomReconciliationEnabled(value) }
    }

    fun setRomReconciliationShowFilenameFallback(value: Boolean) {
        viewModelScope.launch { settingsRepository.setRomReconciliationShowFilenameFallback(value) }
    }

    fun setGameSecondaryMode(mode: String) {
        viewModelScope.launch { settingsRepository.setGameSecondaryMode(mode) }
    }

    fun setNowPlayingDisplay(mode: String) {
        viewModelScope.launch { settingsRepository.setNowPlayingDisplay(mode) }
    }

    fun setGameDimEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setGameDimEnabled(value) }
    }

    fun setGameDimOnlyOnNowPlaying(value: Boolean) {
        viewModelScope.launch { settingsRepository.setGameDimOnlyOnNowPlaying(value) }
    }

    fun setGameDimPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setGameDimPercent(percent) }
    }

    fun setGameplayDimTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setGameplayDimTimeoutSeconds(seconds) }
    }

    fun setSoundsEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundsEnabled(value) }
    }

    fun setGridRows(rows: Int) {
        viewModelScope.launch { settingsRepository.setGridRows(rows) }
    }

    fun setSwapScreenRoles(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSwapScreenRoles(value) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setFocusBorderStyle(style: String) {
        viewModelScope.launch { settingsRepository.setFocusBorderStyle(style) }
    }

    fun setFocusColor(color: String) {
        viewModelScope.launch { settingsRepository.setFocusColor(color) }
    }

    fun setFocusThickness(thickness: Int) {
        viewModelScope.launch { settingsRepository.setFocusThickness(thickness) }
    }

    fun setFocusPlacement(placement: String) {
        viewModelScope.launch { settingsRepository.setFocusPlacement(placement) }
    }

    fun setTopHeroBackdrop(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroBackdrop(value) }
    }

    fun setTopHeroCover(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroCover(value) }
    }

    fun setTopHeroCoverBorder(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroCoverBorder(value) }
    }

    fun setTopHeroLogo(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroLogo(value) }
    }

    fun setTopHeroPlatformIcon(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroPlatformIcon(value) }
    }

    fun setTopHeroPlatform(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroPlatform(value) }
    }

    fun setTopHeroTitle(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroTitle(value) }
    }

    fun setTopHeroMetadata(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroMetadata(value) }
    }

    fun setTopHeroDescription(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroDescription(value) }
    }

    fun setTopHeroPlayStats(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroPlayStats(value) }
    }

    fun setTopHeroFavorite(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroFavorite(value) }
    }

    fun setTopHeroSectionHint(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTopHeroSectionHint(value) }
    }

    fun setIgnorePatternFilesEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setIgnorePatternFilesEnabled(value) }
    }

    fun addIgnoreFileNamePattern(pattern: String) {
        viewModelScope.launch { settingsRepository.addIgnoreFileNamePattern(pattern) }
    }

    fun removeIgnoreFileNamePattern(pattern: String) {
        viewModelScope.launch { settingsRepository.removeIgnoreFileNamePattern(pattern) }
    }

    fun resetIgnoreFileNamePatterns() {
        viewModelScope.launch { settingsRepository.resetIgnoreFileNamePatterns() }
    }

    fun setOnboardingDone() {
        viewModelScope.launch { settingsRepository.setOnboardingDone(true) }
    }

    fun pickRomFolder(platformId: String) = libraryActions.pickRomFolder(platformId)

    fun removeFolder(folderId: Long) {
        viewModelScope.launch { gameRepository.removeRomFolder(folderId) }
    }

    fun rescanLibrary() = libraryActions.rescanLibrary()

    fun rescanPlatform(platformId: String) = libraryActions.rescanPlatform(platformId)

    /** Platforms that belong on the Settings Library list. */
    fun inUsePlatformIds(
        platforms: List<PlatformEntity>,
        folderList: List<RomFolderEntity>,
    ): Set<String> = folderList.map { it.platformId }.toSet()
}
