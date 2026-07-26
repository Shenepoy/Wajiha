package com.wajiha.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.HeroDisplaySlot
import com.wajiha.data.prefs.HeroLayoutFitter
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenStore
import com.wajiha.state.HeroContext
import com.wajiha.state.claimsStatusCorner
import com.wajiha.state.isSettingsHeroInteractive
import com.wajiha.ui.gamedetail.GameDetailMetadataPanel
import com.wajiha.ui.gamedetail.GameDetailViewModel
import com.wajiha.ui.gamedetail.MetadataPanelStyle
import com.wajiha.ui.gamedetail.MetadataPanelVisibility
import com.wajiha.ui.home.hero.HeroCanvas
import com.wajiha.ui.home.hero.HeroLayoutEditorCanvasFromStore
import com.wajiha.ui.scraper.review.ScrapeReviewSlotHero
import com.wajiha.ui.settings.SettingsFocusHero
import com.wajiha.ui.settings.SettingsHeroDetailBody
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaElevation
import com.wajiha.ui.theme.WajihaMotion
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Top screen (3DS style): hero/preview of the focused game — backdrop,
 * logo overlay, metadata, play stats. Idle state when nothing is focused.
 */
@Composable
fun TopScreen(
    focused: GameTile?,
    platformName: String?,
    heroContext: HeroContext = HeroContext.GameLibrary,
    contentFocusRequester: FocusRequester? = null,
    displaySlot: HeroDisplaySlot = HeroDisplaySlot.Primary,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.onBackground.luminance() > 0.5f
    val frameTop = if (isDark) WajihaColors.ScreenFrame else WajihaColors.ScreenFrameLight
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val dualStore = koinInject<DualScreenStore>()
    val layoutEditing by dualStore.heroLayoutEditing.collectAsState()
    val editorScope = rememberCoroutineScope()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(frameTop, scheme.background),
                    ),
                ),
    ) {
        if (layoutEditing) {
            HeroLayoutEditorCanvasFromStore(
                tile = focused,
                platformName = platformName,
                onExit = {
                    // B on the hero canvas — persist, then drop edit mode. Settings
                    // observes heroLayoutEditing and closes the fullscreen page.
                    val draft = dualStore.heroLayoutEditDraft.value
                    val slot = dualStore.heroLayoutEditSlot.value
                    if (draft != null) {
                        editorScope.launch {
                            settingsRepository.updateHeroLayoutSlot(slot) {
                                draft.copy(configured = true)
                            }
                        }
                    }
                    dualStore.clearHeroLayoutEditDraft()
                    dualStore.setHeroLayoutEditing(false)
                },
                modifier = Modifier.fillMaxSize(),
                contentFocusRequester = contentFocusRequester,
            )
        } else {
            AnimatedContent(
                targetState = heroContext,
                contentKey = { it.transitionKey },
                transitionSpec = {
                    fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
                },
                label = "hero-context",
            ) { context ->
                when (context) {
                    is HeroContext.GameLibrary -> {
                        GameLibraryHero(
                            focused,
                            platformName,
                            settings,
                            contentFocusRequester,
                            displaySlot,
                        )
                    }

                    is HeroContext.Settings -> {
                        SettingsHero(context, contentFocusRequester)
                    }

                    is HeroContext.Apps -> {
                        AppsHero(context, contentFocusRequester)
                    }

                    is HeroContext.System -> {
                        SystemHero(context, contentFocusRequester)
                    }

                    is HeroContext.GameDetail -> {
                        GameDetailHero(
                            context.gameId,
                            settings,
                            contentFocusRequester,
                            displaySlot,
                        )
                    }

                    is HeroContext.ScrapeReview -> {
                        ScrapeReviewSlotHero(
                            contentFocusRequester = contentFocusRequester,
                        )
                    }
                }
            }
        }
        TopStatusBar(reserveTopEnd = heroContext.claimsStatusCorner())
    }
}

