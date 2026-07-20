package com.wajiha.platform

import androidx.compose.ui.graphics.ImageBitmap

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null,
    /** Launcher activity class name for icon-pack appfilter lookup (not used for launch). */
    val activityName: String = "",
)

enum class UiSound { Navigate, Open, Back, Launch }

/** Host-side actions the shared UI needs (actual implementation on Android). */
interface AppActions {
    suspend fun installedApps(): List<LaunchableApp>

    fun launchApp(packageName: String)

    /** Launch on a specific display (0 = primary/top, secondary id = bottom). */
    fun launchAppOnDisplay(
        packageName: String,
        displayId: Int,
    )

    /** Returns null on success, otherwise a user-displayable error. */
    suspend fun launchGame(gameId: Long): String?

    /** Launch on a specific display (0 = primary/top, secondary id = bottom). */
    suspend fun launchGameOnDisplay(
        gameId: Long,
        displayId: Int,
    ): String?

    /** Remove from library DB only; ROM file is kept. Returns null on success. */
    suspend fun removeFromLibrary(gameId: Long): String?

    /** Delete ROM file and remove from library. Returns null on success. */
    suspend fun deleteGameFile(gameId: Long): String?

    fun playSound(sound: UiSound)

    fun killApp(packageName: String)

    fun moveAppToDisplay(
        packageName: String,
        displayId: Int,
    )

    /** Bring a running app's task to the foreground on the primary (top) display. */
    fun focusApp(packageName: String)
}

/** Library actions that need the host activity (SAF picker, WorkManager). */
interface LibraryActions {
    /** Opens the system folder picker for the given platform. */
    fun pickRomFolder(platformId: String)

    fun rescanLibrary()

    /** Rescan ROM folders for a single platform (null = all). */
    fun rescanPlatform(platformId: String)

    /**
     * Starts a batch scrape job (all platforms when null).
     * @param mode `fill_gaps`/`force`, optionally suffixed with `:<sourceId>`.
     * @return null if enqueued, or a short reason if blocked / already running.
     */
    suspend fun startScrape(
        platformId: String? = null,
        mode: String = "fill_gaps",
    ): String?

    /**
     * Retries Error + Partial games from the last batch (optionally one platform).
     * @return null if enqueued, or a short reason if blocked.
     */
    suspend fun retryFailedScrape(platformId: String? = null): String?

    fun cancelScrape()
}
