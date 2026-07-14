package com.wajiha.state

/** Kind of settings focus mirrored onto the dual-screen hero. */
enum class SettingsHeroKind {
    Generic,
    PlatformRow,
    LibraryChrome,
    ScreensChrome,
    PickerRow,
}

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
    val valueText: String? = null,
    /** "focusColor", "theme", "glyphs", or null. */
    val previewKind: String? = null,
    /** Hex / theme name / glyph scheme payload for [previewKind]. */
    val previewPayload: String? = null,
    val numberValue: Int? = null,
    val numberUnit: String? = null,
    /** Option chips when settings-hero actions are enabled. */
    val options: List<SettingsHeroOption> = emptyList(),
    val platform: SettingsHeroPlatformExtras? = null,
    val sectionOverview: String? = null,
    val showRoleDiagram: Boolean = false,
    val actions: List<SettingsHeroAction> = emptyList(),
)

fun SettingsHeroDetail?.isSettingsHeroInteractive(showActions: Boolean): Boolean =
    showActions && this != null && (options.isNotEmpty() || actions.isNotEmpty())

/**
 * Process-wide callbacks for settings-hero mutations. Menu screens register
 * handlers when publishing focus; clear on blur. Mirrors [com.wajiha.input.GamepadHintMirror].
 */
object SettingsHeroActionBridge {
    @Volatile
    var onSelectOption: ((String) -> Unit)? = null

    /** Primary hero action (rescan / configure). */
    @Volatile
    var onPrimaryAction: (() -> Unit)? = null

    /** Secondary hero action (edit / open). */
    @Volatile
    var onSecondaryAction: (() -> Unit)? = null

    fun clear() {
        onSelectOption = null
        onPrimaryAction = null
        onSecondaryAction = null
    }
}
