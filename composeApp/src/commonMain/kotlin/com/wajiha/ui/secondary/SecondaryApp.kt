package com.wajiha.ui.secondary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.requestContentFocus
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.AppActions
import com.wajiha.platform.SystemControls
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.state.LauncherPanel
import com.wajiha.state.MenuDestination
import com.wajiha.state.MenuRouteSnapshot
import com.wajiha.state.SecondaryMode
import com.wajiha.ui.apps.AppDrawerScreen
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.quickSettingsGamepadHints
import com.wajiha.ui.components.gamepad.runningAppsGamepadHints
import com.wajiha.ui.components.gamepad.secondaryModeTabGamepadHints
import com.wajiha.ui.gamedetail.GameDetailScreen
import com.wajiha.ui.home.BottomScreen
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.navigation.LauncherHeroPane
import com.wajiha.ui.navigation.heroOnSecondary
import com.wajiha.ui.navigation.menuOnSecondary
import com.wajiha.ui.ra.AchievementsPanel
import com.wajiha.ui.running.RunningAppsPanel
import com.wajiha.ui.scraper.ScraperScreen
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.settings.PlatformPickerScreen
import com.wajiha.ui.settings.PlatformSettingsScreen
import com.wajiha.ui.settings.PlatformSettingsTab
import com.wajiha.ui.settings.SettingsScreen
import com.wajiha.ui.settings.SettingsViewModel
import com.wajiha.ui.system.QuickSettingsPanel
import com.wajiha.ui.theme.WajihaSpacing
import com.wajiha.ui.theme.WajihaTheme
import com.wajiha.ui.theme.themeIsDark
import com.wajiha.ui.theme.toFocusIndicatorStyle
import kotlinx.coroutines.delay
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private enum class SecondaryRoute {
    Modes,
    Settings,
    PlatformPicker,
    PlatformDetail,
    Scraper,
    GameDetail,
}

