package com.wajiha.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.requestContentFocus
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.FolderTabRow
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaSection
import com.wajiha.ui.components.WajihaSectionDivider
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadFormField
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.SettingSectionScrollColumn
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.gameDetailGamepadHints
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.scraper.PlatformScraperSettingsSection
import com.wajiha.ui.scraper.ScrapeModeSelector
import com.wajiha.ui.scraper.ScrapeUiMode
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.scraper.batchPolicyOrNull
import com.wajiha.ui.scraper.review.ScrapeReviewPicker
import com.wajiha.ui.scraper.review.ScrapeReviewViewModel
import com.wajiha.ui.scraper.toPolicy
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class PlatformSettingsTab(
    val label: String,
) {
    General("General"),
    Emulator("Emulator"),
    Folders("Folders"),
    Scraper("Scraper"),
    Info("Info"),
}

/**
 * Per-platform edit page: display names, default emulator, ROM folders,
 * scraper linkage ids, and per-system scraper overrides.
 */
@Composable
fun PlatformSettingsScreen(
    platformId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    viewModel: PlatformSettingsViewModel = koinInject(),
    scraperViewModel: ScraperViewModel = koinInject(),
) {
    LaunchedEffect(platformId) { viewModel.open(platformId) }
    val state by viewModel.uiState.collectAsState()
    val platform = state.platform
    val systemControls = koinInject<SystemControls>()
    val feedback = LocalUiFeedback.current

    val tabs = PlatformSettingsTab.entries
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val sectionFocus = remember { FocusRequester() }
    val screenLayer = "platform_settings"

    fun selectTab(index: Int) {
        val newIndex = index.coerceIn(0, tabs.lastIndex)
        feedback.tabSelect(selectedTabIndex, newIndex)
        selectedTabIndex = newIndex
    }

    LaunchedEffect(selectedTabIndex) {
        try {
            sectionFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    WajihaScreen(
        layerId = screenLayer,
        modifier = modifier,
        onBack = onBack,
        showActionBar = true,
        gamepadHints = gameDetailGamepadHints,
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = { sectionFocus.requestContentFocus() },
        onPreviewKey = { event ->
            if (GamepadLayers.stack.topLayer != screenLayer) return@WajihaScreen false
            when {
                GamepadKeys.isL1(event.type, event.key) -> {
                    if (selectedTabIndex > 0) {
                        selectTab(selectedTabIndex - 1)
                        true
                    } else {
                        false
                    }
                }

                GamepadKeys.isR1(event.type, event.key) -> {
                    if (selectedTabIndex < tabs.lastIndex) {
                        selectTab(selectedTabIndex + 1)
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
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformSettingsToolbar(
                title = platform?.name ?: "Platform",
                enabled = platform?.enabled ?: true,
                onEnabledChange = viewModel::setEnabled,
                onBack = onBack,
                showLibraryToggle = platform != null,
            )

            if (platform == null) {
                Text(
                    text = "Platform not found",
                    modifier = Modifier.padding(WajihaSpacing.md),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = WajihaSpacing.md),
                ) {
                    FolderTabRow(
                        tabs = tabs.map { it.label },
                        selectedIndex = selectedTabIndex,
                        onSelect = ::selectTab,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusProperties { canFocus = false }
                                .padding(top = WajihaSpacing.sm),
                    )

                    PlatformSettingsSectionCard(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .offset(y = (-1).dp)
                                .padding(bottom = WajihaSpacing.md),
                    ) {
                        when (tabs[selectedTabIndex]) {
                            PlatformSettingsTab.General -> {
                                PlatformGeneralTabContent(
                                    platform = platform,
                                    viewModel = viewModel,
                                    firstFocusRequester = sectionFocus,
                                )
                            }

                            PlatformSettingsTab.Emulator -> {
                                PlatformEmulatorTabContent(
                                    platform = platform,
                                    state = state,
                                    systemControls = systemControls,
                                    viewModel = viewModel,
                                    firstFocusRequester = sectionFocus,
                                )
                            }

                            PlatformSettingsTab.Folders -> {
                                PlatformFoldersTabContent(
                                    state = state,
                                    viewModel = viewModel,
                                    firstFocusRequester = sectionFocus,
                                )
                            }

                            PlatformSettingsTab.Scraper -> {
                                PlatformScraperTabContent(
                                    platform = platform,
                                    viewModel = viewModel,
                                    scraperViewModel = scraperViewModel,
                                    firstFocusRequester = sectionFocus,
                                )
                            }

                            PlatformSettingsTab.Info -> {
                                PlatformInfoTabContent(
                                    platform = platform,
                                    state = state,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformSettingsToolbar(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    showLibraryToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        GamepadButton(
            text = "Back",
            onClick = onBack,
            outlined = true,
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showLibraryToggle) {
                HeaderLibraryToggle(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
    }
}

@Composable
private fun HeaderLibraryToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null

    Row(
        modifier =
            modifier
                .clip(WajihaShapes.focus)
                .wajihaFocusIndicator(highlighted = !useCustomNav && focused)
                .then(
                    if (!useCustomNav) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .wajihaGamepadFocus()
                            .onPreviewKeyEvent { event ->
                                if (GamepadKeys.isConfirm(event.type, event.key)) {
                                    onCheckedChange(!checked)
                                    true
                                } else {
                                    false
                                }
                            }
                    } else {
                        Modifier
                    },
                ).pointerInput(checked, onCheckedChange) {
                    detectTapGestures { onCheckedChange(!checked) }
                }.padding(horizontal = WajihaSpacing.xs, vertical = WajihaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PlatformSettingsSectionCard(
    modifier: Modifier = Modifier,
    sectionContent: @Composable () -> Unit,
) {
    val shape = WajihaShapes.folderPanel
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = outlineColor, shape = shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(WajihaSpacing.md),
        ) {
            SettingSectionScrollColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            ) {
                sectionContent()
            }
        }
    }
}

@Composable
private fun PlatformGeneralTabContent(
    platform: PlatformEntity,
    viewModel: PlatformSettingsViewModel,
    firstFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Text(
            text = "Display name and short label shown in the library grid.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = WajihaSpacing.xs),
        )
        NameFields(
            platform = platform,
            onNameCommit = viewModel::setDisplayName,
            onShortNameCommit = viewModel::setShortName,
            firstFocusRequester = firstFocusRequester,
        )
    }
}

@Composable
private fun PlatformEmulatorTabContent(
    platform: PlatformEntity,
    state: PlatformSettingsUiState,
    systemControls: SystemControls,
    viewModel: PlatformSettingsViewModel,
    firstFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Text(
            text =
                "Player used when launching games for this system " +
                    "(same idea as Daijishō / Cocoon players).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = WajihaSpacing.xs),
        )
        if (state.emulators.isEmpty()) {
            Text(
                text = "No emulators configured for this platform.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val selectedEmulatorId =
                platform.defaultEmulatorId
                    ?: state.emulators
                        .firstOrNull { it.isDefault }
                        ?.id
                        .orEmpty()
            val emulatorOptions =
                state.emulators.toEmulatorChoiceOptions(
                    isPackageInstalled = systemControls::isPackageInstalled,
                )
            GamepadSettingRow(
                label = "Default emulator",
                description = "Installed apps are selectable. Missing emulators stay visible but greyed out.",
                type = SettingType.MultiChoice,
                multiChoiceOptions = emulatorOptions,
                selected = selectedEmulatorId,
                onSelect = viewModel::setDefaultEmulator,
                focusRequester = firstFocusRequester,
            )
        }
    }
}

@Composable
private fun PlatformFoldersTabContent(
    state: PlatformSettingsUiState,
    viewModel: PlatformSettingsViewModel,
    firstFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Text(
            text = "Folders scanned for games on this system. Add at least one to include it in your library.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = WajihaSpacing.xs),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            GamepadButton(
                text = "Rescan",
                onClick = viewModel::rescanPlatform,
                outlined = true,
                focusRequester = firstFocusRequester,
            )
            GamepadButton(
                text = "Add folder",
                onClick = viewModel::pickRomFolder,
                modifier = Modifier.padding(start = WajihaSpacing.sm),
            )
        }
        if (state.folders.isEmpty()) {
            Text(
                text = "No folders yet — add a ROM folder to bring this system into your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.folders.forEach { folder ->
                FolderRow(
                    folder = folder,
                    onRemove = { viewModel.removeFolder(folder.id) },
                )
            }
        }
    }
}

@Composable
private fun PlatformScraperTabContent(
    platform: PlatformEntity,
    viewModel: PlatformSettingsViewModel,
    scraperViewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester,
    reviewViewModel: ScrapeReviewViewModel = koinInject(),
    dualStore: DualScreenStore = koinInject(),
) {
    val progress by scraperViewModel.progress.collectAsState()
    val settings by scraperViewModel.settings.collectAsState()
    val batchFeedback by scraperViewModel.batchFeedback.collectAsState()
    val screenState by dualStore.state.collectAsState()
    val dualDisplay = screenState != DualScreenState.SingleDisplay
    val sourcesReady = scraperViewModel.hasConfiguredSources(platform.id)
    val effective = settings.forPlatform(platform.id)
    val canRetry = scraperViewModel.canRetryFailed(platform.id)
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ScrapeUiMode.FillGaps) }
    var estimate by remember { mutableStateOf<Int?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    var includeScraped by remember { mutableStateOf(false) }
    val linkageWarning =
        buildList {
            if ("screenscraper" in effective.enabledSources && platform.screenScraperId == null) {
                add("ScreenScraper system id is empty")
            }
            if ("ra" in effective.enabledSources && platform.raConsoleId == null) {
                add("RetroAchievements console id is empty")
            }
            if ("libretro" in effective.enabledSources && platform.libretroName.isNullOrBlank()) {
                add("Libretro system name is empty")
            }
        }
    val scrapeBusy =
        progress.running &&
            (progress.platformId == platform.id || progress.platformId == null)

    LaunchedEffect(platform.id, mode, includeScraped) {
        estimate =
            when (mode) {
                ScrapeUiMode.Review -> {
                    scraperViewModel.reviewQueueGames(platform.id, includeScraped).size
                }

                else -> {
                    val policy = mode.batchPolicyOrNull() ?: return@LaunchedEffect
                    scraperViewModel.estimateBatchCount(platform.id, policy)
                }
            }
    }

    if (reviewing) {
        ScrapeReviewPicker(
            viewModel = reviewViewModel,
            onCancel = { reviewing = false },
            showSkip = true,
            dualDisplay = dualDisplay,
            hostGamepadOwner = if (dualDisplay) dualStore.menuGamepadOwner() else null,
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Text(
            text =
                "Scrape metadata and media for games on this platform, " +
                    "or adjust linkage IDs and per-system source overrides.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = WajihaSpacing.xs),
        )

        ScrapeModeSelector(
            selected = mode,
            onSelect = { mode = it },
            actionLabel =
                when (mode) {
                    ScrapeUiMode.Review -> {
                        "Start review"
                    }

                    ScrapeUiMode.Force -> {
                        if (scrapeBusy) "Scraping…" else "Force scrape"
                    }

                    ScrapeUiMode.FillGaps -> {
                        if (scrapeBusy) "Scraping…" else "Fill gaps"
                    }
                },
            onAction = {
                when (mode) {
                    ScrapeUiMode.Review -> {
                        scope.launch {
                            val games =
                                scraperViewModel.reviewQueueGames(
                                    platform.id,
                                    includeScraped,
                                )
                            if (games.isEmpty()) {
                                scraperViewModel.dismissBatchFeedback()
                                return@launch
                            }
                            reviewing = true
                            reviewViewModel.openQueue(games) {
                                reviewing = false
                            }
                        }
                    }

                    else -> {
                        scraperViewModel.dismissBatchFeedback()
                        scraperViewModel.startBatch(platform.id, mode.toPolicy())
                    }
                }
            },
            firstFocusRequester = firstFocusRequester,
            enabled = !progress.running,
            actionEnabled =
                !progress.running &&
                    sourcesReady &&
                    (mode != ScrapeUiMode.Review || (estimate ?: 0) > 0),
        )

        if (mode == ScrapeUiMode.Review) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            ) {
                GamepadChip(
                    label = "Gaps only",
                    selected = !includeScraped,
                    onClick = { if (!progress.running) includeScraped = false },
                )
                GamepadChip(
                    label = "Include scraped",
                    selected = includeScraped,
                    onClick = { if (!progress.running) includeScraped = true },
                )
            }
        }

        estimate?.let { count ->
            Text(
                text =
                    when (mode) {
                        ScrapeUiMode.Review -> "$count game(s) in review queue"
                        ScrapeUiMode.Force -> "$count game(s) will be force-scraped"
                        ScrapeUiMode.FillGaps -> "$count game(s) need gap fill"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (canRetry && mode != ScrapeUiMode.Review) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                GamepadButton(
                    text = "Retry failed",
                    onClick = { scraperViewModel.retryFailedBatch(platform.id) },
                    outlined = true,
                    enabled = !progress.running,
                )
            }
        }

        if (scrapeBusy) {
            Row(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
                if (progress.paused) {
                    GamepadButton(
                        text = "Resume",
                        onClick = scraperViewModel::resumeBatch,
                        outlined = true,
                    )
                } else {
                    GamepadButton(
                        text = "Pause",
                        onClick = scraperViewModel::pauseBatch,
                        outlined = true,
                    )
                }
                GamepadButton(
                    text = "Cancel",
                    onClick = scraperViewModel::cancelBatch,
                    outlined = true,
                )
            }
        }

        if (!sourcesReady) {
            Text(
                text = "No scraper sources configured — enable and sign in under Settings → Scraper.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        linkageWarning.forEach { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        batchFeedback?.let { feedback ->
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (scrapeBusy) {
            Text(
                text =
                    when {
                        progress.paused -> "Paused — ${progress.summaryLine()}"
                        progress.currentGameName != null -> "Scraping: ${progress.currentGameName}"
                        else -> "Scraping…"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (
            progress.done > 0 &&
            (progress.platformId == platform.id || progress.platformId == null)
        ) {
            Text(
                text = "Last run: ${progress.summaryLine()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        WajihaSectionDivider()
        WajihaSection(title = "Scraper linkage") {
            Text(
                text =
                    "IDs used to match games on ScreenScraper, RetroAchievements, and Libretro. " +
                        "Defaults come from platform packs; tweak here if a match is wrong.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = WajihaSpacing.xs),
            )
            ScraperIdFields(
                platform = platform,
                onScreenScraperId = viewModel::setScreenScraperId,
                onRaConsoleId = viewModel::setRaConsoleId,
                onLibretroName = viewModel::setLibretroName,
                firstFocusRequester = remember { FocusRequester() },
            )
        }

        WajihaSectionDivider()
        WajihaSection(title = "Scraper overrides") {
            PlatformScraperSettingsSection(
                platformId = platform.id,
                viewModel = scraperViewModel,
            )
        }
    }
}

@Composable
private fun PlatformInfoTabContent(
    platform: PlatformEntity,
    state: PlatformSettingsUiState,
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val bodyStyle = MaterialTheme.typography.bodySmall
    val defaultEmulatorName =
        state.emulators
            .firstOrNull { it.id == platform.defaultEmulatorId }
            ?.name
            ?: state.emulators.firstOrNull { it.isDefault }?.name
            ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        Text(
            text = "Read-only platform metadata and library stats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = WajihaSpacing.xs),
        )

        PlatformInfoRow("Platform id", platform.id, labelStyle, bodyStyle)
        PlatformInfoRow("Display name", platform.name, labelStyle, bodyStyle)
        PlatformInfoRow("Short name", platform.shortName, labelStyle, bodyStyle)
        PlatformInfoRow(
            "In library",
            if (platform.enabled) "Yes" else "No",
            labelStyle,
            bodyStyle,
        )
        PlatformInfoRow("Games", "${state.gameCount}", labelStyle, bodyStyle)
        PlatformInfoRow("ROM folders", "${state.folders.size}", labelStyle, bodyStyle)
        PlatformInfoRow("Default emulator", defaultEmulatorName, labelStyle, bodyStyle)
        platform.boxartAspectRatio?.let {
            PlatformInfoRow("Boxart aspect", it, labelStyle, bodyStyle)
        }
        PlatformInfoRow("Extensions", platform.extensions, labelStyle, bodyStyle)
        PlatformInfoRow(
            "ScreenScraper id",
            platform.screenScraperId?.toString() ?: "—",
            labelStyle,
            bodyStyle,
        )
        PlatformInfoRow(
            "RetroAchievements id",
            platform.raConsoleId?.toString() ?: "—",
            labelStyle,
            bodyStyle,
        )
        PlatformInfoRow(
            "Libretro name",
            platform.libretroName ?: "—",
            labelStyle,
            bodyStyle,
        )

        if (state.folders.isNotEmpty()) {
            InfoGroupDivider()
            Text(
                text = "Folder paths",
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.folders.forEach { folder ->
                PlatformInfoRow(
                    label = "Folder",
                    value = folderDisplayPath(folder),
                    labelStyle = labelStyle,
                    bodyStyle = bodyStyle,
                    maxValueLines = 4,
                )
            }
        }
    }
}

@Composable
private fun PlatformInfoRow(
    label: String,
    value: String,
    labelStyle: TextStyle,
    bodyStyle: TextStyle,
    maxValueLines: Int = 3,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WajihaSpacing.touchMin / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Text(
            text = value,
            style = bodyStyle,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = WajihaSpacing.sm),
            maxLines = maxValueLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoGroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        modifier = Modifier.padding(vertical = WajihaSpacing.xs),
    )
}

private fun folderDisplayPath(folder: RomFolderEntity): String = folder.treeUri.substringAfterLast("%3A").substringAfterLast(':')

@Composable
private fun NameFields(
    platform: PlatformEntity,
    onNameCommit: (String) -> Unit,
    onShortNameCommit: (String) -> Unit,
    firstFocusRequester: FocusRequester,
) {
    var name by remember(platform.id, platform.name) { mutableStateOf(platform.name) }
    var shortName by remember(platform.id, platform.shortName) {
        mutableStateOf(platform.shortName)
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = name,
            onValueChange = { name = it },
            label = "Display name",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFocusRequester),
        )
    }
    LaunchedEffect(name) {
        if (name != platform.name && name.isNotBlank()) {
            kotlinx.coroutines.delay(450)
            if (name != platform.name) onNameCommit(name)
        }
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = shortName,
            onValueChange = { shortName = it },
            label = "Short name",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    LaunchedEffect(shortName) {
        if (shortName != platform.shortName && shortName.isNotBlank()) {
            kotlinx.coroutines.delay(450)
            if (shortName != platform.shortName) onShortNameCommit(shortName)
        }
    }
}

@Composable
private fun FolderRow(
    folder: RomFolderEntity,
    onRemove: () -> Unit,
) {
    GamepadSettingRow(
        label = folderDisplayPath(folder),
        description = "ROM folder on this device.",
        type = SettingType.WithReset,
        onActivate = onRemove,
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

@Composable
private fun ScraperIdFields(
    platform: PlatformEntity,
    onScreenScraperId: (String) -> Unit,
    onRaConsoleId: (String) -> Unit,
    onLibretroName: (String) -> Unit,
    firstFocusRequester: FocusRequester,
) {
    var ss by remember(platform.id, platform.screenScraperId) {
        mutableStateOf(platform.screenScraperId?.toString().orEmpty())
    }
    var ra by remember(platform.id, platform.raConsoleId) {
        mutableStateOf(platform.raConsoleId?.toString().orEmpty())
    }
    var libretro by remember(platform.id, platform.libretroName) {
        mutableStateOf(platform.libretroName.orEmpty())
    }

    GamepadFormField {
        GamepadSafeTextField(
            value = ss,
            onValueChange = { ss = it },
            label = "ScreenScraper system id",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFocusRequester),
        )
    }
    LaunchedEffect(ss) {
        val current = platform.screenScraperId?.toString().orEmpty()
        if (ss != current) {
            kotlinx.coroutines.delay(450)
            if (ss != current) onScreenScraperId(ss)
        }
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = ra,
            onValueChange = { ra = it },
            label = "RetroAchievements console id",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    LaunchedEffect(ra) {
        val current = platform.raConsoleId?.toString().orEmpty()
        if (ra != current) {
            kotlinx.coroutines.delay(450)
            if (ra != current) onRaConsoleId(ra)
        }
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = libretro,
            onValueChange = { libretro = it },
            label = "Libretro thumbnails name",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    LaunchedEffect(libretro) {
        val current = platform.libretroName.orEmpty()
        if (libretro != current) {
            kotlinx.coroutines.delay(450)
            if (libretro != current) onLibretroName(libretro)
        }
    }
}
