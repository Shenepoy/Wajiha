package com.wajiha.ui.scraper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wajiha.data.scraper.PlatformScraperOverride
import com.wajiha.data.scraper.ScrapeFailureKind
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.SteamGridDbStyleHints
import com.wajiha.data.scraper.parseSteamGridDbStyles
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadNavHost
import com.wajiha.input.GamepadNavMode
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.rememberGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaFolderSettingChrome
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.GamepadSettingTrailingActions
import com.wajiha.ui.components.gamepad.LocalSettingRowMinHeight
import com.wajiha.ui.components.gamepad.MultiChoiceOption
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.SettingsTrailingActionButton
import com.wajiha.ui.components.gamepad.WajihaActionSetting
import com.wajiha.ui.components.gamepad.WajihaFieldMessage
import com.wajiha.ui.components.gamepad.WajihaFieldMessageSeverity
import com.wajiha.ui.components.gamepad.WajihaFieldMessageState
import com.wajiha.ui.components.gamepad.WajihaMultiChoiceSetting
import com.wajiha.ui.components.gamepad.WajihaMultiSelectSetting
import com.wajiha.ui.components.gamepad.WajihaNumberSetting
import com.wajiha.ui.components.gamepad.WajihaSettingDivider
import com.wajiha.ui.components.gamepad.WajihaSettingFullscreenPage
import com.wajiha.ui.components.gamepad.WajihaSettingGroup
import com.wajiha.ui.components.gamepad.WajihaSettingOpenSetting
import com.wajiha.ui.components.gamepad.WajihaToggleSetting
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.components.gamepad.withoutDualScreenChrome
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Standalone scraper page: batch scrape actions plus global source settings.
 * Per-system overrides live on each platform's edit page.
 * Review (interactive match) lives on Game detail and Platform → Scraper.
 * Drill-ins (Sources, Accounts, …) replace the folder chrome with their own view.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScraperScreen(
    viewModel: ScraperViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
) {
    val dualStore = koinInject<DualScreenStore>()
    val screenState by dualStore.state.collectAsState()
    val isDual = screenState != DualScreenState.SingleDisplay
    var subpage by remember { mutableStateOf<ScraperSubpage?>(null) }
    var lastSubpage by remember { mutableStateOf<ScraperSubpage?>(null) }
    val contentFocus = remember { FocusRequester() }
    val hubFocus = remember { FocusRequester() }
    val navController =
        rememberGamepadNavController(
            mode = GamepadNavMode.Vertical,
            onBack = {
                if (subpage != null) {
                    subpage = null
                    true
                } else {
                    onBack()
                    true
                }
            },
        )

    BackHandler(enabled = subpage != null) { subpage = null }

    LaunchedEffect(subpage) {
        val target = if (subpage != null) contentFocus else hubFocus.takeIf { lastSubpage != null }
        if (target == null) return@LaunchedEffect
        withFrameNanos { }
        try {
            target.requestFocus()
        } catch (_: Exception) {
        }
    }

    WajihaScreen(
        layerId = "scraper",
        modifier = modifier,
        onBack = {
            if (subpage != null) {
                subpage = null
            } else {
                onBack()
            }
        },
        showActionBar = true,
        gamepadHints =
            listOf(
                GamepadHint(GamepadHintButton.A, "Confirm"),
                GamepadHint(GamepadHintButton.B, "Back"),
                GamepadHint(GamepadHintButton.L2, "Focus screen"),
            ).withoutDualScreenChrome(isDual),
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = {
            navController.focusState.clampIndex()
        },
    ) {
        GamepadNavHost(controller = navController) {
            val page = subpage
            if (page != null) {
                ScraperSubpageFullscreen(
                    page = page,
                    viewModel = viewModel,
                    onBack = { subpage = null },
                    contentFocusRequester = contentFocus,
                )
            } else {
                WajihaFolderSettingChrome(
                    onBack = onBack,
                    tabs = listOf("Scraper"),
                    selectedIndex = 0,
                    onSelect = {},
                ) {
                    ScraperHubContent(
                        onOpen = { opened ->
                            lastSubpage = opened
                            subpage = opened
                        },
                        restoreFocusPage = lastSubpage,
                        hubFocusRequester = hubFocus,
                    )
                }
            }
        }
    }
}

/** Drill-in destinations from the Scraper hub (Sources, Accounts, …). */
enum class ScraperSubpage(
    val title: String,
    val summary: String,
) {
    Batch("Batch scrape", "Run across all platforms or per system."),
    Sources("Sources", "Enable or disable scraper backends."),
    Accounts("Accounts", "Credentials per source."),
    MediaDefaults("Media defaults", "Resolution, variants, and per-source media."),
    BatchOptions("Batch options", "Wi-Fi, region, and language priority."),
}

/**
 * Full-screen scraper drill-in: Back + title, no Settings folder tabs.
 * Hosted by [ScraperScreen] and Settings → Scraper via [WajihaSettingFullscreenPage].
 */
