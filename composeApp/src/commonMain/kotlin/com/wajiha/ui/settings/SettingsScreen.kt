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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.platform.PermissionStates
import com.wajiha.platform.SystemControls
import com.wajiha.input.GamepadKeys
import com.wajiha.ui.components.FolderTabRow
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadFocusable
import com.wajiha.ui.components.gamepad.GamepadSwitch
import com.wajiha.ui.scraper.ScraperSettingsColumn
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

    val sections = SettingsSection.entries
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    var perms by remember { mutableStateOf(PermissionStates()) }
    val sectionFocus = remember { FocusRequester() }

    fun selectSection(index: Int) {
        selectedSectionIndex = index.coerceIn(0, sections.lastIndex)
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
        gamepadHints = listOf(
            "A" to "Select/Confirm",
            "B" to "Back",
            "L1/R1" to "Section"
        ),
        onPreviewKey = { event ->
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
                GamepadFocusable(
                    onClick = { onOpenPlatform(platform.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlatformCard(
                        platform = platform,
                        folders = platformFolders,
                        onOpen = { onOpenPlatform(platform.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScraperSectionContent(
    scraperViewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null
) {
    SettingsSectionBlurb(
        "Configure metadata sources, credentials, and batch scraping for your library."
    )
    ScraperSettingsColumn(scraperViewModel, firstFocusRequester)
}

@Composable
private fun DualScreenSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    firstFocusRequester: FocusRequester? = null
) {
    SettingsSectionBlurb(
        "Control how Wajiha uses the top and bottom displays on clamshell handhelds."
    )
    SettingsToggleItem(
        label = "Black out unused display when a game starts",
        description = "Turn off the idle screen while a game runs for a cleaner play experience.",
        checked = settings.blackoutOnLaunch,
        onCheckedChange = settingsViewModel::setBlackoutOnLaunch,
        focusRequester = firstFocusRequester
    )
    SettingsGroupDivider()
    SettingsToggleItem(
        label = "Show Now Playing for games launched outside Wajiha",
        description = "Detect when another app launches a game and " +
            "show Now Playing on the secondary display.",
        checked = settings.detectManualLaunches,
        onCheckedChange = settingsViewModel::setDetectManualLaunches
    )
    SettingsGroupDivider()
    SettingsToggleItem(
        label = "Swap screen roles (grid on top, hero on bottom)",
        description = "Flip which display shows the game grid versus hero artwork.",
        checked = settings.swapScreenRoles,
        onCheckedChange = settingsViewModel::setSwapScreenRoles
    )
    SettingsGroupDivider()
    SettingsChipPicker(
        label = "Bottom screen while a game runs",
        description = "Choose what the secondary display shows after you launch a game.",
        options = listOf(
            "NowPlaying" to "Now Playing",
            "QuickSettings" to "Quick Settings",
            "RunningApps" to "Running Apps",
            "Achievements" to "Achievements",
            "Clock" to "Clock",
            "Off" to "Off"
        ),
        selected = settings.gameSecondaryMode,
        onSelect = settingsViewModel::setGameSecondaryMode,
        idPrefix = "game_secondary_mode"
    )
}

@Composable
private fun AppearanceSectionContent(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    firstFocusRequester: FocusRequester? = null
) {
    SettingsSectionBlurb("Tune the home grid look and color theme.")
    SettingsChipPicker(
        label = "Theme",
        description = "Follow the system setting or lock dark or light mode.",
        options = listOf(
            "system" to "System",
            "dark" to "Dark",
            "light" to "Light"
        ),
        selected = settings.theme,
        onSelect = settingsViewModel::setTheme,
        idPrefix = "theme",
        firstFocusRequester = firstFocusRequester
    )
    SettingsGroupDivider()
    SettingsChipPicker(
        label = "Grid rows",
        description = "How many rows of games appear on the home grid.",
        options = listOf("2" to "2 rows", "3" to "3 rows"),
        selected = settings.gridRows.toString(),
        onSelect = { settingsViewModel.setGridRows(it.toInt()) },
        idPrefix = "grid_rows"
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
    SettingsSectionBlurb("Permissions, launcher status, and interface feedback.")
    SettingsVersionRow()
    SettingsGroupDivider()
    SettingsToggleItem(
        label = "Navigation sounds",
        description = "Play sounds when moving focus and confirming actions.",
        checked = settings.soundsEnabled,
        onCheckedChange = settingsViewModel::setSoundsEnabled,
        focusRequester = firstFocusRequester
    )
    SettingsGroupDivider()
    SettingsPermissionRow(
        label = "Usage access",
        description = "Follow games launched outside Wajiha on the bottom screen",
        granted = perms.usageAccess,
        onRequest = systemControls::requestUsageAccess
    )
    SettingsGroupDivider()
    SettingsPermissionRow(
        label = "Notifications",
        description = "Progress while scanning and scraping your library",
        granted = perms.notifications,
        onRequest = systemControls::requestNotifications
    )
    SettingsGroupDivider()
    SettingsLauncherRow(
        isDefaultLauncher = perms.isDefaultLauncher,
        onSetDefault = systemControls::openHomeSettings
    )
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
        ) {
            sectionContent()
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
private fun SettingsToggleItem(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WajihaSpacing.touchMin)
    ) {
        GamepadSwitch(
            label = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            focusRequester = focusRequester
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = WajihaSpacing.xs,
                end = WajihaSpacing.md + WajihaSpacing.lg,
                top = WajihaSpacing.xs / 2
            )
        )
    }
}

@Composable
private fun SettingsChipPicker(
    label: String,
    description: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    idPrefix: String,
    firstFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WajihaSpacing.touchMin),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
        ) {
            options.forEachIndexed { index, (value, display) ->
                GamepadChip(
                    label = display,
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    focusRequester = if (index == 0) firstFocusRequester else null
                )
            }
        }
    }
}

@Composable
private fun PlatformCard(
    platform: PlatformEntity,
    folders: List<RomFolderEntity>,
    onOpen: () -> Unit
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
                    .padding(horizontal = WajihaSpacing.md, vertical = WajihaSpacing.sm),
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
    }
}

@Composable
private fun SettingsVersionRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "App version",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Wajiha 0.1.0",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsPermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WajihaSpacing.touchMin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(WajihaSpacing.sm + WajihaSpacing.xs)
                .clip(CircleShape)
                .background(
                    if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = WajihaSpacing.sm + WajihaSpacing.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Text(
                text = "Granted",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            GamepadButton(text = "Grant", onClick = onRequest, outlined = true)
        }
    }
}

@Composable
private fun SettingsLauncherRow(
    isDefaultLauncher: Boolean,
    onSetDefault: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WajihaSpacing.touchMin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Default launcher",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isDefaultLauncher) {
                    "Wajiha is your home screen"
                } else {
                    "Set Wajiha as the default launcher"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isDefaultLauncher) {
            GamepadButton(text = "Set default", onClick = onSetDefault, outlined = true)
        } else {
            Text(
                text = "Active",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
