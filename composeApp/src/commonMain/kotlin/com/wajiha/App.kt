package com.wajiha

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.requestContentFocus
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.AppActions
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.GamepadOwner
import com.wajiha.state.LauncherPanel
import com.wajiha.state.MenuDestination
import com.wajiha.state.MenuRouteSnapshot
import com.wajiha.state.SecondaryMode
import com.wajiha.ui.apps.AppDrawerScreen
import com.wajiha.ui.components.WajihaFolderSettingChrome
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaSnackbarHost
import com.wajiha.ui.components.gamepad.quickSettingsGamepadHints
import com.wajiha.ui.components.rememberWajihaSnackbarHostState
import com.wajiha.ui.gamedetail.GameDetailScreen
import com.wajiha.ui.home.BottomScreen
import com.wajiha.ui.home.HomeChromeBar
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.home.TopScreen
import com.wajiha.ui.navigation.LauncherHeroPane
import com.wajiha.ui.navigation.SyncSystemHeroSnapshot
import com.wajiha.ui.navigation.heroOnPrimary
import com.wajiha.ui.navigation.launcherHero
import com.wajiha.ui.navigation.menuOnPrimary
import com.wajiha.ui.onboarding.OnboardingScreen
import com.wajiha.ui.scraper.ScraperScreen
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.secondary.NowPlayingBackdrop
import com.wajiha.ui.secondary.NowPlayingOverlay
import com.wajiha.ui.secondary.NowPlayingPanel
import com.wajiha.ui.secondary.homeDockNowPlayingInset
import com.wajiha.ui.secondary.nowPlayingOverlayPlacement
import com.wajiha.ui.secondary.rememberOpenSession
import com.wajiha.ui.settings.PlatformPickerScreen
import com.wajiha.ui.settings.PlatformSettingsScreen
import com.wajiha.ui.settings.PlatformSettingsTab
import com.wajiha.ui.settings.SettingsScreen
import com.wajiha.ui.settings.SettingsViewModel
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
    NowRunning,
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
    val settingsLoaded by settingsViewModel.settingsLoaded.collectAsState()

    // Empty until DataStore loads — window background (BootTheme) fills the gap.
    // Do not mount WajihaTheme with AppSettings() default theme=dark.
    if (!settingsLoaded) {
        return
    }

    WajihaTheme(
        darkTheme = themeIsDark(settings.theme),
        focusIndicatorStyle = settings.toFocusIndicatorStyle(),
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
        var platformDetailFromPicker by remember { mutableStateOf(false) }
        var gameDetailId by remember { mutableStateOf<Long?>(null) }
        var onboardingDismissed by remember { mutableStateOf(false) }
        val snackbarHostState = rememberWajihaSnackbarHostState()
        val snackbarScope = rememberCoroutineScope()

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
                onClaimGamepad = dualStore::claimGamepad,
            )
            return@WajihaTheme
        }

        // SELECT swap: adopt the shared menu snapshot on the ownership edge in
        // the same composition that gains the menu — a LaunchedEffect is one
        // frame too late and briefly paints Home (game grid) first.
        var prevPrimaryShowsMenu by remember { mutableStateOf(primaryShowsMenu) }
        if (prevPrimaryShowsMenu != primaryShowsMenu) {
            prevPrimaryShowsMenu = primaryShowsMenu
            if (primaryShowsMenu) {
                val snap = dualStore.menuRoute.value
                platformDetailId = snap.platformDetailId
                platformDetailFromPicker = snap.platformDetailFromPicker
                gameDetailId = snap.gameDetailId
                route =
                    when (snap.destination) {
                        MenuDestination.Home -> Route.Home
                        MenuDestination.Settings -> Route.Settings
                        MenuDestination.PlatformPicker -> Route.PlatformPicker
                        MenuDestination.PlatformDetail -> Route.PlatformDetail
                        MenuDestination.Scraper -> Route.Scraper
                        MenuDestination.Apps -> Route.Apps
                        MenuDestination.System -> Route.System
                        MenuDestination.GameDetail -> Route.GameDetail
                        MenuDestination.NowRunning -> Route.NowRunning
                    }
            } else if (route != Route.Home) {
                route = Route.Home
            }
        }

        // Publish while owning the menu (key on route only so swap adopt wins).
        LaunchedEffect(route, platformDetailId, platformDetailFromPicker, gameDetailId) {
            if (!primaryShowsMenu) return@LaunchedEffect
            dualStore.publishMenuRoute(
                MenuRouteSnapshot(
                    destination =
                        when (route) {
                            Route.Home -> MenuDestination.Home
                            Route.Settings -> MenuDestination.Settings
                            Route.PlatformPicker -> MenuDestination.PlatformPicker
                            Route.PlatformDetail -> MenuDestination.PlatformDetail
                            Route.Scraper -> MenuDestination.Scraper
                            Route.Apps -> MenuDestination.Apps
                            Route.System -> MenuDestination.System
                            Route.GameDetail -> MenuDestination.GameDetail
                            Route.NowRunning -> MenuDestination.NowRunning
                        },
                    platformDetailId = platformDetailId,
                    gameDetailId = gameDetailId,
                    platformDetailFromPicker = platformDetailFromPicker,
                ),
            )
        }

        LaunchedEffect(route, primaryShowsMenu) {
            dualStore.setGamesMenuOnPrimary(primaryShowsMenu)
            dualStore.setPrimaryHoldsGamepad(primaryShowsMenu || route != Route.Home)
        }

        LaunchedEffect(route, primaryShowsMenu) {
            if (primaryShowsMenu) {
                val panel =
                    when (route) {
                        Route.Settings, Route.PlatformPicker, Route.PlatformDetail, Route.Scraper -> {
                            LauncherPanel.Settings
                        }

                        Route.GameDetail -> {
                            LauncherPanel.GameDetail
                        }

                        Route.Apps -> {
                            LauncherPanel.Apps
                        }

                        Route.System -> {
                            LauncherPanel.System
                        }

                        Route.NowRunning -> {
                            LauncherPanel.GameLibrary
                        }

                        Route.Home -> {
                            LauncherPanel.GameLibrary
                        }
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
                gameGridArt = settings.gameGridArt,
                gameGridTileSize = settings.gameGridTileSize,
                gameGridShowTitles = settings.gameGridShowTitles,
                gameGridShowTileChrome = settings.gameGridShowTileChrome,
                onSelectPlatform = viewModel::selectPlatform,
                onOpenCollections = viewModel::openCollections,
                onSelectCollection = viewModel::selectCollection,
                onCreateCollection = { name -> viewModel.createCollection(name) },
                onCreateCollectionForGame = { name, gameId -> viewModel.createCollection(name, gameId) },
                onDeleteCollection = viewModel::deleteCollection,
                onSetGameInCollection = viewModel::setGameInCollection,
                membershipForGame = viewModel::observeMembership,
                onFocusGame = viewModel::focusGame,
                onLaunchGame = viewModel::launchGame,
                onOpenGameDetail = openGameDetail,
                onLaunchGameOnDisplay = viewModel::launchGameOnDisplay,
                onRemoveFromLibrary = viewModel::removeFromLibrary,
                onDeleteGameFile = viewModel::deleteGameFile,
                secondaryDisplayId = secondaryDisplayId,
                dualDisplay = isDual,
                showHeaderChrome = isDual || !settings.showHeroBanner,
                focusedGameTitle =
                    focusedTile
                        ?.game
                        ?.displayName
                        ?.takeIf { !isDual && settings.showSelectedGameName },
                focusedGameHeroBackground = settings.gameGridHeroBackground,
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
                showHomeDock = settings.showHomeDock,
                dockApps = apps,
                dockFavoritePackages = settings.appDrawerFavoritePackages,
                dockIconShape = settings.iconShape,
                onLaunchDockApp = viewModel::launchDockApp,
                onLaunchDockAppOnDisplay = viewModel::launchAppOnDisplay,
                onRemoveDockFavorite = settingsViewModel::removeAppDrawerFavorite,
                onMoveDockFavorite = { pkg, delta ->
                    settingsViewModel.moveAppDrawerFavorite(
                        packageName = pkg,
                        delta = delta,
                        installedPackages = apps.map { it.packageName },
                    )
                },
                onOpenDockAppInfo = systemControls::openAppInfo,
                onLoadDockApps = viewModel::loadApps,
                modifier = gridModifier,
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

        LaunchedEffect(primaryShowsMenu) {
            if (!primaryShowsMenu) return@LaunchedEffect
            dualStore.openSettingsRequests.collect {
                viewModel.playOpen()
                route = Route.Settings
            }
        }

        LaunchedEffect(primaryShowsMenu) {
            if (!primaryShowsMenu) return@LaunchedEffect
            dualStore.openAppsRequests.collect {
                viewModel.playOpen()
                route = Route.Apps
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
            route =
                when (route) {
                    Route.GameDetail -> {
                        Route.Home
                    }

                    Route.NowRunning -> {
                        Route.Home
                    }

                    Route.Scraper -> {
                        if (platformDetailId != null) Route.PlatformDetail else Route.Settings
                    }

                    Route.PlatformDetail -> {
                        if (platformDetailFromPicker) {
                            Route.PlatformPicker
                        } else {
                            Route.Settings
                        }
                    }

                    Route.PlatformPicker -> {
                        Route.Settings
                    }

                    else -> {
                        Route.Home
                    }
                }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (primaryShowsHero) {
                LauncherHeroPane(
                    state = state,
                    focusedGameId = focusedGameId,
                    heroContext = heroContext,
                    gamepadOwner = GamepadOwner.Primary,
                    onClaimGamepad = dualStore::claimGamepad,
                )
            } else {
                when (route) {
                    Route.Apps -> {
                        AppDrawerScreen(
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
                            dualDisplay = isDual,
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad,
                            iconShape = settings.iconShape,
                            gridColumns = settings.appDrawerColumns,
                            gridRows = settings.appDrawerRows,
                            iconSizePreference = settings.appDrawerIconSize,
                            gridOrientation = settings.appDrawerOrientation,
                            gridScrollMode = settings.appDrawerScrollMode,
                            showAppLabels = settings.appDrawerShowLabels,
                            favoritePackages = settings.appDrawerFavoritePackages,
                            onGridColumnsChange = settingsViewModel::setAppDrawerColumns,
                            onGridRowsChange = settingsViewModel::setAppDrawerRows,
                            onIconSizeChange = settingsViewModel::setAppDrawerIconSize,
                            onGridOrientationChange = settingsViewModel::setAppDrawerOrientation,
                            onGridScrollModeChange = settingsViewModel::setAppDrawerScrollMode,
                            onShowAppLabelsChange = settingsViewModel::setAppDrawerShowLabels,
                            onAddFavorite = settingsViewModel::addAppDrawerFavorite,
                            onRemoveFavorite = settingsViewModel::removeAppDrawerFavorite,
                            onMoveFavorite = { pkg, delta ->
                                settingsViewModel.moveAppDrawerFavorite(
                                    packageName = pkg,
                                    delta = delta,
                                    installedPackages = apps.map { it.packageName },
                                )
                            },
                        )
                    }

                    Route.Settings -> {
                        SettingsScreen(
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
                                platformDetailFromPicker = false
                                viewModel.playOpen()
                                route = Route.PlatformDetail
                            },
                            onSectionChange = dualStore::setSettingsSectionLabel,
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad,
                        )
                    }

                    Route.PlatformPicker -> {
                        PlatformPickerScreen(
                            settingsViewModel = settingsViewModel,
                            onBack = {
                                viewModel.playBack()
                                route = Route.Settings
                            },
                            onPick = { id ->
                                platformDetailId = id
                                platformDetailFromPicker = true
                                viewModel.playOpen()
                                route = Route.PlatformDetail
                            },
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad,
                        )
                    }

                    Route.PlatformDetail -> {
                        val id = platformDetailId
                        if (id == null) {
                            route = Route.Settings
                        } else {
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
                                            Route.PlatformPicker
                                        } else {
                                            Route.Settings
                                        }
                                },
                                gamepadOwner = GamepadOwner.Primary,
                                onClaimGamepad = dualStore::claimGamepad,
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
                                route =
                                    if (platformDetailId != null) {
                                        Route.PlatformDetail
                                    } else {
                                        Route.Settings
                                    }
                            },
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad,
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
                                },
                            )
                            LaunchedEffect(id) {
                                dualStore.setGameDetailGameId(id)
                            }
                        }
                    }

                    Route.System -> {
                        SyncSystemHeroSnapshot(systemControls, dualStore)
                        val systemFocus = remember { FocusRequester() }
                        val backHome = {
                            viewModel.playBack()
                            route = Route.Home
                        }
                        WajihaScreen(
                            layerId = "system",
                            onBack = backHome,
                            showActionBar = true,
                            gamepadHints =
                                if (isDual) {
                                    quickSettingsGamepadHints +
                                        GamepadHint(GamepadHintButton.L2, "Focus screen")
                                } else {
                                    quickSettingsGamepadHints
                                },
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad,
                            onOwnerGainedFocus = { systemFocus.requestContentFocus() },
                        ) {
                            WajihaFolderSettingChrome(
                                onBack = backHome,
                                tabs = listOf("System"),
                                selectedIndex = 0,
                                onSelect = {},
                            ) {
                                QuickSettingsPanel(initialFocusRequester = systemFocus)
                            }
                        }
                    }

                    Route.NowRunning -> {
                        val nowRunningFocus = remember { FocusRequester() }
                        WajihaScreen(
                            layerId = "now_running",
                            showActionBar = true,
                            gamepadHints =
                                buildList {
                                    add(GamepadHint(GamepadHintButton.B, "Back"))
                                    if (isDual) {
                                        add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
                                    }
                                },
                            gamepadOwner = GamepadOwner.Primary,
                            onClaimGamepad = dualStore::claimGamepad,
                            onOwnerGainedFocus = { nowRunningFocus.requestContentFocus() },
                            backgroundContent =
                                if (settings.nowPlayingHeroBackground) {
                                    { NowPlayingBackdrop(state = nowPlaying) }
                                } else {
                                    null
                                },
                        ) {
                            WajihaFolderSettingChrome(
                                onBack = {
                                    viewModel.playBack()
                                    route = Route.Home
                                },
                                tabs = listOf("Now Running"),
                                selectedIndex = 0,
                                onSelect = {},
                                scrollable = false,
                                panelColor =
                                    if (settings.nowPlayingHeroBackground) {
                                        Color.Transparent
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                            ) {
                                NowPlayingPanel(
                                    state = nowPlaying,
                                    heroBackground = settings.nowPlayingHeroBackground,
                                    useLogo = settings.nowPlayingLogo,
                                    backgroundInParent = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .focusRequester(nowRunningFocus)
                                            .wajihaGamepadFocus(),
                                )
                            }
                        }
                    }

                    Route.Home -> {
                        if (isDual) {
                            sessionGridParams(Modifier.fillMaxSize())
                        } else if (settings.showHeroBanner) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxWidth().weight(0.42f)) {
                                    TopScreen(
                                        focused = focusedTile,
                                        platformName = platformName,
                                        heroContext = heroContext,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    HomeChromeBar(
                                        state = state,
                                        onSelectPlatform = viewModel::selectPlatform,
                                        onOpenCollections = viewModel::openCollections,
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
                                        overlayOnHero = true,
                                        showAppsAction = !settings.showHomeDock,
                                        showSettingsAction = !settings.showHomeDock,
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                    )
                                }
                                sessionGridParams(Modifier.fillMaxWidth().weight(0.58f))
                            }
                        } else {
                            sessionGridParams(Modifier.fillMaxSize())
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
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (!isDual && route == Route.Home && primaryShowsMenu) {
                NowPlayingOverlay(
                    store = dualStore,
                    modifier =
                        nowPlayingOverlayPlacement(
                            contentBottomInset = homeDockNowPlayingInset(settings.showHomeDock),
                        ),
                    onOpenNowPlaying = {
                        viewModel.playOpen()
                        route = Route.NowRunning
                    },
                )
            }
        }
    }
}
