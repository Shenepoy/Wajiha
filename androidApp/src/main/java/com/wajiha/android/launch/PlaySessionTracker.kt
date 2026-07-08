package com.wajiha.android.launch

import com.wajiha.data.db.GameEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ActiveLaunch(
    val sessionId: Long,
    val gameId: Long?,
    val packageName: String,
    val startedAt: Long
)

/**
 * Tracks the currently running game session. Opened when Wajiha launches a
 * game (or the foreground monitor detects one), closed when the game process
 * exits — not when the launcher regains focus on dual-display Thor.
 */
class PlaySessionTracker(
    private val sessionRepository: SessionRepository,
    private val gameRepository: GameRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _active = MutableStateFlow<ActiveLaunch?>(null)
    val active: StateFlow<ActiveLaunch?> = _active

    fun onGameLaunched(game: GameEntity, packageName: String) {
        startSession(game.id, packageName, origin = "launcher")
    }

    /** Called by ForegroundAppMonitor when a manually launched game is detected. */
    fun onGameDetected(gameId: Long?, packageName: String) {
        if (_active.value?.packageName == packageName) return
        startSession(gameId, packageName, origin = "detected")
    }

    /** Called by ForegroundAppMonitor when the game process is confirmed gone. */
    fun onGameEnded(packageName: String) {
        if (_active.value?.packageName != packageName) return
        scope.launch { closeActiveInternal() }
    }

    /**
     * Call from MainActivity onResume when no game session is active.
     * Dual-display: bottom launcher stays foreground while gaming — do not close then.
     */
    fun onLauncherResumed(sessionActive: Boolean) {
        if (sessionActive) return
        if (_active.value == null) return
        scope.launch { closeActiveInternal() }
    }

    private fun startSession(gameId: Long?, packageName: String, origin: String) {
        val startedAt = System.currentTimeMillis()
        scope.launch {
            // Close any session left open (e.g. process death while gaming)
            closeActiveInternal()
            val sessionId = sessionRepository.startSession(gameId, packageName, startedAt, origin)
            _active.value = ActiveLaunch(sessionId, gameId, packageName, startedAt)
        }
    }

    private suspend fun closeActiveInternal() {
        val launch = _active.value ?: run {
            // Recover sessions orphaned by process death
            val orphan = sessionRepository.latestOpenSession() ?: return
            sessionRepository.closeSession(orphan.id, System.currentTimeMillis(), orphan.startedAt)
            return
        }
        val endedAt = System.currentTimeMillis()
        sessionRepository.closeSession(launch.sessionId, endedAt, launch.startedAt)
        launch.gameId?.let { gameRepository.recordPlay(it, endedAt) }
        _active.value = null
    }
}
