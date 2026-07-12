package com.wajiha

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.backhandler.BackHandler
import com.wajiha.input.requestContentFocus
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.AppActions
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.GamepadOwner
import com.wajiha.state.LauncherPanel
import com.wajiha.state.SecondaryMode
import com.wajiha.ui.apps.AppDrawerScreen
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaSnackbarHost
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.rememberWajihaSnackbarHostState
import com.wajiha.ui.gamedetail.GameDetailScreen
import com.wajiha.ui.home.BottomScreen
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.home.TopScreen
import com.wajiha.ui.onboarding.OnboardingScreen
import com.wajiha.ui.scraper.ScraperScreen
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.settings.PlatformPickerScreen
import com.wajiha.ui.settings.PlatformSettingsScreen
import com.wajiha.ui.settings.SettingsScreen
import com.wajiha.ui.settings.SettingsViewModel
import com.wajiha.ui.components.gamepad.quickSettingsGamepadHints
import com.wajiha.ui.navigation.LauncherHeroPane
import com.wajiha.ui.navigation.SyncSystemHeroSnapshot
import com.wajiha.ui.navigation.heroOnPrimary
import com.wajiha.ui.navigation.launcherHero
import com.wajiha.ui.navigation.menuOnPrimary
import com.wajiha.ui.secondary.NowPlayingPanel
import com.wajiha.ui.secondary.rememberOpenSession
import com.wajiha.ui.system.QuickSettingsPanel
import com.wajiha.ui.theme.WajihaTheme
import com.wajiha.ui.theme.themeIsDark
import com.wajiha.ui.theme.toFocusIndicatorStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class Route {
    Home,
    Apps,
    Settings,
    PlatformPicker,
    PlatformDetail,
    Scraper,
    System,
    GameDetail,
    NowRunning
}

