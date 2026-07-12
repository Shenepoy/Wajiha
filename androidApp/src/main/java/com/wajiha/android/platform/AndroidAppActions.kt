package com.wajiha.android.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.launch.GameLauncher
import com.wajiha.android.launch.LaunchResult
import com.wajiha.android.library.RomFileDeleter
import com.wajiha.android.monitor.ForegroundAppMonitor
import com.wajiha.android.monitor.SessionTaskRegistry
import com.wajiha.domain.repository.GameRepository
import com.wajiha.platform.AppActions
import com.wajiha.platform.LaunchableApp
import com.wajiha.platform.SoundAssets
import com.wajiha.platform.UiSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AndroidAppActions(
    private val context: Context,
    private val gameLauncher: GameLauncher,
    private val displayCoordinator: DisplayCoordinator,
    private val monitor: ForegroundAppMonitor,
    private val gameRepository: GameRepository,
    private val romFileDeleter: RomFileDeleter,
) : AppActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()

    private val soundIds = mutableMapOf<UiSound, Int>()

    var soundsEnabled: Boolean = true

    init {
        scope.launch { loadSounds() }
    }

    private suspend fun loadSounds() {
        val files =
            mapOf(
                UiSound.Navigate to "navigate.wav",
                UiSound.Open to "open.wav",
                UiSound.Back to "back.wav",
                UiSound.Launch to "launch.wav",
            )
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        for ((sound, name) in files) {
            try {
                val file = File(dir, name)
                if (!file.exists()) {
                    file.writeBytes(SoundAssets.read(name))
                }
                soundIds[sound] = soundPool.load(file.absolutePath, 1)
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun installedApps(): List<LaunchableApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm
                .queryIntentActivities(intent, 0)
                .asSequence()
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { info ->
                    val label = info.loadLabel(pm).toString()
                    val icon =
                        try {
                            info.loadIcon(pm).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    LaunchableApp(info.activityInfo.packageName, label, icon)
                }.sortedBy { it.label.lowercase() }
                .toList()
        }

    override fun launchApp(packageName: String) {
        launchAppOnDisplay(packageName, android.view.Display.DEFAULT_DISPLAY)
    }

    override fun launchAppOnDisplay(
        packageName: String,
        displayId: Int,
    ) {
        displayCoordinator.launchOnDisplay(packageName, displayId)
    }

    override suspend fun launchGame(gameId: Long): String? = launchResultToError(gameLauncher.launchGame(gameId))

    override suspend fun launchGameOnDisplay(
        gameId: Long,
        displayId: Int,
    ): String? = launchResultToError(gameLauncher.launchGame(gameId, displayId))

    override suspend fun removeFromLibrary(gameId: Long): String? =
        try {
            gameRepository.removeFromLibrary(gameId)
            null
        } catch (e: Exception) {
            e.message ?: "Could not remove game"
        }

    override suspend fun deleteGameFile(gameId: Long): String? {
        val game = gameRepository.byId(gameId) ?: return "Game not found"
        val fileError = romFileDeleter.delete(game.uri)
        if (fileError != null) return fileError
        return try {
            gameRepository.removeFromLibrary(gameId)
            null
        } catch (e: Exception) {
            e.message ?: "Could not remove from library"
        }
    }

    private fun launchResultToError(result: LaunchResult): String? =
        when (result) {
            is LaunchResult.Success -> {
                null
            }

            is LaunchResult.EmulatorNotInstalled -> {
                "Emulator not installed: ${result.packageName}"
            }

            is LaunchResult.ActivityNotFound -> {
                "Emulator activity missing in ${result.packageName}"
            }

            is LaunchResult.PermissionDenied -> {
                "Permission denied: ${result.message ?: "unknown"}"
            }

            is LaunchResult.Failed -> {
                "Launch failed: ${result.message ?: "unknown"}"
            }
        }

    override fun playSound(sound: UiSound) {
        if (!soundsEnabled) return
        soundIds[sound]?.let { soundPool.play(it, 0.6f, 0.6f, 1, 0, 1f) }
    }

    override fun killApp(packageName: String) = monitor.killApp(packageName)

    override fun moveAppToDisplay(
        packageName: String,
        displayId: Int,
    ) {
        displayCoordinator.launchOnDisplay(packageName, displayId)
    }

    override fun focusApp(packageName: String) {
        monitor.switchToSession(packageName)
        SessionTaskRegistry.moveToFront(context, packageName)
        displayCoordinator.focusGameOnPrimary(packageName)
        monitor.scheduleTopDisplayRefresh()
    }
}
