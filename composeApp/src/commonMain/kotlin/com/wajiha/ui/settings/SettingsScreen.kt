package com.wajiha.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.FocusIndicatorPreferenceValues
import com.wajiha.data.prefs.IconAppearancePreferences
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.rememberFocusContext
import com.wajiha.input.rememberedFocusContext
import com.wajiha.input.rememberedFocusTarget
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.PermissionStates
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.state.SettingsHeroKind
import com.wajiha.state.SettingsHeroOption
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaFolderSettingChrome
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.GamepadSettingTrailingActions
import com.wajiha.ui.components.gamepad.LocalSettingRowMinHeight
import com.wajiha.ui.components.gamepad.LocalSettingSectionScroll
import com.wajiha.ui.components.gamepad.MultiChoiceOption
import com.wajiha.ui.components.gamepad.ProvideSettingsDensity
import com.wajiha.ui.components.gamepad.SettingSectionFocusRestorer
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.WajihaChoiceSetting
import com.wajiha.ui.components.gamepad.WajihaMultiChoiceSetting
import com.wajiha.ui.components.gamepad.WajihaNumberSetting
import com.wajiha.ui.components.gamepad.WajihaSettingBlurb
import com.wajiha.ui.components.gamepad.WajihaSettingDivider
import com.wajiha.ui.components.gamepad.WajihaToggleSetting
import com.wajiha.ui.components.gamepad.libraryChromeGamepadHints
import com.wajiha.ui.components.gamepad.libraryPlatformRowGamepadHints
import com.wajiha.ui.components.gamepad.scrollHeaderToTop
import com.wajiha.ui.components.gamepad.settingsGamepadHints
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.scraper.ScraperPageContent
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.theme.FocusIndicatorDefaults
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaElevation
import com.wajiha.ui.theme.WajihaIconSize
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import com.wajiha.ui.theme.focusColorDisplayLabel
import com.wajiha.ui.theme.focusColorPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import wajiha.composeapp.generated.resources.Res
import wajiha.composeapp.generated.resources.kenney_controller_auto
import wajiha.composeapp.generated.resources.kenney_controller_ps
import wajiha.composeapp.generated.resources.kenney_controller_steam
import wajiha.composeapp.generated.resources.kenney_controller_steamdeck
import wajiha.composeapp.generated.resources.kenney_controller_switch
import wajiha.composeapp.generated.resources.kenney_controller_text
import wajiha.composeapp.generated.resources.kenney_controller_xbox

private enum class SettingsSection(
    val label: String,
) {
    Library("Library"),
    Scraper("Scraper"),
    Screens("Screens"),
    Appearance("Appearance"),
    System("System"),
}

private enum class LibraryFocusKind {
    Chrome,
    PlatformRow,
}

