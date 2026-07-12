package com.wajiha.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import com.wajiha.state.NowPlayingDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val blackoutOnLaunch: Boolean = false,
    val detectManualLaunches: Boolean = true,
    /**
     * Force-stop runaway emulator sessions when RSS balloons or system memory
     * is critically low (protects the launcher from LMK death spirals).
     */
    val memoryGuardEnabled: Boolean = true,
    /** Tier 3: read emulator data files to identify externally launched games. */
    val romReconciliationEnabled: Boolean = false,
    /** Show cleaned filename when ROM is not in the library (Tier 3). */
    val romReconciliationShowFilenameFallback: Boolean = true,
    /** SecondaryMode name shown while a game runs */
    val gameSecondaryMode: String = "NowPlaying",
    /** How active sessions appear on the bottom screen grid and/or floating chip. */
    val nowPlayingDisplay: String = NowPlayingDisplayMode.Both.name,
    /** Overlay dim on the bottom screen while a game runs on the top display. */
    val gameDimEnabled: Boolean = false,
    /** When true, dim applies only on the Now Playing secondary screen; when false, any mode except Off. */
    val gameDimOnlyOnNowPlaying: Boolean = true,
    /** Dim strength in percent (0–100, stepped by 10). */
    val gameDimPercent: Int = 90,
    /** Seconds before dim applies after gameplay starts, and before re-dimming after idle (0 = immediate / stay lifted). */
    val gameplayDimTimeoutSeconds: Int = 10,
    val gridRows: Int = 2,
    val soundsEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    val swapScreenRoles: Boolean = false,
    val theme: String = "dark",
    /** FocusBorderStyle name (Solid, Dotted, …, Neon). */
    val focusBorderStyle: String = "Solid",
    /** Focus ring color preset (theme, white, yellow, cyan, red, …) or custom #RRGGBB hex. */
    val focusColor: String = "theme",
    /** Focus ring width in dp (1, 2, or 3). */
    val focusThickness: Int = 2,
    /** Focus ring placement: Inside (inset on bounds) or Outside (outset beyond bounds). */
    val focusPlacement: String = "Inside",
    // Top-screen game hero / Info preview elements (all on by default except section hint / cover border)
    val topHeroBackdrop: Boolean = true,
    val topHeroCover: Boolean = true,
    /** Subtle outline around top-screen cover / box art (off = borderless). */
    val topHeroCoverBorder: Boolean = false,
    val topHeroLogo: Boolean = true,
    val topHeroPlatformIcon: Boolean = true,
    val topHeroPlatform: Boolean = true,
    val topHeroTitle: Boolean = true,
    val topHeroMetadata: Boolean = true,
    val topHeroDescription: Boolean = true,
    val topHeroPlayStats: Boolean = true,
    val topHeroFavorite: Boolean = true,
    val topHeroSectionHint: Boolean = false,
    /** When true, library scan skips ROMs whose filename or path matches [ignoreFileNamePatterns]. */
    val ignorePatternFilesEnabled: Boolean = false,
    /** Case-insensitive substrings matched against ROM filename and content URI during scan. */
    val ignoreFileNamePatterns: List<String> = SettingsRepository.DEFAULT_IGNORE_FILE_NAME_PATTERNS
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            blackoutOnLaunch = prefs[BLACKOUT_ON_LAUNCH] ?: false,
            detectManualLaunches = prefs[DETECT_MANUAL] ?: true,
            memoryGuardEnabled = prefs[MEMORY_GUARD_ENABLED] ?: true,
            romReconciliationEnabled = prefs[ROM_RECONCILIATION_ENABLED] ?: false,
            romReconciliationShowFilenameFallback =
                prefs[ROM_RECONCILIATION_FILENAME_FALLBACK] ?: true,
            gameSecondaryMode = prefs[GAME_SECONDARY_MODE] ?: "NowPlaying",
            nowPlayingDisplay = normalizeNowPlayingDisplay(prefs[NOW_PLAYING_DISPLAY]),
            gameDimEnabled = prefs[GAME_DIM_ENABLED] ?: false,
            gameDimOnlyOnNowPlaying = prefs[GAME_DIM_ONLY_ON_NOW_PLAYING] ?: true,
            gameDimPercent = normalizeGameDimPercent(prefs[GAME_DIM_PERCENT]),
            gameplayDimTimeoutSeconds = readGameplayDimTimeoutSeconds(prefs),
            gridRows = prefs[GRID_ROWS] ?: 2,
            soundsEnabled = prefs[SOUNDS_ENABLED] ?: true,
            onboardingDone = prefs[ONBOARDING_DONE] ?: false,
            swapScreenRoles = prefs[SWAP_SCREEN_ROLES] ?: false,
            theme = prefs[THEME] ?: "dark",
            focusBorderStyle = normalizeFocusBorderStyle(prefs[FOCUS_BORDER_STYLE]),
            focusColor = normalizeFocusColor(prefs[FOCUS_COLOR]),
            focusThickness = normalizeFocusThickness(prefs[FOCUS_THICKNESS]),
            focusPlacement = normalizeFocusPlacement(prefs[FOCUS_PLACEMENT]),
            topHeroBackdrop = prefs[TOP_HERO_BACKDROP] ?: true,
            topHeroCover = prefs[TOP_HERO_COVER] ?: true,
            topHeroCoverBorder = prefs[TOP_HERO_COVER_BORDER] ?: false,
            topHeroLogo = prefs[TOP_HERO_LOGO] ?: true,
            topHeroPlatformIcon = prefs[TOP_HERO_PLATFORM_ICON] ?: true,
            topHeroPlatform = prefs[TOP_HERO_PLATFORM] ?: true,
            topHeroTitle = prefs[TOP_HERO_TITLE] ?: true,
            topHeroMetadata = prefs[TOP_HERO_METADATA] ?: true,
            topHeroDescription = prefs[TOP_HERO_DESCRIPTION] ?: true,
            topHeroPlayStats = prefs[TOP_HERO_PLAY_STATS] ?: true,
            topHeroFavorite = prefs[TOP_HERO_FAVORITE] ?: true,
            topHeroSectionHint = prefs[TOP_HERO_SECTION_HINT] ?: false,
            ignorePatternFilesEnabled = prefs[IGNORE_PATTERN_FILES_ENABLED] ?: false,
            ignoreFileNamePatterns = parseIgnoreFileNamePatterns(prefs[IGNORE_FILE_NAME_PATTERNS])
        )
    }

    suspend fun setBlackoutOnLaunch(value: Boolean) =
        dataStore.edit { it[BLACKOUT_ON_LAUNCH] = value }

    suspend fun setDetectManualLaunches(value: Boolean) =
        dataStore.edit { it[DETECT_MANUAL] = value }

    suspend fun setMemoryGuardEnabled(value: Boolean) =
        dataStore.edit { it[MEMORY_GUARD_ENABLED] = value }

    suspend fun setRomReconciliationEnabled(value: Boolean) =
        dataStore.edit { it[ROM_RECONCILIATION_ENABLED] = value }

    suspend fun setRomReconciliationShowFilenameFallback(value: Boolean) =
        dataStore.edit { it[ROM_RECONCILIATION_FILENAME_FALLBACK] = value }

    suspend fun setGameSecondaryMode(value: String) =
        dataStore.edit { it[GAME_SECONDARY_MODE] = value }

    suspend fun setNowPlayingDisplay(value: String) =
        dataStore.edit { it[NOW_PLAYING_DISPLAY] = normalizeNowPlayingDisplay(value) }

    suspend fun setGameDimEnabled(value: Boolean) =
        dataStore.edit { it[GAME_DIM_ENABLED] = value }

    suspend fun setGameDimOnlyOnNowPlaying(value: Boolean) =
        dataStore.edit { it[GAME_DIM_ONLY_ON_NOW_PLAYING] = value }

    suspend fun setGameDimPercent(value: Int) =
        dataStore.edit { it[GAME_DIM_PERCENT] = normalizeGameDimPercent(value) }

    suspend fun setGameplayDimTimeoutSeconds(value: Int) =
        dataStore.edit { it[GAMEPLAY_DIM_TIMEOUT_SECONDS] = normalizeGameplayDimTimeoutSeconds(value) }

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

    suspend fun setFocusBorderStyle(value: String) =
        dataStore.edit { it[FOCUS_BORDER_STYLE] = normalizeFocusBorderStyle(value) }

    suspend fun setFocusColor(value: String) =
        dataStore.edit { it[FOCUS_COLOR] = normalizeFocusColor(value) }

    suspend fun setFocusThickness(value: Int) =
        dataStore.edit { it[FOCUS_THICKNESS] = normalizeFocusThickness(value) }

    suspend fun setFocusPlacement(value: String) =
        dataStore.edit { it[FOCUS_PLACEMENT] = normalizeFocusPlacement(value) }

    suspend fun setTopHeroBackdrop(value: Boolean) =
        dataStore.edit { it[TOP_HERO_BACKDROP] = value }

    suspend fun setTopHeroCover(value: Boolean) =
        dataStore.edit { it[TOP_HERO_COVER] = value }

    suspend fun setTopHeroCoverBorder(value: Boolean) =
        dataStore.edit { it[TOP_HERO_COVER_BORDER] = value }

    suspend fun setTopHeroLogo(value: Boolean) =
        dataStore.edit { it[TOP_HERO_LOGO] = value }

    suspend fun setTopHeroPlatformIcon(value: Boolean) =
        dataStore.edit { it[TOP_HERO_PLATFORM_ICON] = value }

    suspend fun setTopHeroPlatform(value: Boolean) =
        dataStore.edit { it[TOP_HERO_PLATFORM] = value }

    suspend fun setTopHeroTitle(value: Boolean) =
        dataStore.edit { it[TOP_HERO_TITLE] = value }

    suspend fun setTopHeroMetadata(value: Boolean) =
        dataStore.edit { it[TOP_HERO_METADATA] = value }

    suspend fun setTopHeroDescription(value: Boolean) =
        dataStore.edit { it[TOP_HERO_DESCRIPTION] = value }

    suspend fun setTopHeroPlayStats(value: Boolean) =
        dataStore.edit { it[TOP_HERO_PLAY_STATS] = value }

    suspend fun setTopHeroFavorite(value: Boolean) =
        dataStore.edit { it[TOP_HERO_FAVORITE] = value }

    suspend fun setTopHeroSectionHint(value: Boolean) =
        dataStore.edit { it[TOP_HERO_SECTION_HINT] = value }

    suspend fun setIgnorePatternFilesEnabled(value: Boolean) =
        dataStore.edit { it[IGNORE_PATTERN_FILES_ENABLED] = value }

    suspend fun setIgnoreFileNamePatterns(patterns: List<String>) =
        dataStore.edit {
            it[IGNORE_FILE_NAME_PATTERNS] = serializeIgnoreFileNamePatterns(patterns)
        }

    suspend fun addIgnoreFileNamePattern(pattern: String) {
        val normalized = pattern.trim().lowercase()
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            val current = parseIgnoreFileNamePatterns(prefs[IGNORE_FILE_NAME_PATTERNS])
            if (normalized !in current) {
                prefs[IGNORE_FILE_NAME_PATTERNS] =
                    serializeIgnoreFileNamePatterns(current + normalized)
            }
        }
    }

    suspend fun removeIgnoreFileNamePattern(pattern: String) {
        val normalized = pattern.trim().lowercase()
        dataStore.edit { prefs ->
            val current = parseIgnoreFileNamePatterns(prefs[IGNORE_FILE_NAME_PATTERNS])
            prefs[IGNORE_FILE_NAME_PATTERNS] =
                serializeIgnoreFileNamePatterns(current.filterNot { it.equals(normalized, ignoreCase = true) })
        }
    }

    suspend fun resetIgnoreFileNamePatterns() =
        setIgnoreFileNamePatterns(DEFAULT_IGNORE_FILE_NAME_PATTERNS)

    companion object {
        val DEFAULT_IGNORE_FILE_NAME_PATTERNS = listOf(
            "dlc",
            "expansion",
            "update",
            "patch",
            "demo",
            "beta",
            "sample",
            "trial",
            "teaser",
            "bonus",
            "soundtrack"
        )

        fun parseIgnoreFileNamePatterns(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return DEFAULT_IGNORE_FILE_NAME_PATTERNS
            return raw.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
        }

        fun serializeIgnoreFileNamePatterns(patterns: List<String>): String =
            patterns
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(",")

        fun ignoreFileNamePatternsAtDefault(patterns: List<String>): Boolean {
            val normalized = patterns.map { it.lowercase() }.toSet()
            val defaults = DEFAULT_IGNORE_FILE_NAME_PATTERNS.map { it.lowercase() }.toSet()
            return normalized == defaults
        }
        fun normalizeGameDimPercent(value: Int?): Int {
            val raw = (value ?: 90).coerceIn(0, 100)
            return ((raw + 5) / 10) * 10
        }

        fun normalizeGameplayDimTimeoutSeconds(value: Int?): Int =
            (value ?: 10).coerceIn(0, 120)

        private fun readGameplayDimTimeoutSeconds(prefs: Preferences): Int {
            prefs[GAMEPLAY_DIM_TIMEOUT_SECONDS]?.let {
                return normalizeGameplayDimTimeoutSeconds(it)
            }
            val idle = prefs[GAMEPLAY_DIM_IDLE_SECONDS]
            val delay = prefs[GAMEPLAY_DIM_DELAY_SECONDS]
            return when {
                idle != null && delay != null ->
                    normalizeGameplayDimTimeoutSeconds(maxOf(idle, delay))
                idle != null -> normalizeGameplayDimTimeoutSeconds(idle)
                delay != null -> normalizeGameplayDimTimeoutSeconds(delay)
                else -> 10
            }
        }

        fun normalizeFocusBorderStyle(value: String?): String =
            FocusIndicatorPreferenceValues.normalizeBorderStyle(value)

        fun normalizeFocusColor(value: String?): String =
            FocusIndicatorPreferenceValues.normalizeColor(value)

        fun normalizeFocusThickness(value: Int?): Int =
            FocusIndicatorPreferenceValues.normalizeThickness(value)

        fun normalizeFocusPlacement(value: String?): String =
            FocusIndicatorPreferenceValues.normalizePlacement(value)

        fun normalizeNowPlayingDisplay(value: String?): String =
            NowPlayingDisplayMode.fromName(value).name

        private val BLACKOUT_ON_LAUNCH = booleanPreferencesKey("blackout_on_launch")
        val DETECT_MANUAL = booleanPreferencesKey("detect_manual_launches")
        val MEMORY_GUARD_ENABLED = booleanPreferencesKey("memory_guard_enabled")
        val ROM_RECONCILIATION_ENABLED = booleanPreferencesKey("rom_reconciliation_enabled")
        val ROM_RECONCILIATION_FILENAME_FALLBACK =
            booleanPreferencesKey("rom_reconciliation_filename_fallback")
        val GAME_SECONDARY_MODE = stringPreferencesKey("game_secondary_mode")
        private val NOW_PLAYING_DISPLAY = stringPreferencesKey("now_playing_display")
        val GAME_DIM_ENABLED = booleanPreferencesKey("game_dim_enabled")
        val GAME_DIM_ONLY_ON_NOW_PLAYING = booleanPreferencesKey("game_dim_only_on_now_playing")
        val GAME_DIM_PERCENT = intPreferencesKey("game_dim_percent")
        val GAMEPLAY_DIM_TIMEOUT_SECONDS = intPreferencesKey("gameplay_dim_timeout_seconds")
        private val GAMEPLAY_DIM_DELAY_SECONDS = intPreferencesKey("gameplay_dim_delay_seconds")
        private val GAMEPLAY_DIM_IDLE_SECONDS = intPreferencesKey("gameplay_dim_idle_seconds")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SWAP_SCREEN_ROLES = booleanPreferencesKey("swap_screen_roles")
        val THEME = stringPreferencesKey("theme")
        private val FOCUS_BORDER_STYLE = stringPreferencesKey("focus_border_style")
        private val FOCUS_COLOR = stringPreferencesKey("focus_color")
        private val FOCUS_THICKNESS = intPreferencesKey("focus_thickness")
        private val FOCUS_PLACEMENT = stringPreferencesKey("focus_placement")
        private val TOP_HERO_BACKDROP = booleanPreferencesKey("top_hero_backdrop")
        private val TOP_HERO_COVER = booleanPreferencesKey("top_hero_cover")
        private val TOP_HERO_COVER_BORDER = booleanPreferencesKey("top_hero_cover_border")
        private val TOP_HERO_LOGO = booleanPreferencesKey("top_hero_logo")
        private val TOP_HERO_PLATFORM_ICON = booleanPreferencesKey("top_hero_platform_icon")
        private val TOP_HERO_PLATFORM = booleanPreferencesKey("top_hero_platform")
        private val TOP_HERO_TITLE = booleanPreferencesKey("top_hero_title")
        private val TOP_HERO_METADATA = booleanPreferencesKey("top_hero_metadata")
        private val TOP_HERO_DESCRIPTION = booleanPreferencesKey("top_hero_description")
        private val TOP_HERO_PLAY_STATS = booleanPreferencesKey("top_hero_play_stats")
        private val TOP_HERO_FAVORITE = booleanPreferencesKey("top_hero_favorite")
        private val TOP_HERO_SECTION_HINT = booleanPreferencesKey("top_hero_section_hint")
        private val IGNORE_PATTERN_FILES_ENABLED = booleanPreferencesKey("ignore_pattern_files_enabled")
        private val IGNORE_FILE_NAME_PATTERNS = stringPreferencesKey("ignore_file_name_patterns")
    }
}

