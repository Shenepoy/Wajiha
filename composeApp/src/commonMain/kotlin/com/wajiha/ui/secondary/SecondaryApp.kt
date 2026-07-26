package com.wajiha.ui.secondary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.wajiha.platform.AppActions
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.state.LauncherPanel
import com.wajiha.state.MenuDestination
import com.wajiha.state.MenuRouteSnapshot
import com.wajiha.state.SecondaryMode
import com.wajiha.state.SecondaryRoute
import com.wajiha.ui.apps.AppDrawerScreen
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaFolderSettingChrome
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.quickSettingsGamepadHints
import com.wajiha.ui.components.gamepad.runningAppsGamepadHints
import com.wajiha.ui.components.gamepad.secondaryModeTabGamepadHints
import com.wajiha.ui.gamedetail.GameDetailScreen
import com.wajiha.ui.home.BottomScreen
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.navigation.LauncherHeroPane
import com.wajiha.ui.navigation.heroOnSecondary
import com.wajiha.ui.navigation.menuOnSecondary
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

private const val MODE_CROSSFADE_MS = 220

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

        val navigation by store.secondaryNavigation.collectAsState()
        val route = navigation.route
        val platformDetailId = navigation.platformDetailId
        val platformDetailFromPicker = navigation.platformDetailFromPicker
        val gameDetailId = navigation.gameDetailId
        val secondaryDisplayId by store.secondaryDisplayId.collectAsState()

        val openGameDetail: (Long) -> Unit = { id ->
            store.updateSecondaryNavigation {
                it.copy(route = SecondaryRoute.GameDetail, gameDetailId = id)
            }
            viewModel.playOpen()
            store.setGameDetailGameId(id)
        }

        // SELECT swap: DualScreenStore.onScreenRolesSwapped adopts the snapshot
        // onto secondary mode/nav before the swap pref recomposes. Keep an
        // ownership-edge backup here for any path that flips roles without that
        // call; clear local route when becoming hero (leave snapshot).
        var prevSecondaryShowsMenu by remember { mutableStateOf(secondaryShowsMenu) }
        if (prevSecondaryShowsMenu != secondaryShowsMenu) {
            prevSecondaryShowsMenu = secondaryShowsMenu
            if (secondaryShowsMenu) {
                store.adoptMenuSnapshotOntoSecondary()
            } else if (route != SecondaryRoute.Modes) {
                store.updateSecondaryNavigation { it.copy(route = SecondaryRoute.Modes) }
            }
        }

        LaunchedEffect(secondaryShowsMenu) {
            if (!secondaryShowsMenu) return@LaunchedEffect
            store.openSettingsRequests.collect {
                viewModel.playOpen()
                store.updateSecondaryNavigation { it.copy(route = SecondaryRoute.Settings) }
            }
        }

        LaunchedEffect(secondaryShowsMenu) {
            if (!secondaryShowsMenu) return@LaunchedEffect
            store.openAppsRequests.collect {
                viewModel.playOpen()
                store.updateSecondaryNavigation { it.copy(route = SecondaryRoute.Modes) }
                store.setSecondaryMode(SecondaryMode.AppDock)
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
                    store.updateSecondaryNavigation {
                        it.copy(route = SecondaryRoute.Modes, gameDetailId = null)
                    }
                }

                SecondaryRoute.Scraper -> {
                    store.updateSecondaryNavigation {
                        it.copy(
                            route =
                                if (platformDetailId != null) {
                                    SecondaryRoute.PlatformDetail
                                } else {
                                    SecondaryRoute.Settings
                                },
                        )
                    }
                }

                SecondaryRoute.PlatformDetail -> {
                    store.updateSecondaryNavigation {
                        it.copy(
                            route =
                                if (platformDetailFromPicker) {
                                    SecondaryRoute.PlatformPicker
                                } else {
                                    SecondaryRoute.Settings
                                },
                        )
                    }
                }

                SecondaryRoute.PlatformPicker -> {
                    store.updateSecondaryNavigation { it.copy(route = SecondaryRoute.Settings) }
                }

                SecondaryRoute.Settings -> {
                    store.updateSecondaryNavigation { it.copy(route = SecondaryRoute.Modes) }
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
                                store.updateSecondaryNavigation {
                                    it.copy(route = SecondaryRoute.Modes)
                                }
                            },
                            onAddPlatform = {
                                viewModel.playOpen()
                                store.updateSecondaryNavigation {
                                    it.copy(route = SecondaryRoute.PlatformPicker)
                                }
                            },
                            onOpenPlatform = { id ->
                                store.updateSecondaryNavigation {
                                    it.copy(
                                        route = SecondaryRoute.PlatformDetail,
                                        platformDetailId = id,
                                        platformDetailFromPicker = false,
                                    )
                                }
                                viewModel.playOpen()
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
                                store.updateSecondaryNavigation {
                                    it.copy(route = SecondaryRoute.Settings)
                                }
                            },
                            onPick = { id ->
                                store.updateSecondaryNavigation {
                                    it.copy(
                                        route = SecondaryRoute.PlatformDetail,
                                        platformDetailId = id,
                                        platformDetailFromPicker = true,
                                    )
                                }
                                viewModel.playOpen()
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
                        store.updateSecondaryNavigation {
                            it.copy(route = SecondaryRoute.Settings)
                        }
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
                                    store.updateSecondaryNavigation {
                                        it.copy(
                                            route =
                                                if (platformDetailFromPicker) {
                                                    SecondaryRoute.PlatformPicker
                                                } else {
                                                    SecondaryRoute.Settings
                                                },
                                        )
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
                                store.updateSecondaryNavigation {
                                    it.copy(
                                        route =
                                            if (platformDetailId != null) {
                                                SecondaryRoute.PlatformDetail
                                            } else {
                                                SecondaryRoute.Settings
                                            },
                                    )
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
                        store.updateSecondaryNavigation {
                            it.copy(route = SecondaryRoute.Modes)
                        }
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
                                    store.updateSecondaryNavigation {
                                        it.copy(route = SecondaryRoute.Modes, gameDetailId = null)
                                    }
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
                    modifier =
                        nowPlayingOverlayPlacement(
                            contentBottomInset =
                                homeDockNowPlayingInset(
                                    showHomeDock =
                                        settings.showHomeDock &&
                                            mode == SecondaryMode.GameGrid,
                                ),
                        ),
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
                        // Crossfade over the opaque backdrop. Instant swap read as a
                        // hard flash; Activity Compose stays mounted under the live
                        // Overlay so mid-fade no longer hits an empty window.
                        transitionSpec = {
                            fadeIn(tween(MODE_CROSSFADE_MS)) togetherWith
                                fadeOut(tween(MODE_CROSSFADE_MS))
                        },
                        label = "secondaryMode",
                        modifier = Modifier.fillMaxSize(),
                    ) { animatedMode ->
                        when (animatedMode) {
                            // Tap the blacked-out screen to restore the grid
                            SecondaryMode.Off -> {
                                BlackoutScreen(onTap = backToGrid)
                            }

                            SecondaryMode.NowPlaying,
                            SecondaryMode.Achievements,
                            -> {
                                SecondaryModeFrame(
                                    current = SecondaryMode.NowPlaying,
                                    store = store,
                                    sessionActive = sessionActive,
                                    backgroundContent =
                                        if (settings.nowPlayingHeroBackground) {
                                            { NowPlayingBackdrop(state = nowPlaying) }
                                        } else {
                                            null
                                        },
                                ) { _ ->
                                    NowPlayingPanel(
                                        state = nowPlaying,
                                        heroBackground = settings.nowPlayingHeroBackground,
                                        useLogo = settings.nowPlayingLogo,
                                        backgroundInParent = true,
                                        modifier = Modifier.fillMaxSize(),
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
                                    gridRows = settings.gameGridSecondaryRows,
                                    gameGridArt = settings.gameGridSecondaryArt,
                                    gameGridTileSize = settings.gameGridSecondaryTileSize,
                                    gameGridShowTitles = settings.gameGridSecondaryShowTitles,
                                    gameGridShowTileChrome = settings.gameGridSecondaryShowTileChrome,
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
                                        store.updateSecondaryNavigation {
                                            it.copy(route = SecondaryRoute.Settings)
                                        }
                                    },
                                    onOpenSystem = { store.setSecondaryMode(SecondaryMode.QuickSettings) },
                                    onAddGames = {
                                        viewModel.playOpen()
                                        store.updateSecondaryNavigation {
                                            it.copy(route = SecondaryRoute.PlatformPicker)
                                        }
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
                                    iconShape = settings.iconShape,
                                    gridColumns = settings.appDrawerSecondaryColumns,
                                    gridRows = settings.appDrawerSecondaryRows,
                                    iconSizePreference = settings.appDrawerSecondaryIconSize,
                                    gridOrientation = settings.appDrawerSecondaryOrientation,
                                    gridScrollMode = settings.appDrawerSecondaryScrollMode,
                                    showAppLabels = settings.appDrawerSecondaryShowLabels,
                                    favoritePackages = settings.appDrawerFavoritePackages,
                                    onGridColumnsChange = settingsViewModel::setAppDrawerSecondaryColumns,
                                    onGridRowsChange = settingsViewModel::setAppDrawerSecondaryRows,
                                    onIconSizeChange = settingsViewModel::setAppDrawerSecondaryIconSize,
                                    onGridOrientationChange = settingsViewModel::setAppDrawerSecondaryOrientation,
                                    onGridScrollModeChange = settingsViewModel::setAppDrawerSecondaryScrollMode,
                                    onShowAppLabelsChange = settingsViewModel::setAppDrawerSecondaryShowLabels,
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

                            SecondaryMode.RunningApps -> {
                                SecondaryModeFrame(
                                    current = animatedMode,
                                    store = store,
                                    sessionActive = sessionActive,
                                ) { contentFocus ->
                                    RunningAppsPanel(initialFocusRequester = contentFocus)
                                }
                            }

                            SecondaryMode.QuickSettings -> {
                                SecondaryModeFrame(
                                    current = animatedMode,
                                    store = store,
                                    sessionActive = sessionActive,
                                ) { contentFocus ->
                                    QuickSettingsPanel(initialFocusRequester = contentFocus)
                                }
                            }

                            SecondaryMode.Clock -> {
                                val backToClockGrid = { store.setSecondaryMode(SecondaryMode.GameGrid) }
                                WajihaScreen(
                                    layerId = "secondary_clock",
                                    onBack = backToClockGrid,
                                    showActionBar = true,
                                    gamepadHints =
                                        listOf(
                                            GamepadHint(GamepadHintButton.B, "Back"),
                                            GamepadHint(GamepadHintButton.L2, "Focus screen"),
                                        ),
                                    gamepadOwner = GamepadOwner.Secondary,
                                    onClaimGamepad = store::claimGamepad,
                                ) {
                                    WajihaFolderSettingChrome(
                                        onBack = backToClockGrid,
                                        tabs = listOf("Clock"),
                                        selectedIndex = 0,
                                        onSelect = {},
                                        scrollable = false,
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.background),
                                    ) {
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

/** Folder chrome + L1/R1 tabs for secondary System / Running / Now Running. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SecondaryModeFrame(
    current: SecondaryMode,
    store: DualScreenStore,
    sessionActive: Boolean,
    backgroundContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(contentFocus: FocusRequester) -> Unit,
) {
    val tabs =
        buildList {
            if (sessionActive) {
                add(SecondaryMode.NowPlaying to "Now Running")
            }
            add(SecondaryMode.RunningApps to "Running")
            add(SecondaryMode.QuickSettings to "System")
        }
    val currentIndex = tabs.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val feedback = LocalUiFeedback.current
    val backToGrid = { store.setSecondaryMode(SecondaryMode.GameGrid) }

    fun selectTab(index: Int) {
        feedback.tabSelect(currentIndex, index)
        tabs.getOrNull(index)?.first?.let { store.setSecondaryMode(it) }
    }

    val modeHints =
        when (current) {
            SecondaryMode.RunningApps -> {
                runningAppsGamepadHints + GamepadHint(GamepadHintButton.L1R1, "Tab")
            }

            SecondaryMode.QuickSettings -> {
                quickSettingsGamepadHints + GamepadHint(GamepadHintButton.L1R1, "Tab")
            }

            else -> {
                secondaryModeTabGamepadHints
            }
        }
    val contentFocus = remember { FocusRequester() }
    WajihaScreen(
        layerId = "secondary_mode_${current.name}",
        onBack = backToGrid,
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
        WajihaFolderSettingChrome(
            onBack = backToGrid,
            tabs = tabs.map { it.second },
            selectedIndex = currentIndex,
            onSelect = ::selectTab,
            scrollable = current == SecondaryMode.QuickSettings,
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
            content(contentFocus)
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

@OptIn(ExperimentalTime::class)
@Composable
private fun ClockScreen() {
    var now by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentTimeText()
            val dateTime =
                Clock.System
                    .now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
            val msIntoMinute = dateTime.second * 1000 + dateTime.nanosecond / 1_000_000
            delay((60_000 - msIntoMinute).coerceAtLeast(1).milliseconds)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = now,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun currentTimeText(): String {
    val dateTime =
        Clock.System
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
