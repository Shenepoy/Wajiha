package com.wajiha

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.LauncherPanel
import com.wajiha.ui.apps.AppDrawerScreen
import com.wajiha.ui.onboarding.OnboardingScreen
import com.wajiha.ui.home.BottomScreen
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.home.TopScreen
import com.wajiha.ui.scraper.ScraperScreen
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.settings.PlatformPickerScreen
import com.wajiha.ui.settings.PlatformSettingsScreen
import com.wajiha.ui.settings.SettingsScreen
import com.wajiha.ui.settings.SettingsViewModel
import com.wajiha.ui.system.QuickSettingsPanel
import com.wajiha.ui.theme.WajihaTheme
import com.wajiha.ui.theme.themeIsDark
import org.koin.compose.koinInject

private enum class Route {
    Home,
    Apps,
    Settings,
    PlatformPicker,
    PlatformDetail,
    Scraper,
    System
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

    WajihaTheme(darkTheme = themeIsDark(settings.theme)) {
        val state by viewModel.uiState.collectAsState()
        val screenState by viewModel.dualScreenStore.state.collectAsState()
        val focusedGameId by viewModel.dualScreenStore.focusedGameId.collectAsState()
        val heroContext by viewModel.dualScreenStore.heroContext.collectAsState()
        val apps by viewModel.apps.collectAsState()

        var route by remember { mutableStateOf(Route.Home) }
        var platformDetailId by remember { mutableStateOf<String?>(null) }
        var onboardingDismissed by remember { mutableStateOf(false) }
        val settingsLoaded by settingsViewModel.settingsLoaded.collectAsState()

        if (!settingsLoaded) {
            // Blank frame until DataStore emits, so onboarding never flashes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            )
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

        // Dual-display gamepad ownership: primary holds keys only for
        // onboarding / non-home routes / swapped games grid.
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
                Route.Apps -> LauncherPanel.Apps
                Route.System -> LauncherPanel.System
                Route.Home -> LauncherPanel.GameLibrary
            }
            dualStore.setPrimaryLauncherPanel(panel)
        }

        val focusedTile = state.tiles.firstOrNull { it.game.id == focusedGameId }
            ?: state.recent.firstOrNull()
        val platformName = focusedTile?.let { tile ->
            state.platforms.firstOrNull { it.id == tile.game.platformId }?.name
        }

        // Gamepad B / system back walks routes back toward Home
        BackHandler(enabled = route != Route.Home) {
            viewModel.playBack()
            route = when (route) {
                Route.Scraper ->
                    if (platformDetailId != null) Route.PlatformDetail else Route.Settings
                Route.PlatformDetail -> Route.Settings
                Route.PlatformPicker -> Route.Settings
                else -> Route.Home
            }
        }

        when (route) {
            Route.Apps -> AppDrawerScreen(
                apps = apps,
                onLoad = viewModel::loadApps,
                onLaunch = viewModel::launchApp,
                onBack = {
                    viewModel.playBack()
                    route = Route.Home
                },
                onFocusChange = { app ->
                    dualStore.setAppsHeroDetail(apps.size, app?.label)
                }
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
                        },
                        onOpenScraper = {
                            viewModel.playOpen()
                            route = Route.Scraper
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
                Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.playBack()
                        route = Route.Home
                    }) { Text("< Back") }
                    QuickSettingsPanel(modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
            Route.Home -> {
                if (isDual) {
                    if (settings.swapScreenRoles) {
                        // Swapped roles: grid on the top display
                        BottomScreen(
                            state = state,
                            gridRows = settings.gridRows,
                            onSelectPlatform = viewModel::selectPlatform,
                            onFocusGame = viewModel::focusGame,
                            onLaunchGame = viewModel::launchGame,
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
                            }
                        )
                    } else {
                        // Top display: hero only, grid is on the secondary screen
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
                            modifier = Modifier.fillMaxWidth().weight(0.58f)
                        )
                    }
                }
            }
        }

        state.launchError?.let { error ->
            LaunchedEffect(error) {
                delay(4000)
                viewModel.dismissLaunchError()
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(error) }
            }
        }
    }
}
