package com.wajiha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.platform.PermissionStates
import com.wajiha.platform.SystemControls
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.ui.components.FolderTabRow
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadFocusable
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.MultiChoiceOption
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.SettingSectionScrollColumn
import com.wajiha.ui.components.gamepad.settingsGamepadHints
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import com.wajiha.data.prefs.FocusIndicatorPreferenceValues
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.LocalSettingSectionScroll
import com.wajiha.ui.components.gamepad.scrollHeaderToTop
import com.wajiha.ui.theme.FocusIndicatorDefaults
import com.wajiha.ui.theme.focusColorDisplayLabel
import com.wajiha.ui.theme.focusColorPreview
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import kotlinx.coroutines.delay
import com.wajiha.ui.scraper.ScraperPageContent
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject

private enum class SettingsSection(val label: String) {
    Library("Library"),
    Scraper("Scraper"),
    DualScreen("Dual screen"),
    Appearance("Appearance"),
    System("System")
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
    modifier: Modifier = Modifier
) {
    val inUsePlatforms by settingsViewModel.inUsePlatforms.collectAsState()
    val folders by settingsViewModel.folders.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val systemControls = koinInject<SystemControls>()
    val scraperViewModel = koinInject<ScraperViewModel>()
    val feedback = LocalUiFeedback.current

    val sections = SettingsSection.entries
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    var perms by remember { mutableStateOf(PermissionStates()) }
    val sectionFocus = remember { FocusRequester() }

    fun selectSection(index: Int) {
        val newIndex = index.coerceIn(0, sections.lastIndex)
        feedback.tabSelect(selectedSectionIndex, newIndex)
        selectedSectionIndex = newIndex
    }

    LaunchedEffect(selectedSectionIndex) {
        onSectionChange(sections[selectedSectionIndex].label)
        try {
            sectionFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            perms = systemControls.permissionStates()
            delay(400)
        }
    }

    WajihaScreen(
        layerId = "settings",
        modifier = modifier,
        onBack = onBack,
        showActionBar = true,
        gamepadHints = settingsGamepadHints,
        onPreviewKey = { event ->
            if (GamepadLayers.stack.topLayer != "settings") return@WajihaScreen false
            when {
                GamepadKeys.isL1(event.type, event.key) -> {
                    if (selectedSectionIndex > 0) {
                        selectSection(selectedSectionIndex - 1)
                        true
                    } else false
                }
                GamepadKeys.isR1(event.type, event.key) -> {
                    if (selectedSectionIndex < sections.lastIndex) {
                        selectSection(selectedSectionIndex + 1)
                        true
                    } else false
                }
                else -> false
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WajihaToolbar(title = "Wajiha Settings", onBack = onBack, backFocusable = false)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = WajihaSpacing.md)
            ) {
                FolderTabRow(
                    tabs = sections.map { it.label },
                    selectedIndex = selectedSectionIndex,
                    onSelect = ::selectSection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false }
                        .padding(
                            top = WajihaSpacing.sm,
                            bottom = 0.dp
                        )
                )

                val selectedSection = sections[selectedSectionIndex]
                SettingsSectionCard(
                    folderPanel = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .offset(y = (-1).dp)
                        .padding(bottom = WajihaSpacing.md)
                ) {
                    when (selectedSection) {
                        SettingsSection.Library -> {
                            LibrarySectionContent(
                                inUsePlatforms = inUsePlatforms,
                                folders = folders,
                                onAddPlatform = onAddPlatform,
                                onOpenPlatform = onOpenPlatform,
                                onRescanLibrary = settingsViewModel::rescanLibrary,
                                onRescanPlatform = settingsViewModel::rescanPlatform,
                                firstFocusRequester = sectionFocus
                            )
                        }
                        SettingsSection.Scraper -> {
                            ScraperSectionContent(
                                scraperViewModel = scraperViewModel,
                                firstFocusRequester = sectionFocus
                            )
                        }
                        SettingsSection.DualScreen -> {
                            DualScreenSectionContent(
                                settings = settings,
                                settingsViewModel = settingsViewModel,
                                perms = perms,
                                systemControls = systemControls,
                                firstFocusRequester = sectionFocus
                            )
                        }
                        SettingsSection.Appearance -> {
                            AppearanceSectionContent(
                                settings = settings,
                                settingsViewModel = settingsViewModel,
                                firstFocusRequester = sectionFocus
                            )
                        }
                        SettingsSection.System -> {
                            SystemSectionContent(
                                settings = settings,
                                settingsViewModel = settingsViewModel,
                                perms = perms,
                                systemControls = systemControls,
                                firstFocusRequester = sectionFocus
                            )
                        }
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
    onAddPlatform: () -> Unit,
    onOpenPlatform: (String) -> Unit,
    onRescanLibrary: () -> Unit,
    onRescanPlatform: (String) -> Unit,
    firstFocusRequester: FocusRequester? = null
) {
    SettingsSectionBlurb(
        "Manage platforms, ROM folders, and emulators. " +
            "Only systems you've added appear here."
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
    ) {
        GamepadButton(
            text = "Add platform",
            onClick = onAddPlatform,
            modifier = Modifier.weight(1f),
            focusRequester = firstFocusRequester
        )
        GamepadButton(
            text = "Rescan all",
            onClick = onRescanLibrary,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
    }
    if (inUsePlatforms.isEmpty()) {
        WajihaEmptyState(
            title = "No platforms yet",
            subtitle = "Add a platform to start building your library.",
            action = {
                GamepadButton(
                    text = "Add platform",
                    onClick = onAddPlatform,
                    focusRequester = firstFocusRequester
                )
            }
        )
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
        ) {
            inUsePlatforms.forEach { platform ->
                val platformFolders = folders.filter { it.platformId == platform.id }
                PlatformCard(
                    platform = platform,
                    folders = platformFolders,
                    onOpen = { onOpenPlatform(platform.id) },
                    onRescan = { onRescanPlatform(platform.id) }
                )
            }
        }
    }
}

@Composable
private fun ScraperSectionContent(
    scraperViewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null
) {
    ScraperPageContent(scraperViewModel, firstFocusRequester)
}

@Composable
private fun DualScreenSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    perms: PermissionStates,
    systemControls: SystemControls,
    firstFocusRequester: FocusRequester? = null
) {
    SettingsSectionBlurb(
        "Control how Wajiha uses the top and bottom displays on clamshell handhelds."
    )
    SettingsToggleRow(
        label = "Black out unused display when a game starts",
        description = "Turn off the idle screen while a game runs for a cleaner play experience.",
        checked = settings.blackoutOnLaunch,
        onCheckedChange = settingsViewModel::setBlackoutOnLaunch,
        defaultChecked = false,
        onReset = { settingsViewModel.setBlackoutOnLaunch(false) },
        focusRequester = firstFocusRequester
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Show Now Playing for games launched outside Wajiha",
        description = "Detect when another app launches a game and " +
            "show Now Playing on the secondary display.",
        checked = settings.detectManualLaunches,
        onCheckedChange = settingsViewModel::setDetectManualLaunches,
        defaultChecked = true,
        onReset = { settingsViewModel.setDetectManualLaunches(true) }
    )
    if (settings.detectManualLaunches) {
        SettingsGroupDivider()
        SettingsToggleRow(
            label = "Identify game from emulator files",
            description = if (perms.allFilesAccess) {
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
            onReset = { settingsViewModel.setRomReconciliationEnabled(false) }
        )
    }
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Swap screen roles (grid on top, hero on bottom)",
        description = "Flip which display shows the game grid versus hero artwork.",
        checked = settings.swapScreenRoles,
        onCheckedChange = settingsViewModel::setSwapScreenRoles,
        defaultChecked = false,
        onReset = { settingsViewModel.setSwapScreenRoles(false) }
    )
    SettingsGroupDivider()
    SettingsMultiChoiceRow(
        label = "Bottom screen while a game runs",
        description = "Choose what the secondary display shows after you launch a game. " +
            "Blackout turns the bottom screen off until you tap it.",
        choiceOptions = listOf(
            MultiChoiceOption("NowPlaying", "Now Running", icon = "▶"),
            MultiChoiceOption("QuickSettings", "Quick Settings", icon = "⚙"),
            MultiChoiceOption("RunningApps", "Running Apps", icon = "▣"),
            MultiChoiceOption("Achievements", "Achievements", icon = "★"),
            MultiChoiceOption("Clock", "Clock", icon = "◷"),
            MultiChoiceOption(
                value = "Off",
                label = "Blackout",
                description = "Screen off until you tap it.",
                icon = "◼"
            )
        ),
        selected = settings.gameSecondaryMode,
        onSelect = settingsViewModel::setGameSecondaryMode,
        defaultValue = "NowPlaying",
        onReset = { settingsViewModel.setGameSecondaryMode("NowPlaying") }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Dim bottom screen while a game runs",
        description = "Darken the secondary display during gameplay. " +
            "Blackout (Off) still turns the screen fully off.",
        checked = settings.gameDimEnabled,
        onCheckedChange = settingsViewModel::setGameDimEnabled,
        defaultChecked = false,
        onReset = { settingsViewModel.setGameDimEnabled(false) }
    )
    if (settings.gameDimEnabled) {
        SettingsGroupDivider()
        SettingsNumberRow(
            label = "Dim strength",
            description = "How dark the overlay is. 100% is near-black but still restores on tap.",
            value = settings.gameDimPercent,
            onValueChange = settingsViewModel::setGameDimPercent,
            range = 0..100,
            step = 10,
            valueLabel = { "$it%" },
            defaultValue = 90,
            onReset = { settingsViewModel.setGameDimPercent(90) }
        )
        SettingsGroupDivider()
        SettingsNumberRow(
            label = "Dim after (seconds)",
            description = "Wait before dimming after gameplay starts. Lifts while you use the bottom screen; " +
                "fades back after the same idle time. 0 = immediate dim, stay lifted on interaction.",
            value = settings.gameplayDimTimeoutSeconds,
            onValueChange = settingsViewModel::setGameplayDimTimeoutSeconds,
            range = 0..120,
            step = 1,
            valueLabel = { if (it == 0) "0 (immediate)" else "$it s" },
            defaultValue = 10,
            onReset = { settingsViewModel.setGameplayDimTimeoutSeconds(10) }
        )
    }
    SettingsGroupDivider()
    SettingsSectionBlurb(
        "Show or hide individual pieces of the top-screen game preview " +
            "(library focus and game Info)."
    )
    SettingsToggleRow(
        label = "Backdrop / hero art",
        description = "Full-bleed background image behind the cover and metadata.",
        checked = settings.topHeroBackdrop,
        onCheckedChange = settingsViewModel::setTopHeroBackdrop,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroBackdrop(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Cover / box art",
        description = "Box art, video preview, or initials placeholder on the left.",
        checked = settings.topHeroCover,
        onCheckedChange = settingsViewModel::setTopHeroCover,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroCover(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Cover border",
        description = "Show border around game cover.",
        checked = settings.topHeroCoverBorder,
        onCheckedChange = settingsViewModel::setTopHeroCoverBorder,
        defaultChecked = false,
        onReset = { settingsViewModel.setTopHeroCoverBorder(false) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Logo overlay",
        description = "Game logo drawn over the bottom of the cover art.",
        checked = settings.topHeroLogo,
        onCheckedChange = settingsViewModel::setTopHeroLogo,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroLogo(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Platform icon",
        description = "Small platform icon beside the platform name on the library hero.",
        checked = settings.topHeroPlatformIcon,
        onCheckedChange = settingsViewModel::setTopHeroPlatformIcon,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroPlatformIcon(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Platform name",
        description = "Platform label under or beside the game title.",
        checked = settings.topHeroPlatform,
        onCheckedChange = settingsViewModel::setTopHeroPlatform,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroPlatform(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Title",
        description = "Game display name on the top screen.",
        checked = settings.topHeroTitle,
        onCheckedChange = settingsViewModel::setTopHeroTitle,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroTitle(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Metadata line",
        description = "Developer, year, genre, region, and age rating on the library hero; " +
            "full metadata panel on Info.",
        checked = settings.topHeroMetadata,
        onCheckedChange = settingsViewModel::setTopHeroMetadata,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroMetadata(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Description",
        description = "Game synopsis / description text.",
        checked = settings.topHeroDescription,
        onCheckedChange = settingsViewModel::setTopHeroDescription,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroDescription(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Play stats",
        description = "Play count on the library hero; plays and play time on Info.",
        checked = settings.topHeroPlayStats,
        onCheckedChange = settingsViewModel::setTopHeroPlayStats,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroPlayStats(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Favorite badge",
        description = "★ Favorite label on the Info / game detail hero.",
        checked = settings.topHeroFavorite,
        onCheckedChange = settingsViewModel::setTopHeroFavorite,
        defaultChecked = true,
        onReset = { settingsViewModel.setTopHeroFavorite(true) }
    )
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Section hint",
        description = "Controller / navigation hint under the title on Info " +
            "(\"Launch, emulator… on the bottom screen\").",
        checked = settings.topHeroSectionHint,
        onCheckedChange = settingsViewModel::setTopHeroSectionHint,
        defaultChecked = false,
        onReset = { settingsViewModel.setTopHeroSectionHint(false) }
    )
}

@Composable
private fun AppearanceSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    firstFocusRequester: FocusRequester? = null
) {
    SettingsSectionBlurb("Tune the home grid look, color theme, and gamepad focus ring.")
    SettingsChoiceRow(
        label = "Theme",
        description = "Follow the system setting or lock dark or light mode.",
        options = listOf(
            "system" to "System",
            "dark" to "Dark",
            "light" to "Light"
        ),
        selected = settings.theme,
        onSelect = settingsViewModel::setTheme,
        defaultValue = "dark",
        onReset = { settingsViewModel.setTheme("dark") },
        focusRequester = firstFocusRequester
    )
    SettingsGroupDivider()
    SettingsNumberRow(
        label = "Grid rows",
        description = "How many rows of games appear on the home grid.",
        value = settings.gridRows,
        onValueChange = settingsViewModel::setGridRows,
        range = 2..3,
        step = 1,
        valueLabel = { "$it rows" },
        defaultValue = 2,
        onReset = { settingsViewModel.setGridRows(2) }
    )
    SettingsGroupDivider()
    SettingsMultiChoiceRow(
        label = "Focus ring style",
        description = "How the gamepad focus outline is drawn around tiles and settings rows.",
        choiceOptions = listOf(
            MultiChoiceOption("Solid", "Solid", icon = "▭"),
            MultiChoiceOption("Dotted", "Dotted", icon = "⋯"),
            MultiChoiceOption("Dashed", "Dashed", icon = "╌"),
            MultiChoiceOption("MarchingAnts", "Marching ants", icon = "▤"),
            MultiChoiceOption("Pulsing", "Pulsing", icon = "◎"),
            MultiChoiceOption("Double", "Double", icon = "▢"),
            MultiChoiceOption("Glow", "Glow", icon = "◉"),
            MultiChoiceOption("CornerBrackets", "Corner brackets", icon = "⌜"),
            MultiChoiceOption("GradientPulse", "Gradient pulse", icon = "◑"),
            MultiChoiceOption("Neon", "Neon", icon = "✦")
        ),
        selected = settings.focusBorderStyle,
        onSelect = settingsViewModel::setFocusBorderStyle,
        defaultValue = FocusIndicatorDefaults.BORDER_STYLE,
        onReset = { settingsViewModel.setFocusBorderStyle(FocusIndicatorDefaults.BORDER_STYLE) }
    )
    SettingsGroupDivider()
    SettingsFocusColorRow(
        selected = settings.focusColor,
        onSelect = settingsViewModel::setFocusColor,
        defaultValue = FocusIndicatorDefaults.COLOR,
        onReset = { settingsViewModel.setFocusColor(FocusIndicatorDefaults.COLOR) }
    )
    SettingsGroupDivider()
    SettingsNumberRow(
        label = "Focus ring thickness",
        description = "Border width in density-independent pixels for unfocused navigation.",
        value = settings.focusThickness,
        onValueChange = settingsViewModel::setFocusThickness,
        range = 1..3,
        step = 1,
        valueLabel = { "$it dp" },
        defaultValue = FocusIndicatorDefaults.THICKNESS,
        onReset = { settingsViewModel.setFocusThickness(FocusIndicatorDefaults.THICKNESS) }
    )
    SettingsGroupDivider()
    SettingsChoiceRow(
        label = "Focus ring placement",
        description = "Draw the focus ring inside item bounds or expand it outward beyond the edge.",
        options = listOf("Inside" to "Inside", "Outside" to "Outside"),
        selected = settings.focusPlacement,
        onSelect = settingsViewModel::setFocusPlacement,
        defaultValue = FocusIndicatorDefaults.PLACEMENT,
        onReset = { settingsViewModel.setFocusPlacement(FocusIndicatorDefaults.PLACEMENT) }
    )
}

@Composable
private fun SystemSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    perms: PermissionStates,
    systemControls: SystemControls,
    firstFocusRequester: FocusRequester? = null
) {
    SettingsSectionBlurb(
        "Permissions, launcher role, navigation feedback, and build info."
    )
    SettingsVersionRow(versionLabel = systemControls.appVersionLabel())
    SettingsGroupDivider()
    SettingsToggleRow(
        label = "Navigation sounds",
        description = "Play sounds when moving focus and confirming actions.",
        checked = settings.soundsEnabled,
        onCheckedChange = settingsViewModel::setSoundsEnabled,
        defaultChecked = true,
        onReset = { settingsViewModel.setSoundsEnabled(true) },
        focusRequester = firstFocusRequester
    )
    SettingsGroupDivider()
    SettingsPermissionRow(
        label = "Usage access",
        description = "Follow games launched outside Wajiha on the bottom screen.",
        granted = perms.usageAccess,
        onRequest = systemControls::requestUsageAccess
    )
    SettingsGroupDivider()
    SettingsPermissionRow(
        label = "All files access",
        description = "Read emulator data to identify games launched outside Wajiha.",
        granted = perms.allFilesAccess,
        onRequest = systemControls::requestAllFilesAccess
    )
    SettingsGroupDivider()
    SettingsPermissionRow(
        label = "Notifications",
        description = "Progress while scanning and scraping your library.",
        granted = perms.notifications,
        onRequest = systemControls::requestNotifications
    )
    SettingsGroupDivider()
    SettingsLauncherRow(
        isDefaultLauncher = perms.isDefaultLauncher,
        onSetDefault = systemControls::openHomeSettings
    )
    SettingsGroupDivider()
    IgnoreFileNamePatternsSection(
        settings = settings,
        settingsViewModel = settingsViewModel
    )
}

@Composable
private fun IgnoreFileNamePatternsSection(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel
) {
    val patterns = settings.ignoreFileNamePatterns
    val patternsAtDefault = SettingsRepository.ignoreFileNamePatternsAtDefault(patterns)
    var newPatternDraft by remember { mutableStateOf("") }
    var showAddField by remember { mutableStateOf(false) }

    SettingsToggleRow(
        label = "Ignore files by name pattern",
        description = "Skip ROMs whose filename or folder path contains listed keywords during library scan.",
        checked = settings.ignorePatternFilesEnabled,
        onCheckedChange = settingsViewModel::setIgnorePatternFilesEnabled,
        defaultChecked = false,
        onReset = { settingsViewModel.setIgnorePatternFilesEnabled(false) }
    )

    if (!settings.ignorePatternFilesEnabled) return

    Text(
        text = "Matched files are excluded from scan and removed from the library on the next full rescan.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = WajihaSpacing.sm)
    )

    if (patterns.isEmpty()) {
        Text(
            text = "No patterns — add keywords below.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs)
        )
    } else {
        patterns.forEach { pattern ->
            GamepadSettingRow(
                label = pattern,
                description = "Remove this keyword from the ignore list.",
                type = SettingType.WithReset,
                onActivate = { settingsViewModel.removeIgnoreFileNamePattern(pattern) },
                content = {
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }

    if (showAddField) {
        GamepadSafeTextField(
            value = newPatternDraft,
            onValueChange = { newPatternDraft = it },
            label = "New keyword",
            modifier = Modifier.fillMaxWidth()
        )
        GamepadSettingRow(
            label = "Add keyword",
            description = "Press A to add \"$newPatternDraft\" to the ignore list.",
            type = SettingType.WithReset,
            onActivate = {
                val trimmed = newPatternDraft.trim()
                if (trimmed.isNotEmpty()) {
                    settingsViewModel.addIgnoreFileNamePattern(trimmed)
                    newPatternDraft = ""
                    showAddField = false
                }
            },
            isAtDefault = true
        )
        GamepadSettingRow(
            label = "Cancel",
            type = SettingType.WithReset,
            onActivate = {
                newPatternDraft = ""
                showAddField = false
            },
            isAtDefault = true
        )
    } else {
        GamepadSettingRow(
            label = "Add keyword",
            description = "Add a filename or path substring to ignore during scan.",
            type = SettingType.WithReset,
            onActivate = { showAddField = true },
            onReset = settingsViewModel::resetIgnoreFileNamePatterns,
            isAtDefault = patternsAtDefault
        )
    }
}

@Composable
private fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    folderPanel: Boolean = false,
    sectionContent: @Composable () -> Unit
) {
    val shape = if (folderPanel) WajihaShapes.folderPanel else WajihaShapes.card
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (folderPanel) {
                    Modifier.border(
                        width = 1.dp,
                        color = outlineColor,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WajihaSpacing.md)
        ) {
            SettingSectionScrollColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
            ) {
                sectionContent()
            }
        }
    }
}

@Composable
private fun SettingsSectionBlurb(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = WajihaSpacing.xs)
    )
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        modifier = Modifier.padding(vertical = WajihaSpacing.xs)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    defaultChecked: Boolean,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.Toggle,
        checked = checked,
        onCheckedChange = onCheckedChange,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = checked == defaultChecked
    )
}

@Composable
private fun SettingsNumberRow(
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    valueLabel: (Int) -> String = { it.toString() },
    defaultValue: Int,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.Number,
        numberValue = value,
        onNumberChange = onValueChange,
        numberRange = range,
        numberStep = step,
        numberLabel = valueLabel,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = value == defaultValue
    )
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    description: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.BinaryChoice,
        options = options,
        selected = selected,
        onSelect = onSelect,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = selected == defaultValue
    )
}

@Composable
private fun SettingsMultiChoiceRow(
    label: String,
    description: String,
    choiceOptions: List<MultiChoiceOption>,
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.MultiChoice,
        multiChoiceOptions = choiceOptions,
        selected = selected,
        onSelect = onSelect,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = selected == defaultValue
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsFocusColorRow(
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null
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
            if (isCustomSelected) selected.removePrefix("#") else "FF5722"
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
        delay(50)
        val focusIndex = when {
            isCustomSelected -> presetIds.size
            else -> presetIds.indexOfFirst {
                it.equals(FocusIndicatorPreferenceValues.normalizeColor(selected), ignoreCase = true)
            }.coerceAtLeast(0)
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
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WajihaSpacing.touchMin)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { headerCoordinates = it }
                .clip(WajihaShapes.focus)
                .wajihaFocusIndicator(highlighted = headerHighlight)
                .focusRequester(headerFocusRequester)
                .then(
                    if (!useCustomNav) {
                        Modifier
                            .onFocusChanged { headerFocused = it.isFocused }
                            .wajihaGamepadFocus()
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
                                    else -> false
                                }
                            }
                    } else {
                        Modifier
                    }
                )
                .pointerInput(expanded) {
                    detectTapGestures {
                        if (expanded) collapse() else expand()
                    }
                }
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)
            ) {
                Text(
                    text = "Focus ring color",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (canReset) {
                    Text(
                        text = "↺",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFFC107).copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.pointerInput(onReset) {
                            detectTapGestures { onReset() }
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
            )
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Text(
                text = if (expanded) "˅" else "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Theme accent follows light or dark mode. Pick a swatch or enter a custom #RRGGBB hex.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = WajihaSpacing.sm,
                end = WajihaSpacing.sm,
                top = WajihaSpacing.xs / 2
            )
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WajihaSpacing.xs / 2),
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(WajihaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
                    ) {
                        presetIds.forEachIndexed { index, presetId ->
                            val isSelected = !isCustomSelected &&
                                presetId.equals(
                                    FocusIndicatorPreferenceValues.normalizeColor(selected),
                                    ignoreCase = true
                                )
                            FocusColorSwatch(
                                label = FocusIndicatorPreferenceValues.displayColorLabel(presetId),
                                color = focusColorPreview(presetId),
                                selected = isSelected,
                                focusRequester = swatchFocusRequesters[index],
                                useCustomNav = useCustomNav,
                                onSelect = { selectPreset(presetId) }
                            )
                        }
                        FocusColorSwatch(
                            label = "Custom",
                            color = if (isCustomSelected) {
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
                            }
                        )
                    }

                    if (showCustomField || isCustomSelected) {
                        GamepadSafeTextField(
                            value = customHexDraft,
                            onValueChange = { raw ->
                                customHexDraft = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                                    .take(6)
                                    .uppercase()
                            },
                            label = "Hex color (#RRGGBB)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        GamepadSettingRow(
                            label = "Apply custom color",
                            type = SettingType.WithReset,
                            onActivate = ::applyCustomHex,
                            isAtDefault = true
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
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val highlight = !useCustomNav && focused

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(WajihaShapes.focus)
            .wajihaFocusIndicator(highlighted = highlight, shape = CircleShape)
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
                }
            )
            .pointerInput(label) {
                detectTapGestures { onSelect() }
            }
            .padding(WajihaSpacing.xs / 2)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showPlusGlyph) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlatformCard(
    platform: PlatformEntity,
    folders: List<RomFolderEntity>,
    onOpen: () -> Unit,
    onRescan: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = WajihaShapes.card,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = WajihaSpacing.touchMin)
                    .padding(
                        start = WajihaSpacing.sm,
                        end = WajihaSpacing.sm,
                        top = WajihaSpacing.sm,
                        bottom = WajihaSpacing.sm
                    ),
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GamepadFocusable(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = WajihaSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = platform.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${folders.size} folder(s)" +
                                    if (!platform.enabled) " · disabled" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                GamepadButton(
                    text = "Rescan",
                    onClick = onRescan,
                    outlined = true
                )
            }
        }
    }
}

@Composable
private fun SettingsVersionRow(versionLabel: String) {
    GamepadSettingRow(
        label = "App version",
        description = "Installed Wajiha build on this device.",
        type = SettingType.WithReset,
        content = {
            Text(
                text = versionLabel.removePrefix("Wajiha ").ifBlank { versionLabel },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun SettingsPermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.WithReset,
        onActivate = if (!granted) onRequest else null,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(WajihaSpacing.sm)
                        .clip(CircleShape)
                        .background(
                            if (granted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                            }
                        )
                )
                Text(
                    text = if (granted) "Granted" else "Grant ›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    )
}

@Composable
private fun SettingsLauncherRow(
    isDefaultLauncher: Boolean,
    onSetDefault: () -> Unit
) {
    GamepadSettingRow(
        label = "Default launcher",
        description = if (isDefaultLauncher) {
            "Wajiha is your home screen on both displays."
        } else {
            "Set Wajiha as the default launcher so HOME always returns here."
        },
        type = SettingType.WithReset,
        onActivate = if (!isDefaultLauncher) onSetDefault else null,
        content = {
            Text(
                text = if (isDefaultLauncher) "Active" else "Set ›",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDefaultLauncher) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    )
}