/** Preset names and helpers for focus ring color / border preferences. */
object FocusIndicatorPreferenceValues {
    val borderStyles = listOf(
        "Solid",
        "Dotted",
        "Dashed",
        "MarchingAnts",
        "Pulsing",
        "Double",
        "Glow",
        "CornerBrackets",
        "GradientPulse",
        "Neon"
    )
    val colors = listOf(
        "theme",
        "white",
        "yellow",
        "cyan",
        "red",
        "green",
        "magenta",
        "orange",
        "lime",
        "pink"
    )
    val thicknesses = listOf(1, 2, 3)
    val placements = listOf("Inside", "Outside")

    fun normalizeBorderStyle(value: String?): String {
        val raw = value ?: "Solid"
        return borderStyles.firstOrNull { it.equals(raw, ignoreCase = true) } ?: "Solid"
    }

    fun normalizeColor(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { "theme" }
        if (raw.startsWith("#")) {
            val upper = raw.uppercase()
            return if (parseHexColor(upper) != null) upper else "theme"
        }
        return colors.firstOrNull { it.equals(raw, ignoreCase = true) } ?: "theme"
    }

    fun parseHexColor(hex: String): Color? {
        val cleaned = hex.removePrefix("#").uppercase()
        if (cleaned.length != 6 || cleaned.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
        return runCatching {
            val rgb = cleaned.toLong(16)
            Color(
                red = ((rgb shr 16) and 0xFF) / 255f,
                green = ((rgb shr 8) and 0xFF) / 255f,
                blue = (rgb and 0xFF) / 255f
            )
        }.getOrNull()
    }

    fun isCustomHex(value: String): Boolean = normalizeColor(value).startsWith("#")

    fun displayColorLabel(value: String): String {
        val normalized = normalizeColor(value)
        if (normalized.startsWith("#")) return "Custom $normalized"
        return when (normalized) {
            "theme" -> "Theme accent"
            "white" -> "White"
            "yellow" -> "Yellow"
            "cyan" -> "Cyan"
            "red" -> "Red"
            "green" -> "Green"
            "magenta" -> "Magenta"
            "orange" -> "Orange"
            "lime" -> "Lime"
            "pink" -> "Pink"
            else -> normalized.replaceFirstChar { it.uppercase() }
        }
    }

    fun normalizeThickness(value: Int?): Int {
        val raw = value ?: 2
        return thicknesses.minByOrNull { kotlin.math.abs(it - raw) } ?: 2
    }

    fun normalizePlacement(value: String?): String {
        val raw = value ?: "Inside"
        return placements.firstOrNull { it.equals(raw, ignoreCase = true) } ?: "Inside"
    }
}