/**
 * Settings with a top section picker and scrollable grouped cards.
 * Cocoon-inspired grouped cards, inline help copy, and handheld gamepad UX.
 */
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAddPlatform: () -> Unit = {},
    onOpenPlatform: (String) -> Unit = {},
    onSectionChange: (String) -> Unit = {},
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val inUsePlatforms by settingsViewModel.inUsePlatforms.collectAsState()
    val folders by settingsViewModel.folders.collectAsState()
    val gameCountsByPlatform by settingsViewModel.gameCountsByPlatform.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val systemControls = koinInject<SystemControls>()
    val scraperViewModel = koinInject<ScraperViewModel>()
    val dualScreenStore = koinInject<DualScreenStore>()
    val feedback = LocalUiFeedback.current

    val sections = SettingsSection.entries
    var selectedSectionIndex by remember {
        mutableIntStateOf(
            (rememberedFocusContext("settings:section") as? Int)
                ?.coerceIn(0, sections.lastIndex)
                ?: 0,
        )
    }
    var libraryFocusKind by remember { mutableStateOf(LibraryFocusKind.Chrome) }
    var perms by remember { mutableStateOf(PermissionStates()) }
    val sectionFocus = remember { FocusRequester() }
    val sectionFocusRestorer =
        remember(selectedSectionIndex) {
            SettingSectionFocusRestorer("settings:section:$selectedSectionIndex")
        }
    val targetBeforeSectionFocus =
        remember(selectedSectionIndex) {
            rememberedFocusTarget("settings")
        }
    val emulatorLabels by settingsViewModel.emulatorLabelsByPlatform.collectAsState()

    fun selectSection(index: Int) {
        val newIndex = index.coerceIn(0, sections.lastIndex)
        feedback.tabSelect(selectedSectionIndex, newIndex)
        selectedSectionIndex = newIndex
        rememberFocusContext("settings:section", newIndex)
    }

    LaunchedEffect(selectedSectionIndex) {
        onSectionChange(sections[selectedSectionIndex].label)
        if (sections[selectedSectionIndex] != SettingsSection.Library) {
            libraryFocusKind = LibraryFocusKind.Chrome
        }
        // Don't clear the hero here — that blanks the top screen before the new
        // section's focused row republishes (double flash). Focus swap overwrites.
        withFrameNanos { }
        sectionFocusRestorer.restore(
            targetId = targetBeforeSectionFocus,
            fallback = sectionFocus,
        )
    }

    DisposableEffect(Unit) {
        onDispose { clearSettingsHero(dualScreenStore) }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            perms = systemControls.permissionStates()
            delay(400)
        }
    }

    ProvideSettingsDensity {
        WajihaScreen(
            layerId = "settings",
            modifier = modifier,
            onBack = onBack,
            showActionBar = true,
            gamepadHints =
                when {
                    sections[selectedSectionIndex] == SettingsSection.Library &&
                        libraryFocusKind == LibraryFocusKind.PlatformRow -> {
                        libraryPlatformRowGamepadHints(isDual = !settings.singleScreen)
                    }

                    sections[selectedSectionIndex] == SettingsSection.Library -> {
                        libraryChromeGamepadHints(isDual = !settings.singleScreen)
                    }

                    else -> {
                        settingsGamepadHints(isDual = !settings.singleScreen)
                    }
                },
            gamepadOwner = gamepadOwner,
            onClaimGamepad = onClaimGamepad,
            onOwnerGainedFocus = {
                sectionFocusRestorer.restore(
                    targetId = rememberedFocusTarget("settings"),
                    fallback = sectionFocus,
                )
            },
            onPreviewKey = { event ->
                if (GamepadLayers.stack.topLayer != "settings") return@WajihaScreen false
                when {
                    GamepadKeys.isL1(event.type, event.key) -> {
                        if (selectedSectionIndex > 0) {
                            selectSection(selectedSectionIndex - 1)
                            true
                        } else {
                            false
                        }
                    }

                    GamepadKeys.isR1(event.type, event.key) -> {
                        if (selectedSectionIndex < sections.lastIndex) {
                            selectSection(selectedSectionIndex + 1)
                            true
                        } else {
                            false
                        }
                    }

                    else -> {
                        false
                    }
                }
            },
        ) {
            val selectedSection = sections[selectedSectionIndex]
            WajihaFolderSettingChrome(
                tabs = sections.map { it.label },
                selectedIndex = selectedSectionIndex,
                onSelect = ::selectSection,
                onBack = onBack,
                focusRestorer = sectionFocusRestorer,
            ) {
                when (selectedSection) {
                    SettingsSection.Library -> {
                        LibrarySectionContent(
                            inUsePlatforms = inUsePlatforms,
                            folders = folders,
                            gameCountsByPlatform = gameCountsByPlatform,
                            emulatorLabels = emulatorLabels,
                            settings = settings,
                            dualScreenStore = dualScreenStore,
                            settingsViewModel = settingsViewModel,
                            onAddPlatform = onAddPlatform,
                            onOpenPlatform = onOpenPlatform,
                            onRescanLibrary = settingsViewModel::rescanLibrary,
                            onRescanPlatform = settingsViewModel::rescanPlatform,
                            onChromeFocused = {
                                libraryFocusKind = LibraryFocusKind.Chrome
                                val totalGames = gameCountsByPlatform.values.sum()
                                publishSettingsHero(
                                    dualScreenStore,
                                    libraryChromeHeroDetail(
                                        platformCount = inUsePlatforms.size,
                                        gameCount = totalGames,
                                        sectionOverview =
                                            "Systems with ROM folders. Add a platform, " +
                                                "edit folders, or rescan.",
                                    ),
                                )
                            },
                            onPlatformRowFocused = {
                                libraryFocusKind = LibraryFocusKind.PlatformRow
                            },
                            firstFocusRequester = sectionFocus,
                        )
                    }

                    SettingsSection.Scraper -> {
                        ScraperSectionContent(
                            scraperViewModel = scraperViewModel,
                            firstFocusRequester = sectionFocus,
                        )
                    }

                    SettingsSection.Screens -> {
                        ScreensSectionContent(
                            settings = settings,
                            settingsViewModel = settingsViewModel,
                            perms = perms,
                            systemControls = systemControls,
                            dualScreenStore = dualScreenStore,
                            firstFocusRequester = sectionFocus,
                        )
                    }

                    SettingsSection.Appearance -> {
                        AppearanceSectionContent(
                            settings = settings,
                            settingsViewModel = settingsViewModel,
                            dualScreenStore = dualScreenStore,
                            firstFocusRequester = sectionFocus,
                        )
                    }

                    SettingsSection.System -> {
                        SystemSectionContent(
                            settings = settings,
                            settingsViewModel = settingsViewModel,
                            perms = perms,
                            systemControls = systemControls,
                            firstFocusRequester = sectionFocus,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionContent(
    inUsePlatforms: List<PlatformEntity>,
    folders: List<RomFolderEntity>,
    gameCountsByPlatform: Map<String, Int>,
    emulatorLabels: Map<String, String>,
    settings: AppSettings,
    dualScreenStore: DualScreenStore,
    settingsViewModel: SettingsViewModel,
    onAddPlatform: () -> Unit,
    onOpenPlatform: (String) -> Unit,
    onRescanLibrary: () -> Unit,
    onRescanPlatform: (String) -> Unit,
    onChromeFocused: () -> Unit,
    onPlatformRowFocused: () -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    WajihaSettingBlurb(
        "Systems with at least one ROM folder. Edit a platform to change " +
            "folders, emulator, scraper ids, or hide it from the home filter.",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        GamepadButton(
            text = "Add platform",
            onClick = onAddPlatform,
            modifier = Modifier.weight(1f),
            focusRequester = firstFocusRequester,
            onFocusedChanged = { if (it) onChromeFocused() },
        )
        GamepadButton(
            text = "Rescan all",
            onClick = onRescanLibrary,
            outlined = true,
            modifier = Modifier.weight(1f),
            onFocusedChanged = { if (it) onChromeFocused() },
        )
    }
    if (inUsePlatforms.isEmpty()) {
        WajihaEmptyState(
            title = "No platforms yet",
            subtitle = "Add a platform, then pick a ROM folder to start building your library.",
            action = {
                GamepadButton(
                    text = "Add platform",
                    onClick = onAddPlatform,
                    focusRequester = firstFocusRequester,
                    onFocusedChanged = { if (it) onChromeFocused() },
                )
            },
        )
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
        ) {
            inUsePlatforms.forEach { platform ->
                val folderCount = folders.count { it.platformId == platform.id }
                val gameCount = gameCountsByPlatform[platform.id] ?: 0
                val paths =
                    folders
                        .filter { it.platformId == platform.id }
                        .map { folderDisplayPath(it) }
                PlatformLibraryRow(
                    platform = platform,
                    folderCount = folderCount,
                    gameCount = gameCount,
                    folderPaths = paths,
                    emulatorLabel = emulatorLabels[platform.id],
                    settings = settings,
                    dualScreenStore = dualScreenStore,
                    settingsViewModel = settingsViewModel,
                    onOpen = { onOpenPlatform(platform.id) },
                    onRescan = { onRescanPlatform(platform.id) },
                    onBecamePlatformRowFocus = onPlatformRowFocused,
                )
            }
        }
    }
}

@Composable
private fun ScraperSectionContent(
    scraperViewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
) {
    ScraperPageContent(scraperViewModel, firstFocusRequester)
}

@Composable
private fun ScreensSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    perms: PermissionStates,
    systemControls: SystemControls,
    dualScreenStore: DualScreenStore,
    firstFocusRequester: FocusRequester? = null,
) {
    val single = settings.singleScreen
    WajihaSettingBlurb(
        "Choose one screen or both. Single screen runs the combined launcher on the " +
            "main display only; dual uses top and bottom on clamshell handhelds.",
    )
    WajihaToggleSetting(
        label = "Single screen",
        description =
            "Use only the main display. Stops the bottom/secondary Wajiha launcher; " +
                "the other panel (if any) is left to the system. Change anytime here.",
        checked = single,
        onCheckedChange = settingsViewModel::setSingleScreen,
        defaultChecked = false,
        onReset = { settingsViewModel.setSingleScreen(false) },
        focusRequester = firstFocusRequester,
        onFocusedChanged =
            settingsToggleHeroFocus(
                store = dualScreenStore,
                title = "Single screen",
                subtitle =
                    "Use only the main display. Stops the bottom/secondary Wajiha launcher.",
                checked = single,
                showRoleDiagram = !single,
                kind = SettingsHeroKind.ScreensChrome,
            ),
    )
    if (single) {
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Show hero banner",
            description =
                "Preview artwork above the game grid. When off, the library fills the " +
                    "screen and filter / settings return to the top bar.",
            checked = settings.showHeroBanner,
            onCheckedChange = settingsViewModel::setShowHeroBanner,
            defaultChecked = true,
            onReset = { settingsViewModel.setShowHeroBanner(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Show selected game name",
            description =
                "Display the focused game's title above the library. Useful when the " +
                    "hero banner is hidden.",
            checked = settings.showSelectedGameName,
            onCheckedChange = settingsViewModel::setShowSelectedGameName,
            defaultChecked = false,
            onReset = { settingsViewModel.setShowSelectedGameName(false) },
        )
    }
    WajihaSettingDivider()
    WajihaToggleSetting(
        label = "Show Now Playing for games launched outside Wajiha",
        description =
            "Detect when another app launches a game and show Now Playing " +
                if (single) {
                    "on this display."
                } else {
                    "on the secondary display."
                },
        checked = settings.detectManualLaunches,
        onCheckedChange = settingsViewModel::setDetectManualLaunches,
        defaultChecked = true,
        onReset = { settingsViewModel.setDetectManualLaunches(true) },
    )
    WajihaSettingDivider()
    WajihaToggleSetting(
        label = "Close runaway games when memory is low",
        description =
            "Force-stop emulator sessions that balloon RAM so the " +
                "device stays responsive. May lose unsaved progress.",
        checked = settings.memoryGuardEnabled,
        onCheckedChange = settingsViewModel::setMemoryGuardEnabled,
        defaultChecked = true,
        onReset = { settingsViewModel.setMemoryGuardEnabled(true) },
    )
    if (settings.detectManualLaunches) {
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Identify game from emulator files",
            description =
                if (perms.allFilesAccess) {
                    "Read emulator config to match externally launched games to your library. " +
                        "Does not access your ROM folders."
                } else {
                    "Requires All files access (System section). Reads emulator config only."
                },
            checked = settings.romReconciliationEnabled && perms.allFilesAccess,
            onCheckedChange = { enabled ->
                if (enabled && !perms.allFilesAccess) {
                    systemControls.requestAllFilesAccess()
                } else {
                    settingsViewModel.setRomReconciliationEnabled(enabled)
                }
            },
            defaultChecked = false,
            onReset = { settingsViewModel.setRomReconciliationEnabled(false) },
        )
    }
    if (!single) {
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Black out unused display when a game starts",
            description =
                "Turn off the idle screen while a game runs for a cleaner play experience.",
            checked = settings.blackoutOnLaunch,
            onCheckedChange = settingsViewModel::setBlackoutOnLaunch,
            defaultChecked = false,
            onReset = { settingsViewModel.setBlackoutOnLaunch(false) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Swap screen roles (grid on top, hero on bottom)",
            description = "Flip which display shows the game grid versus hero artwork.",
            checked = settings.swapScreenRoles,
            onCheckedChange = { swapped ->
                // Adopt destination before the swap pref notifies Compose.
                dualScreenStore.onScreenRolesSwapped(menuOnPrimary = swapped)
                settingsViewModel.setSwapScreenRoles(swapped)
            },
            defaultChecked = false,
            onReset = {
                dualScreenStore.onScreenRolesSwapped(menuOnPrimary = false)
                settingsViewModel.setSwapScreenRoles(false)
            },
            onFocusedChanged =
                settingsToggleHeroFocus(
                    store = dualScreenStore,
                    title = "Swap screen roles",
                    subtitle = "Flip which display shows the game grid versus hero artwork.",
                    checked = settings.swapScreenRoles,
                    showRoleDiagram = true,
                    kind = SettingsHeroKind.ScreensChrome,
                ),
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Swap gamepad hints",
            description =
                "Show the controller hint bar on the hero display instead of under " +
                    "the game grid and menus.",
            checked = settings.swapGamepadHints,
            onCheckedChange = settingsViewModel::setSwapGamepadHints,
            defaultChecked = false,
            onReset = { settingsViewModel.setSwapGamepadHints(false) },
            onFocusedChanged =
                settingsToggleHeroFocus(
                    store = dualScreenStore,
                    title = "Swap gamepad hints",
                    subtitle =
                        "Show the controller hint bar on the hero display instead of under " +
                            "the game grid and menus.",
                    checked = settings.swapGamepadHints,
                    kind = SettingsHeroKind.ScreensChrome,
                ),
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Show focused setting on other display",
            description =
                "While Settings is open, the hero display explains the focused row — " +
                    "value, hints, and platform details.",
            checked = settings.settingsHeroHelp,
            onCheckedChange = { enabled ->
                settingsViewModel.setSettingsHeroHelp(enabled)
                if (!enabled) settingsViewModel.setSettingsHeroActions(false)
            },
            defaultChecked = true,
            onReset = {
                settingsViewModel.setSettingsHeroHelp(true)
            },
            onFocusedChanged =
                settingsToggleHeroFocus(
                    store = dualScreenStore,
                    title = "Show focused setting on other display",
                    subtitle =
                        "While Settings is open, the hero display explains the focused row.",
                    checked = settings.settingsHeroHelp,
                    showRoleDiagram = true,
                    kind = SettingsHeroKind.ScreensChrome,
                ),
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Show actions on other display",
            description =
                "Allow changing the focused setting from the hero display " +
                    "(options, Edit / Rescan). Requires focused-setting help.",
            checked = settings.settingsHeroActions,
            onCheckedChange = settingsViewModel::setSettingsHeroActions,
            defaultChecked = false,
            onReset = { settingsViewModel.setSettingsHeroActions(false) },
            enabled = settings.settingsHeroHelp,
            onFocusedChanged =
                settingsToggleHeroFocus(
                    store = dualScreenStore,
                    title = "Show actions on other display",
                    subtitle =
                        "Allow changing the focused setting from the hero display.",
                    checked = settings.settingsHeroActions,
                    valueText =
                        when {
                            !settings.settingsHeroHelp -> "Requires help on"
                            settings.settingsHeroActions -> "On"
                            else -> "Off"
                        },
                    kind = SettingsHeroKind.ScreensChrome,
                ),
        )
        WajihaSettingDivider()
        WajihaMultiChoiceSetting(
            label = "Bottom screen while a game runs",
            description =
                "Choose what the secondary display shows after you launch a game. " +
                    "Blackout turns the bottom screen off until you tap it.",
            choiceOptions =
                listOf(
                    MultiChoiceOption("NowPlaying", "Now Running", icon = "▶"),
                    MultiChoiceOption("QuickSettings", "Quick Settings", icon = "⚙"),
                    MultiChoiceOption("RunningApps", "Running Apps", icon = "▣"),
                    MultiChoiceOption("Clock", "Clock", icon = "◷"),
                    MultiChoiceOption(
                        value = "Off",
                        label = "Blackout",
                        description = "Screen off until you tap it.",
                        icon = "◼",
                    ),
                ),
            selected = settings.gameSecondaryMode,
            onSelect = settingsViewModel::setGameSecondaryMode,
            defaultValue = "NowPlaying",
            onReset = { settingsViewModel.setGameSecondaryMode("NowPlaying") },
        )
    }
    WajihaSettingDivider()
    WajihaMultiChoiceSetting(
        label = "Show active sessions",
        description =
            if (single) {
                "How running games appear on the library while you play. " +
                    "None hides session indicators; grid tiles sit in the game strip; " +
                    "the floating chip stays above the action bar."
            } else {
                "Choose how running games appear on the bottom screen while you play. " +
                    "None hides session indicators; grid tiles sit in the game strip; " +
                    "the floating chip stays above the action bar."
            },
        choiceOptions =
            listOf(
                MultiChoiceOption("None", "None", icon = "○"),
                MultiChoiceOption("GridTiles", "Grid tiles", icon = "▦"),
                MultiChoiceOption("FloatingChip", "Floating chip", icon = "◉"),
                MultiChoiceOption("Both", "Both", icon = "⊞"),
            ),
        selected = settings.nowPlayingDisplay,
        onSelect = settingsViewModel::setNowPlayingDisplay,
        defaultValue = "Both",
        onReset = { settingsViewModel.setNowPlayingDisplay("Both") },
    )
    WajihaSettingDivider()
    WajihaToggleSetting(
        label = "Focused game hero background",
        description =
            "Use the focused game's hero art as a full-screen background behind the game grid.",
        checked = settings.gameGridHeroBackground,
        onCheckedChange = settingsViewModel::setGameGridHeroBackground,
        defaultChecked = false,
        onReset = { settingsViewModel.setGameGridHeroBackground(false) },
    )
    WajihaSettingDivider()
    WajihaSettingBlurb(
        "Now Running page layout: hero backdrop and logo in place of the game title.",
    )
    WajihaToggleSetting(
        label = "Hero as background",
        description =
            "Full-bleed hero art (or box art) behind the Now Running panel. " +
                "Off keeps the solid background with a small box-art thumbnail.",
        checked = settings.nowPlayingHeroBackground,
        onCheckedChange = settingsViewModel::setNowPlayingHeroBackground,
        defaultChecked = true,
        onReset = { settingsViewModel.setNowPlayingHeroBackground(true) },
    )
    WajihaSettingDivider()
    WajihaToggleSetting(
        label = "Use logo for name",
        description =
            "Show the game logo instead of the title when scraped logo art is available. " +
                "Falls back to the game title when there is no logo.",
        checked = settings.nowPlayingLogo,
        onCheckedChange = settingsViewModel::setNowPlayingLogo,
        defaultChecked = true,
        onReset = { settingsViewModel.setNowPlayingLogo(true) },
    )
    if (!single) {
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Dim bottom screen while a game runs",
            description =
                "Darken the secondary display during gameplay. " +
                    "Blackout (Off) still turns the screen fully off.",
            checked = settings.gameDimEnabled,
            onCheckedChange = settingsViewModel::setGameDimEnabled,
            defaultChecked = false,
            onReset = { settingsViewModel.setGameDimEnabled(false) },
        )
        if (settings.gameDimEnabled) {
            WajihaSettingDivider()
            WajihaToggleSetting(
                label = "Dim only on Now Playing page",
                description =
                    "When on, the dim scrim applies only while the bottom screen shows Now Playing. " +
                        "When off, dim applies on any secondary screen except Blackout.",
                checked = settings.gameDimOnlyOnNowPlaying,
                onCheckedChange = settingsViewModel::setGameDimOnlyOnNowPlaying,
                defaultChecked = true,
                onReset = { settingsViewModel.setGameDimOnlyOnNowPlaying(true) },
            )
            WajihaSettingDivider()
            WajihaNumberSetting(
                label = "Dim strength",
                description =
                    "How dark the overlay is. 100% is near-black but still restores on tap.",
                value = settings.gameDimPercent,
                onValueChange = settingsViewModel::setGameDimPercent,
                range = 0..100,
                step = 10,
                valueLabel = { "$it%" },
                defaultValue = 90,
                onReset = { settingsViewModel.setGameDimPercent(90) },
            )
            WajihaSettingDivider()
            WajihaNumberSetting(
                label = "Dim after (seconds)",
                description =
                    "Wait before dimming after gameplay starts. Lifts while you use the bottom screen; " +
                        "fades back after the same idle time. 0 = immediate dim, stay lifted on interaction.",
                value = settings.gameplayDimTimeoutSeconds,
                onValueChange = settingsViewModel::setGameplayDimTimeoutSeconds,
                range = 0..120,
                step = 1,
                valueLabel = { if (it == 0) "0 (immediate)" else "$it s" },
                defaultValue = 10,
                onReset = { settingsViewModel.setGameplayDimTimeoutSeconds(10) },
            )
        }
    }
    // Dual-only hero piece toggles — hidden entirely in single-screen mode.
    if (!single) {
        WajihaSettingDivider()
        WajihaSettingBlurb(
            "Show or hide individual pieces of the top-screen game preview " +
                "(library focus and game Info).",
        )
        WajihaToggleSetting(
            label = "Backdrop / hero art",
            description = "Full-bleed background image behind the cover and metadata.",
            checked = settings.topHeroBackdrop,
            onCheckedChange = settingsViewModel::setTopHeroBackdrop,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroBackdrop(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Cover / box art",
            description = "Box art, video preview, or initials placeholder on the left.",
            checked = settings.topHeroCover,
            onCheckedChange = settingsViewModel::setTopHeroCover,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroCover(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Cover border",
            description = "Show border around game cover.",
            checked = settings.topHeroCoverBorder,
            onCheckedChange = settingsViewModel::setTopHeroCoverBorder,
            defaultChecked = false,
            onReset = { settingsViewModel.setTopHeroCoverBorder(false) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Logo overlay",
            description = "Game logo drawn over the bottom of the cover art.",
            checked = settings.topHeroLogo,
            onCheckedChange = settingsViewModel::setTopHeroLogo,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroLogo(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Platform icon",
            description = "Small platform icon beside the platform name on the library hero.",
            checked = settings.topHeroPlatformIcon,
            onCheckedChange = settingsViewModel::setTopHeroPlatformIcon,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroPlatformIcon(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Platform name",
            description = "Platform label under or beside the game title.",
            checked = settings.topHeroPlatform,
            onCheckedChange = settingsViewModel::setTopHeroPlatform,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroPlatform(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Title",
            description = "Game display name on the top screen.",
            checked = settings.topHeroTitle,
            onCheckedChange = settingsViewModel::setTopHeroTitle,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroTitle(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Metadata line",
            description =
                "Developer, year, genre, region, and age rating on the library hero; " +
                    "full metadata panel on Info.",
            checked = settings.topHeroMetadata,
            onCheckedChange = settingsViewModel::setTopHeroMetadata,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroMetadata(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Description",
            description = "Game synopsis / description text.",
            checked = settings.topHeroDescription,
            onCheckedChange = settingsViewModel::setTopHeroDescription,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroDescription(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Play stats",
            description = "Play count on the library hero; plays and play time on Info.",
            checked = settings.topHeroPlayStats,
            onCheckedChange = settingsViewModel::setTopHeroPlayStats,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroPlayStats(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Favorite badge",
            description = "★ Favorite label on the Info / game detail hero.",
            checked = settings.topHeroFavorite,
            onCheckedChange = settingsViewModel::setTopHeroFavorite,
            defaultChecked = true,
            onReset = { settingsViewModel.setTopHeroFavorite(true) },
        )
        WajihaSettingDivider()
        WajihaToggleSetting(
            label = "Section hint",
            description =
                "Controller / navigation hint under the title on Info " +
                    "(\"Launch, emulator… on the bottom screen\").",
            checked = settings.topHeroSectionHint,
            onCheckedChange = settingsViewModel::setTopHeroSectionHint,
            defaultChecked = false,
            onReset = { settingsViewModel.setTopHeroSectionHint(false) },
        )
    }
}

@Composable
private fun AppearanceSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    dualScreenStore: DualScreenStore,
    firstFocusRequester: FocusRequester? = null,
) {
    val iconPacks by settingsViewModel.iconPacks.collectAsState()
    LaunchedEffect(Unit) { settingsViewModel.refreshIconPacks() }
    val packOptions =
        remember(iconPacks) {
            iconPacks
                .map { pack ->
                    pack.packageName to pack.label
                }.ifEmpty {
                    listOf(IconAppearancePreferences.SYSTEM_PACK to "System icons")
                }
        }
    val selectedPackLabel =
        packOptions.firstOrNull { it.first == settings.iconPackPackage }?.second
            ?: if (settings.iconPackPackage.isEmpty()) {
                "System icons"
            } else {
                settings.iconPackPackage
            }

    WajihaSettingBlurb("Tune the home grid look, color theme, gamepad focus ring, and controller glyphs.")
    WajihaChoiceSetting(
        label = "Theme",
        description = "Follow the system setting or lock dark or light mode.",
        options =
            listOf(
                "system" to "System",
                "dark" to "Dark",
                "light" to "Light",
            ),
        selected = settings.theme,
        onSelect = settingsViewModel::setTheme,
        defaultValue = "dark",
        onReset = { settingsViewModel.setTheme("dark") },
        focusRequester = firstFocusRequester,
        onFocusedChanged = { focused ->
            if (focused) {
                val label =
                    when (settings.theme) {
                        "system" -> "System"
                        "light" -> "Light"
                        else -> "Dark"
                    }
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Theme",
                        subtitle = "Follow the system setting or lock dark or light mode.",
                        valueText = label,
                        previewKind = "theme",
                        previewPayload = label,
                        options =
                            if (settings.settingsHeroActions) {
                                listOf(
                                    SettingsHeroOption("system", "System", settings.theme == "system"),
                                    SettingsHeroOption("dark", "Dark", settings.theme == "dark"),
                                    SettingsHeroOption("light", "Light", settings.theme == "light"),
                                )
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setTheme
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    WajihaChoiceSetting(
        label = "Icon pack",
        description = "Apply an installed Android icon pack to the Apps drawer.",
        options = packOptions,
        selected = settings.iconPackPackage,
        onSelect = settingsViewModel::setIconPackPackage,
        defaultValue = IconAppearancePreferences.SYSTEM_PACK,
        onReset = { settingsViewModel.setIconPackPackage(IconAppearancePreferences.SYSTEM_PACK) },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Icon pack",
                        subtitle = "Apply an installed Android icon pack to the Apps drawer.",
                        valueText = selectedPackLabel,
                        options =
                            if (settings.settingsHeroActions) {
                                packOptions.map { (value, label) ->
                                    SettingsHeroOption(value, label, value == settings.iconPackPackage)
                                }
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setIconPackPackage
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    WajihaChoiceSetting(
        label = "Icon shape",
        description = "Clip shape for app icons in the Apps drawer.",
        options =
            IconAppearancePreferences.shapes.map { id ->
                id to IconAppearancePreferences.shapeLabel(id)
            },
        selected = settings.iconShape,
        onSelect = settingsViewModel::setIconShape,
        defaultValue = IconAppearancePreferences.DEFAULT_SHAPE,
        onReset = { settingsViewModel.setIconShape(IconAppearancePreferences.DEFAULT_SHAPE) },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Icon shape",
                        subtitle = "Clip shape for app icons in the Apps drawer.",
                        valueText = IconAppearancePreferences.shapeLabel(settings.iconShape),
                        options =
                            if (settings.settingsHeroActions) {
                                IconAppearancePreferences.shapes.map { id ->
                                    SettingsHeroOption(
                                        id,
                                        IconAppearancePreferences.shapeLabel(id),
                                        id == settings.iconShape,
                                    )
                                }
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setIconShape
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    WajihaNumberSetting(
        label = "Grid rows",
        description = "How many rows of games appear on the home grid.",
        value = settings.gridRows,
        onValueChange = settingsViewModel::setGridRows,
        range = 2..3,
        step = 1,
        valueLabel = { "$it rows" },
        defaultValue = 2,
        onReset = { settingsViewModel.setGridRows(2) },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Grid rows",
                        subtitle = "How many rows of games appear on the home grid.",
                        valueText = "${settings.gridRows} rows",
                        numberValue = settings.gridRows,
                        numberUnit = "rows",
                    ),
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    WajihaMultiChoiceSetting(
        label = "Focus ring style",
        description = "How the gamepad focus outline is drawn around tiles and settings rows.",
        choiceOptions =
            listOf(
                MultiChoiceOption("Solid", "Solid", icon = "▭"),
                MultiChoiceOption("Dotted", "Dotted", icon = "⋯"),
                MultiChoiceOption("Dashed", "Dashed", icon = "╌"),
                MultiChoiceOption("MarchingAnts", "Marching ants", icon = "▤"),
                MultiChoiceOption("Pulsing", "Pulsing", icon = "◎"),
                MultiChoiceOption("SoftPulse", "Soft pulse", icon = "◌"),
                MultiChoiceOption("Double", "Double", icon = "▢"),
                MultiChoiceOption("Glow", "Glow", icon = "◉"),
                MultiChoiceOption("Aura", "Aura", icon = "✧"),
                MultiChoiceOption("CornerBrackets", "Corner brackets", icon = "⌜"),
                MultiChoiceOption("GradientPulse", "Gradient pulse", icon = "◑"),
                MultiChoiceOption("Neon", "Neon", icon = "✦"),
            ),
        selected = settings.focusBorderStyle,
        onSelect = settingsViewModel::setFocusBorderStyle,
        defaultValue = FocusIndicatorDefaults.BORDER_STYLE,
        onReset = { settingsViewModel.setFocusBorderStyle(FocusIndicatorDefaults.BORDER_STYLE) },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Focus ring style",
                        subtitle = "How the gamepad focus outline is drawn around tiles and settings rows.",
                        valueText = settings.focusBorderStyle,
                        options =
                            if (settings.settingsHeroActions) {
                                listOf(
                                    "Solid",
                                    "Dotted",
                                    "Dashed",
                                    "MarchingAnts",
                                    "Pulsing",
                                    "SoftPulse",
                                    "Double",
                                    "Glow",
                                    "Aura",
                                    "CornerBrackets",
                                    "GradientPulse",
                                    "Neon",
                                ).map {
                                    SettingsHeroOption(it, it, it == settings.focusBorderStyle)
                                }
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setFocusBorderStyle
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    SettingsFocusColorRow(
        selected = settings.focusColor,
        onSelect = settingsViewModel::setFocusColor,
        defaultValue = FocusIndicatorDefaults.COLOR,
        onReset = { settingsViewModel.setFocusColor(FocusIndicatorDefaults.COLOR) },
        dualScreenStore = dualScreenStore,
        actionsEnabled = settings.settingsHeroActions,
    )
    WajihaSettingDivider()
    WajihaNumberSetting(
        label = "Focus ring thickness",
        description = "Border width in density-independent pixels for unfocused navigation.",
        value = settings.focusThickness,
        onValueChange = settingsViewModel::setFocusThickness,
        range = 1..3,
        step = 1,
        valueLabel = { "$it dp" },
        defaultValue = FocusIndicatorDefaults.THICKNESS,
        onReset = { settingsViewModel.setFocusThickness(FocusIndicatorDefaults.THICKNESS) },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Focus ring thickness",
                        subtitle = "Border width in density-independent pixels.",
                        valueText = "${settings.focusThickness} dp",
                        numberValue = settings.focusThickness,
                        numberUnit = "dp",
                    ),
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    WajihaChoiceSetting(
        label = "Focus ring placement",
        description = "Draw the focus ring inside item bounds or expand it outward beyond the edge.",
        options = listOf("Inside" to "Inside", "Outside" to "Outside"),
        selected = settings.focusPlacement,
        onSelect = settingsViewModel::setFocusPlacement,
        defaultValue = FocusIndicatorDefaults.PLACEMENT,
        onReset = { settingsViewModel.setFocusPlacement(FocusIndicatorDefaults.PLACEMENT) },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Focus ring placement",
                        subtitle = "Inside item bounds or expand outward beyond the edge.",
                        valueText = settings.focusPlacement,
                        options =
                            if (settings.settingsHeroActions) {
                                listOf(
                                    SettingsHeroOption("Inside", "Inside", settings.focusPlacement == "Inside"),
                                    SettingsHeroOption("Outside", "Outside", settings.focusPlacement == "Outside"),
                                )
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setFocusPlacement
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    ControllerGlyphSettings(
        settings = settings,
        settingsViewModel = settingsViewModel,
        dualScreenStore = dualScreenStore,
    )
}

@Composable
private fun ControllerGlyphSettings(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    dualScreenStore: DualScreenStore,
) {
    WajihaToggleSetting(
        label = "Controller glyphs",
        description =
            "Show Kenney Input Prompts icons in the bottom hint bar " +
                "(Xbox, PlayStation, Switch, Steam Deck, Steam Controller).",
        checked = settings.controllerGlyphsEnabled,
        onCheckedChange = settingsViewModel::setControllerGlyphsEnabled,
        defaultChecked = true,
        onReset = { settingsViewModel.setControllerGlyphsEnabled(true) },
        onFocusedChanged =
            settingsToggleHeroFocus(
                store = dualScreenStore,
                title = "Controller glyphs",
                subtitle = "Show Kenney Input Prompts icons in the bottom hint bar.",
                checked = settings.controllerGlyphsEnabled,
                previewKind = "glyphs",
                previewPayload = if (settings.controllerGlyphsEnabled) "On" else "Off",
            ),
    )
    WajihaSettingDivider()
    WajihaMultiChoiceSetting(
        label = "Glyph style",
        description =
            "Auto uses the last controller that pressed a button " +
                "(hides hints when none are connected). " +
                "Any other choice always shows that scheme.",
        choiceOptions =
            listOf(
                MultiChoiceOption(
                    "Auto",
                    "Auto",
                    iconRes = Res.drawable.kenney_controller_auto,
                ),
                MultiChoiceOption(
                    "Xbox",
                    "Xbox",
                    iconRes = Res.drawable.kenney_controller_xbox,
                ),
                MultiChoiceOption(
                    "PlayStation",
                    "PlayStation",
                    iconRes = Res.drawable.kenney_controller_ps,
                ),
                MultiChoiceOption(
                    "Switch",
                    "Switch",
                    iconRes = Res.drawable.kenney_controller_switch,
                ),
                MultiChoiceOption(
                    "SteamDeck",
                    "Steam Deck",
                    iconRes = Res.drawable.kenney_controller_steamdeck,
                ),
                MultiChoiceOption(
                    "SteamController",
                    "Steam Controller",
                    iconRes = Res.drawable.kenney_controller_steam,
                ),
                MultiChoiceOption(
                    "Text",
                    "Text",
                    iconRes = Res.drawable.kenney_controller_text,
                ),
            ),
        selected = settings.controllerGlyphScheme,
        onSelect = settingsViewModel::setControllerGlyphScheme,
        defaultValue = "Auto",
        onReset = { settingsViewModel.setControllerGlyphScheme("Auto") },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Glyph style",
                        subtitle = "Which controller icon scheme the hint bar uses.",
                        valueText = settings.controllerGlyphScheme,
                        previewKind = "glyphs",
                        previewPayload = settings.controllerGlyphScheme,
                        options =
                            if (settings.settingsHeroActions) {
                                listOf(
                                    "Auto",
                                    "Xbox",
                                    "PlayStation",
                                    "Switch",
                                    "SteamDeck",
                                    "SteamController",
                                    "Text",
                                ).map {
                                    SettingsHeroOption(it, it, it == settings.controllerGlyphScheme)
                                }
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setControllerGlyphScheme
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    WajihaMultiChoiceSetting(
        label = "Face button glyphs",
        description =
            "Kenney look for A/B/X/Y (and PlayStation shapes). " +
                "Switch / Steam Deck Color reuses Xbox lettered color faces.",
        choiceOptions =
            listOf(
                MultiChoiceOption("Color", "Color", icon = "●"),
                MultiChoiceOption("ColorOutline", "Color outline", icon = "○"),
                MultiChoiceOption("White", "White", icon = "◻"),
                MultiChoiceOption("Dark", "Dark", icon = "◼"),
            ),
        selected = settings.controllerGlyphFaceStyle,
        onSelect = settingsViewModel::setControllerGlyphFaceStyle,
        defaultValue = "Color",
        onReset = { settingsViewModel.setControllerGlyphFaceStyle("Color") },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Face button glyphs",
                        subtitle = "Kenney look for A/B/X/Y (and PlayStation shapes).",
                        valueText = settings.controllerGlyphFaceStyle,
                        previewKind = "glyphs",
                        previewPayload = settings.controllerGlyphFaceStyle,
                        options =
                            if (settings.settingsHeroActions) {
                                listOf("Color", "ColorOutline", "White", "Dark").map {
                                    SettingsHeroOption(it, it, it == settings.controllerGlyphFaceStyle)
                                }
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setControllerGlyphFaceStyle
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
    WajihaSettingDivider()
    WajihaMultiChoiceSetting(
        label = "Other button glyphs",
        description = "Kenney look for shoulders, triggers, d-pad, and system buttons.",
        choiceOptions =
            listOf(
                MultiChoiceOption("Filled", "Filled", icon = "◼"),
                MultiChoiceOption("Outline", "Outline", icon = "◻"),
            ),
        selected = settings.controllerGlyphOtherStyle,
        onSelect = settingsViewModel::setControllerGlyphOtherStyle,
        defaultValue = "Filled",
        onReset = { settingsViewModel.setControllerGlyphOtherStyle("Filled") },
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualScreenStore,
                    genericSettingHeroDetail(
                        title = "Other button glyphs",
                        subtitle = "Kenney look for shoulders, triggers, d-pad, and system buttons.",
                        valueText = settings.controllerGlyphOtherStyle,
                        previewKind = "glyphs",
                        previewPayload = settings.controllerGlyphOtherStyle,
                        options =
                            if (settings.settingsHeroActions) {
                                listOf("Filled", "Outline").map {
                                    SettingsHeroOption(it, it, it == settings.controllerGlyphOtherStyle)
                                }
                            } else {
                                emptyList()
                            },
                    ),
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            settingsViewModel::setControllerGlyphOtherStyle
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
    )
}

@Composable
private fun SystemSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    perms: PermissionStates,
    systemControls: SystemControls,
    firstFocusRequester: FocusRequester? = null,
) {
    WajihaSettingBlurb(
        "Permissions, launcher role, navigation feedback, and build info.",
    )
    SettingsVersionRow(versionLabel = systemControls.appVersionLabel())
    WajihaSettingDivider()
    WajihaToggleSetting(
        label = "Navigation sounds",
        description = "Play sounds when moving focus and confirming actions.",
        checked = settings.soundsEnabled,
        onCheckedChange = settingsViewModel::setSoundsEnabled,
        defaultChecked = true,
        onReset = { settingsViewModel.setSoundsEnabled(true) },
        focusRequester = firstFocusRequester,
    )
    WajihaSettingDivider()
    SettingsPermissionRow(
        label = "Usage access",
        description = "Follow games launched outside Wajiha on the bottom screen.",
        granted = perms.usageAccess,
        onRequest = systemControls::requestUsageAccess,
    )
    WajihaSettingDivider()
    SettingsPermissionRow(
        label = "All files access",
        description = "Read emulator data to identify games launched outside Wajiha.",
        granted = perms.allFilesAccess,
        onRequest = systemControls::requestAllFilesAccess,
    )
    WajihaSettingDivider()
    SettingsPermissionRow(
        label = "Notifications",
        description = "Progress while scanning and scraping your library.",
        granted = perms.notifications,
        onRequest = systemControls::requestNotifications,
    )
    WajihaSettingDivider()
    SettingsLauncherRow(
        isDefaultLauncher = perms.isDefaultLauncher,
        onSetDefault = systemControls::openHomeSettings,
    )
    WajihaSettingDivider()
    IgnoreFileNamePatternsSection(
        settings = settings,
        settingsViewModel = settingsViewModel,
    )
}

@Composable
private fun IgnoreFileNamePatternsSection(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
) {
    val patterns = settings.ignoreFileNamePatterns
    val patternsAtDefault = SettingsRepository.ignoreFileNamePatternsAtDefault(patterns)
    var newPatternDraft by remember { mutableStateOf("") }
    var showAddField by remember { mutableStateOf(false) }

    WajihaToggleSetting(
        label = "Ignore files by name pattern",
        description = "Skip ROMs whose filename or folder path contains listed keywords during library scan.",
        checked = settings.ignorePatternFilesEnabled,
        onCheckedChange = settingsViewModel::setIgnorePatternFilesEnabled,
        defaultChecked = false,
        onReset = { settingsViewModel.setIgnorePatternFilesEnabled(false) },
    )

    if (!settings.ignorePatternFilesEnabled) return

    Text(
        text = "Matched files are excluded from scan and removed from the library on the next full rescan.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = WajihaSpacing.sm),
    )

    if (patterns.isEmpty()) {
        Text(
            text = "No patterns — add keywords below.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
        )
    } else {
        patterns.forEach { pattern ->
            GamepadSettingRow(
                label = pattern,
                description = "Remove this keyword from the ignore list.",
                type = SettingType.Action,
                onActivate = { settingsViewModel.removeIgnoreFileNamePattern(pattern) },
                content = {
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }

    if (showAddField) {
        GamepadSafeTextField(
            value = newPatternDraft,
            onValueChange = { newPatternDraft = it },
            label = "New keyword",
            modifier = Modifier.fillMaxWidth(),
        )
        GamepadSettingRow(
            label = "Add keyword",
            description = "Press A to add \"$newPatternDraft\" to the ignore list.",
            type = SettingType.Action,
            onActivate = {
                val trimmed = newPatternDraft.trim()
                if (trimmed.isNotEmpty()) {
                    settingsViewModel.addIgnoreFileNamePattern(trimmed)
                    newPatternDraft = ""
                    showAddField = false
                }
            },
            isAtDefault = true,
        )
        GamepadSettingRow(
            label = "Cancel",
            type = SettingType.Action,
            onActivate = {
                newPatternDraft = ""
                showAddField = false
            },
            isAtDefault = true,
        )
    } else {
        GamepadSettingRow(
            label = "Add keyword",
            description = "Add a filename or path substring to ignore during scan.",
            type = SettingType.Action,
            onActivate = { showAddField = true },
            onReset = settingsViewModel::resetIgnoreFileNamePatterns,
            isAtDefault = patternsAtDefault,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsFocusColorRow(
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    dualScreenStore: DualScreenStore,
    actionsEnabled: Boolean,
    focusRequester: FocusRequester? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var headerFocused by remember { mutableStateOf(false) }
    val useCustomNav = com.wajiha.input.LocalGamepadNavController.current != null
    val headerHighlight = !useCustomNav && headerFocused && !expanded
    val isCustomSelected = FocusIndicatorPreferenceValues.isCustomHex(selected)
    val isAtDefault = FocusIndicatorPreferenceValues.normalizeColor(selected) == defaultValue
    val canReset = !isAtDefault
    val selectedLabel = focusColorDisplayLabel(selected)
    val previewColor = focusColorPreview(selected)

    val headerFocusRequester = focusRequester ?: remember { FocusRequester() }
    val sectionScroll = LocalSettingSectionScroll.current
    var headerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    val presetIds = FocusIndicatorPreferenceValues.colors
    val swatchCount = presetIds.size + 1
    val swatchFocusRequesters = remember(swatchCount) { List(swatchCount) { FocusRequester() } }

    var customHexDraft by remember(selected) {
        mutableStateOf(
            if (isCustomSelected) selected.removePrefix("#") else "FF5722",
        )
    }
    var showCustomField by remember { mutableStateOf(isCustomSelected) }

    fun collapse() {
        expanded = false
        try {
            headerFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun expand() {
        expanded = true
    }

    fun selectPreset(presetId: String) {
        showCustomField = false
        onSelect(presetId)
        collapse()
    }

    fun applyCustomHex() {
        val normalized = customHexDraft.trim().removePrefix("#").uppercase()
        if (normalized.length == 6 && FocusIndicatorPreferenceValues.parseHexColor("#$normalized") != null) {
            onSelect("#$normalized")
            collapse()
        }
    }

    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        val header = headerCoordinates
        if (sectionScroll != null && header != null) {
            sectionScroll.scrollHeaderToTop(header)
        }
        withFrameNanos { }
        val focusIndex =
            when {
                isCustomSelected -> {
                    presetIds.size
                }

                else -> {
                    presetIds
                        .indexOfFirst {
                            it.equals(FocusIndicatorPreferenceValues.normalizeColor(selected), ignoreCase = true)
                        }.coerceAtLeast(0)
                }
            }
        try {
            swatchFocusRequesters[focusIndex].requestFocus()
        } catch (_: Exception) {
        }
    }

    BackHandler(enabled = expanded) {
        collapse()
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = LocalSettingRowMinHeight.current),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerCoordinates = it }
                    .wajihaFocusIndicator(highlighted = headerHighlight)
                    .clip(WajihaShapes.focus)
                    .focusRequester(headerFocusRequester)
                    .then(
                        if (!useCustomNav) {
                            Modifier
                                .onFocusChanged { state ->
                                    headerFocused = state.isFocused
                                    if (state.isFocused) {
                                        publishSettingsHero(
                                            dualScreenStore,
                                            genericSettingHeroDetail(
                                                title = "Focus color",
                                                subtitle = "Color of the gamepad focus ring.",
                                                valueText = selectedLabel,
                                                previewKind = "focusColor",
                                                previewPayload = selected,
                                                options =
                                                    if (actionsEnabled) {
                                                        FocusIndicatorPreferenceValues.colors.map { id ->
                                                            SettingsHeroOption(
                                                                id,
                                                                FocusIndicatorPreferenceValues.displayColorLabel(id),
                                                                FocusIndicatorPreferenceValues.normalizeColor(selected) == id,
                                                            )
                                                        }
                                                    } else {
                                                        emptyList()
                                                    },
                                            ),
                                            onSelectOption = if (actionsEnabled) onSelect else null,
                                        )
                                    } else {
                                        clearSettingsHero(dualScreenStore)
                                    }
                                }.wajihaGamepadFocus()
                                .onPreviewKeyEvent { event ->
                                    when {
                                        GamepadKeys.isConfirm(event.type, event.key) -> {
                                            if (expanded) collapse() else expand()
                                            true
                                        }

                                        GamepadKeys.isY(event.type, event.key) && canReset -> {
                                            onReset()
                                            true
                                        }

                                        else -> {
                                            false
                                        }
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ).pointerInput(expanded) {
                        detectTapGestures {
                            if (expanded) collapse() else expand()
                        }
                    }.padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                Text(
                    text = "Focus ring color",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (canReset) {
                    Text(
                        text = "↺",
                        style = MaterialTheme.typography.titleMedium,
                        color =
                            WajihaColors.OverrideAmber.copy(
                                alpha = WajihaAlphas.overrideAmberStrong,
                            ),
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier.pointerInput(onReset) {
                                detectTapGestures { onReset() }
                            },
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(WajihaIconSize.sm)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(
                            WajihaSpacing.folderEdge,
                            MaterialTheme.colorScheme.outline.copy(
                                alpha = WajihaAlphas.outlineSubtle,
                            ),
                            CircleShape,
                        ),
            )
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Text(
                text = if (expanded) "˅" else "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = "Theme accent follows light or dark mode. Pick a swatch or enter a custom #RRGGBB hex.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    start = WajihaSpacing.sm,
                    end = WajihaSpacing.sm,
                    top = WajihaSpacing.xs / 2,
                ),
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = WajihaSpacing.xs / 2),
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = WajihaElevation.low,
            ) {
                Column(
                    modifier = Modifier.padding(WajihaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                    ) {
                        presetIds.forEachIndexed { index, presetId ->
                            val isSelected =
                                !isCustomSelected &&
                                    presetId.equals(
                                        FocusIndicatorPreferenceValues.normalizeColor(selected),
                                        ignoreCase = true,
                                    )
                            FocusColorSwatch(
                                label = FocusIndicatorPreferenceValues.displayColorLabel(presetId),
                                color = focusColorPreview(presetId),
                                selected = isSelected,
                                focusRequester = swatchFocusRequesters[index],
                                useCustomNav = useCustomNav,
                                onSelect = { selectPreset(presetId) },
                            )
                        }
                        FocusColorSwatch(
                            label = "Custom",
                            color =
                                if (isCustomSelected) {
                                    focusColorPreview(selected)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            selected = isCustomSelected || showCustomField,
                            focusRequester = swatchFocusRequesters[presetIds.size],
                            useCustomNav = useCustomNav,
                            showPlusGlyph = !isCustomSelected,
                            onSelect = {
                                showCustomField = true
                            },
                        )
                    }

                    if (showCustomField || isCustomSelected) {
                        GamepadSafeTextField(
                            value = customHexDraft,
                            onValueChange = { raw ->
                                customHexDraft =
                                    raw
                                        .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                                        .take(6)
                                        .uppercase()
                            },
                            label = "Hex color (#RRGGBB)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        GamepadSettingRow(
                            label = "Apply custom color",
                            type = SettingType.Action,
                            onActivate = ::applyCustomHex,
                            isAtDefault = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusColorSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    focusRequester: FocusRequester,
    useCustomNav: Boolean,
    showPlusGlyph: Boolean = false,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val highlight = !useCustomNav && focused

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.micro),
        modifier =
            Modifier
                .wajihaFocusIndicator(highlighted = highlight, shape = CircleShape)
                .clip(CircleShape)
                .focusRequester(focusRequester)
                .then(
                    if (!useCustomNav) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .wajihaGamepadFocus()
                            .onPreviewKeyEvent { event ->
                                if (GamepadKeys.isConfirm(event.type, event.key)) {
                                    onSelect()
                                    true
                                } else {
                                    false
                                }
                            }
                    } else {
                        Modifier
                    },
                ).pointerInput(label) {
                    detectTapGestures { onSelect() }
                }.padding(WajihaSpacing.xs / 2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(WajihaIconSize.xxl)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) WajihaSpacing.micro else WajihaSpacing.folderEdge,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(
                                    alpha = WajihaAlphas.outlineMuted,
                                )
                            },
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (showPlusGlyph) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlatformLibraryRow(
    platform: PlatformEntity,
    folderCount: Int,
    gameCount: Int,
    folderPaths: List<String>,
    emulatorLabel: String?,
    settings: AppSettings,
    dualScreenStore: DualScreenStore,
    settingsViewModel: SettingsViewModel,
    onOpen: () -> Unit,
    onRescan: () -> Unit,
    onBecamePlatformRowFocus: () -> Unit = {},
) {
    var rowFocused by remember { mutableStateOf(false) }
    val boxarts by settingsViewModel.boxartSampleFor(platform.id, 6).collectAsState(initial = emptyList())
    val emulators by settingsViewModel.observeEmulators(platform.id).collectAsState(initial = emptyList())
    val stats =
        buildString {
            append("$folderCount folder(s) · $gameCount game(s)")
            if (!platform.enabled) append(" · disabled")
        }
    val emulatorOptions =
        remember(emulators, platform.defaultEmulatorId) {
            emulators.map { emu ->
                SettingsHeroOption(
                    value = emu.id,
                    label = emu.name,
                    selected = emu.id == platform.defaultEmulatorId || (platform.defaultEmulatorId == null && emu.isDefault),
                )
            }
        }
    GamepadSettingRow(
        label = platform.name,
        labelMeta = stats,
        type = SettingType.Action,
        onActivate = onRescan,
        onSecondaryActivate = onOpen,
        onFocusedChanged = { focused ->
            rowFocused = focused
            if (focused) {
                onBecamePlatformRowFocus()
                publishSettingsHero(
                    store = dualScreenStore,
                    detail =
                        platformRowHeroDetail(
                            name = platform.name,
                            platformId = platform.id,
                            enabled = platform.enabled,
                            folderCount = folderCount,
                            gameCount = gameCount,
                            folderPaths = folderPaths,
                            emulatorLabel = emulatorLabel,
                            boxartPaths = boxarts,
                            actionsEnabled = settings.settingsHeroActions,
                            emulatorOptions = emulatorOptions,
                            shortName = platform.shortName,
                            extensions = platform.extensions,
                            screenScraperId = platform.screenScraperId,
                            raConsoleId = platform.raConsoleId,
                        ),
                    onPrimaryAction = onRescan,
                    onSecondaryAction = onOpen,
                    onSelectOption =
                        if (settings.settingsHeroActions) {
                            { emulatorId ->
                                settingsViewModel.setPlatformDefaultEmulator(platform.id, emulatorId)
                            }
                        } else {
                            null
                        },
                )
            } else {
                clearSettingsHero(dualScreenStore)
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (!platform.enabled) Modifier.alpha(0.55f) else Modifier),
        content = {
            GamepadSettingTrailingActions(
                secondaryLabel = "Edit",
                onSecondaryClick = onOpen,
                primaryLabel = "Rescan",
                onPrimaryClick = onRescan,
                showHints = rowFocused,
            )
        },
    )
}

@Composable
private fun SettingsVersionRow(versionLabel: String) {
    GamepadSettingRow(
        label = "App version",
        description = "Installed Wajiha build on this device.",
        type = SettingType.Action,
        content = {
            Text(
                text = versionLabel.removePrefix("Wajiha ").ifBlank { versionLabel },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun SettingsPermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.Action,
        onActivate = if (!granted) onRequest else null,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(WajihaSpacing.sm)
                            .clip(CircleShape)
                            .background(
                                if (granted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(
                                        alpha = WajihaAlphas.outlineStrong,
                                    )
                                },
                            ),
                )
                Text(
                    text = if (granted) "Granted" else "Grant ›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
    )
}

@Composable
private fun SettingsLauncherRow(
    isDefaultLauncher: Boolean,
    onSetDefault: () -> Unit,
) {
    GamepadSettingRow(
        label = "Default launcher",
        description =
            if (isDefaultLauncher) {
                "Wajiha is your home screen on both displays."
            } else {
                "Set Wajiha as the default launcher so HOME always returns here."
            },
        type = SettingType.Action,
        onActivate = if (!isDefaultLauncher) onSetDefault else null,
        content = {
            Text(
                text = if (isDefaultLauncher) "Active" else "Set ›",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color =
                    if (isDefaultLauncher) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        },
    )
}