@Composable
fun ScraperSubpageFullscreen(
    page: ScraperSubpage,
    viewModel: ScraperViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
) {
    val apiLogs by viewModel.apiLogs.collectAsState()
    var showApiLogs by remember(page) { mutableStateOf(false) }
    val apiLogsOpenFocus = remember { FocusRequester() }
    var restoreApiLogsOpenFocus by remember { mutableStateOf(false) }

    if (page == ScraperSubpage.Batch && showApiLogs) {
        ScrapeApiLogsPage(
            entries = apiLogs,
            onBack = {
                restoreApiLogsOpenFocus = true
                showApiLogs = false
            },
            onClear = viewModel::clearApiLogs,
            modifier = modifier,
            firstFocusRequester = contentFocusRequester,
        )
        return
    }

    // Key on showApiLogs so clearing the restore flag cannot cancel requestFocus.
    LaunchedEffect(showApiLogs) {
        if (showApiLogs || !restoreApiLogsOpenFocus) return@LaunchedEffect
        restoreApiLogsOpenFocus = false
        repeat(2) { withFrameNanos { } }
        try {
            apiLogsOpenFocus.requestFocus()
        } catch (_: Exception) {
            try {
                contentFocusRequester?.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    WajihaSettingFullscreenPage(
        title = page.title,
        onBack = onBack,
        modifier = modifier,
    ) {
        WajihaSettingGroup {
            ScraperSubpageBody(
                page = page,
                viewModel = viewModel,
                firstFocusRequester = contentFocusRequester,
                onOpenApiLogs =
                    if (page == ScraperSubpage.Batch) {
                        { showApiLogs = true }
                    } else {
                        null
                    },
                apiLogsFocusRequester =
                    if (page == ScraperSubpage.Batch) {
                        apiLogsOpenFocus
                    } else {
                        null
                    },
            )
        }
    }
}

/** Scraper hub rows — open a [ScraperSubpage] via [onOpen]. */
@Composable
fun ScraperHubContent(
    onOpen: (ScraperSubpage) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
    restoreFocusPage: ScraperSubpage? = null,
    hubFocusRequester: FocusRequester? = null,
) {
    val openRowFocus: (ScraperSubpage) -> FocusRequester? = { candidate ->
        hubFocusRequester?.takeIf { restoreFocusPage == candidate }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        WajihaSettingGroup(title = "Scrape") {
            WajihaSettingOpenSetting(
                label = ScraperSubpage.Batch.title,
                onOpen = { onOpen(ScraperSubpage.Batch) },
                focusRequester = openRowFocus(ScraperSubpage.Batch) ?: firstFocusRequester,
            )
        }
        WajihaSettingGroup(title = "Setup") {
            WajihaSettingOpenSetting(
                label = ScraperSubpage.Sources.title,
                onOpen = { onOpen(ScraperSubpage.Sources) },
                focusRequester = openRowFocus(ScraperSubpage.Sources),
            )
            WajihaSettingDivider()
            WajihaSettingOpenSetting(
                label = ScraperSubpage.Accounts.title,
                onOpen = { onOpen(ScraperSubpage.Accounts) },
                focusRequester = openRowFocus(ScraperSubpage.Accounts),
            )
            WajihaSettingDivider()
            WajihaSettingOpenSetting(
                label = ScraperSubpage.MediaDefaults.title,
                onOpen = { onOpen(ScraperSubpage.MediaDefaults) },
                focusRequester = openRowFocus(ScraperSubpage.MediaDefaults),
            )
            WajihaSettingDivider()
            WajihaSettingOpenSetting(
                label = ScraperSubpage.BatchOptions.title,
                onOpen = { onOpen(ScraperSubpage.BatchOptions) },
                focusRequester = openRowFocus(ScraperSubpage.BatchOptions),
            )
        }
    }
}

@Composable
private fun ScraperSubpageBody(
    page: ScraperSubpage,
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
    onOpenApiLogs: (() -> Unit)? = null,
    apiLogsFocusRequester: FocusRequester? = null,
) {
    when (page) {
        ScraperSubpage.Batch -> {
            ScraperBatchBlock(
                viewModel,
                firstFocusRequester = firstFocusRequester,
                onOpenApiLogs = onOpenApiLogs,
                apiLogsFocusRequester = apiLogsFocusRequester,
            )
        }

        ScraperSubpage.Sources -> {
            ScraperSourcesSection(viewModel, firstFocusRequester = firstFocusRequester)
        }

        ScraperSubpage.Accounts -> {
            ScraperAccountsSection(viewModel, firstFocusRequester = firstFocusRequester)
        }

        ScraperSubpage.MediaDefaults -> {
            ScraperMediaDefaultsSection(viewModel, firstFocusRequester = firstFocusRequester)
        }

        ScraperSubpage.BatchOptions -> {
            ScraperBatchOptionsSection(viewModel, firstFocusRequester = firstFocusRequester)
        }
    }
}

@Composable
fun PlatformScraperSettingsSection(
    platformId: String,
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
) {
    val settings by viewModel.settings.collectAsState()
    val override = settings.platformOverrides[platformId]
    val effectiveSources = override?.enabledSources ?: settings.enabledSources

    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        Text(
            text = "Sources for this platform",
            style = MaterialTheme.typography.labelMedium,
        )
        allSourceIds.forEachIndexed { index, sourceId ->
            SourceToggle(
                label = sourceId,
                checked = sourceId in effectiveSources,
                overridden = isPlatformSourceOverridden(override, sourceId, settings.enabledSources),
                focusRequester = if (index == 0) firstFocusRequester else null,
            ) { enabled ->
                viewModel.togglePlatformSource(platformId, sourceId, enabled)
            }
        }

        if (effectiveSources.isNotEmpty()) {
            CredentialField(
                label = "Region priority",
                value = override?.regionPriority?.joinToString(",") ?: "",
                overridden = override?.regionPriority != null,
            ) { v ->
                viewModel.setPlatformRegionPriority(
                    platformId,
                    v.split(',').map(String::trim).filter(String::isNotEmpty),
                )
            }
            WajihaSettingDivider()
            PlatformSourceOptionsBlock(
                viewModel = viewModel,
                platformId = platformId,
                global = settings,
                override = override,
                effectiveSources = effectiveSources.toSet(),
            )
        }

        if (override != null) {
            Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
                Text(
                    text = "Remove per-platform scraper settings and inherit global defaults.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    GamepadButton(
                        text = "Clear",
                        onClick = { viewModel.clearPlatformOverride(platformId) },
                        outlined = true,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ScraperAccountSubsection(
    title: String,
    configured: Boolean,
    sourceId: String? = null,
    onTest: (() -> Unit)? = null,
    viewModel: ScraperViewModel? = null,
    focusRequester: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var headerFocused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val headerHighlight = !useCustomNav && headerFocused && !expanded
    val localHeaderFocus = remember { FocusRequester() }
    val headerFocusRequester = focusRequester ?: localHeaderFocus
    val statusLabel = if (configured) "Configured" else "Not set"

    fun collapse() {
        expanded = false
        try {
            headerFocusRequester.requestFocus()
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
                .clip(WajihaShapes.chip)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    shape = WajihaShapes.chip,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wajihaFocusIndicator(highlighted = headerHighlight)
                    .clip(WajihaShapes.focus)
                    .focusRequester(headerFocusRequester)
                    .then(
                        if (!useCustomNav) {
                            Modifier
                                .onFocusChanged { headerFocused = it.isFocused }
                                .wajihaGamepadFocus()
                                .onPreviewKeyEvent { event ->
                                    if (GamepadKeys.isConfirm(event.type, event.key)) {
                                        expanded = !expanded
                                        true
                                    } else {
                                        false
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ).pointerInput(Unit) {
                        detectTapGestures { expanded = !expanded }
                    }.padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (configured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = if (expanded) "˅" else "›",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                content()
                if (onTest != null && sourceId != null && viewModel != null) {
                    SourceTestLoginRow(
                        sourceId = sourceId,
                        onTest = onTest,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScraperSourcesSection(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
) {
    val settings by viewModel.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        SourceToggle(
            label = "ScreenScraper",
            checked = "screenscraper" in settings.enabledSources,
            focusRequester = firstFocusRequester,
        ) { viewModel.toggleSource("screenscraper", it) }
        SourceToggle(
            label = "SteamGridDB",
            checked = "steamgriddb" in settings.enabledSources,
        ) { viewModel.toggleSource("steamgriddb", it) }
        SourceToggle(
            label = "Libretro thumbnails (no account)",
            checked = "libretro" in settings.enabledSources,
        ) { viewModel.toggleSource("libretro", it) }
        SourceToggle(
            label = "RetroAchievements",
            checked = "ra" in settings.enabledSources,
        ) { viewModel.toggleSource("ra", it) }
        SourceToggle(
            label = "RomM server",
            checked = "romm" in settings.enabledSources,
        ) { viewModel.toggleSource("romm", it) }
        SourceToggle(
            label = "Local media",
            checked = "local" in settings.enabledSources,
        ) { viewModel.toggleSource("local", it) }
    }
}

@Composable
private fun ScraperAccountsSection(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
) {
    val settings by viewModel.settings.collectAsState()
    val enabled = settings.enabledSources.toSet()
    val firstAccountSource =
        listOf("screenscraper", "steamgriddb", "ra", "romm", "local").firstOrNull { it in enabled }

    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        if ("screenscraper" in enabled) {
            ScraperAccountSubsection(
                title = "ScreenScraper",
                configured = viewModel.isScreenScraperConfigured(settings),
                sourceId = "screenscraper",
                onTest = { viewModel.testSourceCredentials("screenscraper") },
                viewModel = viewModel,
                focusRequester =
                    firstFocusRequester.takeIf { firstAccountSource == "screenscraper" },
            ) {
                CompactCredentialField("Username", settings.screenScraperUser) { v ->
                    viewModel.update { it.copy(screenScraperUser = v) }
                }
                CompactCredentialField("Password", settings.screenScraperPassword, secret = true) { v ->
                    viewModel.update { it.copy(screenScraperPassword = v) }
                }
                CompactCredentialField("Dev ID (or built in)", settings.screenScraperDevId) { v ->
                    viewModel.update { it.copy(screenScraperDevId = v) }
                }
                CompactCredentialField(
                    "Dev password (or built in)",
                    settings.screenScraperDevPassword,
                    secret = true,
                ) { v ->
                    viewModel.update { it.copy(screenScraperDevPassword = v) }
                }
                Text(
                    "ScreenScraper requires a developer app pair on every request. " +
                        "Leave these fields blank only when this build includes that pair.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if ("steamgriddb" in enabled) {
            ScraperAccountSubsection(
                title = "SteamGridDB",
                configured = settings.steamGridDbApiKey.isNotBlank(),
                sourceId = "steamgriddb",
                onTest = { viewModel.testSourceCredentials("steamgriddb") },
                viewModel = viewModel,
                focusRequester =
                    firstFocusRequester.takeIf { firstAccountSource == "steamgriddb" },
            ) {
                CompactCredentialField("API key", settings.steamGridDbApiKey, secret = true) { v ->
                    viewModel.update { it.copy(steamGridDbApiKey = v) }
                }
            }
        }
        if ("ra" in enabled) {
            ScraperAccountSubsection(
                title = "RetroAchievements",
                configured = settings.raUsername.isNotBlank() && settings.raApiKey.isNotBlank(),
                sourceId = "ra",
                onTest = { viewModel.testSourceCredentials("ra") },
                viewModel = viewModel,
                focusRequester = firstFocusRequester.takeIf { firstAccountSource == "ra" },
            ) {
                CompactCredentialField("Username", settings.raUsername) { v ->
                    viewModel.update { it.copy(raUsername = v) }
                }
                CompactCredentialField("Web API key", settings.raApiKey, secret = true) { v ->
                    viewModel.update { it.copy(raApiKey = v) }
                }
            }
        }
        if ("romm" in enabled) {
            ScraperAccountSubsection(
                title = "RomM",
                configured = settings.rommUrl.isNotBlank(),
                sourceId = "romm",
                onTest = { viewModel.testSourceCredentials("romm") },
                viewModel = viewModel,
                focusRequester = firstFocusRequester.takeIf { firstAccountSource == "romm" },
            ) {
                CompactCredentialField("Server URL", settings.rommUrl) { v ->
                    viewModel.update { it.copy(rommUrl = v) }
                }
                CompactCredentialField("Username", settings.rommUsername) { v ->
                    viewModel.update { it.copy(rommUsername = v) }
                }
                CompactCredentialField("Password", settings.rommPassword, secret = true) { v ->
                    viewModel.update { it.copy(rommPassword = v) }
                }
            }
        }
        if ("local" in enabled) {
            ScraperAccountSubsection(
                title = "Local media",
                configured = settings.localMediaPath.isNotBlank(),
                focusRequester = firstFocusRequester.takeIf { firstAccountSource == "local" },
            ) {
                CompactCredentialField("Media folder (ES-DE layout)", settings.localMediaPath) { v ->
                    viewModel.update { it.copy(localMediaPath = v) }
                }
            }
        }
        if (enabled.intersect(setOf("screenscraper", "steamgriddb", "ra", "romm", "local")).isEmpty()) {
            Text(
                text = "Enable a source above to configure its account.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScraperMediaDefaultsSection(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
) {
    val settings by viewModel.settings.collectAsState()
    val defaults = remember { ScraperSettings() }
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        WajihaNumberSetting(
            label = "Max image resolution",
            description = "Longest edge in pixels; 0 keeps the original size.",
            value = settings.maxImageResolution,
            onValueChange = { px ->
                viewModel.update { it.copy(maxImageResolution = px.coerceIn(0, 8192)) }
            },
            range = 0..8192,
            step = 256,
            valueLabel = { if (it == 0) "Original" else "${it}px" },
            defaultValue = defaults.maxImageResolution,
            onReset = {
                viewModel.update { it.copy(maxImageResolution = defaults.maxImageResolution) }
            },
            focusRequester = firstFocusRequester,
        )
        WajihaNumberSetting(
            label = "Cover variant index",
            description = "0 = first/best alternate; 1 = second, and so on.",
            value = settings.mediaVariantIndex,
            onValueChange = { idx ->
                viewModel.update { it.copy(mediaVariantIndex = idx.coerceAtLeast(0)) }
            },
            range = 0..99,
            step = 1,
            defaultValue = defaults.mediaVariantIndex,
            onReset = {
                viewModel.update { it.copy(mediaVariantIndex = defaults.mediaVariantIndex) }
            },
        )
        WajihaSettingDivider()
        ScraperSourceOptionsBlock(viewModel)
        WajihaSettingDivider()
        Text(
            "Media priority (tap a source to promote it)",
            style = MaterialTheme.typography.labelLarge,
        )
        settings.mediaPriority.entries.forEach { (mediaType, chain) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mediaType,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(96.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
                    items(chain) { sourceId ->
                        GamepadChip(
                            label = sourceId,
                            selected = false,
                            onClick = { viewModel.promoteMediaSource(mediaType, sourceId) },
                            sound = UiSound.Open,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScraperBatchOptionsSection(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
) {
    val settings by viewModel.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        WajihaToggleSetting(
            label = "Wi-Fi only",
            checked = settings.wifiOnly,
            onCheckedChange = { v -> viewModel.update { it.copy(wifiOnly = v) } },
            defaultChecked = false,
            onReset = { viewModel.update { it.copy(wifiOnly = false) } },
            focusRequester = firstFocusRequester,
        )
        CompactCredentialField(
            "Region priority (comma separated)",
            settings.regionPriority.joinToString(","),
        ) { v ->
            viewModel.update {
                it.copy(regionPriority = v.split(',').map(String::trim).filter(String::isNotEmpty))
            }
        }
        CompactCredentialField(
            "Language priority (comma separated)",
            settings.languagePriority.joinToString(","),
        ) { v ->
            viewModel.update {
                it.copy(languagePriority = v.split(',').map(String::trim).filter(String::isNotEmpty))
            }
        }
    }
}

private val allSourceIds =
    listOf("screenscraper", "steamgriddb", "libretro", "ra", "romm", "local")

private val screenScraperBoxOptions =
    listOf(
        MultiChoiceOption("prefer_2d", "Prefer 2D", "Use 2D box art when available"),
        MultiChoiceOption("prefer_3d", "Prefer 3D", "Use 3D box art when available"),
        MultiChoiceOption("2d_only", "2D only"),
        MultiChoiceOption("3d_only", "3D only"),
    )

private val screenScraperScreenshotOptions =
    listOf(
        MultiChoiceOption("screenshot", "In-game", "ScreenScraper ss"),
        MultiChoiceOption("title", "Title screen", "ScreenScraper ss-title"),
        MultiChoiceOption("both", "Both"),
    )

private val screenScraperLogoOptions =
    listOf(
        MultiChoiceOption("wheel", "Wheel"),
        MultiChoiceOption("wheel_hd", "Wheel HD"),
        MultiChoiceOption("marquee", "Marquee"),
        MultiChoiceOption("screenmarquee", "Screen marquee"),
    )

private val steamGridDbAnimationOptions =
    listOf(
        MultiChoiceOption("static", "Static"),
        MultiChoiceOption("animated", "Animated"),
        MultiChoiceOption("both", "Both"),
    )

@Composable
private fun ScraperSourceOptionsBlock(viewModel: ScraperViewModel) {
    val settings by viewModel.settings.collectAsState()
    val enabled = settings.enabledSources.toSet()

    if ("screenscraper" in enabled) {
        Text("ScreenScraper media", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "Box art, screenshot, and logo types returned by ScreenScraper API.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val defaults = remember { ScraperSettings() }
        WajihaMultiChoiceSetting(
            label = "Box art type",
            description = "ScreenScraper box art preference.",
            choiceOptions = screenScraperBoxOptions,
            selected = settings.screenScraperBoxType,
            onSelect = { viewModel.update { s -> s.copy(screenScraperBoxType = it) } },
            defaultValue = defaults.screenScraperBoxType,
            onReset = {
                viewModel.update { s -> s.copy(screenScraperBoxType = defaults.screenScraperBoxType) }
            },
        )
        WajihaMultiChoiceSetting(
            label = "Screenshot type",
            description = "ScreenScraper screenshot preference.",
            choiceOptions = screenScraperScreenshotOptions,
            selected = settings.screenScraperScreenshotType,
            onSelect = { viewModel.update { s -> s.copy(screenScraperScreenshotType = it) } },
            defaultValue = defaults.screenScraperScreenshotType,
            onReset = {
                viewModel.update { s ->
                    s.copy(screenScraperScreenshotType = defaults.screenScraperScreenshotType)
                }
            },
        )
        WajihaMultiChoiceSetting(
            label = "Logo / marquee type",
            description = "ScreenScraper logo / marquee preference.",
            choiceOptions = screenScraperLogoOptions,
            selected = settings.screenScraperLogoType,
            onSelect = { viewModel.update { s -> s.copy(screenScraperLogoType = it) } },
            defaultValue = defaults.screenScraperLogoType,
            onReset = {
                viewModel.update { s -> s.copy(screenScraperLogoType = defaults.screenScraperLogoType) }
            },
        )
        WajihaToggleSetting(
            label = "Map fanart as hero",
            checked = settings.screenScraperFanartAsHero,
            onCheckedChange = { v -> viewModel.update { it.copy(screenScraperFanartAsHero = v) } },
            defaultChecked = defaults.screenScraperFanartAsHero,
            onReset = {
                viewModel.update {
                    it.copy(screenScraperFanartAsHero = defaults.screenScraperFanartAsHero)
                }
            },
        )
        WajihaSettingDivider()
    }

    if ("steamgriddb" in enabled) {
        Text("SteamGridDB media", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "Per photo-type style filters. ${SteamGridDbStyleHints.EmptyMeansAll}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SteamGridDbStyleField(
            label = "Grid styles (boxart)",
            hint = SteamGridDbStyleHints.Grid,
            value = settings.steamGridDbGridStyles,
        ) { styles ->
            viewModel.update { it.copy(steamGridDbGridStyles = styles) }
        }
        SteamGridDbStyleField(
            label = "Hero styles",
            hint = SteamGridDbStyleHints.Hero,
            value = settings.steamGridDbHeroStyles,
        ) { styles ->
            viewModel.update { it.copy(steamGridDbHeroStyles = styles) }
        }
        SteamGridDbStyleField(
            label = "Logo styles",
            hint = SteamGridDbStyleHints.Logo,
            value = settings.steamGridDbLogoStyles,
        ) { styles ->
            viewModel.update { it.copy(steamGridDbLogoStyles = styles) }
        }
        SteamGridDbStyleField(
            label = "Icon styles",
            hint = SteamGridDbStyleHints.Icon,
            value = settings.steamGridDbIconStyles,
        ) { styles ->
            viewModel.update { it.copy(steamGridDbIconStyles = styles) }
        }
        val sgdbDefaults = remember { ScraperSettings() }
        WajihaMultiChoiceSetting(
            label = "Grid animation",
            description = "SteamGridDB animation filter.",
            choiceOptions = steamGridDbAnimationOptions,
            selected = settings.steamGridDbAnimation,
            onSelect = { viewModel.update { s -> s.copy(steamGridDbAnimation = it) } },
            defaultValue = sgdbDefaults.steamGridDbAnimation,
            onReset = {
                viewModel.update { s ->
                    s.copy(steamGridDbAnimation = sgdbDefaults.steamGridDbAnimation)
                }
            },
        )
        WajihaToggleSetting(
            label = "Include NSFW grids",
            checked = settings.steamGridDbIncludeNsfw,
            onCheckedChange = { v -> viewModel.update { it.copy(steamGridDbIncludeNsfw = v) } },
            defaultChecked = sgdbDefaults.steamGridDbIncludeNsfw,
            onReset = {
                viewModel.update {
                    it.copy(steamGridDbIncludeNsfw = sgdbDefaults.steamGridDbIncludeNsfw)
                }
            },
        )
        WajihaToggleSetting(
            label = "Include humor grids",
            checked = settings.steamGridDbIncludeHumor,
            onCheckedChange = { v -> viewModel.update { it.copy(steamGridDbIncludeHumor = v) } },
            defaultChecked = sgdbDefaults.steamGridDbIncludeHumor,
            onReset = {
                viewModel.update {
                    it.copy(steamGridDbIncludeHumor = sgdbDefaults.steamGridDbIncludeHumor)
                }
            },
        )
        WajihaSettingDivider()
    }

    Text("Media author ranking", style = MaterialTheme.typography.labelLarge)
    Text(
        text =
            "Prefer or blacklist SteamGridDB authors (steam64 or name). " +
                "Blacklisted authors are skipped even with the highest score.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CompactCredentialField(
        "Preferred authors (comma separated)",
        settings.preferredMediaAuthors.joinToString(", "),
    ) { v ->
        viewModel.update {
            it.copy(
                preferredMediaAuthors =
                    v.split(',').map(String::trim).filter(String::isNotEmpty),
            )
        }
    }
    CompactCredentialField(
        "Blacklisted authors (comma separated)",
        settings.blacklistedMediaAuthors.joinToString(", "),
    ) { v ->
        viewModel.update {
            it.copy(
                blacklistedMediaAuthors =
                    v.split(',').map(String::trim).filter(String::isNotEmpty),
            )
        }
    }
    WajihaSettingDivider()

    if ("libretro" in enabled) {
        val defaults = remember { ScraperSettings() }
        Text("Libretro thumbnails", style = MaterialTheme.typography.labelLarge)
        SourceToggle(
            label = "Fetch box art (Named_Boxarts)",
            checked = settings.libretroFetchBoxart,
            defaultChecked = defaults.libretroFetchBoxart,
            onChecked = { v -> viewModel.update { it.copy(libretroFetchBoxart = v) } },
        )
        SourceToggle(
            label = "Fetch snaps (Named_Snaps)",
            checked = settings.libretroFetchSnaps,
            defaultChecked = defaults.libretroFetchSnaps,
            onChecked = { v -> viewModel.update { it.copy(libretroFetchSnaps = v) } },
        )
        SourceToggle(
            label = "Fetch titles (Named_Titles)",
            checked = settings.libretroFetchTitles,
            defaultChecked = defaults.libretroFetchTitles,
            onChecked = { v -> viewModel.update { it.copy(libretroFetchTitles = v) } },
        )
        WajihaSettingDivider()
    }

    if ("ra" in enabled) {
        val defaults = remember { ScraperSettings() }
        Text("RetroAchievements media", style = MaterialTheme.typography.labelLarge)
        SourceToggle(
            label = "Fetch icon",
            checked = settings.raFetchIcon,
            defaultChecked = defaults.raFetchIcon,
            onChecked = { v -> viewModel.update { it.copy(raFetchIcon = v) } },
        )
        SourceToggle(
            label = "Fetch box art",
            checked = settings.raFetchBoxArt,
            defaultChecked = defaults.raFetchBoxArt,
            onChecked = { v -> viewModel.update { it.copy(raFetchBoxArt = v) } },
        )
        SourceToggle(
            label = "Fetch title screen",
            checked = settings.raFetchTitle,
            defaultChecked = defaults.raFetchTitle,
            onChecked = { v -> viewModel.update { it.copy(raFetchTitle = v) } },
        )
    }
}

@Composable
private fun PlatformSourceOptionsBlock(
    viewModel: ScraperViewModel,
    platformId: String,
    global: ScraperSettings,
    override: PlatformScraperOverride?,
    effectiveSources: Set<String>,
) {
    val effective = global.forPlatform(platformId)

    if ("screenscraper" in effectiveSources) {
        Text("ScreenScraper", style = MaterialTheme.typography.labelMedium)
        WajihaMultiChoiceSetting(
            label = "Box art type",
            description = "ScreenScraper box art preference for this system.",
            choiceOptions = screenScraperBoxOptions,
            selected = effective.screenScraperBoxType,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperBoxType,
                    { o, v -> o.copy(screenScraperBoxType = v) },
                    it,
                )
            },
            defaultValue = global.screenScraperBoxType,
            onReset = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperBoxType,
                    { o, v -> o.copy(screenScraperBoxType = v) },
                    global.screenScraperBoxType,
                )
            },
            overridden = override?.screenScraperBoxType != null,
        )
        WajihaMultiChoiceSetting(
            label = "Screenshot type",
            description = "ScreenScraper screenshot preference for this system.",
            choiceOptions = screenScraperScreenshotOptions,
            selected = effective.screenScraperScreenshotType,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperScreenshotType,
                    { o, v -> o.copy(screenScraperScreenshotType = v) },
                    it,
                )
            },
            defaultValue = global.screenScraperScreenshotType,
            onReset = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperScreenshotType,
                    { o, v -> o.copy(screenScraperScreenshotType = v) },
                    global.screenScraperScreenshotType,
                )
            },
            overridden = override?.screenScraperScreenshotType != null,
        )
        WajihaMultiChoiceSetting(
            label = "Logo type",
            description = "ScreenScraper logo preference for this system.",
            choiceOptions = screenScraperLogoOptions,
            selected = effective.screenScraperLogoType,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperLogoType,
                    { o, v -> o.copy(screenScraperLogoType = v) },
                    it,
                )
            },
            defaultValue = global.screenScraperLogoType,
            onReset = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperLogoType,
                    { o, v -> o.copy(screenScraperLogoType = v) },
                    global.screenScraperLogoType,
                )
            },
            overridden = override?.screenScraperLogoType != null,
        )
        SourceToggle(
            label = "Map fanart as hero",
            checked = effective.screenScraperFanartAsHero,
            overridden = override?.screenScraperFanartAsHero != null,
            defaultChecked = global.screenScraperFanartAsHero,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.screenScraperFanartAsHero,
                    { o, value -> o.copy(screenScraperFanartAsHero = value) },
                    v,
                )
            },
        )
        WajihaSettingDivider()
    }

    if ("steamgriddb" in effectiveSources) {
        Text("SteamGridDB", style = MaterialTheme.typography.labelMedium)
        Text(
            text = SteamGridDbStyleHints.EmptyMeansAll,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SteamGridDbStyleField(
            label = "Grid styles (boxart)",
            hint = SteamGridDbStyleHints.Grid,
            value = override?.steamGridDbGridStyles ?: global.steamGridDbGridStyles,
            overridden = override?.steamGridDbGridStyles != null,
        ) { styles ->
            viewModel.setPlatformGridStyles(platformId, styles)
        }
        SteamGridDbStyleField(
            label = "Hero styles",
            hint = SteamGridDbStyleHints.Hero,
            value = override?.steamGridDbHeroStyles ?: global.steamGridDbHeroStyles,
            overridden = override?.steamGridDbHeroStyles != null,
        ) { styles ->
            viewModel.setPlatformHeroStyles(platformId, styles)
        }
        SteamGridDbStyleField(
            label = "Logo styles",
            hint = SteamGridDbStyleHints.Logo,
            value = override?.steamGridDbLogoStyles ?: global.steamGridDbLogoStyles,
            overridden = override?.steamGridDbLogoStyles != null,
        ) { styles ->
            viewModel.setPlatformLogoStyles(platformId, styles)
        }
        SteamGridDbStyleField(
            label = "Icon styles",
            hint = SteamGridDbStyleHints.Icon,
            value = override?.steamGridDbIconStyles ?: global.steamGridDbIconStyles,
            overridden = override?.steamGridDbIconStyles != null,
        ) { styles ->
            viewModel.setPlatformIconStyles(platformId, styles)
        }
        WajihaMultiChoiceSetting(
            label = "Grid animation",
            description = "SteamGridDB animation filter for this system.",
            choiceOptions = steamGridDbAnimationOptions,
            selected = effective.steamGridDbAnimation,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.steamGridDbAnimation,
                    { o, v -> o.copy(steamGridDbAnimation = v) },
                    it,
                )
            },
            defaultValue = global.steamGridDbAnimation,
            onReset = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.steamGridDbAnimation,
                    { o, v -> o.copy(steamGridDbAnimation = v) },
                    global.steamGridDbAnimation,
                )
            },
            overridden = override?.steamGridDbAnimation != null,
        )
        SourceToggle(
            label = "Include NSFW grids",
            checked = effective.steamGridDbIncludeNsfw,
            overridden = override?.steamGridDbIncludeNsfw != null,
            defaultChecked = global.steamGridDbIncludeNsfw,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.steamGridDbIncludeNsfw,
                    { o, value -> o.copy(steamGridDbIncludeNsfw = value) },
                    v,
                )
            },
        )
        SourceToggle(
            label = "Include humor grids",
            checked = effective.steamGridDbIncludeHumor,
            overridden = override?.steamGridDbIncludeHumor != null,
            defaultChecked = global.steamGridDbIncludeHumor,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.steamGridDbIncludeHumor,
                    { o, value -> o.copy(steamGridDbIncludeHumor = value) },
                    v,
                )
            },
        )
        WajihaSettingDivider()
    }

    if ("libretro" in effectiveSources) {
        Text("Libretro thumbnails", style = MaterialTheme.typography.labelMedium)
        SourceToggle(
            label = "Fetch box art",
            checked = effective.libretroFetchBoxart,
            overridden = override?.libretroFetchBoxart != null,
            defaultChecked = global.libretroFetchBoxart,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.libretroFetchBoxart,
                    { o, value -> o.copy(libretroFetchBoxart = value) },
                    v,
                )
            },
        )
        SourceToggle(
            label = "Fetch snaps",
            checked = effective.libretroFetchSnaps,
            overridden = override?.libretroFetchSnaps != null,
            defaultChecked = global.libretroFetchSnaps,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.libretroFetchSnaps,
                    { o, value -> o.copy(libretroFetchSnaps = value) },
                    v,
                )
            },
        )
        SourceToggle(
            label = "Fetch titles",
            checked = effective.libretroFetchTitles,
            overridden = override?.libretroFetchTitles != null,
            defaultChecked = global.libretroFetchTitles,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.libretroFetchTitles,
                    { o, value -> o.copy(libretroFetchTitles = value) },
                    v,
                )
            },
        )
        WajihaSettingDivider()
    }

    if ("ra" in effectiveSources) {
        Text("RetroAchievements", style = MaterialTheme.typography.labelMedium)
        SourceToggle(
            label = "Fetch icon",
            checked = effective.raFetchIcon,
            overridden = override?.raFetchIcon != null,
            defaultChecked = global.raFetchIcon,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.raFetchIcon,
                    { o, value -> o.copy(raFetchIcon = value) },
                    v,
                )
            },
        )
        SourceToggle(
            label = "Fetch box art",
            checked = effective.raFetchBoxArt,
            overridden = override?.raFetchBoxArt != null,
            defaultChecked = global.raFetchBoxArt,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.raFetchBoxArt,
                    { o, value -> o.copy(raFetchBoxArt = value) },
                    v,
                )
            },
        )
        SourceToggle(
            label = "Fetch title screen",
            checked = effective.raFetchTitle,
            overridden = override?.raFetchTitle != null,
            defaultChecked = global.raFetchTitle,
            onChecked = { v ->
                viewModel.setPlatformBooleanOption(
                    platformId,
                    global.raFetchTitle,
                    { o, value -> o.copy(raFetchTitle = value) },
                    v,
                )
            },
        )
        WajihaSettingDivider()
    }

    WajihaNumberSetting(
        label = "Cover variant index",
        description = "0 = first alternate; 1 = second, and so on.",
        value = effective.mediaVariantIndex,
        onValueChange = { idx ->
            viewModel.setPlatformIntOption(
                platformId,
                global.mediaVariantIndex,
                { o, value -> o.copy(mediaVariantIndex = value) },
                idx.coerceAtLeast(0),
            )
        },
        range = 0..99,
        step = 1,
        defaultValue = global.mediaVariantIndex,
        onReset = {
            viewModel.setPlatformIntOption(
                platformId,
                global.mediaVariantIndex,
                { o, value -> o.copy(mediaVariantIndex = value) },
                global.mediaVariantIndex,
            )
        },
        overridden = override?.mediaVariantIndex != null,
    )
}

