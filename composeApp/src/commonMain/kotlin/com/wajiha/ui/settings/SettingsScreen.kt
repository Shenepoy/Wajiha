package com.wajiha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.platform.SystemControls
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.scraper.scraperSettingsItems
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class SettingsSection(val label: String) {
    Library("Library"),
    Scraper("Scraper"),
    DualScreen("Dual screen"),
    Appearance("Appearance"),
    System("System")
}

private val settingsNavGroups = listOf(
    listOf(SettingsSection.Library, SettingsSection.Scraper),
    listOf(SettingsSection.DualScreen, SettingsSection.Appearance),
    listOf(SettingsSection.System)
)

/**
 * Settings master-detail: left section nav, right continuous scroll.
 * NeoStation-style gamepad: menu vs content focus, A confirms, B handled upstream.
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
    var focusOnMenu by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bringIntoView = remember {
        sections.associateWith { BringIntoViewRequester() }
    }
    val sectionContentFocus = remember {
        sections.associateWith { FocusRequester() }
    }
    val menuFocusRequesters = remember(sections.size) {
        List(sections.size) { FocusRequester() }
    }
    val firstMenuFocus = menuFocusRequesters.firstOrNull()

    fun scrollToSection(index: Int) {
        selectedSectionIndex = index.coerceIn(0, sections.lastIndex)
        scope.launch {
            bringIntoView[sections[selectedSectionIndex]]?.bringIntoView()
        }
    }

    fun moveFocusToContent() {
        focusOnMenu = false
        scrollToSection(selectedSectionIndex)
        try {
            sectionContentFocus[sections[selectedSectionIndex]]?.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun moveFocusToMenu() {
        focusOnMenu = true
        try {
            menuFocusRequesters[selectedSectionIndex].requestFocus()
        } catch (_: Exception) {
        }
    }

    fun selectMenuSection(index: Int) {
        scrollToSection(index)
        if (focusOnMenu) {
            try {
                menuFocusRequesters[selectedSectionIndex].requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(selectedSectionIndex) {
        bringIntoView[sections[selectedSectionIndex]]?.bringIntoView()
        onSectionChange(sections[selectedSectionIndex].label)
    }

    LaunchedEffect(Unit) {
        try {
            firstMenuFocus?.requestFocus()
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (!focusOnMenu) {
                            moveFocusToMenu()
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (focusOnMenu) {
                            moveFocusToContent()
                            true
                        } else false
                    }
                    Key.DirectionUp -> {
                        if (focusOnMenu && selectedSectionIndex > 0) {
                            selectMenuSection(selectedSectionIndex - 1)
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (focusOnMenu && selectedSectionIndex < sections.lastIndex) {
                            selectMenuSection(selectedSectionIndex + 1)
                            true
                        } else false
                    }
                    Key.ButtonL1, Key.PageUp -> {
                        if (selectedSectionIndex > 0) {
                            selectMenuSection(selectedSectionIndex - 1)
                            true
                        } else false
                    }
                    Key.ButtonR1, Key.PageDown -> {
                        if (selectedSectionIndex < sections.lastIndex) {
                            selectMenuSection(selectedSectionIndex + 1)
                            true
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                        if (focusOnMenu) {
                            moveFocusToContent()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsBackButton(onBack = onBack)
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(196.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(vertical = 8.dp)
            ) {
                settingsNavGroups.forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }
                    group.forEach { section ->
                        val index = sections.indexOf(section)
                        SettingsNavItem(
                            label = section.label,
                            selected = index == selectedSectionIndex,
                            focusOnMenu = focusOnMenu,
                            focusRequester = menuFocusRequesters[index],
                            onFocus = {
                                focusOnMenu = true
                                selectedSectionIndex = index
                            },
                            onTap = { selectMenuSection(index) }
                        )
                    }
                }
            }

            VerticalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = SettingsSection.Library) {
                    SectionHeader(
                        title = "Library",
                        bringIntoViewRequester = bringIntoView[SettingsSection.Library],
                        contentFocusRequester = sectionContentFocus[SettingsSection.Library]
                    )
                }
                item {
                    Text(
                        text = "Only systems you've added appear here. Use Add to pick a " +
                            "platform and configure folders, emulator, and scraper ids.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    SettingsActionButton(label = "Add platform", onActivate = onAddPlatform)
                }
                item {
                    SettingsActionButton(
                        label = "Rescan all",
                        onActivate = settingsViewModel::rescanLibrary
                    )
                }
                if (inUsePlatforms.isEmpty()) {
                    item {
                        Text(
                            text = "No platforms configured yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(inUsePlatforms, key = { it.id }) { platform ->
                        val platformFolders = folders.filter { it.platformId == platform.id }
                        InUsePlatformRow(
                            platform = platform,
                            folders = platformFolders,
                            onOpen = { onOpenPlatform(platform.id) }
                        )
                    }
                }

                item(key = SettingsSection.Scraper) {
                    SectionHeader(
                        title = "Scraper",
                        bringIntoViewRequester = bringIntoView[SettingsSection.Scraper],
                        contentFocusRequester = sectionContentFocus[SettingsSection.Scraper]
                    )
                }
                scraperSettingsItems(scraperViewModel)

                item(key = SettingsSection.DualScreen) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(
                        title = "Dual screen",
                        bringIntoViewRequester = bringIntoView[SettingsSection.DualScreen],
                        contentFocusRequester = sectionContentFocus[SettingsSection.DualScreen]
                    )
                }
                item {
                    SettingSwitch(
                        label = "Black out unused display when a game starts",
                        checked = settings.blackoutOnLaunch,
                        onChecked = settingsViewModel::setBlackoutOnLaunch
                    )
                }
                item {
                    SettingSwitch(
                        label = "Show Now Playing for games launched outside Wajiha",
                        checked = settings.detectManualLaunches,
                        onChecked = settingsViewModel::setDetectManualLaunches
                    )
                }
                item {
                    SettingSwitch(
                        label = "Swap screen roles (grid on top, hero on bottom)",
                        checked = settings.swapScreenRoles,
                        onChecked = settingsViewModel::setSwapScreenRoles
                    )
                }
                item {
                    ChoiceRow(
                        label = "Bottom screen while a game runs",
                        options = listOf(
                            "NowPlaying" to "Now Playing",
                            "QuickSettings" to "Quick Settings",
                            "RunningApps" to "Running Apps",
                            "Achievements" to "Achievements",
                            "Clock" to "Clock",
                            "Off" to "Off"
                        ),
                        selected = settings.gameSecondaryMode,
                        onSelect = settingsViewModel::setGameSecondaryMode
                    )
                }

                item(key = SettingsSection.Appearance) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(
                        title = "Appearance",
                        bringIntoViewRequester = bringIntoView[SettingsSection.Appearance],
                        contentFocusRequester = sectionContentFocus[SettingsSection.Appearance]
                    )
                }
                item {
                    ChoiceRow(
                        label = "Theme",
                        options = listOf("system" to "System", "dark" to "Dark", "light" to "Light"),
                        selected = settings.theme,
                        onSelect = settingsViewModel::setTheme
                    )
                }
                item {
                    ChoiceRow(
                        label = "Grid rows",
                        options = listOf("2" to "2", "3" to "3"),
                        selected = settings.gridRows.toString(),
                        onSelect = { settingsViewModel.setGridRows(it.toInt()) }
                    )
                }
                item {
                    SettingSwitch(
                        label = "Navigation sounds",
                        checked = settings.soundsEnabled,
                        onChecked = settingsViewModel::setSoundsEnabled
                    )
                }

                item(key = SettingsSection.System) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(
                        title = "System",
                        bringIntoViewRequester = bringIntoView[SettingsSection.System],
                        contentFocusRequester = sectionContentFocus[SettingsSection.System]
                    )
                }
                item {
                    val perms = systemControls.permissionStates()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (perms.isDefaultLauncher) {
                                "Wajiha is the default launcher"
                            } else {
                                "Wajiha is not the default launcher"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (!perms.isDefaultLauncher) {
                            SettingsActionButton(
                                label = "Set default",
                                onActivate = systemControls::openHomeSettings
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    bringIntoViewRequester: BringIntoViewRequester?,
    contentFocusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
            .then(
                if (bringIntoViewRequester != null) {
                    Modifier.bringIntoViewRequester(bringIntoViewRequester)
                } else {
                    Modifier
                }
            )
            .then(
                if (contentFocusRequester != null) {
                    Modifier
                        .focusRequester(contentFocusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = if (focused) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        )
                } else {
                    Modifier
                }
            )
    )
}

@Composable
private fun SettingsNavItem(
    label: String,
    selected: Boolean,
    focusOnMenu: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onTap: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val highlight = selected
    val showBorder = focused && focusOnMenu

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .pointerInput(onTap) {
                detectTapGestures { onTap() }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    highlight && focusOnMenu -> MaterialTheme.colorScheme.primaryContainer
                    highlight -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    focused -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.background.copy(alpha = 0f)
                }
            )
            .border(
                width = if (showBorder) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onBackground
            }
        )
    }
}

@Composable
private fun SettingsBackButton(onBack: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    TextButton(
        onClick = onBack,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        Text("< Back")
    }
}

@Composable
private fun SettingsActionButton(label: String, onActivate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    Button(
        onClick = onActivate,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        Text(label)
    }
}

@Composable
private fun InUsePlatformRow(
    platform: PlatformEntity,
    folders: List<RomFolderEntity>,
    onOpen: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.key == Key.ButtonA)
                ) {
                    onOpen()
                    true
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    onOpen()
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(platform.name, style = MaterialTheme.typography.bodyLarge)
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
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, display) ->
                var focused by remember(value) { mutableStateOf(false) }
                val focusRequester = remember { FocusRequester() }
                val isSelected = selected == value
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.background.copy(alpha = 0f)
                            }
                        )
                        .border(
                            width = if (focused) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter ||
                                    event.key == Key.Enter ||
                                    event.key == Key.NumPadEnter ||
                                    event.key == Key.ButtonA)
                            ) {
                                onSelect(value)
                                true
                            } else {
                                false
                            }
                        }
                        .pointerInput(value) {
                            detectTapGestures {
                                onSelect(value)
                                try {
                                    focusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = display,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
