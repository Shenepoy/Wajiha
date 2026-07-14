package com.wajiha.state

/** Kind of settings focus mirrored onto the dual-screen hero. */
enum class SettingsHeroKind {
    Generic,
    PlatformRow,
    LibraryChrome,
    ScreensChrome,
    SectionOverview,
    PickerRow,
}

data class SettingsHeroHint(
    val button: String,
    val label: String,
)

data class SettingsHeroAction(
    /** Stable id: "edit", "rescan", "configure", "open", "select". */
    val id: String,
    val label: String,
)

data class SettingsHeroOption(
    val value: String,
    val label: String,
    val selected: Boolean,
)

data class SettingsHeroPlatformExtras(
    val platformId: String,
    val enabled: Boolean,
    val folderCount: Int,
    val gameCount: Int,
    val folderPaths: List<String>,
    val emulatorLabel: String?,
    /** Local boxart sample paths for the platform. */
    val boxartPaths: List<String>,
    val shortName: String? = null,
    /** Comma-separated ROM extensions without dots. */
    val extensions: String? = null,
    val screenScraperId: Int? = null,
    val raConsoleId: Int? = null,
)

/**
 * Snapshot published by the settings menu when a row / section has focus.
 * Callbacks cannot live here — use [SettingsHeroActionBridge] or action ids.
 */
data class SettingsHeroDetail(
    val kind: SettingsHeroKind,
    val title: String,
    val subtitle: String? = null,
    /** Omit when identical to [subtitle] at publish time. */
    val whyItMatters: String? = null,
    val valueText: String? = null,
    /** e.g. "Default: … · Y resets" */
    val defaultLine: String? = null,
    val controlHints: List<SettingsHeroHint> = emptyList(),
    /** "focusColor", "theme", "glyphs", or null. */
    val previewKind: String? = null,
    /** Hex / theme name / glyph scheme payload for [previewKind]. */
    val previewPayload: String? = null,
    val numberValue: Int? = null,
    val numberUnit: String? = null,
    /** Option chips when settings-hero actions are enabled. */
    val options: List<SettingsHeroOption> = emptyList(),
    val platform: SettingsHeroPlatformExtras? = null,
    /** Platforms to games totals for library chrome. */
    val libraryTotals: Pair<Int, Int>? = null,
    val sectionOverview: String? = null,
    val showRoleDiagram: Boolean = false,
    val actions: List<SettingsHeroAction> = emptyList(),
)

/**
 * Process-wide callbacks for settings-hero mutations. Menu screens register
 * handlers when publishing focus; clear on blur. Mirrors [com.wajiha.input.GamepadHintMirror].
 */
object SettingsHeroActionBridge {
    @Volatile
    var onSelectOption: ((String) -> Unit)? = null

    @Volatile
    var onSetEmulator: ((String) -> Unit)? = null

    /** Primary hero action (rescan / configure). */
    @Volatile
    var onPrimaryAction: (() -> Unit)? = null

    /** Secondary hero action (edit / open). */
    @Volatile
    var onSecondaryAction: (() -> Unit)? = null

    fun clear() {
        onSelectOption = null
        onSetEmulator = null
        onPrimaryAction = null
        onSecondaryAction = null
    }
}
