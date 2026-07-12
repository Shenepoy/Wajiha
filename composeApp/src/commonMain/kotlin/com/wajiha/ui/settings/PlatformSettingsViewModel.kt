package com.wajiha.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.platform.LibraryActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlatformSettingsUiState(
    val platform: PlatformEntity? = null,
    val emulators: List<EmulatorEntity> = emptyList(),
    val folders: List<RomFolderEntity> = emptyList(),
    val gameCount: Int = 0,
)

/**
 * Backing store for a single platform's settings page (Cocoon / RetroHrai style:
 * name, default player, ROM folders, scraper id fields, enable toggle).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSettingsViewModel(
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository,
    private val libraryActions: LibraryActions,
) : ViewModel() {
    private val platformId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlatformSettingsUiState> =
        platformId
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf(PlatformSettingsUiState())
                } else {
                    combine(
                        platformRepository.observeById(id),
                        platformRepository.observeEmulators(id),
                        gameRepository.observeRomFolders().map { list ->
                            list.filter { it.platformId == id }
                        },
                        gameRepository.observeCountForPlatform(id),
                    ) { platform, emulators, folders, gameCount ->
                        PlatformSettingsUiState(
                            platform = platform,
                            emulators = emulators,
                            folders = folders,
                            gameCount = gameCount,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlatformSettingsUiState())

    fun open(id: String) {
        platformId.value = id
    }

    fun setDisplayName(name: String) {
        val platform = uiState.value.platform ?: return
        viewModelScope.launch {
            platformRepository.update(platform.copy(name = name.trim().ifBlank { platform.name }))
        }
    }

    fun setShortName(shortName: String) {
        val platform = uiState.value.platform ?: return
        viewModelScope.launch {
            platformRepository.update(
                platform.copy(shortName = shortName.trim().ifBlank { platform.shortName }),
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        val id = platformId.value ?: return
        viewModelScope.launch { platformRepository.setEnabled(id, enabled) }
    }

    fun setDefaultEmulator(emulatorId: String?) {
        val id = platformId.value ?: return
        viewModelScope.launch {
            platformRepository.setDefaultEmulator(id, emulatorId)
            // Keep EmulatorEntity.isDefault consistent with the platform default
            val emulators = platformRepository.emulatorsFor(id)
            if (emulators.isNotEmpty()) {
                platformRepository.upsertEmulators(
                    emulators.map { it.copy(isDefault = it.id == emulatorId) },
                )
            }
        }
    }

    fun setScreenScraperId(value: String) {
        val platform = uiState.value.platform ?: return
        viewModelScope.launch {
            platformRepository.update(
                platform.copy(screenScraperId = value.trim().toIntOrNull()),
            )
        }
    }

    fun setRaConsoleId(value: String) {
        val platform = uiState.value.platform ?: return
        viewModelScope.launch {
            platformRepository.update(
                platform.copy(raConsoleId = value.trim().toIntOrNull()),
            )
        }
    }

    fun setLibretroName(value: String) {
        val platform = uiState.value.platform ?: return
        viewModelScope.launch {
            platformRepository.update(
                platform.copy(libretroName = value.trim().ifBlank { null }),
            )
        }
    }

    fun pickRomFolder() {
        platformId.value?.let(libraryActions::pickRomFolder)
    }

    fun removeFolder(folderId: Long) {
        viewModelScope.launch { gameRepository.removeRomFolder(folderId) }
    }

    fun rescanPlatform() {
        platformId.value?.let(libraryActions::rescanPlatform)
    }
}
