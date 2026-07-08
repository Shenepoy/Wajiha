package com.wajiha.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val blackoutOnLaunch: Boolean = false,
    val detectManualLaunches: Boolean = true,
    /** SecondaryMode name shown while a game runs */
    val gameSecondaryMode: String = "NowPlaying",
    /** Overlay dim on the bottom screen while a game runs on the top display. */
    val gameDimEnabled: Boolean = false,
    /** Dim strength in percent (25, 50, 75, or 100). */
    val gameDimPercent: Int = 50,
    /** Seconds to wait after gameplay starts before applying the dim overlay (0 = immediate). */
    val gameplayDimDelaySeconds: Int = 5,
    /** Seconds of bottom-screen idle before re-dimming during gameplay (0 = stay lifted). */
    val gameplayDimIdleSeconds: Int = 10,
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
            gameSecondaryMode = prefs[GAME_SECONDARY_MODE] ?: "NowPlaying",
            gameDimEnabled = prefs[GAME_DIM_ENABLED] ?: false,
            gameDimPercent = normalizeGameDimPercent(prefs[GAME_DIM_PERCENT]),
            gameplayDimDelaySeconds = normalizeGameplayDimDelaySeconds(prefs[GAMEPLAY_DIM_DELAY_SECONDS]),
            gameplayDimIdleSeconds = normalizeGameplayDimIdleSeconds(prefs[GAMEPLAY_DIM_IDLE_SECONDS]),
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

    suspend fun setGameSecondaryMode(value: String) =
        dataStore.edit { it[GAME_SECONDARY_MODE] = value }

    suspend fun setGameDimEnabled(value: Boolean) =
        dataStore.edit { it[GAME_DIM_ENABLED] = value }

    suspend fun setGameDimPercent(value: Int) =
        dataStore.edit { it[GAME_DIM_PERCENT] = normalizeGameDimPercent(value) }

    suspend fun setGameplayDimDelaySeconds(value: Int) =
        dataStore.edit { it[GAMEPLAY_DIM_DELAY_SECONDS] = normalizeGameplayDimDelaySeconds(value) }

    suspend fun setGameplayDimIdleSeconds(value: Int) =
        dataStore.edit { it[GAMEPLAY_DIM_IDLE_SECONDS] = normalizeGameplayDimIdleSeconds(value) }

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
        val GAME_DIM_PERCENTS = listOf(25, 50, 75, 100)

        fun normalizeGameDimPercent(value: Int?): Int {
            val raw = value ?: 50
            return GAME_DIM_PERCENTS.minByOrNull { kotlin.math.abs(it - raw) } ?: 50
        }

        fun normalizeGameplayDimDelaySeconds(value: Int?): Int =
            (value ?: 5).coerceIn(0, 60)

        fun normalizeGameplayDimIdleSeconds(value: Int?): Int =
            (value ?: 10).coerceIn(0, 120)

        fun normalizeFocusBorderStyle(value: String?): String =
            FocusIndicatorPreferenceValues.normalizeBorderStyle(value)

        fun normalizeFocusColor(value: String?): String =
            FocusIndicatorPreferenceValues.normalizeColor(value)

        fun normalizeFocusThickness(value: Int?): Int =
            FocusIndicatorPreferenceValues.normalizeThickness(value)

        fun normalizeFocusPlacement(value: String?): String =
            FocusIndicatorPreferenceValues.normalizePlacement(value)

        private val BLACKOUT_ON_LAUNCH = booleanPreferencesKey("blackout_on_launch")
        val DETECT_MANUAL = booleanPreferencesKey("detect_manual_launches")
        val GAME_SECONDARY_MODE = stringPreferencesKey("game_secondary_mode")
        val GAME_DIM_ENABLED = booleanPreferencesKey("game_dim_enabled")
        val GAME_DIM_PERCENT = intPreferencesKey("game_dim_percent")
        val GAMEPLAY_DIM_DELAY_SECONDS = intPreferencesKey("gameplay_dim_delay_seconds")
        val GAMEPLAY_DIM_IDLE_SECONDS = intPreferencesKey("gameplay_dim_idle_seconds")
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