@Composable
private fun SourceTestLoginRow(
    sourceId: String,
    onTest: () -> Unit,
    viewModel: ScraperViewModel,
) {
    val tests by viewModel.sourceTests.collectAsState()
    val state = tests[sourceId] ?: SourceTestState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GamepadButton(
            text = "Test login",
            onClick = onTest,
            enabled = !state.loading,
            modifier = Modifier.defaultMinSize(minHeight = LocalSettingRowMinHeight.current),
        )
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        state.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color =
                    when (state.success) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun SourceToggle(
    label: String,
    checked: Boolean,
    focusRequester: FocusRequester? = null,
    overridden: Boolean = false,
    defaultChecked: Boolean? = null,
    onChecked: (Boolean) -> Unit,
) {
    GamepadSettingRow(
        label = label,
        type = SettingType.Toggle,
        checked = checked,
        onCheckedChange = onChecked,
        overridden = overridden,
        focusRequester = focusRequester,
        onReset = defaultChecked?.let { default -> { onChecked(default) } },
        isAtDefault = defaultChecked == null || checked == defaultChecked,
    )
}

@Composable
private fun CompactCredentialField(
    label: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    GamepadSafeTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = label,
        secret = secret,
        navItemId = "field_$label",
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
    )
}

@Composable
private fun SteamGridDbStyleField(
    label: String,
    hint: String,
    value: List<String>,
    overridden: Boolean = false,
    onChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CredentialField(
            label = label,
            value = value.joinToString(","),
            overridden = overridden,
        ) { raw ->
            onChange(parseSteamGridDbStyles(raw))
        }
        Text(
            text = "e.g. $hint — empty = all",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    secret: Boolean = false,
    overridden: Boolean = false,
    onChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    GamepadSafeTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = label,
        secret = secret,
        overridden = overridden,
        navItemId = "field_$label",
        modifier = Modifier.fillMaxWidth(),
    )
}

