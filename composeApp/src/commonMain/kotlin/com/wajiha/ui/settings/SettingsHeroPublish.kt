package com.wajiha.ui.settings

import com.wajiha.state.DualScreenStore
import com.wajiha.state.SettingsHeroAction
import com.wajiha.state.SettingsHeroActionBridge
import com.wajiha.state.SettingsHeroDetail
import com.wajiha.state.SettingsHeroHint
import com.wajiha.state.SettingsHeroKind
import com.wajiha.state.SettingsHeroOption
import com.wajiha.state.SettingsHeroPlatformExtras

fun clearSettingsHero(store: DualScreenStore) {
    store.setSettingsHeroPicking(false)
    store.setSettingsHeroDetail(null)
    SettingsHeroActionBridge.clear()
}

fun publishSettingsHero(
    store: DualScreenStore,
    detail: SettingsHeroDetail?,
    onSelectOption: ((String) -> Unit)? = null,
    onSetEmulator: ((String) -> Unit)? = null,
    onPrimaryAction: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    if (detail == null) {
        clearSettingsHero(store)
        return
    }
    SettingsHeroActionBridge.clear()
    SettingsHeroActionBridge.onSelectOption = onSelectOption
    SettingsHeroActionBridge.onSetEmulator = onSetEmulator
    SettingsHeroActionBridge.onPrimaryAction = onPrimaryAction
    SettingsHeroActionBridge.onSecondaryAction = onSecondaryAction
    store.setSettingsHeroDetail(detail)
}

fun genericSettingHeroDetail(
    title: String,
    subtitle: String? = null,
    whyItMatters: String? = null,
    valueText: String? = null,
    defaultLine: String? = null,
    controlHints: List<SettingsHeroHint> = emptyList(),
    options: List<SettingsHeroOption> = emptyList(),
    previewKind: String? = null,
    previewPayload: String? = null,
    numberValue: Int? = null,
    numberUnit: String? = null,
    showRoleDiagram: Boolean = false,
    sectionOverview: String? = null,
    kind: SettingsHeroKind = SettingsHeroKind.Generic,
): SettingsHeroDetail {
    val why =
        whyItMatters?.takeIf { it.isNotBlank() && it != subtitle }
    return SettingsHeroDetail(
        kind = kind,
        title = title,
        subtitle = subtitle,
        whyItMatters = why,
        valueText = valueText,
        defaultLine = defaultLine,
        controlHints = controlHints,
        previewKind = previewKind,
        previewPayload = previewPayload,
        numberValue = numberValue,
        numberUnit = numberUnit,
        options = options,
        showRoleDiagram = showRoleDiagram,
        sectionOverview = sectionOverview,
    )
}

fun platformRowHeroDetail(
    name: String,
    platformId: String,
    enabled: Boolean,
    folderCount: Int,
    gameCount: Int,
    folderPaths: List<String>,
    emulatorLabel: String?,
    boxartPaths: List<String>,
    actionsEnabled: Boolean,
    emulatorOptions: List<SettingsHeroOption> = emptyList(),
    shortName: String? = null,
    extensions: String? = null,
    screenScraperId: Int? = null,
    raConsoleId: Int? = null,
): SettingsHeroDetail =
    SettingsHeroDetail(
        kind = SettingsHeroKind.PlatformRow,
        title = name,
        subtitle = null,
        platform =
            SettingsHeroPlatformExtras(
                platformId = platformId,
                enabled = enabled,
                folderCount = folderCount,
                gameCount = gameCount,
                folderPaths = folderPaths,
                emulatorLabel = emulatorLabel,
                boxartPaths = boxartPaths,
                shortName = shortName,
                extensions = extensions,
                screenScraperId = screenScraperId,
                raConsoleId = raConsoleId,
            ),
        options = if (actionsEnabled) emulatorOptions else emptyList(),
        actions =
            if (actionsEnabled) {
                listOf(
                    SettingsHeroAction("edit", "Edit"),
                    SettingsHeroAction("rescan", "Rescan"),
                )
            } else {
                emptyList()
            },
    )

/** Focus callback for a simple toggle / text setting mirrored to the hero. */
fun settingsToggleHeroFocus(
    store: DualScreenStore,
    title: String,
    subtitle: String?,
    checked: Boolean,
    whyItMatters: String? = null,
    defaultLine: String = "Default applies · Y resets",
    showRoleDiagram: Boolean = false,
    kind: SettingsHeroKind = SettingsHeroKind.Generic,
    previewKind: String? = null,
    previewPayload: String? = null,
): (Boolean) -> Unit =
    { focused ->
        if (focused) {
            publishSettingsHero(
                store,
                genericSettingHeroDetail(
                    title = title,
                    subtitle = subtitle,
                    whyItMatters = whyItMatters,
                    valueText = if (checked) "On" else "Off",
                    defaultLine = defaultLine,
                    controlHints =
                        listOf(
                            SettingsHeroHint("A", "Toggle"),
                            SettingsHeroHint("Y", "Reset"),
                        ),
                    showRoleDiagram = showRoleDiagram,
                    kind = kind,
                    previewKind = previewKind,
                    previewPayload = previewPayload,
                ),
            )
        } else {
            clearSettingsHero(store)
        }
    }

fun libraryChromeHeroDetail(
    platformCount: Int,
    gameCount: Int,
    sectionOverview: String,
): SettingsHeroDetail =
    SettingsHeroDetail(
        kind = SettingsHeroKind.LibraryChrome,
        title = "Library",
        subtitle = "$platformCount platform(s) · $gameCount game(s)",
        libraryTotals = platformCount to gameCount,
        sectionOverview = sectionOverview,
        controlHints = listOf(SettingsHeroHint("A", "Select")),
    )

fun pickerRowHeroDetail(
    name: String,
    alreadyInUse: Boolean,
    actionsEnabled: Boolean,
): SettingsHeroDetail =
    SettingsHeroDetail(
        kind = SettingsHeroKind.PickerRow,
        title = name,
        subtitle = if (alreadyInUse) "Already in library" else "Not configured yet",
        valueText = if (alreadyInUse) "Open" else "Configure",
        controlHints =
            listOf(
                SettingsHeroHint("A", if (alreadyInUse) "Open" else "Configure"),
            ),
        actions =
            if (actionsEnabled) {
                listOf(
                    SettingsHeroAction(
                        if (alreadyInUse) "open" else "configure",
                        if (alreadyInUse) "Open" else "Configure",
                    ),
                )
            } else {
                emptyList()
            },
    )