/**
 * Root of the primary-display experience.
 *
 * - Dual display: this screen is the TOP display → hero/preview only
 *   (the bottom grid lives in SecondaryHomeActivity).
 * - Single display: combined 3DS-like vertical split (hero on top,
 *   touch grid below).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    val viewModel = koinInject<HomeViewModel>()
    val settingsViewModel = koinInject<SettingsViewModel>()
    val systemControls = koinInject<SystemControls>()
    val appActions = koinInject<AppActions>()
    val settings by settingsViewModel.settings.collectAsState()

    WajihaTheme(
        darkTheme = themeIsDark(settings.theme),
        focusIndicatorStyle = settings.toFocusIndicatorStyle()
    ) {
        val state by viewModel.uiState.collectAsState()
        val screenState by viewModel.dualScreenStore.state.collectAsState()
        val focusedGameId by viewModel.dualScreenStore.focusedGameId.collectAsState()
        val heroContext by viewModel.dualScreenStore.heroContext.collectAsState()
        val secondaryDisplayId by viewModel.dualScreenStore.secondaryDisplayId.collectAsState()
        val nowPlaying by viewModel.dualScreenStore.nowPlaying.collectAsState()
        val activeSessions by viewModel.dualScreenStore.activeSessions.collectAsState()
        val topDisplayPackage by viewModel.dualScreenStore.topDisplayForegroundPackage.collectAsState()
        val secondaryMode by viewModel.dualScreenStore.secondaryMode.collectAsState()
        val apps by viewModel.apps.collectAsState()

        var route by remember { mutableStateOf(Route.Home) }
        var platformDetailId by remember { mutableStateOf<String?>(null) }
        var gameDetailId by remember { mutableStateOf<Long?>(null) }
        var onboardingDismissed by remember { mutableStateOf(false) }
        val snackbarHostState = rememberWajihaSnackbarHostState()
        val snackbarScope = rememberCoroutineScope()
        val settingsLoaded by settingsViewModel.settingsLoaded.collectAsState()

        if (!settingsLoaded) {
            Box(modifier = Modifier.fillMaxSize())
            return@WajihaTheme
        }

        val dualStore = viewModel.dualScreenStore
        val isDual = screenState != DualScreenState.SingleDisplay
        val swapped = settings.swapScreenRoles
        val primaryShowsHero = heroOnPrimary(isDual, swapped)
        val primaryShowsMenu = menuOnPrimary(isDual, swapped)

        if (!settings.onboardingDone && !onboardingDismissed) {
            LaunchedEffect(Unit) {
                dualStore.setPrimaryHoldsGamepad(true)
            }
            OnboardingScreen(
                settingsViewModel = settingsViewModel,
                onFinished = {
                    settingsViewModel.setOnboardingDone()
                    onboardingDismissed = true
                },
                onOpenPlatformPicker = {
                    settingsViewModel.setOnboardingDone()
                    onboardingDismissed = true
                    route = Route.PlatformPicker
                },
                gamepadOwner = GamepadOwner.Primary,
                onClaimGamepad = dualStore::claimGamepad
            )
            return@WajihaTheme
        }

        LaunchedEffect(primaryShowsHero) {
            if (primaryShowsHero && route != Route.Home) {
                route = Route.Home
            }
        }

        LaunchedEffect(route, primaryShowsMenu) {
            dualStore.setGamesMenuOnPrimary(primaryShowsMenu)
            dualStore.setPrimaryHoldsGamepad(primaryShowsMenu || route != Route.Home)
        }

        LaunchedEffect(route, primaryShowsMenu) {
            if (primaryShowsMenu) {
                val panel = when (route) {
                    Route.Settings, Route.PlatformPicker, Route.PlatformDetail, Route.Scraper ->
                        LauncherPanel.Settings
                    Route.GameDetail -> LauncherPanel.GameDetail
                    Route.Apps -> LauncherPanel.Apps
                    Route.System -> LauncherPanel.System
                    Route.NowRunning -> LauncherPanel.GameLibrary
                    Route.Home -> LauncherPanel.GameLibrary
                }
                dualStore.setPrimaryLauncherPanel(panel)
                if (route != Route.GameDetail) {
                    dualStore.setGameDetailGameId(null)
                }
            } else {
                dualStore.setPrimaryLauncherPanel(LauncherPanel.GameLibrary)
            }
        }

        val openGameDetail: (Long) -> Unit = { id ->
            gameDetailId = id
            viewModel.playOpen()
            dualStore.setGameDetailGameId(id)
            route = Route.GameDetail
        }

        val hero = launcherHero(state, focusedGameId)
        val focusedTile = hero.focusedTile
        val platformName = hero.platformName

        val openSession = rememberOpenSession(dualStore, appActions, topDisplayPackage)

        val sessionGridParams: @Composable (Modifier) -> Unit = { gridModifier ->
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
                onOpenApps = {
                    viewModel.playOpen()
                    route = Route.Apps
                },
                onOpenSettings = {
                    viewModel.playOpen()
                    route = Route.Settings
                },
                onOpenSystem = {
                    viewModel.playOpen()
                    route = Route.System
                },
                onAddGames = {
                    viewModel.playOpen()
                    route = Route.PlatformPicker
                },
                gamepadOwner = GamepadOwner.Primary,
                onClaimGamepad = dualStore::claimGamepad,
                sessions = activeSessions,
                featuredSessionPackage = nowPlaying?.packageName,
                topDisplayPackage = topDisplayPackage,
                onFocusSession = dualStore::featureSession,
                onOpenSession = openSession,
                onCloseSession = { appActions.killApp(it) },
                showSessionGrid = dualStore.nowPlayingDisplay.showsGridTiles,
                modifier = gridModifier
            )
        }

        LaunchedEffect(nowPlaying, route) {
            if (nowPlaying == null && route == Route.NowRunning) {
                route = Route.Home
            }
        }

        LaunchedEffect(isDual) {
            if (isDual) return@LaunchedEffect
            dualStore.navigateToNowPlayingRequests.collect {
                route = Route.NowRunning
            }
        }

        LaunchedEffect(isDual, swapped, secondaryMode) {
            if (isDual && swapped && secondaryMode == SecondaryMode.NowPlaying) {
                route = Route.NowRunning
                dualStore.setSecondaryMode(SecondaryMode.GameGrid)
            }
        }

        LaunchedEffect(isDual, swapped, route, secondaryMode) {
            if (isDual && swapped && route != Route.NowRunning &&
                secondaryMode == SecondaryMode.NowPlaying
            ) {
                dualStore.setSecondaryMode(SecondaryMode.GameGrid)
            }
        }

        BackHandler(enabled = route != Route.Home) {
            viewModel.playBack()
            if (route == Route.GameDetail) {
                dualStore.setGameDetailGameId(null)
            }
            route = when (route) {
                Route.GameDetail -> Route.Home
                Route.NowRunning -> Route.Home
                Route.Scraper ->
                    if (platformDetailId != null) Route.PlatformDetail else Route.Settings
                Route.PlatformDetail -> Route.Settings
                Route.PlatformPicker -> Route.Settings
                else -> Route.Home
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (primaryShowsHero) {
                LauncherHeroPane(
                    state = state,
                    focusedGameId = focusedGameId,
                    heroContext = heroContext,
                    gamepadOwner = GamepadOwner.Primary,
                    onClaimGamepad = dualStore::claimGamepad
                )
            } else when (route) {
                Route.Apps -> AppDrawerScreen(
                    apps = apps,
                    onLoad = viewModel::loadApps,
                    onLaunch = viewModel::launchApp,
                    onLaunchOnDisplay = viewModel::launchAppOnDisplay,
                    onOpenAppInfo = systemControls::openAppInfo,
                    onBack = {
                        viewModel.playBack()
                        route = Route.Home
                    },
                    onFocusChange = { app ->
                        dualStore.setAppsHeroDetail(apps.size, app?.label)
                    },
                    secondaryDisplayId = secondaryDisplayId,
                    gamepadOwner = GamepadOwner.Primary,
                    onClaimGamepad = dualStore::claimGamepad
                )
                Route.Settings -> SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = {
                        viewModel.playBack()
                        route = Route.Home
                    },
                    onAddPlatform = {
                        viewModel.playOpen()
                        route = Route.PlatformPicker
                    },
                    onOpenPlatform = { id ->
                        platformDetailId = id
                        viewModel.playOpen()
                        route = Route.PlatformDetail
                    },
                    onSectionChange = dualStore::setSettingsSectionLabel,
                    gamepadOwner = GamepadOwner.Primary,
                    onClaimGamepad = dualStore::claimGamepad
                )
                Route.PlatformPicker -> PlatformPickerScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = {
                        viewModel.playBack()
                        route = Route.Settings
                    },
                    onPick = { id ->
                        platformDetailId = id
                        viewModel.playOpen()
                        route = Route.PlatformDetail
                    },
                    gamepadOwner = GamepadOwner.Primary,
                    onClaimGamepad = dualStore::claimGamepad
                )
                Route.PlatformDetail -> {
                    val id = platformDetailId
                    if (id == null) {
                        route = Route.Settings
                    } else {
                        PlatformSettingsScreen(
                            platformId = id,
                            onBack = {
                                viewModel.playBack()
                                route = Route.Settings
                            },
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad
                        )
                        LaunchedEffect(Unit) {
                            dualStore.setSettingsSectionLabel("Library")
                        }
                    }
                }
                Route.Scraper -> {
                    ScraperScreen(
                        viewModel = koinInject<ScraperViewModel>(),
                        onBack = {
                            viewModel.playBack()
                            route = if (platformDetailId != null) {
                                Route.PlatformDetail
                            } else {
                                Route.Settings
                            }
                        },
                        gamepadOwner = GamepadOwner.Primary,
                        onClaimGamepad = dualStore::claimGamepad
                    )
                    LaunchedEffect(Unit) {
                        dualStore.setSettingsSectionLabel("Scraper")
                    }
                }
                Route.GameDetail -> {
                    val id = gameDetailId
                    if (id == null) {
                        route = Route.Home
                    } else {
                        val secondaryId by dualStore.secondaryDisplayId.collectAsState()
                        GameDetailScreen(
                            gameId = id,
                            secondaryDisplayId = secondaryId,
                            dualDisplay = isDual,
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad,
                            onBack = {
                                viewModel.playBack()
                                dualStore.setGameDetailGameId(null)
                                route = Route.Home
                            }
                        )
                        LaunchedEffect(id) {
                            dualStore.setGameDetailGameId(id)
                        }
                    }
                }
                Route.System -> {
                    SyncSystemHeroSnapshot(systemControls, dualStore)
                    val systemFocus = remember { FocusRequester() }
                    WajihaScreen(
                        layerId = "system",
                        onBack = {
                            viewModel.playBack()
                            route = Route.Home
                        },
                        showActionBar = true,
                        gamepadHints = quickSettingsGamepadHints + ("L2" to "Focus screen"),
                        gamepadOwner = GamepadOwner.Primary,
                        onClaimGamepad = dualStore::claimGamepad,
                        onOwnerGainedFocus = { systemFocus.requestContentFocus() }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            WajihaToolbar(
                                title = "System",
                                onBack = {
                                    viewModel.playBack()
                                    route = Route.Home
                                }
                            )
                            QuickSettingsPanel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .focusRequester(systemFocus)
                                    .wajihaGamepadFocus(),
                                showGamepadHints = false
                            )
                        }
                    }
                }
                Route.NowRunning -> {
                    val nowRunningFocus = remember { FocusRequester() }
                    WajihaScreen(
                        layerId = "now_running",
                        showActionBar = true,
                        gamepadHints = listOf("B" to "Back", "L2" to "Focus screen"),
                        gamepadOwner = GamepadOwner.Primary,
                        onClaimGamepad = dualStore::claimGamepad,
                        onOwnerGainedFocus = { nowRunningFocus.requestContentFocus() }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            WajihaToolbar(
                                title = "Now Running",
                                onBack = {
                                    viewModel.playBack()
                                    route = Route.Home
                                }
                            )
                            NowPlayingPanel(
                                state = nowPlaying,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .focusRequester(nowRunningFocus)
                                    .wajihaGamepadFocus()
                            )
                        }
                    }
                }
                Route.Home -> {
                    if (isDual) {
                        sessionGridParams(Modifier.fillMaxSize())
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TopScreen(
                                focused = focusedTile,
                                platformName = platformName,
                                heroContext = heroContext,
                                modifier = Modifier.fillMaxWidth().weight(0.42f)
                            )
                            sessionGridParams(Modifier.fillMaxWidth().weight(0.58f))
                        }
                    }
                }
            }

            state.launchError?.let { error ->
                LaunchedEffect(error) {
                    snackbarScope.launch {
                        snackbarHostState.showSnackbar(error)
                    }
                    delay(4000)
                    viewModel.dismissLaunchError()
                }
            }
            WajihaSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
