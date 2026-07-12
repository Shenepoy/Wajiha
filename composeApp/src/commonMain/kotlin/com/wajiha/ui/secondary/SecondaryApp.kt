package com.wajiha.ui.secondary

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
            SecondarySetupWaiting()
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
        var gameDetailId by remember { mutableStateOf<Long?>(null) }
        val secondaryDisplayId by store.secondaryDisplayId.collectAsState()

        val openGameDetail: (Long) -> Unit = { id ->
            gameDetailId = id
            viewModel.playOpen()
            store.setGameDetailGameId(id)
            route = SecondaryRoute.GameDetail
        }

        // Swapped roles: menu lives on primary — keep secondary as hero-only.
        LaunchedEffect(secondaryShowsHero) {
            if (secondaryShowsHero && route != SecondaryRoute.Modes) {
                route = SecondaryRoute.Modes
            }
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

                SecondaryRoute.PlatformDetail, SecondaryRoute.PlatformPicker -> {
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
                                onBack = {
                                    viewModel.playBack()
                                    route = SecondaryRoute.Settings
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
                when (mode) {
                    // Tap the blacked-out screen to restore the grid
                    SecondaryMode.Off -> {
                        BlackoutScreen(onTap = backToGrid)
                    }

                    SecondaryMode.NowPlaying -> {
                        SecondaryModeFrame(
                            current = mode,
                            store = store,
                            sessionActive = sessionActive,
                        ) {
                            NowPlayingPanel(state = nowPlaying)
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
                            gamepadOwner = GamepadOwner.Secondary,
                            onClaimGamepad = store::claimGamepad,
                        )
                    }

                    SecondaryMode.RunningApps -> {
                        SecondaryModeFrame(
                            current = mode,
                            store = store,
                            sessionActive = sessionActive,
                        ) {
                            RunningAppsPanel(showGamepadHints = false)
                        }
                    }

                    SecondaryMode.QuickSettings -> {
                        SecondaryModeFrame(
                            current = mode,
                            store = store,
                            sessionActive = sessionActive,
                        ) {
                            QuickSettingsPanel(showGamepadHints = false)
                        }
                    }

                    SecondaryMode.Achievements -> {
                        SecondaryModeFrame(
                            current = mode,
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
                            gamepadHints = listOf("B" to "Games", "L2" to "Focus screen"),
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

/** Header with back-to-grid plus tabs so every mode stays reachable. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SecondaryModeFrame(
    current: SecondaryMode,
    store: DualScreenStore,
    sessionActive: Boolean,
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
            SecondaryMode.NowPlaying -> listOf("B" to "Games", "L1/R1" to "Tab")
            SecondaryMode.RunningApps -> runningAppsGamepadHints + ("L1/R1" to "Tab")
            SecondaryMode.QuickSettings -> quickSettingsGamepadHints + listOf("B" to "Games", "L1/R1" to "Tab")
            SecondaryMode.Achievements -> listOf("B" to "Games", "L1/R1" to "Tab")
            else -> secondaryModeTabGamepadHints
        }
    val contentFocus = remember { FocusRequester() }
    WajihaScreen(
        layerId = "secondary_mode_${current.name}",
        showActionBar = true,
        gamepadHints = modeHints + ("L2" to "Focus screen"),
        gamepadOwner = GamepadOwner.Secondary,
        onClaimGamepad = store::claimGamepad,
        onOwnerGainedFocus = { contentFocus.requestContentFocus() },
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
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
private fun SecondarySetupWaiting() {
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
                text = "Bottom Screen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Getting ready…",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "Finish the quick tour on the top screen.\n" +
                        "Your library will land here when you're done.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
