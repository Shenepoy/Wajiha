package com.wajiha.data.prefs

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wajiha.log.WajihaLog
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
    /** Now Running panel: full-bleed hero (or boxart) behind the content. */
    val nowPlayingHeroBackground: Boolean = true,
    /** Now Running panel: show logo in place of the game title when available. */
    val nowPlayingLogo: Boolean = true,
    /** Game grid: use the focused game's hero art as a full-screen backdrop. */
    val gameGridHeroBackground: Boolean = false,
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
    /**
     * Primary-only launcher: combined layout on the main display, no
     * SecondaryHome. Escape hatch for phones / non-Thor / single-panel use.
     */
    val singleScreen: Boolean = false,
    /**
     * When [singleScreen] is on: show the hero preview above the library.
     * Off = library fills the screen; filter/settings sit in the grid header.
     */
    val showHeroBanner: Boolean = true,
    /** When [singleScreen] is on: show the focused game's name above the library. */
    val showSelectedGameName: Boolean = false,
    val swapScreenRoles: Boolean = false,
    /**
     * Dual browsing only: show the menu screen's gamepad hint bar on the hero
     * display instead of under the grid / settings.
     */
    val swapGamepadHints: Boolean = false,
    /**
     * Settings hero (top screen): mirror title, value, preview, and actions
     * for the focused settings row.
     */
    val settingsHeroHelp: Boolean = true,
    /**
     * Settings hero: show action chips / option picker on the hero display
     * (edit, rescan, configure, option select).
     */
    val settingsHeroActions: Boolean = false,
    val theme: String = "dark",
    /** FocusBorderStyle name (Solid, Dotted, …, Neon). */
    val focusBorderStyle: String = "Solid",
    /** Focus ring color preset (theme, white, yellow, cyan, red, …) or custom #RRGGBB hex. */
    val focusColor: String = "theme",
    /** Focus ring width in dp (1, 2, or 3). */
    val focusThickness: Int = 2,
    /** Focus ring placement: Inside (inset on bounds) or Outside (outset beyond bounds). */
    val focusPlacement: String = "Inside",
    /** Show scheme-aware controller glyphs in the bottom action bar (false = Text labels). */
    val controllerGlyphsEnabled: Boolean = true,
    /** Auto / Xbox / PlayStation / Switch / Steam Deck / Steam Controller / Text. */
    val controllerGlyphScheme: String = "Auto",
    /** Color / ColorOutline / White / Dark — Kenney face-button appearance. */
    val controllerGlyphFaceStyle: String = "Color",
    /** Filled / Outline — Kenney look for shoulders, d-pad, and system buttons. */
    val controllerGlyphOtherStyle: String = "Filled",
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
    val ignoreFileNamePatterns: List<String> = SettingsRepository.DEFAULT_IGNORE_FILE_NAME_PATTERNS,
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                blackoutOnLaunch = prefs[BLACKOUT_ON_LAUNCH] ?: false,
                detectManualLaunches = prefs[DETECT_MANUAL] ?: true,
                memoryGuardEnabled = prefs[MEMORY_GUARD_ENABLED] ?: true,
                romReconciliationEnabled = prefs[ROM_RECONCILIATION_ENABLED] ?: false,
                romReconciliationShowFilenameFallback =
                    prefs[ROM_RECONCILIATION_FILENAME_FALLBACK] ?: true,
                gameSecondaryMode = prefs[GAME_SECONDARY_MODE] ?: "NowPlaying",
                nowPlayingDisplay = normalizeNowPlayingDisplay(prefs[NOW_PLAYING_DISPLAY]),
                nowPlayingHeroBackground = prefs[NOW_PLAYING_HERO_BACKGROUND] ?: true,
                nowPlayingLogo = prefs[NOW_PLAYING_LOGO] ?: true,
                gameGridHeroBackground = prefs[GAME_GRID_HERO_BACKGROUND] ?: false,
                gameDimEnabled = prefs[GAME_DIM_ENABLED] ?: false,
                gameDimOnlyOnNowPlaying = prefs[GAME_DIM_ONLY_ON_NOW_PLAYING] ?: true,
                gameDimPercent = normalizeGameDimPercent(prefs[GAME_DIM_PERCENT]),
                gameplayDimTimeoutSeconds = readGameplayDimTimeoutSeconds(prefs),
                gridRows = prefs[GRID_ROWS] ?: 2,
                soundsEnabled = prefs[SOUNDS_ENABLED] ?: true,
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
                singleScreen = prefs[SINGLE_SCREEN] ?: false,
                showHeroBanner = prefs[SHOW_HERO_BANNER] ?: true,
                showSelectedGameName = prefs[SHOW_SELECTED_GAME_NAME] ?: false,
                swapScreenRoles = prefs[SWAP_SCREEN_ROLES] ?: false,
                swapGamepadHints = prefs[SWAP_GAMEPAD_HINTS] ?: false,
                settingsHeroHelp = prefs[SETTINGS_HERO_HELP] ?: true,
                settingsHeroActions = prefs[SETTINGS_HERO_ACTIONS] ?: false,
                theme = prefs[THEME] ?: "dark",
                focusBorderStyle = normalizeFocusBorderStyle(prefs[FOCUS_BORDER_STYLE]),
                focusColor = normalizeFocusColor(prefs[FOCUS_COLOR]),
                focusThickness = normalizeFocusThickness(prefs[FOCUS_THICKNESS]),
                focusPlacement = normalizeFocusPlacement(prefs[FOCUS_PLACEMENT]),
                controllerGlyphsEnabled = prefs[CONTROLLER_GLYPHS_ENABLED] ?: true,
                controllerGlyphScheme = normalizeControllerGlyphScheme(prefs[CONTROLLER_GLYPH_SCHEME]),
                controllerGlyphFaceStyle = normalizeControllerGlyphFaceStyle(prefs[CONTROLLER_GLYPH_FACE_STYLE]),
                controllerGlyphOtherStyle = normalizeControllerGlyphOtherStyle(prefs[CONTROLLER_GLYPH_OTHER_STYLE]),
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
                ignoreFileNamePatterns = parseIgnoreFileNamePatterns(prefs[IGNORE_FILE_NAME_PATTERNS]),
            )
        }

    suspend fun setBlackoutOnLaunch(value: Boolean) = setPref(BLACKOUT_ON_LAUNCH, value, "blackoutOnLaunch")

    suspend fun setDetectManualLaunches(value: Boolean) = setPref(DETECT_MANUAL, value, "detectManualLaunches")

    suspend fun setMemoryGuardEnabled(value: Boolean) = setPref(MEMORY_GUARD_ENABLED, value, "memoryGuardEnabled")

    suspend fun setRomReconciliationEnabled(value: Boolean) = setPref(ROM_RECONCILIATION_ENABLED, value, "romReconciliationEnabled")

    suspend fun setRomReconciliationShowFilenameFallback(value: Boolean) =
        setPref(ROM_RECONCILIATION_FILENAME_FALLBACK, value, "romReconciliationShowFilenameFallback")

    suspend fun setGameSecondaryMode(value: String) = setPref(GAME_SECONDARY_MODE, value, "gameSecondaryMode")

    suspend fun setNowPlayingDisplay(value: String) = setPref(NOW_PLAYING_DISPLAY, normalizeNowPlayingDisplay(value), "nowPlayingDisplay")

    suspend fun setNowPlayingHeroBackground(value: Boolean) = setPref(NOW_PLAYING_HERO_BACKGROUND, value, "nowPlayingHeroBackground")

    suspend fun setNowPlayingLogo(value: Boolean) = setPref(NOW_PLAYING_LOGO, value, "nowPlayingLogo")

    suspend fun setGameGridHeroBackground(value: Boolean) = setPref(GAME_GRID_HERO_BACKGROUND, value, "gameGridHeroBackground")

    suspend fun setGameDimEnabled(value: Boolean) = setPref(GAME_DIM_ENABLED, value, "gameDimEnabled")

    suspend fun setGameDimOnlyOnNowPlaying(value: Boolean) = setPref(GAME_DIM_ONLY_ON_NOW_PLAYING, value, "gameDimOnlyOnNowPlaying")

    suspend fun setGameDimPercent(value: Int) = setPref(GAME_DIM_PERCENT, normalizeGameDimPercent(value), "gameDimPercent")

    suspend fun setGameplayDimTimeoutSeconds(value: Int) =
        setPref(
            GAMEPLAY_DIM_TIMEOUT_SECONDS,
            normalizeGameplayDimTimeoutSeconds(value),
            "gameplayDimTimeoutSeconds",
        )

    suspend fun setGridRows(value: Int) = setPref(GRID_ROWS, value, "gridRows")

    suspend fun setSoundsEnabled(value: Boolean) = setPref(SOUNDS_ENABLED, value, "soundsEnabled")

    suspend fun setOnboardingDone(value: Boolean) = setPref(ONBOARDING_DONE, value, "onboardingDone")

    suspend fun setSingleScreen(value: Boolean) = setPref(SINGLE_SCREEN, value, "singleScreen")

    suspend fun setShowHeroBanner(value: Boolean) = setPref(SHOW_HERO_BANNER, value, "showHeroBanner")

    suspend fun setShowSelectedGameName(value: Boolean) = setPref(SHOW_SELECTED_GAME_NAME, value, "showSelectedGameName")

    suspend fun setSwapScreenRoles(value: Boolean) = setPref(SWAP_SCREEN_ROLES, value, "swapScreenRoles")

    suspend fun setSwapGamepadHints(value: Boolean) = setPref(SWAP_GAMEPAD_HINTS, value, "swapGamepadHints")

    suspend fun setSettingsHeroHelp(value: Boolean) = setPref(SETTINGS_HERO_HELP, value, "settingsHeroHelp")

    suspend fun setSettingsHeroActions(value: Boolean) = setPref(SETTINGS_HERO_ACTIONS, value, "settingsHeroActions")

    suspend fun toggleSwapScreenRoles(): Boolean {
        var next = false
        dataStore.edit { prefs ->
            next = !(prefs[SWAP_SCREEN_ROLES] ?: false)
            prefs[SWAP_SCREEN_ROLES] = next
        }
        WajihaLog.setting("swapScreenRoles", next)
        return next
    }

    suspend fun setTheme(value: String) = setPref(THEME, value, "theme")

    suspend fun setFocusBorderStyle(value: String) = setPref(FOCUS_BORDER_STYLE, normalizeFocusBorderStyle(value), "focusBorderStyle")

    suspend fun setFocusColor(value: String) = setPref(FOCUS_COLOR, normalizeFocusColor(value), "focusColor")

    suspend fun setFocusThickness(value: Int) = setPref(FOCUS_THICKNESS, normalizeFocusThickness(value), "focusThickness")

    suspend fun setFocusPlacement(value: String) = setPref(FOCUS_PLACEMENT, normalizeFocusPlacement(value), "focusPlacement")

    suspend fun setControllerGlyphsEnabled(value: Boolean) = setPref(CONTROLLER_GLYPHS_ENABLED, value, "controllerGlyphsEnabled")

    suspend fun setControllerGlyphScheme(value: String) =
        setPref(CONTROLLER_GLYPH_SCHEME, normalizeControllerGlyphScheme(value), "controllerGlyphScheme")

    suspend fun setControllerGlyphFaceStyle(value: String) =
        setPref(CONTROLLER_GLYPH_FACE_STYLE, normalizeControllerGlyphFaceStyle(value), "controllerGlyphFaceStyle")

    suspend fun setControllerGlyphOtherStyle(value: String) =
        setPref(CONTROLLER_GLYPH_OTHER_STYLE, normalizeControllerGlyphOtherStyle(value), "controllerGlyphOtherStyle")

    suspend fun setTopHeroBackdrop(value: Boolean) = setPref(TOP_HERO_BACKDROP, value, "topHeroBackdrop")

    suspend fun setTopHeroCover(value: Boolean) = setPref(TOP_HERO_COVER, value, "topHeroCover")

    suspend fun setTopHeroCoverBorder(value: Boolean) = setPref(TOP_HERO_COVER_BORDER, value, "topHeroCoverBorder")

    suspend fun setTopHeroLogo(value: Boolean) = setPref(TOP_HERO_LOGO, value, "topHeroLogo")

    suspend fun setTopHeroPlatformIcon(value: Boolean) = setPref(TOP_HERO_PLATFORM_ICON, value, "topHeroPlatformIcon")

    suspend fun setTopHeroPlatform(value: Boolean) = setPref(TOP_HERO_PLATFORM, value, "topHeroPlatform")

    suspend fun setTopHeroTitle(value: Boolean) = setPref(TOP_HERO_TITLE, value, "topHeroTitle")

    suspend fun setTopHeroMetadata(value: Boolean) = setPref(TOP_HERO_METADATA, value, "topHeroMetadata")

    suspend fun setTopHeroDescription(value: Boolean) = setPref(TOP_HERO_DESCRIPTION, value, "topHeroDescription")

    suspend fun setTopHeroPlayStats(value: Boolean) = setPref(TOP_HERO_PLAY_STATS, value, "topHeroPlayStats")

    suspend fun setTopHeroFavorite(value: Boolean) = setPref(TOP_HERO_FAVORITE, value, "topHeroFavorite")

    suspend fun setTopHeroSectionHint(value: Boolean) = setPref(TOP_HERO_SECTION_HINT, value, "topHeroSectionHint")

    suspend fun setIgnorePatternFilesEnabled(value: Boolean) = setPref(IGNORE_PATTERN_FILES_ENABLED, value, "ignorePatternFilesEnabled")

    suspend fun setIgnoreFileNamePatterns(patterns: List<String>) {
        dataStore.edit {
            it[IGNORE_FILE_NAME_PATTERNS] = serializeIgnoreFileNamePatterns(patterns)
        }
        WajihaLog.setting("ignoreFileNamePatterns", patterns.size)
    }

    private suspend fun <T> setPref(
        key: Preferences.Key<T>,
        value: T,
        name: String,
    ) {
        dataStore.edit { it[key] = value }
        WajihaLog.setting(name, value)
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

    suspend fun resetIgnoreFileNamePatterns() = setIgnoreFileNamePatterns(DEFAULT_IGNORE_FILE_NAME_PATTERNS)

    companion object {
        val DEFAULT_IGNORE_FILE_NAME_PATTERNS =
            listOf(
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
                "soundtrack",
            )

        fun parseIgnoreFileNamePatterns(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return DEFAULT_IGNORE_FILE_NAME_PATTERNS
            return raw
                .split(',')
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

        fun normalizeGameplayDimTimeoutSeconds(value: Int?): Int = (value ?: 10).coerceIn(0, 120)

        private fun readGameplayDimTimeoutSeconds(prefs: Preferences): Int {
            prefs[GAMEPLAY_DIM_TIMEOUT_SECONDS]?.let {
                return normalizeGameplayDimTimeoutSeconds(it)
            }
            val idle = prefs[GAMEPLAY_DIM_IDLE_SECONDS]
            val delay = prefs[GAMEPLAY_DIM_DELAY_SECONDS]
            return when {
                idle != null && delay != null -> {
                    normalizeGameplayDimTimeoutSeconds(maxOf(idle, delay))
                }

                idle != null -> {
                    normalizeGameplayDimTimeoutSeconds(idle)
                }

                delay != null -> {
                    normalizeGameplayDimTimeoutSeconds(delay)
                }

                else -> {
                    10
                }
            }
        }

        fun normalizeFocusBorderStyle(value: String?): String = FocusIndicatorPreferenceValues.normalizeBorderStyle(value)

        fun normalizeFocusColor(value: String?): String = FocusIndicatorPreferenceValues.normalizeColor(value)

        fun normalizeFocusThickness(value: Int?): Int = FocusIndicatorPreferenceValues.normalizeThickness(value)

        fun normalizeFocusPlacement(value: String?): String = FocusIndicatorPreferenceValues.normalizePlacement(value)

        fun normalizeNowPlayingDisplay(value: String?): String = NowPlayingDisplayMode.fromName(value).name

        fun normalizeControllerGlyphScheme(value: String?): String =
            com.wajiha.input.ControllerGlyphScheme
                .fromName(value)
                .name

        fun normalizeControllerGlyphFaceStyle(value: String?): String =
            com.wajiha.input.ControllerGlyphFaceStyle
                .fromName(value)
                .name

        fun normalizeControllerGlyphOtherStyle(value: String?): String =
            com.wajiha.input.ControllerGlyphOtherStyle
                .fromName(value)
                .name

        private val BLACKOUT_ON_LAUNCH = booleanPreferencesKey("blackout_on_launch")
        val DETECT_MANUAL = booleanPreferencesKey("detect_manual_launches")
        val MEMORY_GUARD_ENABLED = booleanPreferencesKey("memory_guard_enabled")
        val ROM_RECONCILIATION_ENABLED = booleanPreferencesKey("rom_reconciliation_enabled")
        val ROM_RECONCILIATION_FILENAME_FALLBACK =
            booleanPreferencesKey("rom_reconciliation_filename_fallback")
        val GAME_SECONDARY_MODE = stringPreferencesKey("game_secondary_mode")
        private val NOW_PLAYING_DISPLAY = stringPreferencesKey("now_playing_display")
        private val NOW_PLAYING_HERO_BACKGROUND = booleanPreferencesKey("now_playing_hero_background")
        private val NOW_PLAYING_LOGO = booleanPreferencesKey("now_playing_logo")
        private val GAME_GRID_HERO_BACKGROUND = booleanPreferencesKey("game_grid_hero_background")
        val GAME_DIM_ENABLED = booleanPreferencesKey("game_dim_enabled")
        val GAME_DIM_ONLY_ON_NOW_PLAYING = booleanPreferencesKey("game_dim_only_on_now_playing")
        val GAME_DIM_PERCENT = intPreferencesKey("game_dim_percent")
        val GAMEPLAY_DIM_TIMEOUT_SECONDS = intPreferencesKey("gameplay_dim_timeout_seconds")
        private val GAMEPLAY_DIM_DELAY_SECONDS = intPreferencesKey("gameplay_dim_delay_seconds")
        private val GAMEPLAY_DIM_IDLE_SECONDS = intPreferencesKey("gameplay_dim_idle_seconds")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SINGLE_SCREEN = booleanPreferencesKey("single_screen")
        val SHOW_HERO_BANNER = booleanPreferencesKey("show_hero_banner")
        val SHOW_SELECTED_GAME_NAME = booleanPreferencesKey("show_selected_game_name")
        val SWAP_SCREEN_ROLES = booleanPreferencesKey("swap_screen_roles")
        val SWAP_GAMEPAD_HINTS = booleanPreferencesKey("swap_gamepad_hints")
        val SETTINGS_HERO_HELP = booleanPreferencesKey("settings_hero_help")
        val SETTINGS_HERO_ACTIONS = booleanPreferencesKey("settings_hero_actions")
        val THEME = stringPreferencesKey("theme")
        private val FOCUS_BORDER_STYLE = stringPreferencesKey("focus_border_style")
        private val FOCUS_COLOR = stringPreferencesKey("focus_color")
        private val FOCUS_THICKNESS = intPreferencesKey("focus_thickness")
        private val FOCUS_PLACEMENT = stringPreferencesKey("focus_placement")
        private val CONTROLLER_GLYPHS_ENABLED = booleanPreferencesKey("controller_glyphs_enabled")
        private val CONTROLLER_GLYPH_SCHEME = stringPreferencesKey("controller_glyph_scheme")
        private val CONTROLLER_GLYPH_FACE_STYLE = stringPreferencesKey("controller_glyph_face_style")
        private val CONTROLLER_GLYPH_OTHER_STYLE = stringPreferencesKey("controller_glyph_other_style")
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
    val borderStyles =
        listOf(
            "Solid",
            "Dotted",
            "Dashed",
            "MarchingAnts",
            "Pulsing",
            "Double",
            "Glow",
            "CornerBrackets",
            "GradientPulse",
            "Neon",
        )
    val colors =
        listOf(
            "theme",
            "white",
            "yellow",
            "cyan",
            "red",
            "green",
            "magenta",
            "orange",
            "lime",
            "pink",
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
                blue = (rgb and 0xFF) / 255f,
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
