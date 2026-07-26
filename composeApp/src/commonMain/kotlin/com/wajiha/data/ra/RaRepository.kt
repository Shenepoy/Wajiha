package com.wajiha.data.ra

import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.log.logClockMs

/**
 * RetroAchievements feature layer: credential check, hash/title → game linking,
 * and per-game achievement progress (with a short in-memory TTL).
 */
class RaRepository(
    private val client: RaClient,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val settingsRepository: ScraperSettingsRepository,
) {
    private val progressCache = mutableMapOf<Long, CachedProgress>()

    private data class Creds(
        val username: String,
        val apiKey: String,
        val userId: String,
    )

    private data class CachedProgress(
        val progress: RaGameProgress,
        val fetchedAtMs: Long,
    )

    private suspend fun credentials(): Creds? {
        val settings = settingsRepository.current()
        if (settings.raUsername.isBlank() || settings.raApiKey.isBlank()) return null
        val userId = settings.raUlid.ifBlank { settings.raUsername }
        return Creds(
            username = settings.raUsername,
            apiKey = settings.raApiKey,
            userId = userId,
        )
    }

    suspend fun isConfigured(): Boolean = credentials() != null

    /** Validates the stored credentials; returns the profile or null. */
    suspend fun login(): RaUserProfile? {
        val (user, key, _) = credentials() ?: return null
        val profile = client.profile(user, key) ?: return null
        if (profile.ulid.isNotBlank() || profile.user.isNotBlank()) {
            settingsRepository.update { current ->
                current.copy(
                    raUsername = profile.user.ifBlank { current.raUsername },
                    raUlid = profile.ulid.ifBlank { current.raUlid },
                )
            }
        }
        return profile
    }

    /**
     * Ensures the game has an RA link (hash library, then title fallback).
     * Returns the RA game id or null when unlinked/unlinkable.
     */
    suspend fun linkGame(gameId: Long): Long? {
        val game = gameRepository.byId(gameId) ?: return null
        game.raGameId?.let {
            WajihaLog.i(WajihaLogKind.NETWORK, "ra link skip gameId=$gameId already raGameId=$it")
            return it
        }
        val consoleId =
            platformRepository.byId(game.platformId)?.raConsoleId
                ?: run {
                    WajihaLog.w(
                        WajihaLogKind.NETWORK,
                        "ra link fail gameId=$gameId platform=${game.platformId} no raConsoleId",
                    )
                    return null
                }
        val creds = credentials() ?: return null
        val resolved =
            client.resolveGameId(
                username = creds.username,
                apiKey = creds.apiKey,
                md5 = game.md5,
                displayName = game.displayName.ifBlank { game.fileName },
                consoleId = consoleId,
            )
        val hit =
            when (resolved) {
                is ResolveRaGameId.Hit -> {
                    resolved
                }

                is ResolveRaGameId.Miss -> {
                    WajihaLog.i(
                        WajihaLogKind.NETWORK,
                        "ra link miss gameId=$gameId console=$consoleId " +
                            "md5=${game.md5?.take(8) ?: "-"} name=${game.displayName} " +
                            "reason=${resolved.reason}",
                    )
                    return null
                }
            }
        gameRepository.update(game.copy(raGameId = hit.raGameId))
        WajihaLog.i(
            WajihaLogKind.NETWORK,
            "ra link ok gameId=$gameId → raGameId=${hit.raGameId} via=${hit.via}",
        )
        return hit.raGameId
    }

    /** Achievements + user progress for a library game (hash-links first). */
    suspend fun progressForGame(
        gameId: Long,
        forceRefresh: Boolean = false,
    ): RaGameProgress? {
        val raId = linkGame(gameId) ?: return null
        val now = logClockMs()
        if (!forceRefresh) {
            progressCache[raId]?.let { cached ->
                if (now - cached.fetchedAtMs < PROGRESS_TTL_MS) {
                    return cached.progress
                }
            }
        }
        val creds = credentials() ?: return null
        val progress =
            client.gameProgress(
                username = creds.username,
                apiKey = creds.apiKey,
                userId = creds.userId,
                raGameId = raId,
            ) ?: return null
        progressCache[raId] = CachedProgress(progress, logClockMs())
        return progress
    }

    private companion object {
        /** Our policy — progress changes while playing; keep short. */
        const val PROGRESS_TTL_MS = 12L * 60 * 1000
    }
}
