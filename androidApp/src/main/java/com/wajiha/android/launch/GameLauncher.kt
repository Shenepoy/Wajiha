package com.wajiha.android.launch

import android.content.Context
import android.view.Display
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.monitor.ForegroundAppMonitor
import com.wajiha.android.service.KeepAliveService
import com.wajiha.data.WajihaJson
import com.wajiha.data.config.AmStartArgumentsParser
import com.wajiha.data.config.IntentExtra
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.GameEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    private val dualScreenStore: DualScreenStore,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val displayCoordinator: DisplayCoordinator,
) {
    private val json = WajihaJson.Default

    suspend fun launchGame(gameId: Long): LaunchResult {
        val game =
            gameRepository.byId(gameId)
                ?: return LaunchResult.Failed("Game $gameId not found").also {
                    WajihaLog.w(WajihaLogKind.LAUNCH, "launchGame: failed gameId=$gameId reason=not found")
                }
        val emulator =
            platformRepository.resolveEmulator(game.platformId, game.emulatorOverrideId)
                ?: return LaunchResult.Failed("No emulator configured for ${game.platformId}").also {
                    WajihaLog.w(
                        WajihaLogKind.LAUNCH,
                        "launchGame: failed gameId=$gameId reason=no emulator platform=${game.platformId}",
                    )
                }
        return launchGame(game, emulator, game.launchOnDisplay)
    }

    suspend fun launchGame(
        gameId: Long,
        displayId: Int,
    ): LaunchResult {
        val game =
            gameRepository.byId(gameId)
                ?: return LaunchResult.Failed("Game $gameId not found").also {
                    WajihaLog.w(WajihaLogKind.LAUNCH, "launchGame: failed gameId=$gameId reason=not found")
                }
        val emulator =
            platformRepository.resolveEmulator(game.platformId, game.emulatorOverrideId)
                ?: return LaunchResult.Failed("No emulator configured for ${game.platformId}").also {
                    WajihaLog.w(
                        WajihaLogKind.LAUNCH,
                        "launchGame: failed gameId=$gameId reason=no emulator platform=${game.platformId}",
                    )
                }
        return launchGame(game, emulator, displayId)
    }

    suspend fun launchGame(
        game: GameEntity,
        emulator: EmulatorEntity,
    ): LaunchResult = launchGame(game, emulator, game.launchOnDisplay)

    suspend fun launchGame(
        game: GameEntity,
        emulator: EmulatorEntity,
        displayId: Int?,
    ): LaunchResult {
        val packageName =
            pickInstalledPackage(emulator)
                ?: return LaunchResult.EmulatorNotInstalled(
                    emulator.packageNames.substringBefore(','),
                )
        val resolvedDisplay = resolveLaunchDisplay(displayId)
        val spec = buildSpec(game, emulator, packageName, resolvedDisplay)
        // Session must exist before startActivity — SecondaryHomeActivity's
        // onUserLeaveHint/onPause fire synchronously and used to reclaim the
        // bottom task before hasActiveSessions(), starving the top-display launch.
        val now = System.currentTimeMillis()
        val media = gameRepository.media(game.id)
        val session =
            NowPlayingState(
                packageName = packageName,
                gameId = game.id,
                gameName = game.displayName,
                platformId = game.platformId,
                boxartPath = media.firstOrNull { it.type == "boxart" }?.localPath,
                heroPath = media.firstOrNull { it.type == "hero" }?.localPath,
                logoPath = media.firstOrNull { it.type == "logo" }?.localPath,
                iconPath = media.firstOrNull { it.type == "icon" }?.localPath,
                squarePath = media.firstOrNull { it.type == "square" }?.localPath,
                sessionStartedAt = now,
                sessionResumedAt = now,
                launchedByWajiha = true,
            )
        // Keep GameGrid on a live Overlay through startActivity (bitmap freeze
        // alone flashed black when dismissed onto a paused Activity). Preferred
        // mode (Now Playing) is applied on the Overlay after launch, then we
        // hand Overlay → Activity once both trees already show the same UI.
        dualScreenStore.beginGameSession(session, deferSecondaryUi = true)
        suspendCancellableCoroutine { cont ->
            displayCoordinator.armSecondaryLiveCoverForLaunch {
                if (cont.isActive) cont.resume(Unit)
            }
        }
        WajihaLog.i(
            WajihaLogKind.LAUNCH,
            "launchGame: start pkg=$packageName displayId=$resolvedDisplay " +
                "gameId=${game.id} name=${game.displayName} emu=${emulator.id}",
        )
        val result = EmulatorLauncher.launch(context, spec)
        if (result is LaunchResult.Success) {
            sessionTracker.onGameLaunched(game, packageName)
            if (resolvedDisplay == Display.DEFAULT_DISPLAY) {
                dualScreenStore.setTopDisplayForeground(packageName)
            }
            foregroundAppMonitor.onSessionStarted(packageName)
            displayCoordinator.focusGameOnPrimary(packageName)
            KeepAliveService.start(context)
            // Mode apply is posted off this frame. Switching the bottom UI in
            // the same turn as startActivity flashes the panel black.
            displayCoordinator.finishLaunchCoverAfterSecondaryUi()
            WajihaLog.i(WajihaLogKind.LAUNCH, "launchGame: ok pkg=$packageName gameId=${game.id}")
        } else {
            dualScreenStore.endGameSession(packageName)
            displayCoordinator.finishLaunchCoverAfterSecondaryUi()
            WajihaLog.w(
                WajihaLogKind.LAUNCH,
                "launchGame: failed pkg=$packageName gameId=${game.id} result=$result",
            )
        }
        return result
    }

    /** Thor dual-display: games belong on the top panel unless overridden per game. */
    private fun resolveLaunchDisplay(displayId: Int?): Int? =
        displayId ?: if (dualScreenStore.state.value != DualScreenState.SingleDisplay) {
            Display.DEFAULT_DISPLAY
        } else {
            null
        }

    private fun pickInstalledPackage(emulator: EmulatorEntity): String? =
        emulator.packageNames
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { EmulatorLauncher.isPackageInstalled(context, it) }

    internal fun buildSpec(
        game: GameEntity,
        emulator: EmulatorEntity,
        packageName: String,
        launchDisplayId: Int? = null,
    ): LaunchSpec {
        // Prefer raw am-start arguments when present (Daijishō/iiSU imports);
        // fall back to structured fields.
        val parsed =
            emulator.amStartArguments
                ?.let { AmStartArgumentsParser.parse(it.replace("%PACKAGE%", packageName)) }

        val activity = parsed?.activityName ?: emulator.activityName
        val action = parsed?.action ?: emulator.action
        val extras: List<IntentExtra> =
            parsed?.extras
                ?: emulator.extrasJson?.let { json.decodeFromString<List<IntentExtra>>(it) }
                ?: emptyList()
        val flags: List<String> =
            parsed?.activityFlags
                ?: emulator.activityFlagsJson?.let { json.decodeFromString<List<String>>(it) }
                ?: emptyList()
        val rawData =
            parsed?.dataUri
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
            launchDisplayId = launchDisplayId,
        )
    }

    private fun substitute(
        value: String,
        game: GameEntity,
    ): String {
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