/** True when this source's on/off state differs from the global enabled-sources list. */
private fun isPlatformSourceOverridden(
    override: PlatformScraperOverride?,
    sourceId: String,
    globalSources: List<String>,
): Boolean {
    val platformSources = override?.enabledSources ?: return false
    return (sourceId in platformSources) != (sourceId in globalSources)
}

private val scraperRunSourceOptions =
    listOf(
        MultiChoiceOption("screenscraper", "ScreenScraper", "Metadata and media from ScreenScraper."),
        MultiChoiceOption("steamgriddb", "SteamGridDB", "Community artwork from SteamGridDB."),
        MultiChoiceOption("libretro", "Libretro", "No-account thumbnail library."),
        MultiChoiceOption("ra", "RetroAchievements", "Achievement-linked game artwork."),
        MultiChoiceOption("romm", "RomM", "Metadata and media from your RomM server."),
        MultiChoiceOption("local", "Local media", "Match artwork already stored on this device."),
    )

@Composable
private fun ScraperBatchBlock(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
    onOpenApiLogs: (() -> Unit)? = null,
    apiLogsFocusRequester: FocusRequester? = null,
) {
    val progress by viewModel.progress.collectAsState()
    val inUsePlatforms by viewModel.inUsePlatforms.collectAsState()
    val batchFeedback by viewModel.batchFeedback.collectAsState()
    val apiLogs by viewModel.apiLogs.collectAsState()
    var issuesExpanded by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ScrapeUiMode.FillGaps) }

    /** Empty = use every source enabled under Sources. */
    var selectedSources by remember { mutableStateOf(emptySet<String>()) }
    val selectedSourceIds = selectedSources.toList().takeIf { it.isNotEmpty() }
    val sourcesReady = viewModel.hasConfiguredSources(null, selectedSourceIds)
    val runPolicy = mode.toPolicy().copy(sourceIds = selectedSourceIds)
    val openLogs = onOpenApiLogs

    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.smPlus)) {
        batchFeedback?.let { feedback ->
            WajihaFieldMessage(
                WajihaFieldMessageState(
                    text = feedback,
                    severity = WajihaFieldMessageSeverity.Error,
                ),
            )
        }
        if (!sourcesReady && !progress.running) {
            WajihaFieldMessage(
                WajihaFieldMessageState(
                    text = "No scraper sources configured — enable and sign in under Sources / Accounts.",
                    severity = WajihaFieldMessageSeverity.Warning,
                ),
            )
        }
        if (!progress.running) {
            WajihaFieldMessage(
                WajihaFieldMessageState(
                    text =
                        when (mode) {
                            ScrapeUiMode.Force -> {
                                "Force overwrites metadata and media for every game."
                            }

                            else -> {
                                "Fill gaps prefers missing boxart, square, logo, or hero."
                            }
                        },
                    severity = WajihaFieldMessageSeverity.Supporting,
                ),
            )
        }
        if (progress.running) {
            Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
                Text(
                    text =
                        when {
                            progress.paused -> "Paused"
                            progress.statusMessage != null -> progress.statusMessage!!
                            progress.currentGameName != null -> "Scraping: ${progress.currentGameName}"
                            else -> "Scraping…"
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (progress.paused) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                LinearProgressIndicator(
                    progress = {
                        if (progress.total == 0) 0f else progress.done.toFloat() / progress.total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${progress.done} / ${progress.total} — ${progress.summaryLine()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                GamepadSettingRow(
                    label = if (progress.paused) "Batch paused" else "Batch running",
                    type = SettingType.Action,
                    focusRequester = firstFocusRequester,
                    onActivate =
                        if (progress.paused) {
                            viewModel::resumeBatch
                        } else {
                            viewModel::pauseBatch
                        },
                    onSecondaryActivate = viewModel::cancelBatch,
                    content = {
                        GamepadSettingTrailingActions(
                            secondaryLabel = "Cancel",
                            onSecondaryClick = viewModel::cancelBatch,
                            primaryLabel = if (progress.paused) "Resume" else "Pause",
                            onPrimaryClick =
                                if (progress.paused) {
                                    viewModel::resumeBatch
                                } else {
                                    viewModel::pauseBatch
                                },
                        )
                    },
                )
                if (openLogs != null) {
                    WajihaActionSetting(
                        label = "API logs",
                        actionLabel = "Open",
                        onClick = openLogs,
                        labelMeta = apiLogs.size.toString(),
                        focusRequester = apiLogsFocusRequester,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
                WajihaMultiSelectSetting(
                    label = "Scraper",
                    description =
                        if (selectedSources.isEmpty()) {
                            "Use every source enabled under Sources."
                        } else {
                            "Use only the selected scraper(s) for this run."
                        },
                    choiceOptions = scraperRunSourceOptions,
                    selected = selectedSources,
                    onSelectionChange = { selectedSources = it },
                    emptySelectionLabel = "All enabled",
                    focusRequester = firstFocusRequester,
                )
                ScrapeModeSelector(
                    selected = mode,
                    onSelect = { mode = it },
                    onAction = {
                        viewModel.dismissBatchFeedback()
                        viewModel.startBatch(null, runPolicy)
                    },
                    showReview = false,
                    enabled = !progress.running,
                    actionEnabled = sourcesReady && !progress.running,
                )
                if (viewModel.canRetryFailed(null)) {
                    WajihaActionSetting(
                        label = "Retry failed games",
                        actionLabel = "Retry",
                        onClick = { viewModel.retryFailedBatch(null) },
                    )
                }
                if (openLogs != null) {
                    WajihaActionSetting(
                        label = "API logs",
                        actionLabel = "Open",
                        onClick = openLogs,
                        labelMeta = apiLogs.size.toString(),
                        focusRequester = apiLogsFocusRequester,
                    )
                }
                if (progress.done > 0) {
                    WajihaFieldMessage(
                        WajihaFieldMessageState(
                            text = "Last run: ${progress.summaryLine()}",
                            severity = WajihaFieldMessageSeverity.Supporting,
                        ),
                    )
                }
            }
        }
        if (progress.issues.isNotEmpty()) {
            WajihaActionSetting(
                label =
                    if (issuesExpanded) {
                        "Hide issues"
                    } else {
                        "Show issues"
                    },
                actionLabel = if (issuesExpanded) "Hide" else "Show",
                onClick = { issuesExpanded = !issuesExpanded },
                labelMeta = progress.issues.size.toString(),
            )
            if (issuesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
                    progress.issues.take(40).forEach { issue ->
                        Text(
                            text = "${issue.gameName} — ${issue.kind.displayLabel()}: ${issue.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (progress.issues.size > 40) {
                        Text(
                            text = "…and ${progress.issues.size - 40} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (inUsePlatforms.isNotEmpty()) {
            WajihaSettingDivider()
            WajihaSettingGroup(title = "Per platform") {
                inUsePlatforms.forEachIndexed { index, platform ->
                    if (index > 0) WajihaSettingDivider()
                    val platformReady =
                        !progress.running &&
                            viewModel.hasConfiguredSources(platform.id, selectedSourceIds)
                    val actionLabel =
                        when (mode) {
                            ScrapeUiMode.Force -> "Force"
                            else -> "Fill gaps"
                        }
                    GamepadSettingRow(
                        label = platform.name,
                        type = SettingType.Action,
                        onActivate =
                            if (platformReady) {
                                {
                                    viewModel.startBatch(platform.id, runPolicy)
                                }
                            } else {
                                null
                            },
                        content = {
                            SettingsTrailingActionButton(
                                text = actionLabel,
                                onClick = {
                                    viewModel.startBatch(platform.id, runPolicy)
                                },
                                enabled = platformReady,
                            )
                        },
                    )
                }
            }
        } else if (!progress.running) {
            WajihaFieldMessage(
                WajihaFieldMessageState(
                    text = "Add a platform to scrape per-system batches.",
                    severity = WajihaFieldMessageSeverity.Supporting,
                ),
            )
        }
    }
}
