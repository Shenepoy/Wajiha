package com.wajiha.ui.ra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.ra.RaGameProgress
import com.wajiha.data.ra.RaRepository
import com.wajiha.data.ra.RaUserProfile
import com.wajiha.state.DualScreenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class RaUiState(
    val configured: Boolean = false,
    val profile: RaUserProfile? = null,
    val loading: Boolean = false,
    /** Progress for the game currently shown (now playing or selected). */
    val progress: RaGameProgress? = null,
    val message: String? = null,
)

class RaViewModel(
    private val raRepository: RaRepository,
    private val dualScreenStore: DualScreenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(RaUiState())
    val state: StateFlow<RaUiState> = _state

    /** While set, game detail owns the panel and the running session is ignored. */
    private var pinnedGameId: Long? = null

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(configured = raRepository.isConfigured())
        }
        dualScreenStore.nowPlaying
            .map { it?.gameId }
            .distinctUntilChanged()
            .onEach { gameId ->
                if (pinnedGameId != null) return@onEach
                showGame(gameId)
            }.launchIn(viewModelScope)
    }

    /**
     * Pin progress to [gameId], or pass null to follow the running session again.
     */
    fun pinGame(gameId: Long?) {
        pinnedGameId = gameId
        val target = gameId ?: dualScreenStore.nowPlaying.value?.gameId
        showGame(target)
    }

    private fun showGame(gameId: Long?) {
        if (gameId != null) {
            loadForGame(gameId)
        } else {
            _state.value = _state.value.copy(progress = null, loading = false)
        }
    }

    fun login() {
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val profile = raRepository.login()
            _state.value =
                _state.value.copy(
                    configured = raRepository.isConfigured(),
                    profile = profile,
                    loading = false,
                    message = if (profile == null) "Login failed — check username/API key" else null,
                )
        }
    }

    fun loadForGame(
        gameId: Long,
        forceRefresh: Boolean = false,
    ) {
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val progress = raRepository.progressForGame(gameId, forceRefresh)
            _state.value =
                _state.value.copy(
                    loading = false,
                    progress = progress,
                    message = if (progress == null) "No RetroAchievements data for this game" else null,
                )
        }
    }
}
