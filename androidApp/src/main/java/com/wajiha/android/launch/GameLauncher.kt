package com.wajiha.android.launch

import android.content.Context
import com.wajiha.android.service.KeepAliveService
import com.wajiha.data.config.AmStartArgumentsParser
import com.wajiha.data.config.IntentExtra
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.GameEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import kotlinx.serialization.json.Json

/**
 * Resolves a game to a [LaunchSpec] (per-game emulator override → platform
 * default → first configured) and launches it. Placeholders in configs are
 * substituted after parsing:
 *
 * - `{file.path}`, `%ROM%`, `%ROM_PATH%`, `%ROMRAW%` → `wajiha-realpath:` marker
 * - `{file.uri}`, `%ROM_URI%`, `%ROM_CONTENT%` → `wajiha-localuri:` marker
 */
class GameLauncher(
    private val context: Context,
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository,
    private val sessionTracker: PlaySessionTracker,
    private val dualScreenStore: DualScreenStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun launchGame(gameId: Long): LaunchResult {
        val game = gameRepository.byId(gameId)
            ?: return LaunchResult.Failed("Game $gameId not found")
        val emulator = platformRepository.resolveEmulator(game.platformId, game.emulatorOverrideId)
            ?: return LaunchResult.Failed("No emulator configured for ${game.platformId}")
        return launchGame(game, emulator, game.launchOnDisplay)
    }

    suspend fun launchGame(
        gameId: Long,
        displayId: Int
    ): LaunchResult {
        val game = gameRepository.byId(gameId)
            ?: return LaunchResult.Failed("Game $gameId not found")
        val emulator = platformRepository.resolveEmulator(game.platformId, game.emulatorOverrideId)
            ?: return LaunchResult.Failed("No emulator configured for ${game.platformId}")
        return launchGame(game, emulator, displayId)
    }

    suspend fun launchGame(game: GameEntity, emulator: EmulatorEntity): LaunchResult =
        launchGame(game, emulator, game.launchOnDisplay)

    suspend fun launchGame(
        game: GameEntity,
        emulator: EmulatorEntity,
        displayId: Int?
    ): LaunchResult {
        val packageName = pickInstalledPackage(emulator)
            ?: return LaunchResult.EmulatorNotInstalled(
                emulator.packageNames.substringBefore(',')
            )
        val spec = buildSpec(game, emulator, packageName, displayId)
        val result = EmulatorLauncher.launch(context, spec)
        if (result is LaunchResult.Success) {
            sessionTracker.onGameLaunched(game, packageName)
            dualScreenStore.onGameStarted(
                NowPlayingState(
                    packageName = packageName,
                    gameId = game.id,
                    gameName = game.displayName,
                    platformId = game.platformId,
                    sessionStartedAt = System.currentTimeMillis(),
                    launchedByWajiha = true
                )
            )
            KeepAliveService.start(context)
        }
        return result
    }

    private fun pickInstalledPackage(emulator: EmulatorEntity): String? =
        emulator.packageNames.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { EmulatorLauncher.isPackageInstalled(context, it) }

    internal fun buildSpec(
        game: GameEntity,
        emulator: EmulatorEntity,
        packageName: String,
        launchDisplayId: Int? = null
    ): LaunchSpec {
        // Prefer raw am-start arguments when present (Daijishō/iiSU imports);
        // fall back to structured fields.
        val parsed = emulator.amStartArguments
            ?.let { AmStartArgumentsParser.parse(it.replace("%PACKAGE%", packageName)) }

        val activity = parsed?.activityName ?: emulator.activityName
        val action = parsed?.action ?: emulator.action
        val extras: List<IntentExtra> = parsed?.extras
            ?: emulator.extrasJson?.let { json.decodeFromString<List<IntentExtra>>(it) }
            ?: emptyList()
        val flags: List<String> = parsed?.activityFlags
            ?: emulator.activityFlagsJson?.let { json.decodeFromString<List<String>>(it) }
            ?: emptyList()
        val rawData = parsed?.dataUri
            // Structured configs with routeType uri and no explicit data get the ROM as data
            ?: if (parsed == null && emulator.routeType == "uri") "{file.uri}" else null

        return LaunchSpec(
            packageName = packageName,
            activityName = activity,
            action = action,
            data = rawData?.let { substitute(it, game) },
            mimeType = parsed?.mimeType,
            extras = extras.map { LaunchExtra(it.key, substitute(it.value, game), it.type) },
            activityFlags = flags,
            keepSafUri = emulator.keepSafUri,
            killBeforeLaunch = emulator.killBeforeLaunch,
            launchDisplayId = launchDisplayId
        )
    }

    private fun substitute(value: String, game: GameEntity): String {
        var result = value
        for (token in pathTokens) {
            result = result.replace(token, "wajiha-realpath:${game.uri}")
        }
        for (token in uriTokens) {
            result = result.replace(token, "wajiha-localuri:${game.uri}")
        }
        return result
    }

    private companion object {
        val pathTokens = listOf("{file.path}", "%ROM%", "%ROM_PATH%", "%ROMRAW%")
        val uriTokens = listOf("{file.uri}", "%ROM_URI%", "%ROM_CONTENT%")
    }
}
