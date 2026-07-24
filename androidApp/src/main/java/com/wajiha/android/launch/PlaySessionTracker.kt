package com.wajiha.android.launch

import com.wajiha.data.db.GameEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.SessionRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveLaunch(
    val sessionId: Long,
    val gameId: Long?,
    val packageName: String,
    val startedAt: Long,
    /** Active play ms before the current segment. */
    val accumulatedActiveMs: Long = 0L,
    /** Wall time when the current active segment started (0 = paused). */
    val segmentStartedAt: Long = 0L,
)

/**
 * Tracks running game sessions for playtime persistence. Supports multiple
 * concurrent sessions keyed by package — aligned with [DualScreenStore.sessionCache].
 * Duration pauses while the session is not the top-display foreground.
 */
class PlaySessionTracker(
    private val sessionRepository: SessionRepository,
    private val gameRepository: GameRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessions = MutableStateFlow<Map<String, ActiveLaunch>>(emptyMap())
    val activeSessions: StateFlow<Map<String, ActiveLaunch>> = _activeSessions.asStateFlow()

    fun isTracking(packageName: String): Boolean = packageName in _activeSessions.value

    fun onGameLaunched(
        game: GameEntity,
        packageName: String,
    ) {
        startSession(game.id, packageName, origin = "launcher")
    }

    fun onGameDetected(
        gameId: Long?,
        packageName: String,
    ) {
        if (isTracking(packageName)) return
        startSession(gameId, packageName, origin = "detected")
    }

    fun updateGameId(
        gameId: Long,
        packageName: String,
    ) {
        val launch = _activeSessions.value[packageName] ?: return
        if (launch.gameId != null) return
        scope.launch {
            sessionRepository.updateGameId(launch.sessionId, gameId)
            _activeSessions.value =
                _activeSessions.value.toMutableMap().apply {
                    put(packageName, launch.copy(gameId = gameId))
                }
        }
    }

    /**
     * Align DB clocks with top-display foreground: only [topPackage] accumulates;
     * every other tracked session pauses.
     */
    fun syncTopDisplayForeground(topPackage: String?) {
        val now = System.currentTimeMillis()
        val next = _activeSessions.value.toMutableMap()
        var changed = false
        next.keys.toList().forEach { pkg ->
            val launch = next[pkg] ?: return@forEach
            val updated =
                if (pkg == topPackage) {
                    resumeLaunch(launch, now)
                } else {
                    pauseLaunch(launch, now)
                }
            if (updated != launch) {
                next[pkg] = updated
                changed = true
            }
        }
        if (changed) {
            _activeSessions.value = next
            WajihaLog.d(
                WajihaTags.NOW_PLAYING,
                "playtimeSync: top=${topPackage ?: "none"} tracked=${next.size}",
            )
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

    private fun startSession(
        gameId: Long?,
        packageName: String,
        origin: String,
    ) {
        val startedAt = System.currentTimeMillis()
        scope.launch {
            val sessionId = sessionRepository.startSession(gameId, packageName, startedAt, origin)
            _activeSessions.value =
                _activeSessions.value.toMutableMap().apply {
                    put(
                        packageName,
                        ActiveLaunch(
                            sessionId = sessionId,
                            gameId = gameId,
                            packageName = packageName,
                            startedAt = startedAt,
                            accumulatedActiveMs = 0L,
                            segmentStartedAt = startedAt,
                        ),
                    )
                }
        }
    }

    private fun pauseLaunch(
        launch: ActiveLaunch,
        now: Long,
    ): ActiveLaunch {
        if (launch.segmentStartedAt <= 0L) return launch
        val segment = (now - launch.segmentStartedAt).coerceAtLeast(0L)
        return launch.copy(
            accumulatedActiveMs = launch.accumulatedActiveMs + segment,
            segmentStartedAt = 0L,
        )
    }

    private fun resumeLaunch(
        launch: ActiveLaunch,
        now: Long,
    ): ActiveLaunch {
        if (launch.segmentStartedAt > 0L) return launch
        return launch.copy(segmentStartedAt = now)
    }

    private fun activeDurationMs(
        launch: ActiveLaunch,
        now: Long,
    ): Long {
        val open =
            if (launch.segmentStartedAt > 0L) {
                (now - launch.segmentStartedAt).coerceAtLeast(0L)
            } else {
                0L
            }
        return launch.accumulatedActiveMs + open
    }

    private suspend fun closeSession(packageName: String) {
        val launch =
            _activeSessions.value[packageName] ?: run {
                recoverOrphanForPackage(packageName)
                return
            }
        val endedAt = System.currentTimeMillis()
        val activeMs = activeDurationMs(launch, endedAt)
        sessionRepository.closeSessionWithActiveMs(launch.sessionId, endedAt, activeMs)
        launch.gameId?.let { gameRepository.recordPlay(it, endedAt) }
        _activeSessions.value =
            _activeSessions.value.toMutableMap().apply {
                remove(packageName)
            }
    }

    private suspend fun closeAllSessions() {
        val packages = _activeSessions.value.keys.toList()
        packages.forEach { closeSession(it) }
        sessionRepository.allOpenSessions().forEach { orphan ->
            if (orphan.packageName !in _activeSessions.value) {
                // Orphans have no pause tracking — fall back to wall clock.
                sessionRepository.closeSession(
                    orphan.id,
                    System.currentTimeMillis(),
                    orphan.startedAt,
                )
            }
        }
    }

    private suspend fun recoverOrphanForPackage(packageName: String) {
        val orphan = sessionRepository.latestOpenSessionForPackage(packageName) ?: return
        sessionRepository.closeSession(orphan.id, System.currentTimeMillis(), orphan.startedAt)
    }
}