@Composable
private fun GameLibraryHero(
    focused: GameTile?,
    platformName: String?,
    settings: AppSettings,
    contentFocusRequester: FocusRequester? = null,
    displaySlot: HeroDisplaySlot = HeroDisplaySlot.Primary,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (contentFocusRequester != null) {
                        Modifier
                            .focusRequester(contentFocusRequester)
                            .wajihaGamepadFocus()
                    } else {
                        Modifier
                    },
                ),
    ) {
        AnimatedContent(
            targetState = focused != null,
            transitionSpec = {
                fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
            },
            label = "hero-mode",
        ) { hasFocus ->
            if (!hasFocus) {
                IdleHero()
            } else {
                GameHero(requireNotNull(focused), platformName, settings, displaySlot)
            }
        }
    }
}

@Composable
private fun IdleHero() {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Wajiha",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
            Text(
                text = "Pick a game on the touch screen",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = WajihaSpacing.sm),
            )
        }
    }
}

@Composable
private fun ContextHeroFrame(
    title: String,
    subtitle: String? = null,
    hint: String? = null,
    accent: Color? = null,
    contentFocusRequester: FocusRequester? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val accentColor = accent ?: scheme.primary
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (contentFocusRequester != null) {
                        Modifier
                            .focusRequester(contentFocusRequester)
                            .wajihaGamepadFocus()
                    } else {
                        Modifier
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    accentColor.copy(alpha = 0.22f),
                                    scheme.background.copy(alpha = 0.95f),
                                ),
                            radius = 900f,
                        ),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = WajihaSpacing.xl, vertical = WajihaSpacing.lgPlus),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onBackground,
                    modifier = Modifier.padding(top = WajihaSpacing.smPlus),
                )
            }
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WajihaSpacing.md),
                )
            }
        }
    }
}

@Composable
private fun SettingsHero(
    context: HeroContext.Settings,
    contentFocusRequester: FocusRequester? = null,
) {
    SettingsFocusHero(
        sectionLabel = context.sectionLabel,
        contentFocusRequester = contentFocusRequester,
    )
}

