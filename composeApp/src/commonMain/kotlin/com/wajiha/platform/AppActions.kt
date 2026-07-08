package com.wajiha.platform

import androidx.compose.ui.graphics.ImageBitmap

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null
)

enum class UiSound { Navigate, Open, Back, Launch }

/** Host-side actions the shared UI needs (actual implementation on Android). */
interface AppActions {
    suspend fun installedApps(): List<LaunchableApp>
    fun launchApp(packageName: String)

    /** Returns null on success, otherwise a user-displayable error. */
    suspend fun launchGame(gameId: Long): String?

    fun playSound(sound: UiSound)

    fun killApp(packageName: String)
    fun moveAppToDisplay(packageName: String, displayId: Int)
}

/** Library actions that need the host activity (SAF picker, WorkManager). */
interface LibraryActions {
    /** Opens the system folder picker for the given platform. */
    fun pickRomFolder(platformId: String)

    fun rescanLibrary()

    /** Rescan ROM folders for a single platform (null = all). */
    fun rescanPlatform(platformId: String)

    /** Starts a batch scrape job (all platforms when null). */
    fun startScrape(platformId: String? = null)

    fun cancelScrape()
}
