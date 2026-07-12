package com.wajiha.android.launch

import com.wajiha.data.db.GameEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveLaunch(
    val sessionId: Long,
    val gameId: Long?,
    val packageName: String,
    val startedAt: Long
)

/**
 * Tracks running game sessions for playtime persistence. Supports multiple
 * concurrent sessions keyed by package — aligned with [DualScreenStore.sessionCache].
 */
class PlaySessionTracker(
    private val sessionRepository: SessionRepository,
    private val gameRepository: GameRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessions = MutableStateFlow<Map<String, ActiveLaunch>>(emptyMap())
    val activeSessions: StateFlow<Map<String, ActiveLaunch>> = _activeSessions.asStateFlow()

    fun isTracking(packageName: String): Boolean =
        packageName in _activeSessions.value

    fun onGameLaunched(game: GameEntity, packageName: String) {
        startSession(game.id, packageName, origin = "launcher")
    }

    fun onGameDetected(gameId: Long?, packageName: String) {
        if (isTracking(packageName)) return
        startSession(gameId, packageName, origin = "detected")
    }

    fun updateGameId(gameId: Long, packageName: String) {
        val launch = _activeSessions.value[packageName] ?: return
        if (launch.gameId != null) return
        scope.launch {
            sessionRepository.updateGameId(launch.sessionId, gameId)
            _activeSessions.value = _activeSessions.value.toMutableMap().apply {
                put(packageName, launch.copy(gameId = gameId))
            }
        }
    }

    fun onGameEnded(packageName: String) {
        if (!isTracking(packageName)) return
        scope.launch { closeSession(packageName) }
    }

    fun onLauncherResumed(sessionActive: Boolean) {
        if (sessionActive) return
        if (_activeSessions.value.isEmpty()) return
        scope.launch { closeAllSessions() }
    }

    private fun startSession(gameId: Long?, packageName: String, origin: String) {
        val startedAt = System.currentTimeMillis()
        scope.launch {
            val sessionId = sessionRepository.startSession(gameId, packageName, startedAt, origin)
            _activeSessions.value = _activeSessions.value.toMutableMap().apply {
                put(packageName, ActiveLaunch(sessionId, gameId, packageName, startedAt))
            }
        }
    }

    private suspend fun closeSession(packageName: String) {
        val launch = _activeSessions.value[packageName] ?: run {
            recoverOrphanForPackage(packageName)
            return
        }
        val endedAt = System.currentTimeMillis()
        sessionRepository.closeSession(launch.sessionId, endedAt, launch.startedAt)
        launch.gameId?.let { gameRepository.recordPlay(it, endedAt) }
        _activeSessions.value = _activeSessions.value.toMutableMap().apply {
            remove(packageName)
        }
    }

    private suspend fun closeAllSessions() {
        val packages = _activeSessions.value.keys.toList()
        packages.forEach { closeSession(it) }
        sessionRepository.allOpenSessions().forEach { orphan ->
            if (orphan.packageName !in _activeSessions.value) {
                sessionRepository.closeSession(
                    orphan.id,
                    System.currentTimeMillis(),
                    orphan.startedAt
                )
            }
        }
    }

    private suspend fun recoverOrphanForPackage(packageName: String) {
        val orphan = sessionRepository.latestOpenSessionForPackage(packageName) ?: return
        sessionRepository.closeSession(orphan.id, System.currentTimeMillis(), orphan.startedAt)
    }
}