/**
 * Root of the secondary-display experience (AYN Thor bottom screen).
 * Renders the mode selected in [DualScreenStore]; full Settings (library,
 * scraper) are reachable here too since this is the touch screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SecondaryApp() {
    val settingsViewModel = koinInject<SettingsViewModel>()
    val settings by settingsViewModel.settings.collectAsState()

    WajihaTheme(
        darkTheme = themeIsDark(settings.theme),
        focusIndicatorStyle = settings.toFocusIndicatorStyle(),
    ) {
        val settingsLoaded by settingsViewModel.settingsLoaded.collectAsState()

        // Wait for DataStore so theme matches the top screen before painting
        if (!settingsLoaded) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            return@WajihaTheme
        }

        // Setup runs on the top screen — keep the bottom screen friendly
        if (!settings.onboardingDone) {
            SecondarySetupWaiting(singleScreen = settings.singleScreen)
            return@WajihaTheme
        }

        val store = koinInject<DualScreenStore>()
        val viewModel = koinInject<HomeViewModel>()
        val systemControls = koinInject<SystemControls>()
        val screenState by store.state.collectAsState()
        val isDual = screenState != DualScreenState.SingleDisplay
        val swapped = settings.swapScreenRoles
        val secondaryShowsHero = heroOnSecondary(isDual, swapped)
        val secondaryShowsMenu = menuOnSecondary(isDual, swapped)
        val mode by store.secondaryMode.collectAsState()
        val heroContext by store.heroContext.collectAsState()
        val focusedGameId by store.focusedGameId.collectAsState()
        val nowPlaying by store.nowPlaying.collectAsState()
        val activeSessions by store.activeSessions.collectAsState()
        val sessionActive = activeSessions.isNotEmpty()
        val state by viewModel.uiState.collectAsState()
        val apps by viewModel.apps.collectAsState()
        val backToGrid = { store.setSecondaryMode(SecondaryMode.GameGrid) }

        var route by remember { mutableStateOf(SecondaryRoute.Modes) }
        var platformDetailId by remember { mutableStateOf<String?>(null) }
        var platformDetailFromPicker by remember { mutableStateOf(false) }
        var gameDetailId by remember { mutableStateOf<Long?>(null) }
        val secondaryDisplayId by store.secondaryDisplayId.collectAsState()

        val openGameDetail: (Long) -> Unit = { id ->
            gameDetailId = id
            viewModel.playOpen()
            store.setGameDetailGameId(id)
            route = SecondaryRoute.GameDetail
        }

        // SELECT swap: adopt shared menu snapshot when this display gains the
        // menu; only clear local route when becoming hero (leave snapshot).
        LaunchedEffect(secondaryShowsMenu) {
            if (secondaryShowsMenu) {
                val snap = store.menuRoute.value
                platformDetailId = snap.platformDetailId
                platformDetailFromPicker = snap.platformDetailFromPicker
                gameDetailId = snap.gameDetailId
                when (snap.destination) {
                    MenuDestination.Home -> {
                        route = SecondaryRoute.Modes
                        store.setSecondaryMode(SecondaryMode.GameGrid)
                    }

                    MenuDestination.Settings -> {
                        route = SecondaryRoute.Settings
                    }

                    MenuDestination.PlatformPicker -> {
                        route = SecondaryRoute.PlatformPicker
                    }

                    MenuDestination.PlatformDetail -> {
                        route = SecondaryRoute.PlatformDetail
                    }

                    MenuDestination.Scraper -> {
                        route = SecondaryRoute.Scraper
                    }

                    MenuDestination.Apps -> {
                        route = SecondaryRoute.Modes
                        store.setSecondaryMode(SecondaryMode.AppDock)
                    }

                    MenuDestination.System -> {
                        route = SecondaryRoute.Modes
                        store.setSecondaryMode(SecondaryMode.QuickSettings)
                    }

                    MenuDestination.GameDetail -> {
                        route = SecondaryRoute.GameDetail
                    }

                    MenuDestination.NowRunning -> {
                        route = SecondaryRoute.Modes
                        store.setSecondaryMode(SecondaryMode.NowPlaying)
                    }
                }
            } else if (route != SecondaryRoute.Modes) {
                route = SecondaryRoute.Modes
            }
        }

        LaunchedEffect(secondaryShowsMenu) {
            if (!secondaryShowsMenu) return@LaunchedEffect
            store.openSettingsRequests.collect {
                viewModel.playOpen()
                route = SecondaryRoute.Settings
            }
        }

        // Publish while owning the menu (key on route/mode only so swap adopt wins).
        LaunchedEffect(route, mode, platformDetailId, platformDetailFromPicker, gameDetailId) {
            if (!secondaryShowsMenu) return@LaunchedEffect
            val destination =
                when (route) {
                    SecondaryRoute.Settings -> {
                        MenuDestination.Settings
                    }

                    SecondaryRoute.PlatformPicker -> {
                        MenuDestination.PlatformPicker
                    }

                    SecondaryRoute.PlatformDetail -> {
                        MenuDestination.PlatformDetail
                    }

                    SecondaryRoute.Scraper -> {
                        MenuDestination.Scraper
                    }

                    SecondaryRoute.GameDetail -> {
                        MenuDestination.GameDetail
                    }

                    SecondaryRoute.Modes -> {
                        when (mode) {
                            SecondaryMode.AppDock -> MenuDestination.Apps
                            SecondaryMode.QuickSettings -> MenuDestination.System
                            SecondaryMode.NowPlaying -> MenuDestination.NowRunning
                            else -> MenuDestination.Home
                        }
                    }
                }
            store.publishMenuRoute(
                MenuRouteSnapshot(
                    destination = destination,
                    platformDetailId = platformDetailId,
                    gameDetailId = gameDetailId,
                    platformDetailFromPicker = platformDetailFromPicker,
                ),
            )
        }

        // Dual-display: secondary owns gamepad while on settings/apps modes
        // (Games grid ownership comes from gamesMenuOnPrimary default).
        LaunchedEffect(route, mode, secondaryShowsMenu) {
            if (secondaryShowsMenu) {
                store.setSecondaryHoldsGamepad(
                    route != SecondaryRoute.Modes || mode != SecondaryMode.GameGrid,
                )
                val panel =
                    when {
                        route == SecondaryRoute.Settings ||
                            route == SecondaryRoute.PlatformPicker ||
                            route == SecondaryRoute.PlatformDetail ||
                            route == SecondaryRoute.Scraper -> LauncherPanel.Settings

                        route == SecondaryRoute.GameDetail -> LauncherPanel.GameDetail

                        mode == SecondaryMode.AppDock -> LauncherPanel.Apps

                        mode == SecondaryMode.QuickSettings -> LauncherPanel.System

                        else -> LauncherPanel.GameLibrary
                    }
                store.setSecondaryLauncherPanel(panel)
                if (route != SecondaryRoute.GameDetail) {
                    store.setGameDetailGameId(null)
                }
                if (panel == LauncherPanel.System) {
                    systemControls.refreshStatus()
                    val status = systemControls.status.value
                    store.setSystemHeroSnapshot(
                        batteryPercent = status.batteryPercent,
                        charging = status.charging,
                        wifiEnabled = status.wifiEnabled,
                    )
                }
            } else {
                store.setSecondaryHoldsGamepad(false)
                store.setSecondaryLauncherPanel(LauncherPanel.GameLibrary)
            }
        }

        // Gamepad B / system back: scraper/platform → settings → modes → game grid
        BackHandler(
            enabled =
                secondaryShowsMenu &&
                    (route != SecondaryRoute.Modes || mode != SecondaryMode.GameGrid),
        ) {
            viewModel.playBack()
            when (route) {
                SecondaryRoute.GameDetail -> {
                    store.setGameDetailGameId(null)
                    route = SecondaryRoute.Modes
                }

                SecondaryRoute.Scraper -> {
                    route =
                        if (platformDetailId != null) {
                            SecondaryRoute.PlatformDetail
                        } else {
                            SecondaryRoute.Settings
                        }
                }

                SecondaryRoute.PlatformDetail -> {
                    route =
                        if (platformDetailFromPicker) {
                            SecondaryRoute.PlatformPicker
                        } else {
                            SecondaryRoute.Settings
                        }
                }

                SecondaryRoute.PlatformPicker -> {
                    route = SecondaryRoute.Settings
                }

                SecondaryRoute.Settings -> {
                    route = SecondaryRoute.Modes
                }

                else -> {
                    backToGrid()
                }
            }
        }

        if (secondaryShowsMenu) {
            when (route) {
                SecondaryRoute.Settings -> {
                    SecondarySurface(store = store) {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            onBack = {
                                viewModel.playBack()
                                route = SecondaryRoute.Modes
                            },
                            onAddPlatform = {
                                viewModel.playOpen()
                                route = SecondaryRoute.PlatformPicker
                            },
                            onOpenPlatform = { id ->
                                platformDetailId = id
                                platformDetailFromPicker = false
                                viewModel.playOpen()
                                route = SecondaryRoute.PlatformDetail
                            },
                            onSectionChange = store::setSettingsSectionLabel,
                            gamepadOwner = GamepadOwner.Secondary,
                            onClaimGamepad = store::claimGamepad,
                        )
                    }
                    return@WajihaTheme
                }

                SecondaryRoute.PlatformPicker -> {
                    SecondarySurface(store = store) {
                        PlatformPickerScreen(
                            settingsViewModel = settingsViewModel,
                            onBack = {
                                viewModel.playBack()
                                route = SecondaryRoute.Settings
                            },
                            onPick = { id ->
                                platformDetailId = id
                                platformDetailFromPicker = true
                                viewModel.playOpen()
                                route = SecondaryRoute.PlatformDetail
                            },
                            gamepadOwner = GamepadOwner.Secondary,
                            onClaimGamepad = store::claimGamepad,
                        )
                    }
                    LaunchedEffect(Unit) {
                        store.setSettingsSectionLabel("Library")
                    }
                    return@WajihaTheme
                }

                SecondaryRoute.PlatformDetail -> {
                    val id = platformDetailId
                    if (id == null) {
                        route = SecondaryRoute.Settings
                    } else {
                        SecondarySurface(store = store) {
                            PlatformSettingsScreen(
                                platformId = id,
                                initialTab =
                                    if (platformDetailFromPicker) {
                                        PlatformSettingsTab.Folders
                                    } else {
                                        PlatformSettingsTab.General
                                    },
                                onBack = {
                                    viewModel.playBack()
                                    route =
                                        if (platformDetailFromPicker) {
                                            SecondaryRoute.PlatformPicker
                                        } else {
                                            SecondaryRoute.Settings
                                        }
                                },
                                gamepadOwner = GamepadOwner.Secondary,
                                onClaimGamepad = store::claimGamepad,
                            )
                        }
                        LaunchedEffect(Unit) {
                            store.setSettingsSectionLabel("Library")
                        }
                    }
                    return@WajihaTheme
                }

                SecondaryRoute.Scraper -> {
                    SecondarySurface(store = store) {
                        ScraperScreen(
                            viewModel = koinInject<ScraperViewModel>(),
                            onBack = {
                                viewModel.playBack()
                                route =
                                    if (platformDetailId != null) {
                                        SecondaryRoute.PlatformDetail
                                    } else {
                                        SecondaryRoute.Settings
                                    }
                            },
                            gamepadOwner = GamepadOwner.Secondary,
                            onClaimGamepad = store::claimGamepad,
                        )
                    }
                    LaunchedEffect(Unit) {
                        store.setSettingsSectionLabel("Scraper")
                    }
                    return@WajihaTheme
                }

                SecondaryRoute.GameDetail -> {
                    val id = gameDetailId
                    if (id == null) {
                        route = SecondaryRoute.Modes
                    } else {
                        SecondarySurface(store = store) {
                            GameDetailScreen(
                                gameId = id,
                                secondaryDisplayId = secondaryDisplayId,
                                dualDisplay = true,
                                gamepadOwner = GamepadOwner.Secondary,
                                onClaimGamepad = store::claimGamepad,
                                onBack = {
                                    viewModel.playBack()
                                    store.setGameDetailGameId(null)
                                    route = SecondaryRoute.Modes
                                },
                            )
                        }
                        LaunchedEffect(id) {
                            store.setGameDetailGameId(id)
                        }
                    }
                    return@WajihaTheme
                }

                SecondaryRoute.Modes -> {
                    Unit
                }
            }
        }

        LaunchedEffect(nowPlaying, mode) {
            if (nowPlaying == null && mode == SecondaryMode.NowPlaying) {
                store.setSecondaryMode(SecondaryMode.GameGrid)
            }
        }

        SecondarySurface(
            store = store,
            foreground = { gameplayDimScrimVisible ->
                NowPlayingOverlay(
                    store = store,
                    modifier = nowPlayingOverlayPlacement(),
                    hiddenByGameplayDim = gameplayDimScrimVisible,
                )
            },
        ) {
            if (secondaryShowsHero) {
                LauncherHeroPane(
                    state = state,
                    focusedGameId = focusedGameId,
                    heroContext = heroContext,
                    gamepadOwner = GamepadOwner.Secondary,
                    onClaimGamepad = store::claimGamepad,
                )
            } else {
                // Opaque backdrop so mode fades never reveal a black window gap.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "secondaryMode",
                        modifier = Modifier.fillMaxSize(),
                    ) { animatedMode ->
                        when (animatedMode) {
                            // Tap the blacked-out screen to restore the grid
                            SecondaryMode.Off -> {
                                BlackoutScreen(onTap = backToGrid)
                            }

                            SecondaryMode.NowPlaying -> {
                                SecondaryModeFrame(
                                    current = animatedMode,
                                    store = store,
                                    sessionActive = sessionActive,
                                    backgroundContent =
                                        if (settings.nowPlayingHeroBackground) {
                                            { NowPlayingBackdrop(state = nowPlaying) }
                                        } else {
                                            null
                                        },
                                ) {
                                    NowPlayingPanel(
                                        state = nowPlaying,
                                        heroBackground = settings.nowPlayingHeroBackground,
                                        useLogo = settings.nowPlayingLogo,
                                        backgroundInParent = true,
                                    )
                                }
                            }

                            SecondaryMode.GameGrid -> {
                                val featured by store.nowPlaying.collectAsState()
                                val topPackage by store.topDisplayForegroundPackage.collectAsState()
                                val appActions = koinInject<AppActions>()
                                val openSession = rememberOpenSession(store, appActions, topPackage)
                                BottomScreen(
                                    state = state,
                                    gridRows = settings.gridRows,
                                    onSelectPlatform = viewModel::selectPlatform,
                                    onFocusGame = viewModel::focusGame,
                                    onLaunchGame = viewModel::launchGame,
                                    onOpenGameDetail = openGameDetail,
                                    onLaunchGameOnDisplay = viewModel::launchGameOnDisplay,
                                    onRemoveFromLibrary = viewModel::removeFromLibrary,
                                    onDeleteGameFile = viewModel::deleteGameFile,
                                    secondaryDisplayId = secondaryDisplayId,
                                    dualDisplay = true,
                                    focusedGameHeroBackground = settings.gameGridHeroBackground,
                                    onOpenApps = { store.setSecondaryMode(SecondaryMode.AppDock) },
                                    onOpenSettings = {
                                        viewModel.playOpen()
                                        route = SecondaryRoute.Settings
                                    },
                                    onOpenSystem = { store.setSecondaryMode(SecondaryMode.QuickSettings) },
                                    onAddGames = {
                                        viewModel.playOpen()
                                        route = SecondaryRoute.PlatformPicker
                                    },
                                    gamepadOwner = GamepadOwner.Secondary,
                                    onClaimGamepad = store::claimGamepad,
                                    sessions = activeSessions,
                                    featuredSessionPackage = featured?.packageName,
                                    topDisplayPackage = topPackage,
                                    onFocusSession = store::featureSession,
                                    onOpenSession = openSession,
                                    onCloseSession = { appActions.killApp(it) },
                                    showSessionGrid = store.nowPlayingDisplay.showsGridTiles,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            SecondaryMode.AppDock -> {
                                AppDrawerScreen(
                                    apps = apps,
                                    onLoad = viewModel::loadApps,
                                    onLaunch = viewModel::launchApp,
                                    onLaunchOnDisplay = viewModel::launchAppOnDisplay,
                                    onOpenAppInfo = systemControls::openAppInfo,
                                    onBack = backToGrid,
                                    onFocusChange = { app ->
                                        store.setAppsHeroDetail(apps.size, app?.label)
                                    },
                                    secondaryDisplayId = secondaryDisplayId,
                                    dualDisplay = true,
                                    gamepadOwner = GamepadOwner.Secondary,
                                    onClaimGamepad = store::claimGamepad,
                                )
                            }

                            SecondaryMode.RunningApps -> {
                                SecondaryModeFrame(
                                    current = animatedMode,
                                    store = store,
                                    sessionActive = sessionActive,
                                ) {
                                    RunningAppsPanel(showGamepadHints = false)
                                }
                            }

                            SecondaryMode.QuickSettings -> {
                                SecondaryModeFrame(
                                    current = animatedMode,
                                    store = store,
                                    sessionActive = sessionActive,
                                ) {
                                    QuickSettingsPanel(showGamepadHints = false)
                                }
                            }

                            SecondaryMode.Achievements -> {
                                SecondaryModeFrame(
                                    current = animatedMode,
                                    store = store,
                                    sessionActive = sessionActive,
                                ) {
                                    AchievementsPanel(showGamepadHints = false)
                                }
                            }

                            SecondaryMode.Clock -> {
                                val clockFocus = remember { FocusRequester() }
                                WajihaScreen(
                                    layerId = "secondary_clock",
                                    showActionBar = true,
                                    gamepadHints =
                                        listOf(
                                            GamepadHint(GamepadHintButton.B, "Games"),
                                            GamepadHint(GamepadHintButton.L2, "Focus screen"),
                                        ),
                                    gamepadOwner = GamepadOwner.Secondary,
                                    onClaimGamepad = store::claimGamepad,
                                    onOwnerGainedFocus = { clockFocus.requestContentFocus() },
                                ) {
                                    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = WajihaSpacing.xs),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            GamepadChip(
                                                label = "< Games",
                                                selected = false,
                                                onClick = { store.setSecondaryMode(SecondaryMode.GameGrid) },
                                                sound = UiSound.Back,
                                                focusRequester = clockFocus,
                                            )
                                        }
                                        ClockScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Header with back-to-grid plus tabs so every mode stays reachable. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SecondaryModeFrame(
    current: SecondaryMode,
    store: DualScreenStore,
    sessionActive: Boolean,
    backgroundContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tabs =
        buildList {
            if (sessionActive) {
                add(SecondaryMode.NowPlaying to "Now Running")
            }
            add(SecondaryMode.RunningApps to "Running")
            add(SecondaryMode.QuickSettings to "System")
            add(SecondaryMode.Achievements to "Trophies")
        }
    val currentIndex = tabs.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val feedback = LocalUiFeedback.current

    fun selectTab(index: Int) {
        feedback.tabSelect(currentIndex, index)
        tabs.getOrNull(index)?.first?.let { store.setSecondaryMode(it) }
    }

    val modeHints =
        when (current) {
            SecondaryMode.NowPlaying -> {
                listOf(
                    GamepadHint(GamepadHintButton.B, "Games"),
                    GamepadHint(GamepadHintButton.L1R1, "Tab"),
                )
            }

            SecondaryMode.RunningApps -> {
                runningAppsGamepadHints + GamepadHint(GamepadHintButton.L1R1, "Tab")
            }

            SecondaryMode.QuickSettings -> {
                quickSettingsGamepadHints +
                    listOf(
                        GamepadHint(GamepadHintButton.B, "Games"),
                        GamepadHint(GamepadHintButton.L1R1, "Tab"),
                    )
            }

            SecondaryMode.Achievements -> {
                listOf(
                    GamepadHint(GamepadHintButton.B, "Games"),
                    GamepadHint(GamepadHintButton.L1R1, "Tab"),
                )
            }

            else -> {
                secondaryModeTabGamepadHints
            }
        }
    val contentFocus = remember { FocusRequester() }
    WajihaScreen(
        layerId = "secondary_mode_${current.name}",
        showActionBar = true,
        gamepadHints = modeHints + GamepadHint(GamepadHintButton.L2, "Focus screen"),
        gamepadOwner = GamepadOwner.Secondary,
        onClaimGamepad = store::claimGamepad,
        onOwnerGainedFocus = { contentFocus.requestContentFocus() },
        backgroundContent = backgroundContent,
        onPreviewKey = { event ->
            when {
                GamepadKeys.isL1(event.type, event.key) && currentIndex > 0 -> {
                    selectTab(currentIndex - 1)
                    true
                }

                GamepadKeys.isR1(event.type, event.key) && currentIndex < tabs.lastIndex -> {
                    selectTab(currentIndex + 1)
                    true
                }

                else -> {
                    false
                }
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (backgroundContent == null) {
                            Modifier.background(MaterialTheme.colorScheme.background)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = WajihaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                GamepadChip(
                    label = "< Games",
                    selected = false,
                    onClick = { store.setSecondaryMode(SecondaryMode.GameGrid) },
                    sound = UiSound.Back,
                    gamepadFocusable = false,
                )
                tabs.forEachIndexed { index, (mode, label) ->
                    GamepadChip(
                        label = label,
                        selected = mode == current,
                        onClick = { selectTab(index) },
                        sound = UiSound.Navigate,
                        soundWhenUnselected = true,
                        gamepadFocusable = false,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(contentFocus)
                        .wajihaGamepadFocus(),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun BlackoutScreen(onTap: () -> Unit) {
    // Pure black; on OLED-class panels this effectively turns the display off
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = null, indication = null, onClick = onTap),
    )
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun ClockScreen() {
    var now by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(currentTimeText())
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = currentTimeText()
            kotlinx.coroutines.delay(1000)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = now,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun currentTimeText(): String {
    val dateTime =
        kotlin.time.Clock.System
            .now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val h = dateTime.hour.toString().padStart(2, '0')
    val m = dateTime.minute.toString().padStart(2, '0')
    return "$h:$m"
}

/** Cocoon-style: quiet bottom screen while setup talks on the top display. */
@Composable
private fun SecondarySetupWaiting(singleScreen: Boolean = false) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = if (singleScreen) "Main display only" else "Bottom Screen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (singleScreen) "Unused in single-screen mode" else "Getting ready…",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    if (singleScreen) {
                        "Finish setup on the main display.\n" +
                            "This panel is left to the system while Single screen is on."
                    } else {
                        "Finish the quick tour on the top screen.\n" +
                            "Your library will land here when you're done."
                    },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
