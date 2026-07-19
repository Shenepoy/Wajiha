package com.wajiha.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.rememberFocusContext
import com.wajiha.input.rememberedFocusContext
import com.wajiha.input.rememberedFocusTarget
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.state.SettingsHeroAction
import com.wajiha.state.SettingsHeroOption
import com.wajiha.ui.components.FolderTabRow
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadFormField
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.LocalSettingRowMinHeight
import com.wajiha.ui.components.gamepad.ProvideSettingsDensity
import com.wajiha.ui.components.gamepad.SettingSectionFocusRestorer
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.WajihaSettingBlurb
import com.wajiha.ui.components.gamepad.WajihaSettingDivider
import com.wajiha.ui.components.gamepad.WajihaSettingPanel
import com.wajiha.ui.components.gamepad.WajihaToggleSetting
import com.wajiha.ui.components.gamepad.gameDetailGamepadHints
import com.wajiha.ui.scraper.PlatformScraperSettingsSection
import com.wajiha.ui.scraper.ScrapeModeSelector
import com.wajiha.ui.scraper.ScrapeUiMode
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.scraper.batchPolicyOrNull
import com.wajiha.ui.scraper.review.ScrapeReviewPicker
import com.wajiha.ui.scraper.review.ScrapeReviewViewModel
import com.wajiha.ui.scraper.toPolicy
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class PlatformSettingsTab(
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
    initialTab: PlatformSettingsTab = PlatformSettingsTab.General,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    viewModel: PlatformSettingsViewModel = koinInject(),
    scraperViewModel: ScraperViewModel = koinInject(),
) {
    LaunchedEffect(platformId) { viewModel.open(platformId) }
    val state by viewModel.uiState.collectAsState()
    val platform = state.platform
    val systemControls = koinInject<SystemControls>()
    val dualStore = koinInject<DualScreenStore>()
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val screenState by dualStore.state.collectAsState()
    val dualDisplay = screenState != DualScreenState.SingleDisplay
    val feedback = LocalUiFeedback.current

    val tabs = PlatformSettingsTab.entries
    var selectedTabIndex by
        remember(platformId, initialTab) {
            val remembered =
                rememberedFocusContext("platform_settings:$platformId:tab") as? Int
            mutableIntStateOf(
                if (initialTab != PlatformSettingsTab.General) {
                    initialTab.ordinal
                } else {
                    remembered?.coerceIn(0, tabs.lastIndex) ?: initialTab.ordinal
                },
            )
        }
    val sectionFocus = remember { FocusRequester() }
    val screenLayer = "platform_settings"
    val sectionFocusRestorer =
        remember(platformId, selectedTabIndex) {
            SettingSectionFocusRestorer(
                "platform_settings:$platformId:tab:$selectedTabIndex",
            )
        }
    val targetBeforeTabFocus =
        remember(platformId, selectedTabIndex) {
            rememberedFocusTarget(screenLayer)
        }

    fun selectTab(index: Int) {
        val newIndex = index.coerceIn(0, tabs.lastIndex)
        feedback.tabSelect(selectedTabIndex, newIndex)
        selectedTabIndex = newIndex
        rememberFocusContext("platform_settings:$platformId:tab", newIndex)
    }

    LaunchedEffect(selectedTabIndex, platform?.id, state.folders.isEmpty()) {
        if (platform == null) return@LaunchedEffect
        // Keep previous hero until the new tab's focus publishes (avoids double flash).
        withFrameNanos { }
        sectionFocusRestorer.restore(
            targetId = targetBeforeTabFocus,
            fallback = sectionFocus,
        )
    }

    DisposableEffect(Unit) {
        onDispose { clearSettingsHero(dualStore) }
    }

    ProvideSettingsDensity {
        WajihaScreen(
            layerId = screenLayer,
            modifier = modifier,
            onBack = onBack,
            showActionBar = true,
            gamepadHints = gameDetailGamepadHints(isDual = dualDisplay),
            gamepadOwner = gamepadOwner,
            onClaimGamepad = onClaimGamepad,
            onOwnerGainedFocus = {
                sectionFocusRestorer.restore(
                    targetId = rememberedFocusTarget(screenLayer),
                    fallback = sectionFocus,
                )
            },
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
                if (!dualDisplay) {
                    WajihaToolbar(
                        title = platform?.name ?: "Platform",
                        onBack = onBack,
                        backFocusable = false,
                    )
                }

                if (platform == null) {
                    if (dualDisplay) {
                        WajihaToolbar(
                            title = "",
                            onBack = onBack,
                            backFocusable = false,
                        )
                    }
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
                        if (dualDisplay) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .zIndex(1f)
                                        .padding(top = WajihaSpacing.sm),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                            ) {
                                GamepadButton(
                                    text = "Back",
                                    onClick = onBack,
                                    outlined = true,
                                    gamepadFocusable = false,
                                    sound = null,
                                )
                                FolderTabRow(
                                    tabs = tabs.map { it.label },
                                    selectedIndex = selectedTabIndex,
                                    onSelect = ::selectTab,
                                    minHeight = LocalSettingRowMinHeight.current,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .focusProperties { canFocus = false },
                                )
                            }
                        } else {
                            FolderTabRow(
                                tabs = tabs.map { it.label },
                                selectedIndex = selectedTabIndex,
                                onSelect = ::selectTab,
                                minHeight = LocalSettingRowMinHeight.current,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .zIndex(1f)
                                        .focusProperties { canFocus = false }
                                        .padding(top = WajihaSpacing.sm),
                            )
                        }

                        WajihaSettingPanel(
                            folderPanel = true,
                            focusRestorer = sectionFocusRestorer,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(bottom = WajihaSpacing.md),
                        ) {
                            when (tabs[selectedTabIndex]) {
                                PlatformSettingsTab.General -> {
                                    PlatformGeneralTabContent(
                                        platform = platform,
                                        viewModel = viewModel,
                                        dualStore = dualStore,
                                        firstFocusRequester = sectionFocus,
                                    )
                                }

                                PlatformSettingsTab.Emulator -> {
                                    PlatformEmulatorTabContent(
                                        platform = platform,
                                        state = state,
                                        systemControls = systemControls,
                                        viewModel = viewModel,
                                        dualStore = dualStore,
                                        settingsHeroActions = settings.settingsHeroActions,
                                        firstFocusRequester = sectionFocus,
                                    )
                                }

                                PlatformSettingsTab.Folders -> {
                                    PlatformFoldersTabContent(
                                        state = state,
                                        viewModel = viewModel,
                                        dualStore = dualStore,
                                        settingsHeroActions = settings.settingsHeroActions,
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
                                        firstFocusRequester = sectionFocus,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformGeneralTabContent(
    platform: PlatformEntity,
    viewModel: PlatformSettingsViewModel,
    dualStore: DualScreenStore,
    firstFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        WajihaSettingBlurb(
            "Show this system in the home filter when it has games. " +
                "Turning off does not remove ROM folders.",
        )
        WajihaToggleSetting(
            label = "In library",
            description = "When off, this system is hidden from the home platform filter.",
            checked = platform.enabled,
            onCheckedChange = viewModel::setEnabled,
            defaultChecked = true,
            onReset = { viewModel.setEnabled(true) },
            focusRequester = firstFocusRequester,
            onFocusedChanged =
                settingsToggleHeroFocus(
                    store = dualStore,
                    title = "In library",
                    subtitle = "When off, this system is hidden from the home platform filter.",
                    checked = platform.enabled,
                ),
        )
        WajihaSettingDivider()
        WajihaSettingBlurb("Display name and short label shown in the library grid.")
        NameFields(
            platform = platform,
            onNameCommit = viewModel::setDisplayName,
            onShortNameCommit = viewModel::setShortName,
        )
    }
}

@Composable
private fun PlatformEmulatorTabContent(
    platform: PlatformEntity,
    state: PlatformSettingsUiState,
    systemControls: SystemControls,
    viewModel: PlatformSettingsViewModel,
    dualStore: DualScreenStore,
    settingsHeroActions: Boolean,
    firstFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        WajihaSettingBlurb(
            "Player used when launching games for this system " +
                "(same idea as Daijishō / Cocoon players).",
        )
        if (state.emulators.isEmpty()) {
            WajihaEmptyState(
                title = "No emulators",
                subtitle = "No players are configured for this platform yet.",
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
            val selectedLabel =
                emulatorOptions.firstOrNull { it.value == selectedEmulatorId }?.label
                    ?: "—"
            val heroOptions =
                if (settingsHeroActions) {
                    emulatorOptions.map { opt ->
                        SettingsHeroOption(
                            value = opt.value,
                            label = opt.label,
                            selected = opt.value == selectedEmulatorId,
                        )
                    }
                } else {
                    emptyList()
                }
            GamepadSettingRow(
                label = "Default emulator",
                description = "Installed apps are selectable. Missing emulators stay visible but greyed out.",
                type = SettingType.MultiChoice,
                multiChoiceOptions = emulatorOptions,
                selected = selectedEmulatorId,
                onSelect = viewModel::setDefaultEmulator,
                focusRequester = firstFocusRequester,
                onFocusedChanged = { focused ->
                    if (focused) {
                        publishSettingsHero(
                            store = dualStore,
                            detail =
                                genericSettingHeroDetail(
                                    title = "Default emulator",
                                    subtitle =
                                        "Installed apps are selectable. Missing emulators stay visible but greyed out.",
                                    valueText = selectedLabel,
                                    options = heroOptions,
                                ),
                            onSelectOption =
                                if (settingsHeroActions) {
                                    viewModel::setDefaultEmulator
                                } else {
                                    null
                                },
                        )
                    } else {
                        clearSettingsHero(dualStore)
                    }
                },
            )
        }
    }
}

@Composable
private fun PlatformFoldersTabContent(
    state: PlatformSettingsUiState,
    viewModel: PlatformSettingsViewModel,
    dualStore: DualScreenStore,
    settingsHeroActions: Boolean,
    firstFocusRequester: FocusRequester,
) {
    val platform = state.platform ?: return
    val addFolderHeroFocus: (Boolean) -> Unit = { focused ->
        if (focused) {
            publishSettingsHero(
                dualStore,
                genericSettingHeroDetail(
                    title = "Add folder",
                    subtitle = "Pick a ROM folder on this device for this system.",
                ),
                onPrimaryAction = viewModel::pickRomFolder,
            )
        } else {
            clearSettingsHero(dualStore)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        WajihaSettingBlurb(
            "Folders scanned for games on this system. " +
                "Add at least one to include it in Settings → Library.",
        )
        if (state.folders.isEmpty()) {
            WajihaEmptyState(
                title = "Choose your games folder",
                subtitle =
                    "Select the folder that contains this system's ROMs. " +
                        "Wajiha will scan it and add discovered games to your library.",
                action = {
                    GamepadButton(
                        text = "Add folder",
                        onClick = viewModel::pickRomFolder,
                        focusRequester = firstFocusRequester,
                        onFocusedChanged = addFolderHeroFocus,
                    )
                },
            )
        }
        WajihaToggleSetting(
            label = "Deep scan",
            description = "Walk nested subfolders more thoroughly when scanning (depth 15 vs 3).",
            checked = platform.deepScan,
            onCheckedChange = viewModel::setDeepScan,
            defaultChecked = false,
            onReset = { viewModel.setDeepScan(false) },
            focusRequester = if (state.folders.isEmpty()) null else firstFocusRequester,
            onFocusedChanged =
                settingsToggleHeroFocus(
                    store = dualStore,
                    title = "Deep scan",
                    subtitle = "Walk nested subfolders more thoroughly when scanning (depth 15 vs 3).",
                    checked = platform.deepScan,
                ),
        )
        if (state.folders.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                GamepadButton(
                    text = "Rescan",
                    onClick = viewModel::rescanPlatform,
                    outlined = true,
                    onFocusedChanged = { focused ->
                        if (focused) {
                            publishSettingsHero(
                                store = dualStore,
                                detail =
                                    genericSettingHeroDetail(
                                        title = "Rescan",
                                        subtitle = "Scan ROM folders for this system and refresh the library.",
                                    ).copy(
                                        actions =
                                            if (settingsHeroActions) {
                                                listOf(SettingsHeroAction("rescan", "Rescan"))
                                            } else {
                                                emptyList()
                                            },
                                    ),
                                onPrimaryAction = viewModel::rescanPlatform,
                            )
                        } else {
                            clearSettingsHero(dualStore)
                        }
                    },
                )
                GamepadButton(
                    text = "Add folder",
                    onClick = viewModel::pickRomFolder,
                    modifier = Modifier.padding(start = WajihaSpacing.sm),
                    onFocusedChanged = addFolderHeroFocus,
                )
            }
            state.folders.forEach { folder ->
                FolderRow(
                    folder = folder,
                    dualStore = dualStore,
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
        WajihaSettingBlurb(
            "Scrape metadata and media for games on this platform, " +
                "or adjust linkage IDs and per-system source overrides.",
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

        WajihaSettingDivider()
        WajihaSettingBlurb(
            "IDs used to match games on ScreenScraper, RetroAchievements, and Libretro. " +
                "Defaults come from platform packs; tweak here if a match is wrong.",
        )
        ScraperIdFields(
            platform = platform,
            onScreenScraperId = viewModel::setScreenScraperId,
            onRaConsoleId = viewModel::setRaConsoleId,
            onLibretroName = viewModel::setLibretroName,
        )

        WajihaSettingDivider()
        WajihaSettingBlurb("Override global scraper sources and options for this system only.")
        PlatformScraperSettingsSection(
            platformId = platform.id,
            viewModel = scraperViewModel,
        )
    }
}

@Composable
private fun PlatformInfoTabContent(
    platform: PlatformEntity,
    state: PlatformSettingsUiState,
    firstFocusRequester: FocusRequester,
) {
    val defaultEmulatorName =
        state.emulators
            .firstOrNull { it.id == platform.defaultEmulatorId }
            ?.name
            ?: state.emulators.firstOrNull { it.isDefault }?.name
            ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        WajihaSettingBlurb("Read-only platform metadata and library stats.")

        PlatformInfoValueRow("Platform id", platform.id, firstFocusRequester)
        PlatformInfoValueRow("Display name", platform.name)
        PlatformInfoValueRow("Short name", platform.shortName)
        PlatformInfoValueRow("In library", if (platform.enabled) "Yes" else "No")
        PlatformInfoValueRow("Games", "${state.gameCount}")
        PlatformInfoValueRow("ROM folders", "${state.folders.size}")
        PlatformInfoValueRow("Default emulator", defaultEmulatorName)
        platform.boxartAspectRatio?.let {
            PlatformInfoValueRow("Boxart aspect", it)
        }
        PlatformInfoValueRow("Extensions", platform.extensions)
        PlatformInfoValueRow(
            "ScreenScraper id",
            platform.screenScraperId?.toString() ?: "—",
        )
        PlatformInfoValueRow(
            "RetroAchievements id",
            platform.raConsoleId?.toString() ?: "—",
        )
        PlatformInfoValueRow(
            "Libretro name",
            platform.libretroName ?: "—",
        )

        if (state.folders.isNotEmpty()) {
            WajihaSettingDivider()
            WajihaSettingBlurb("Folder paths")
            state.folders.forEach { folder ->
                PlatformInfoValueRow(
                    label = "Folder",
                    value = folderDisplayPath(folder),
                    maxLines = 4,
                )
            }
        }
    }
}

@Composable
private fun PlatformInfoValueRow(
    label: String,
    value: String,
    focusRequester: FocusRequester? = null,
    maxLines: Int = 2,
) {
    GamepadSettingRow(
        label = label,
        type = SettingType.Action,
        focusRequester = focusRequester,
        content = {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Human-readable label for a SAF tree URI (percent-decoded path segment). */
internal fun folderDisplayPath(folder: RomFolderEntity): String {
    val raw = folder.treeUri
    val treeSegment =
        raw
            .substringAfter("/tree/", missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
            .ifBlank {
                raw.substringAfterLast('/').substringBefore('?').substringBefore('#')
            }
    val decoded = percentDecode(treeSegment).ifBlank { raw }
    return decoded.ifBlank { raw }
}

private fun percentDecode(input: String): String {
    if (input.isEmpty() || '%' !in input) return input
    val out = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '%' && i + 2 < input.length) {
            val hex = input.substring(i + 1, i + 3)
            val byte = hex.toIntOrNull(16)
            if (byte != null) {
                out.append(byte.toChar())
                i += 3
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

@Composable
private fun NameFields(
    platform: PlatformEntity,
    onNameCommit: (String) -> Unit,
    onShortNameCommit: (String) -> Unit,
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
            modifier = Modifier.fillMaxWidth(),
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
    dualStore: DualScreenStore,
    onRemove: () -> Unit,
) {
    val path = folderDisplayPath(folder)
    GamepadSettingRow(
        label = path,
        description = "ROM folder on this device.",
        type = SettingType.Action,
        onActivate = onRemove,
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualStore,
                    genericSettingHeroDetail(
                        title = path,
                        subtitle = "ROM folder on this device.",
                    ),
                    onPrimaryAction = onRemove,
                )
            } else {
                clearSettingsHero(dualStore)
            }
        },
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
            modifier = Modifier.fillMaxWidth(),
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
