package com.wajiha.ui.scraper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.data.scraper.PlatformScraperOverride
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeFailureKind
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.SteamGridDbStyleHints
import com.wajiha.data.scraper.parseSteamGridDbStyles
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadNavHost
import com.wajiha.input.GamepadNavItem
import com.wajiha.input.GamepadNavMode
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.rememberGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaFolderSettingChrome
import com.wajiha.ui.components.WajihaLoadingState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadFocusable
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.LocalSettingRowMinHeight
import com.wajiha.ui.components.gamepad.MultiChoiceOption
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.components.gamepad.withoutDualScreenChrome
import com.wajiha.ui.scraper.review.ScrapeReviewPicker
import com.wajiha.ui.scraper.review.ScrapeReviewViewModel
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Standalone scraper page: batch/manual scrape actions plus global source settings.
 * Per-system overrides live on each platform's edit page.
 */
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
    val navController =
        rememberGamepadNavController(
            mode = GamepadNavMode.Vertical,
            onBack = {
                onBack()
                true
            },
        )
    WajihaScreen(
        layerId = "scraper",
        modifier = modifier,
        onBack = onBack,
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
            WajihaFolderSettingChrome(
                onBack = onBack,
                tabs = listOf("Scraper"),
                selectedIndex = 0,
                onSelect = {},
            ) {
                ScraperPageContent(viewModel)
            }
        }
    }
}

