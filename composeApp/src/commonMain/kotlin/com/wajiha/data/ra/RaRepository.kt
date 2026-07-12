package com.wajiha.data.ra

import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.domain.repository.GameRepository

/**
 * RetroAchievements feature layer: credential check, hash → game linking,
 * and per-game achievement progress (with a small in-memory cache).
 */
class RaRepository(
    private val client: RaClient,
    private val gameRepository: GameRepository,
    private val settingsRepository: ScraperSettingsRepository,
) {
    private val progressCache = mutableMapOf<Long, RaGameProgress>()

    private suspend fun credentials(): Pair<String, String>? {
        val settings = settingsRepository.current()
        if (settings.raUsername.isBlank() || settings.raApiKey.isBlank()) return null
        return settings.raUsername to settings.raApiKey
    }

    suspend fun isConfigured(): Boolean = credentials() != null

    /** Validates the stored credentials; returns the profile or null. */
    suspend fun login(): RaUserProfile? {
        val (user, key) = credentials() ?: return null
        return client.profile(user, key)
    }

    /**
     * Ensures the game has an RA link (via its md5 hash). Returns the RA game
     * id or null when unlinked/unlinkable.
     */
    suspend fun linkGame(gameId: Long): Long? {
        val game = gameRepository.byId(gameId) ?: return null
        game.raGameId?.let { return it }
        val md5 = game.md5 ?: return null
        val (user, key) = credentials() ?: return null
        val raId = client.gameIdForHash(user, key, md5) ?: return null
        gameRepository.update(game.copy(raGameId = raId))
        return raId
    }

    /** Achievements + user progress for a library game (hash-links first). */
    suspend fun progressForGame(
        gameId: Long,
        forceRefresh: Boolean = false,
    ): RaGameProgress? {
        val raId = linkGame(gameId) ?: return null
        if (!forceRefresh) progressCache[raId]?.let { return it }
        val (user, key) = credentials() ?: return null
        val progress = client.gameProgress(user, key, raId) ?: return null
        progressCache[raId] = progress
        return progress
    }
}
