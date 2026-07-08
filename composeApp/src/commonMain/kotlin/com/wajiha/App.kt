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
import androidx.compose.ui.backhandler.BackHandler
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
import com.wajiha.ui.secondary.NowPlayingPanel
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
        val nowPlayingUi by viewModel.dualScreenStore.nowPlayingUiState.collectAsState()
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
                }
            )
            return@WajihaTheme
        }

        LaunchedEffect(route, isDual, settings.swapScreenRoles) {
            dualStore.setGamesMenuOnPrimary(!isDual || settings.swapScreenRoles)
            dualStore.setPrimaryHoldsGamepad(
                route != Route.Home || (!isDual) || settings.swapScreenRoles
            )
        }

        LaunchedEffect(route) {
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
        }

        val openGameDetail: (Long) -> Unit = { id ->
            gameDetailId = id
            viewModel.playOpen()
            dualStore.setGameDetailGameId(id)
            route = Route.GameDetail
        }

        val focusedTile = state.tiles.firstOrNull { it.game.id == focusedGameId }
            ?: state.recent.firstOrNull()
        val platformName = focusedTile?.let { tile ->
            state.platforms.firstOrNull { it.id == tile.game.platformId }?.name
        }

        LaunchedEffect(nowPlayingUi, route) {
            if (nowPlayingUi == null && route == Route.NowRunning) {
                route = Route.Home
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
            when (route) {
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
                    onSectionChange = dualStore::setSettingsSectionLabel
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
                    }
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
                            }
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
                        }
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
                    LaunchedEffect(Unit) {
                        systemControls.refreshStatus()
                        val status = systemControls.status.value
                        dualStore.setSystemHeroSnapshot(
                            batteryPercent = status.batteryPercent,
                            charging = status.charging,
                            wifiEnabled = status.wifiEnabled
                        )
                    }
                    WajihaScreen(
                        layerId = "system",
                        onBack = {
                            viewModel.playBack()
                            route = Route.Home
                        },
                        showActionBar = true,
                        gamepadHints = quickSettingsGamepadHints
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
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                showGamepadHints = false
                            )
                        }
                    }
                }
                Route.NowRunning -> WajihaScreen(
                    layerId = "now_running",
                    showActionBar = true,
                    gamepadHints = listOf("B" to "Back")
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
                            state = nowPlayingUi,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                }
                Route.Home -> {
                    if (isDual) {
                        if (settings.swapScreenRoles) {
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
                                onAddGames = {
                                    viewModel.playOpen()
                                    route = Route.PlatformPicker
                                },
                                gamepadOwner = GamepadOwner.Primary,
                                onClaimGamepad = dualStore::claimGamepad
                            )
                        } else {
                            TopScreen(
                                focused = focusedTile,
                                platformName = platformName,
                                heroContext = heroContext
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TopScreen(
                                focused = focusedTile,
                                platformName = platformName,
                                heroContext = heroContext,
                                modifier = Modifier.fillMaxWidth().weight(0.42f)
                            )
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
                                modifier = Modifier.fillMaxWidth().weight(0.58f)
                            )
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
