package com.wajiha.domain.repository

import com.wajiha.data.db.PlaySessionDao
import com.wajiha.data.db.PlaySessionEntity
import kotlinx.coroutines.flow.Flow

class SessionRepository(
    private val sessionDao: PlaySessionDao,
) {
    suspend fun startSession(
        gameId: Long?,
        packageName: String,
        startedAt: Long,
        origin: String = "launcher",
    ): Long =
        sessionDao.insert(
            PlaySessionEntity(
                gameId = gameId,
                packageName = packageName,
                startedAt = startedAt,
                origin = origin,
            ),
        )

    suspend fun closeSession(
        id: Long,
        endedAt: Long,
        startedAt: Long,
    ) {
        val duration = ((endedAt - startedAt) / 1000).coerceAtLeast(0)
        sessionDao.close(id, endedAt, duration)
    }

    suspend fun latestOpenSession(): PlaySessionEntity? = sessionDao.latestOpen()

    suspend fun latestOpenSessionForPackage(packageName: String): PlaySessionEntity? = sessionDao.latestOpenForPackage(packageName)

    suspend fun allOpenSessions(): List<PlaySessionEntity> = sessionDao.allOpen()

    suspend fun updateGameId(
        sessionId: Long,
        gameId: Long?,
    ) = sessionDao.updateGameId(sessionId, gameId)

    fun observeForGame(gameId: Long): Flow<List<PlaySessionEntity>> = sessionDao.observeForGame(gameId)

    fun observeTotalPlaytime(gameId: Long): Flow<Long> = sessionDao.observeTotalPlaytime(gameId)

    suspend fun totalPlaytime(gameId: Long): Long = sessionDao.totalPlaytime(gameId)
}