/** Full scraper page: actions and global settings — used on Scraper route and Settings → Scraper. */
@Composable
fun ScraperPageContent(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        Text(
            text = "Scrape artwork and metadata, configure sources, and run batch or manual matches.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScraperExpandableSection(
            title = "Batch scrape",
            summary = "Run across all platforms or per system",
            initiallyExpanded = true,
            focusRequester = firstFocusRequester,
        ) {
            ScraperBatchBlock(viewModel, firstFocusRequester)
        }
        ScraperExpandableSection(
            title = "Manual match",
            summary = "Pick a game and apply artwork from sources",
        ) {
            ScraperManualBlock(viewModel)
        }
        ScraperExpandableSection(
            title = "Sources",
            summary = "Enable or disable scraper backends",
        ) {
            ScraperSourcesSection(viewModel)
        }
        ScraperExpandableSection(
            title = "Accounts",
            summary = "Credentials per source",
        ) {
            ScraperAccountsSection(viewModel)
        }
        ScraperExpandableSection(
            title = "Media defaults",
            summary = "Resolution, variants, and per-source media options",
        ) {
            ScraperMediaDefaultsSection(viewModel)
        }
        ScraperExpandableSection(
            title = "Batch options",
            summary = "Wi-Fi, skip rules, and region/language priority",
        ) {
            ScraperBatchOptionsSection(viewModel)
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
            HorizontalDivider(Modifier.padding(vertical = WajihaSpacing.xs))
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
private fun ScraperExpandableSection(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    focusRequester: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var headerFocused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val headerHighlight = !useCustomNav && headerFocused && !expanded
    val headerFocusRequester = focusRequester ?: remember { FocusRequester() }

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

    BackHandler(enabled = expanded) {
        collapse()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WajihaShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
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
                        .wajihaFocusIndicator(highlighted = headerHighlight)
                        .clip(WajihaShapes.focus)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = if (expanded) "˅" else "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
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
                            .padding(
                                start = WajihaSpacing.sm,
                                end = WajihaSpacing.sm,
                                bottom = WajihaSpacing.sm,
                            ),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
                ) {
                    content()
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
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var headerFocused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val headerHighlight = !useCustomNav && headerFocused && !expanded
    val headerFocusRequester = remember { FocusRequester() }
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
private fun ScraperSourcesSection(viewModel: ScraperViewModel) {
    val settings by viewModel.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        SourceToggle(
            label = "ScreenScraper",
            checked = "screenscraper" in settings.enabledSources,
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
private fun ScraperAccountsSection(viewModel: ScraperViewModel) {
    val settings by viewModel.settings.collectAsState()
    val enabled = settings.enabledSources.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        if ("screenscraper" in enabled) {
            ScraperAccountSubsection(
                title = "ScreenScraper",
                configured = viewModel.isScreenScraperConfigured(settings),
                sourceId = "screenscraper",
                onTest = { viewModel.testSourceCredentials("screenscraper") },
                viewModel = viewModel,
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
private fun ScraperMediaDefaultsSection(viewModel: ScraperViewModel) {
    val settings by viewModel.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        GamepadSettingRow(
            label = "Max image resolution",
            description = "Longest edge in pixels; 0 keeps the original size.",
            type = SettingType.Number,
            numberValue = settings.maxImageResolution,
            onNumberChange = { px ->
                viewModel.update { it.copy(maxImageResolution = px.coerceIn(0, 8192)) }
            },
            numberRange = 0..8192,
            numberStep = 256,
            numberLabel = { if (it == 0) "Original" else "${it}px" },
        )
        GamepadSettingRow(
            label = "Cover variant index",
            description = "0 = first/best alternate; 1 = second, and so on.",
            type = SettingType.Number,
            numberValue = settings.mediaVariantIndex,
            onNumberChange = { idx ->
                viewModel.update { it.copy(mediaVariantIndex = idx.coerceAtLeast(0)) }
            },
            numberRange = 0..99,
            numberStep = 1,
        )
        HorizontalDivider(Modifier.padding(vertical = WajihaSpacing.xs))
        ScraperSourceOptionsBlock(viewModel)
        HorizontalDivider(Modifier.padding(vertical = WajihaSpacing.xs))
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
private fun ScraperBatchOptionsSection(viewModel: ScraperViewModel) {
    val settings by viewModel.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        SourceToggle("Wi-Fi only", settings.wifiOnly) { v ->
            viewModel.update { it.copy(wifiOnly = v) }
        }
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
        GamepadSettingRow(
            label = "Box art type",
            type = SettingType.MultiChoice,
            multiChoiceOptions = screenScraperBoxOptions,
            selected = settings.screenScraperBoxType,
            onSelect = { viewModel.update { s -> s.copy(screenScraperBoxType = it) } },
        )
        GamepadSettingRow(
            label = "Screenshot type",
            type = SettingType.MultiChoice,
            multiChoiceOptions = screenScraperScreenshotOptions,
            selected = settings.screenScraperScreenshotType,
            onSelect = { viewModel.update { s -> s.copy(screenScraperScreenshotType = it) } },
        )
        GamepadSettingRow(
            label = "Logo / marquee type",
            type = SettingType.MultiChoice,
            multiChoiceOptions = screenScraperLogoOptions,
            selected = settings.screenScraperLogoType,
            onSelect = { viewModel.update { s -> s.copy(screenScraperLogoType = it) } },
        )
        SourceToggle("Map fanart as hero", settings.screenScraperFanartAsHero) { v ->
            viewModel.update { it.copy(screenScraperFanartAsHero = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
        GamepadSettingRow(
            label = "Grid animation",
            type = SettingType.MultiChoice,
            multiChoiceOptions = steamGridDbAnimationOptions,
            selected = settings.steamGridDbAnimation,
            onSelect = { viewModel.update { s -> s.copy(steamGridDbAnimation = it) } },
        )
        SourceToggle("Include NSFW grids", settings.steamGridDbIncludeNsfw) { v ->
            viewModel.update { it.copy(steamGridDbIncludeNsfw = v) }
        }
        SourceToggle("Include humor grids", settings.steamGridDbIncludeHumor) { v ->
            viewModel.update { it.copy(steamGridDbIncludeHumor = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
    HorizontalDivider(Modifier.padding(vertical = 4.dp))

    if ("libretro" in enabled) {
        Text("Libretro thumbnails", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Fetch box art (Named_Boxarts)", settings.libretroFetchBoxart) { v ->
            viewModel.update { it.copy(libretroFetchBoxart = v) }
        }
        SourceToggle("Fetch snaps (Named_Snaps)", settings.libretroFetchSnaps) { v ->
            viewModel.update { it.copy(libretroFetchSnaps = v) }
        }
        SourceToggle("Fetch titles (Named_Titles)", settings.libretroFetchTitles) { v ->
            viewModel.update { it.copy(libretroFetchTitles = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }

    if ("ra" in enabled) {
        Text("RetroAchievements media", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Fetch icon", settings.raFetchIcon) { v ->
            viewModel.update { it.copy(raFetchIcon = v) }
        }
        SourceToggle("Fetch box art", settings.raFetchBoxArt) { v ->
            viewModel.update { it.copy(raFetchBoxArt = v) }
        }
        SourceToggle("Fetch title screen", settings.raFetchTitle) { v ->
            viewModel.update { it.copy(raFetchTitle = v) }
        }
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
        GamepadSettingRow(
            label = "Box art type",
            type = SettingType.MultiChoice,
            multiChoiceOptions = screenScraperBoxOptions,
            selected = effective.screenScraperBoxType,
            overridden = override?.screenScraperBoxType != null,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperBoxType,
                    { o, v -> o.copy(screenScraperBoxType = v) },
                    it,
                )
            },
        )
        GamepadSettingRow(
            label = "Screenshot type",
            type = SettingType.MultiChoice,
            multiChoiceOptions = screenScraperScreenshotOptions,
            selected = effective.screenScraperScreenshotType,
            overridden = override?.screenScraperScreenshotType != null,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperScreenshotType,
                    { o, v -> o.copy(screenScraperScreenshotType = v) },
                    it,
                )
            },
        )
        GamepadSettingRow(
            label = "Logo type",
            type = SettingType.MultiChoice,
            multiChoiceOptions = screenScraperLogoOptions,
            selected = effective.screenScraperLogoType,
            overridden = override?.screenScraperLogoType != null,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.screenScraperLogoType,
                    { o, v -> o.copy(screenScraperLogoType = v) },
                    it,
                )
            },
        )
        SourceToggle(
            label = "Map fanart as hero",
            checked = effective.screenScraperFanartAsHero,
            overridden = override?.screenScraperFanartAsHero != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.screenScraperFanartAsHero,
                { o, value -> o.copy(screenScraperFanartAsHero = value) },
                v,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
        GamepadSettingRow(
            label = "Grid animation",
            type = SettingType.MultiChoice,
            multiChoiceOptions = steamGridDbAnimationOptions,
            selected = effective.steamGridDbAnimation,
            overridden = override?.steamGridDbAnimation != null,
            onSelect = {
                viewModel.setPlatformStringOption(
                    platformId,
                    global.steamGridDbAnimation,
                    { o, v -> o.copy(steamGridDbAnimation = v) },
                    it,
                )
            },
        )
        SourceToggle(
            label = "Include NSFW grids",
            checked = effective.steamGridDbIncludeNsfw,
            overridden = override?.steamGridDbIncludeNsfw != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.steamGridDbIncludeNsfw,
                { o, value -> o.copy(steamGridDbIncludeNsfw = value) },
                v,
            )
        }
        SourceToggle(
            label = "Include humor grids",
            checked = effective.steamGridDbIncludeHumor,
            overridden = override?.steamGridDbIncludeHumor != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.steamGridDbIncludeHumor,
                { o, value -> o.copy(steamGridDbIncludeHumor = value) },
                v,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }

    if ("libretro" in effectiveSources) {
        Text("Libretro thumbnails", style = MaterialTheme.typography.labelMedium)
        SourceToggle(
            label = "Fetch box art",
            checked = effective.libretroFetchBoxart,
            overridden = override?.libretroFetchBoxart != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.libretroFetchBoxart,
                { o, value -> o.copy(libretroFetchBoxart = value) },
                v,
            )
        }
        SourceToggle(
            label = "Fetch snaps",
            checked = effective.libretroFetchSnaps,
            overridden = override?.libretroFetchSnaps != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.libretroFetchSnaps,
                { o, value -> o.copy(libretroFetchSnaps = value) },
                v,
            )
        }
        SourceToggle(
            label = "Fetch titles",
            checked = effective.libretroFetchTitles,
            overridden = override?.libretroFetchTitles != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.libretroFetchTitles,
                { o, value -> o.copy(libretroFetchTitles = value) },
                v,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }

    if ("ra" in effectiveSources) {
        Text("RetroAchievements", style = MaterialTheme.typography.labelMedium)
        SourceToggle(
            label = "Fetch icon",
            checked = effective.raFetchIcon,
            overridden = override?.raFetchIcon != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.raFetchIcon,
                { o, value -> o.copy(raFetchIcon = value) },
                v,
            )
        }
        SourceToggle(
            label = "Fetch box art",
            checked = effective.raFetchBoxArt,
            overridden = override?.raFetchBoxArt != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.raFetchBoxArt,
                { o, value -> o.copy(raFetchBoxArt = value) },
                v,
            )
        }
        SourceToggle(
            label = "Fetch title screen",
            checked = effective.raFetchTitle,
            overridden = override?.raFetchTitle != null,
        ) { v ->
            viewModel.setPlatformBooleanOption(
                platformId,
                global.raFetchTitle,
                { o, value -> o.copy(raFetchTitle = value) },
                v,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }

    Text("Cover variant index", style = MaterialTheme.typography.labelMedium)
    GamepadSettingRow(
        label = "Variant index",
        description = "0 = first alternate; 1 = second, and so on.",
        type = SettingType.Number,
        numberValue = effective.mediaVariantIndex,
        overridden = override?.mediaVariantIndex != null,
        onNumberChange = { idx ->
            viewModel.setPlatformIntOption(
                platformId,
                global.mediaVariantIndex,
                { o, value -> o.copy(mediaVariantIndex = value) },
                idx.coerceAtLeast(0),
            )
        },
        numberRange = 0..99,
        numberStep = 1,
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
            modifier = Modifier.heightIn(max = 40.dp),
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
    onChecked: (Boolean) -> Unit,
) {
    val navId = remember(label) { "toggle_$label" }
    val row = @Composable {
        GamepadSettingRow(
            label = label,
            type = SettingType.Toggle,
            checked = checked,
            onCheckedChange = onChecked,
            overridden = overridden,
            focusRequester = focusRequester,
        )
    }
    if (LocalGamepadNavController.current != null) {
        GamepadNavItem(
            onActivate = { onChecked(!checked) },
            itemId = navId,
        ) {
            row()
        }
    } else {
        row()
    }
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

private const val AllScraperSources = "all"

private val scraperRunSourceOptions =
    listOf(
        MultiChoiceOption(AllScraperSources, "All enabled", "Use the configured source priority."),
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
) {
    val progress by viewModel.progress.collectAsState()
    val inUsePlatforms by viewModel.inUsePlatforms.collectAsState()
    val batchFeedback by viewModel.batchFeedback.collectAsState()
    val apiLogs by viewModel.apiLogs.collectAsState()
    var issuesExpanded by remember { mutableStateOf(false) }
    var showApiLogs by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ScrapeUiMode.FillGaps) }
    var selectedSource by remember { mutableStateOf(AllScraperSources) }
    val selectedSourceId = selectedSource.takeUnless { it == AllScraperSources }
    val selectedSourceLabel =
        scraperRunSourceOptions.firstOrNull { it.value == selectedSource }?.label
            ?: "All enabled"
    val sourcesReady = viewModel.hasConfiguredSources(null, selectedSourceId)
    val runPolicy = mode.toPolicy().copy(sourceId = selectedSourceId)

    ScrapeApiLogsDialog(
        visible = showApiLogs,
        entries = apiLogs,
        onDismiss = { showApiLogs = false },
        onClear = viewModel::clearApiLogs,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        batchFeedback?.let { feedback ->
            Text(
                text = feedback,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!sourcesReady && !progress.running) {
            Text(
                text = "No scraper sources configured — enable and sign in under Sources / Accounts.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (progress.running) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (progress.paused) {
                        Button(onClick = viewModel::resumeBatch) { Text("Resume") }
                    } else {
                        Button(onClick = viewModel::pauseBatch) { Text("Pause") }
                    }
                    TextButton(onClick = viewModel::cancelBatch) { Text("Cancel") }
                    TextButton(onClick = { showApiLogs = true }) {
                        Text("API logs (${apiLogs.size})")
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GamepadSettingRow(
                    label = "Scraper",
                    description =
                        if (selectedSourceId == null) {
                            "Use every source enabled under Sources."
                        } else {
                            "Use only $selectedSourceLabel for this run."
                        },
                    type = SettingType.MultiChoice,
                    multiChoiceOptions = scraperRunSourceOptions,
                    selected = selectedSource,
                    onSelect = { selectedSource = it },
                )
                ScrapeModeSelector(
                    selected = mode,
                    onSelect = { mode = it },
                    actionLabel =
                        when (mode) {
                            ScrapeUiMode.Force -> "Force scrape all platforms"
                            else -> "Fill gaps — all platforms"
                        },
                    onAction = {
                        viewModel.dismissBatchFeedback()
                        viewModel.startBatch(null, runPolicy)
                    },
                    showReview = false,
                    firstFocusRequester = firstFocusRequester,
                    enabled = !progress.running,
                    actionEnabled = sourcesReady && !progress.running,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (viewModel.canRetryFailed(null)) {
                        TextButton(onClick = { viewModel.retryFailedBatch(null) }) {
                            Text("Retry failed")
                        }
                    }
                    TextButton(onClick = { showApiLogs = true }) {
                        Text("API logs (${apiLogs.size})")
                    }
                }
                if (progress.done > 0) {
                    Text(
                        text = "Last run: ${progress.summaryLine()}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (progress.issues.isNotEmpty()) {
            TextButton(onClick = { issuesExpanded = !issuesExpanded }) {
                Text(
                    if (issuesExpanded) {
                        "Hide issues (${progress.issues.size})"
                    } else {
                        "Show issues (${progress.issues.size})"
                    },
                )
            }
            if (issuesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            HorizontalDivider()
            Text("Per platform", style = MaterialTheme.typography.labelMedium)
            inUsePlatforms.forEach { platform ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(platform.name, style = MaterialTheme.typography.bodyMedium)
                    GamepadButton(
                        text =
                            when (mode) {
                                ScrapeUiMode.Force -> "Force"
                                else -> "Fill gaps"
                            },
                        onClick = {
                            viewModel.startBatch(platform.id, runPolicy)
                        },
                        enabled =
                            !progress.running &&
                                viewModel.hasConfiguredSources(platform.id, selectedSourceId),
                        outlined = true,
                    )
                }
            }
        } else if (!progress.running) {
            Text(
                text = "Add a platform to scrape per-system batches.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScraperManualBlock(
    viewModel: ScraperViewModel,
    reviewViewModel: ScrapeReviewViewModel = koinInject(),
    dualStore: DualScreenStore = koinInject(),
) {
    val manual by viewModel.manual.collectAsState()
    val results by viewModel.gameResults.collectAsState()
    val screenState by dualStore.state.collectAsState()
    val dualDisplay = screenState != DualScreenState.SingleDisplay
    val game = manual.game
    var reviewing by remember { mutableStateOf(false) }

    if (reviewing && game != null) {
        ScrapeReviewPicker(
            viewModel = reviewViewModel,
            onCancel = {
                reviewing = false
                viewModel.selectGame(game)
            },
            showSkip = false,
            dualDisplay = dualDisplay,
            hostGamepadOwner = if (dualDisplay) dualStore.menuGamepadOwner() else null,
        )
    }

    if (game == null) {
        var query by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GamepadSafeTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.searchLibrary(it)
                },
                label = "Search your library",
                modifier = Modifier.fillMaxWidth(),
            )
            GamepadList(
                items = results,
                key = { it.id },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
            ) { g ->
                ScraperGamePickRow(
                    title = g.displayName,
                    subtitle =
                        "${g.platformId} — ${g.fileName}" +
                            if (g.scrapedAt != null) " — scraped" else "",
                    onActivate = { viewModel.selectGame(g) },
                )
            }
        }
        return
    }

    var searchName by remember(game.id) { mutableStateOf(game.displayName) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(game.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = game.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = viewModel::clearSelection) { Text("Change game") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::rescrapeSelected, enabled = !manual.applying) {
                Text("Auto scrape")
            }
            GamepadButton(
                text = "Review",
                onClick = {
                    reviewing = true
                    reviewViewModel.openGame(game)
                },
                outlined = true,
                enabled = !manual.applying,
            )
        }
        manual.message?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.labelMedium,
                color =
                    when (manual.messageSuccess) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        if (manual.applying || manual.searching) {
            WajihaLoadingState(message = if (manual.searching) "Searching…" else "Applying…")
        }

        if (manual.media.isNotEmpty()) {
            Text("Current media", style = MaterialTheme.typography.labelLarge)
            manual.media.forEach { media ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AsyncImage(
                        model = media.localPath ?: media.remoteUrl,
                        contentDescription = media.type,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(media.type, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = media.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.deleteMedia(media) }) { Text("Delete") }
                }
            }
            HorizontalDivider()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GamepadSafeTextField(
                value = searchName,
                onValueChange = { searchName = it },
                label = "Search sources by name",
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.searchSources(searchName) },
                enabled = !manual.searching,
            ) { Text("Search") }
        }
        manual.candidates.forEach { candidate ->
            CandidateRow(
                candidate = candidate,
                onApply = { viewModel.applyCandidate(candidate) },
                onReview = {
                    reviewing = true
                    reviewViewModel.openGame(game, preferredCandidate = candidate)
                },
            )
        }
    }
}

@Composable
private fun ScraperGamePickRow(
    title: String,
    subtitle: String,
    onActivate: () -> Unit,
) {
    GamepadFocusable(
        onClick = onActivate,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = WajihaSpacing.sm, horizontal = WajihaSpacing.xs),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ScrapeCandidate,
    onApply: () -> Unit,
    onReview: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = candidate.thumbnailUrl ?: candidate.media.firstOrNull()?.url,
            contentDescription = candidate.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${candidate.sourceId} — ${candidate.media.size} media",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onReview != null) {
            TextButton(onClick = onReview) { Text("Review") }
        }
        Button(onClick = onApply) { Text("Apply") }
    }
}
