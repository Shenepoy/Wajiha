package com.wajiha.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val blackoutOnLaunch: Boolean = false,
    val detectManualLaunches: Boolean = true,
    /** SecondaryMode name shown while a game runs */
    val gameSecondaryMode: String = "NowPlaying",
    val gridRows: Int = 2,
    val soundsEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    val swapScreenRoles: Boolean = false,
    val theme: String = "dark"
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            blackoutOnLaunch = prefs[BLACKOUT_ON_LAUNCH] ?: false,
            detectManualLaunches = prefs[DETECT_MANUAL] ?: true,
            gameSecondaryMode = prefs[GAME_SECONDARY_MODE] ?: "NowPlaying",
            gridRows = prefs[GRID_ROWS] ?: 2,
            soundsEnabled = prefs[SOUNDS_ENABLED] ?: true,
            onboardingDone = prefs[ONBOARDING_DONE] ?: false,
            swapScreenRoles = prefs[SWAP_SCREEN_ROLES] ?: false,
            theme = prefs[THEME] ?: "dark"
        )
    }

    suspend fun setBlackoutOnLaunch(value: Boolean) =
        dataStore.edit { it[BLACKOUT_ON_LAUNCH] = value }

    suspend fun setDetectManualLaunches(value: Boolean) =
        dataStore.edit { it[DETECT_MANUAL] = value }

    suspend fun setGameSecondaryMode(value: String) =
        dataStore.edit { it[GAME_SECONDARY_MODE] = value }

    suspend fun setGridRows(value: Int) = dataStore.edit { it[GRID_ROWS] = value }

    suspend fun setSoundsEnabled(value: Boolean) = dataStore.edit { it[SOUNDS_ENABLED] = value }

    suspend fun setOnboardingDone(value: Boolean) = dataStore.edit { it[ONBOARDING_DONE] = value }

    suspend fun setSwapScreenRoles(value: Boolean) =
        dataStore.edit { it[SWAP_SCREEN_ROLES] = value }

    suspend fun toggleSwapScreenRoles(): Boolean {
        var next = false
        dataStore.edit { prefs ->
            next = !(prefs[SWAP_SCREEN_ROLES] ?: false)
            prefs[SWAP_SCREEN_ROLES] = next
        }
        return next
    }

    suspend fun setTheme(value: String) = dataStore.edit { it[THEME] = value }

    private companion object {
        val BLACKOUT_ON_LAUNCH = booleanPreferencesKey("blackout_on_launch")
        val DETECT_MANUAL = booleanPreferencesKey("detect_manual_launches")
        val GAME_SECONDARY_MODE = stringPreferencesKey("game_secondary_mode")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SWAP_SCREEN_ROLES = booleanPreferencesKey("swap_screen_roles")
        val THEME = stringPreferencesKey("theme")
    }
}