@Composable
private fun GameDetailHero(
    gameId: Long,
    settings: AppSettings,
    contentFocusRequester: FocusRequester? = null,
    displaySlot: HeroDisplaySlot = HeroDisplaySlot.Primary,
) {
    val viewModel = koinInject<GameDetailViewModel>()
    val dualStore = koinInject<DualScreenStore>()
    LaunchedEffect(gameId) { viewModel.open(gameId) }
    val state by viewModel.uiState.collectAsState()
    val heroDetail by dualStore.settingsHeroDetail.collectAsState()
    val game = state.game
    val scheme = MaterialTheme.colorScheme
    val showFocusedHelp = settings.settingsHeroHelp && heroDetail != null
    val showActions = settings.settingsHeroActions
    val focusedHero = heroDetail
    val interactive =
        showFocusedHelp && focusedHero.isSettingsHeroInteractive(showActions)

    LaunchedEffect(interactive) {
        dualStore.setSettingsHeroPicking(interactive)
    }
    DisposableEffect(Unit) {
        onDispose { dualStore.setSettingsHeroPicking(false) }
    }

    val firstFocus = remember { FocusRequester() }
    val resolvedFocus = contentFocusRequester ?: firstFocus
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    if (game == null) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (contentFocusRequester != null) {
                            Modifier
                                .focusRequester(contentFocusRequester)
                                .wajihaGamepadFocus()
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        return
    }

    val tile =
        remember(state) {
            GameTile(
                game = game,
                boxartPath = state.media.firstOrNull { it.type == "boxart" }?.localPath,
                heroPath = state.media.firstOrNull { it.type == "hero" }?.localPath,
                logoPath = state.media.firstOrNull { it.type == "logo" }?.localPath,
                iconPath = state.media.firstOrNull { it.type == "icon" }?.localPath,
                squarePath = state.media.firstOrNull { it.type == "square" }?.localPath,
            )
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (!interactive && contentFocusRequester != null) {
                        Modifier
                            .focusRequester(contentFocusRequester)
                            .wajihaGamepadFocus()
                    } else {
                        Modifier
                    },
                ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout =
                remember(settings.heroLayoutBundle, displaySlot, maxWidth, maxHeight, rtl) {
                    HeroLayoutFitter
                        .resolveForPaint(
                            bundle = settings.heroLayoutBundle,
                            slot = displaySlot,
                            canvasWidthPx = constraints.maxWidth.toFloat(),
                            canvasHeightPx = constraints.maxHeight.toFloat(),
                            rtl = false, // HeroCanvas mirrors for RTL once
                        ).copy(configured = true)
                }
            HeroCanvas(
                tile = tile,
                platformName = state.platform?.name,
                layout = layout,
                displaySlot = displaySlot,
                playCoverVideo = false,
                detailContext = true,
                dimFreeform = interactive,
                sectionHint =
                    "Launch, emulator, and scraper on the bottom screen  ·  L1 / R1 switch tabs",
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Safe-band overlays for settings-hero help / full metadata.
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
                    .verticalScroll(rememberScrollState())
                    .padding(WajihaSpacing.lg),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (showFocusedHelp && focusedHero != null) {
                Surface(
                    shape = WajihaShapes.dialog,
                    color = scheme.surfaceContainerLow.copy(alpha = 0.92f),
                    tonalElevation = WajihaElevation.low,
                    shadowElevation = WajihaElevation.menu,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(WajihaSpacing.md)) {
                        SettingsHeroDetailBody(
                            detail = focusedHero,
                            showActions = showActions,
                            firstFocusRequester = if (interactive) resolvedFocus else null,
                            compact = true,
                        )
                    }
                }
            } else {
                GameDetailMetadataPanel(
                    game = game,
                    platformName = state.platform?.name,
                    totalPlaytimeSec = state.totalPlaytimeSec,
                    style = MetadataPanelStyle.Full,
                    visibility =
                        MetadataPanelVisibility(
                            metadata =
                                layoutElementVisible(settings, displaySlot, com.wajiha.data.prefs.HeroElementId.Metadata),
                            description =
                                layoutElementVisible(settings, displaySlot, com.wajiha.data.prefs.HeroElementId.Description),
                            playStats =
                                layoutElementVisible(settings, displaySlot, com.wajiha.data.prefs.HeroElementId.PlayStats),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun layoutElementVisible(
    settings: AppSettings,
    slot: HeroDisplaySlot,
    id: com.wajiha.data.prefs.HeroElementId,
): Boolean =
    settings.heroLayoutBundle
        .slot(slot)
        .element(id)
        ?.visible ?: true

@Composable
private fun AppsHero(
    context: HeroContext.Apps,
    contentFocusRequester: FocusRequester? = null,
) {
    val countLine =
        when (context.appCount) {
            0 -> "No apps found"
            1 -> "1 app installed"
            else -> "${context.appCount} apps installed"
        }
    ContextHeroFrame(
        title = "Apps",
        subtitle = context.focusedLabel ?: countLine,
        hint =
            context.focusedLabel?.let { countLine }
                ?: "A to launch  ·  B back to games",
        contentFocusRequester = contentFocusRequester,
    )
}

@Composable
private fun SystemHero(
    context: HeroContext.System,
    contentFocusRequester: FocusRequester? = null,
) {
    val controls = koinInject<SystemControls>()
    val liveStatus by controls.status.collectAsState()
    val battery =
        if (liveStatus.batteryPercent >= 0) {
            liveStatus.batteryPercent
        } else {
            context.batteryPercent
        }
    val charging = liveStatus.charging || context.charging
    val wifiOn = liveStatus.wifiEnabled || context.wifiEnabled
    val statusLine =
        buildList {
            if (battery >= 0) {
                add(
                    buildString {
                        append("Battery $battery%")
                        if (charging) append(" ⚡")
                    },
                )
            }
            add(if (wifiOn) "Wi‑Fi on" else "Wi‑Fi off")
        }.joinToString("  ·  ")
    ContextHeroFrame(
        title = "System",
        subtitle = statusLine.ifBlank { "Quick settings" },
        hint = "Brightness, volume, and device controls below",
        accent = MaterialTheme.colorScheme.tertiary,
        contentFocusRequester = contentFocusRequester,
    )
}

@Composable
private fun HeroArtworkTransition(
    tile: GameTile,
    modifier: Modifier = Modifier,
    content: @Composable (GameTile) -> Unit,
) {
    AnimatedContent(
        targetState = tile,
        modifier = modifier,
        contentKey = { it.game.id },
        transitionSpec = {
            (
                fadeIn(WajihaMotion.fadeInSpec()) +
                    slideInHorizontally(WajihaMotion.fadeInSpec()) { it / 24 }
            ).togetherWith(
                fadeOut(WajihaMotion.fadeOutSpec()) +
                    slideOutHorizontally(WajihaMotion.fadeOutSpec()) { -it / 24 },
            )
        },
        label = "hero-artwork",
    ) { current ->
        content(current)
    }
}

@Composable
private fun HeroMetadataTransition(
    tile: GameTile,
    modifier: Modifier = Modifier,
    content: @Composable (GameTile) -> Unit,
) {
    AnimatedContent(
        targetState = tile,
        modifier = modifier,
        contentKey = { it.game.id },
        transitionSpec = {
            fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
        },
        label = "hero-metadata",
    ) { current ->
        content(current)
    }
}

@Composable
private fun GameHero(
    tile: GameTile,
    platformName: String?,
    settings: AppSettings,
    displaySlot: HeroDisplaySlot = HeroDisplaySlot.Primary,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout =
            remember(settings.heroLayoutBundle, displaySlot, maxWidth, maxHeight, rtl) {
                HeroLayoutFitter
                    .resolveForPaint(
                        bundle = settings.heroLayoutBundle,
                        slot = displaySlot,
                        canvasWidthPx = constraints.maxWidth.toFloat(),
                        canvasHeightPx = constraints.maxHeight.toFloat(),
                        rtl = false, // HeroCanvas mirrors for RTL once
                    ).copy(configured = true) // paint-only; keep ephemeral auto-fit from mutating twice
            }
        HeroCanvas(
            tile = tile,
            platformName = platformName,
            layout = layout,
            displaySlot = displaySlot,
            playCoverVideo = true,
            detailContext = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GameHeroMetadata(
    tile: GameTile,
    platformName: String?,
    settings: AppSettings,
) {
    val scheme = MaterialTheme.colorScheme
    val showPlatformRow =
        (settings.topHeroPlatformIcon && tile.iconPath != null) ||
            (settings.topHeroPlatform && platformName != null)
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        if (showPlatformRow) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.smPlus),
            ) {
                if (settings.topHeroPlatformIcon) {
                    tile.iconPath?.let { icon ->
                        AsyncImage(
                            model = icon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .size(WajihaSpacing.actionBarHeight)
                                    .clip(WajihaShapes.tile),
                        )
                    }
                }
                if (settings.topHeroPlatform) {
                    platformName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                        )
                    }
                }
            }
        }
        if (settings.topHeroTitle) {
            Text(
                text = tile.game.displayName,
                style = MaterialTheme.typography.headlineLarge,
                color = scheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.padding(top = if (showPlatformRow) WajihaSpacing.xs else 0.dp),
            )
        }
        if (settings.topHeroMetadata) {
            val meta =
                listOfNotNull(
                    tile.game.developer,
                    tile.game.releaseDate?.take(4),
                    tile.game.genre,
                    tile.game.region?.uppercase(),
                    tile.game.ageRating,
                ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WajihaSpacing.sm),
                )
            }
        }
        if (settings.topHeroDescription) {
            tile.game.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (settings.topHeroPlayStats && tile.game.playCount > 0) {
            Text(
                text = "Played ${tile.game.playCount}×",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.tertiary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
