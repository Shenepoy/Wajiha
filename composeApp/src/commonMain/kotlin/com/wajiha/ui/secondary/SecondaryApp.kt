package com.wajiha.ui.secondary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import com.wajiha.state.DualScreenStore
import com.wajiha.state.LauncherPanel
import com.wajiha.state.SecondaryMode
import com.wajiha.ui.apps.AppDrawerScreen
import com.wajiha.ui.home.BottomScreen
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.home.TopScreen
import com.wajiha.ui.ra.AchievementsPanel
import com.wajiha.ui.running.RunningAppsPanel
import com.wajiha.ui.scraper.ScraperScreen
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.settings.PlatformPickerScreen
import com.wajiha.ui.settings.PlatformSettingsScreen
import com.wajiha.ui.settings.SettingsScreen
import com.wajiha.ui.settings.SettingsViewModel
import com.wajiha.ui.system.QuickSettingsPanel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.wajiha.platform.SystemControls
import com.wajiha.ui.theme.WajihaTheme
import com.wajiha.ui.theme.themeIsDark
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
    Scraper
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

    WajihaTheme(darkTheme = themeIsDark(settings.theme)) {
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
        val mode by store.secondaryMode.collectAsState()
        val heroContext by store.heroContext.collectAsState()
        val nowPlaying by store.nowPlaying.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val apps by viewModel.apps.collectAsState()
        val backToGrid = { store.setSecondaryMode(SecondaryMode.GameGrid) }

        var route by remember { mutableStateOf(SecondaryRoute.Modes) }
        var platformDetailId by remember { mutableStateOf<String?>(null) }

        // Dual-display: secondary owns gamepad while on settings/apps modes
        // (Games grid ownership comes from gamesMenuOnPrimary default).
        LaunchedEffect(route, mode) {
            store.setSecondaryHoldsGamepad(
                route != SecondaryRoute.Modes || mode != SecondaryMode.GameGrid
            )
            val panel = when {
                route == SecondaryRoute.Settings ||
                    route == SecondaryRoute.PlatformPicker ||
                    route == SecondaryRoute.PlatformDetail ||
                    route == SecondaryRoute.Scraper -> LauncherPanel.Settings
                mode == SecondaryMode.AppDock -> LauncherPanel.Apps
                mode == SecondaryMode.QuickSettings -> LauncherPanel.System
                else -> LauncherPanel.GameLibrary
            }
            store.setSecondaryLauncherPanel(panel)
            if (panel == LauncherPanel.System) {
                systemControls.refreshStatus()
                val status = systemControls.status.value
                store.setSystemHeroSnapshot(
                    batteryPercent = status.batteryPercent,
                    charging = status.charging,
                    wifiEnabled = status.wifiEnabled
                )
            }
        }

        // Gamepad B / system back: scraper/platform → settings → modes → game grid
        BackHandler(enabled = route != SecondaryRoute.Modes || mode != SecondaryMode.GameGrid) {
            viewModel.playBack()
            when (route) {
                SecondaryRoute.Scraper ->
                    route = if (platformDetailId != null) {
                        SecondaryRoute.PlatformDetail
                    } else {
                        SecondaryRoute.Settings
                    }
                SecondaryRoute.PlatformDetail, SecondaryRoute.PlatformPicker ->
                    route = SecondaryRoute.Settings
                SecondaryRoute.Settings -> route = SecondaryRoute.Modes
                else -> backToGrid()
            }
        }

        when (route) {
            SecondaryRoute.Settings -> {
                Box(modifier = Modifier.fillMaxSize()) {
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
                        onSectionChange = store::setSettingsSectionLabel
                    )
                    NowPlayingOverlay(
                        store = store,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
                return@WajihaTheme
            }
            SecondaryRoute.PlatformPicker -> {
                Box(modifier = Modifier.fillMaxSize()) {
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
                        }
                    )
                    NowPlayingOverlay(
                        store = store,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlatformSettingsScreen(
                            platformId = id,
                            onBack = {
                                viewModel.playBack()
                                route = SecondaryRoute.Settings
                            },
                            onOpenScraper = {
                                viewModel.playOpen()
                                route = SecondaryRoute.Scraper
                            }
                        )
                        NowPlayingOverlay(
                            store = store,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                    LaunchedEffect(Unit) {
                        store.setSettingsSectionLabel("Library")
                    }
                }
                return@WajihaTheme
            }
            SecondaryRoute.Scraper -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    ScraperScreen(
                        viewModel = koinInject<ScraperViewModel>(),
                        onBack = {
                            viewModel.playBack()
                            route = if (platformDetailId != null) {
                                SecondaryRoute.PlatformDetail
                            } else {
                                SecondaryRoute.Settings
                            }
                        }
                    )
                    NowPlayingOverlay(
                        store = store,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
                LaunchedEffect(Unit) {
                    store.setSettingsSectionLabel("Scraper")
                }
                return@WajihaTheme
            }
            SecondaryRoute.Modes -> Unit
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (mode) {
            // Tap the blacked-out screen to restore the grid
            SecondaryMode.Off -> BlackoutScreen(onTap = backToGrid)
            SecondaryMode.NowPlaying -> IdleDimHost(enabled = true) {
                SecondaryModeFrame(mode, store) {
                    NowPlayingScreen(
                        packageName = nowPlaying?.packageName,
                        gameName = nowPlaying?.gameName ?: nowPlaying?.appLabel
                    )
                }
            }
            SecondaryMode.GameGrid -> if (settings.swapScreenRoles) {
                // Swapped roles: this (bottom) display shows the hero instead
                val focusedGameId by store.focusedGameId.collectAsState()
                val focusedTile = state.tiles.firstOrNull { it.game.id == focusedGameId }
                    ?: state.recent.firstOrNull()
                TopScreen(
                    focused = focusedTile,
                    platformName = focusedTile?.let { tile ->
                        state.platforms.firstOrNull { it.id == tile.game.platformId }?.name
                    },
                    heroContext = heroContext
                )
            } else {
                BottomScreen(
                    state = state,
                    gridRows = settings.gridRows,
                    onSelectPlatform = viewModel::selectPlatform,
                    onFocusGame = viewModel::focusGame,
                    onLaunchGame = viewModel::launchGame,
                    onOpenApps = { store.setSecondaryMode(SecondaryMode.AppDock) },
                    onOpenSettings = {
                        viewModel.playOpen()
                        route = SecondaryRoute.Settings
                    },
                    onOpenSystem = { store.setSecondaryMode(SecondaryMode.QuickSettings) },
                    onAddGames = {
                        viewModel.playOpen()
                        route = SecondaryRoute.PlatformPicker
                    }
                )
            }
            SecondaryMode.AppDock -> AppDrawerScreen(
                apps = apps,
                onLoad = viewModel::loadApps,
                onLaunch = viewModel::launchApp,
                onBack = backToGrid,
                onFocusChange = { app ->
                    store.setAppsHeroDetail(apps.size, app?.label)
                }
            )
            SecondaryMode.RunningApps -> SecondaryModeFrame(mode, store) {
                RunningAppsPanel()
            }
            SecondaryMode.QuickSettings -> SecondaryModeFrame(mode, store) {
                QuickSettingsPanel()
            }
            SecondaryMode.Achievements -> SecondaryModeFrame(mode, store) {
                AchievementsPanel()
            }
            SecondaryMode.Clock -> SecondaryModeFrame(mode, store) {
                ClockScreen()
            }
            }
            NowPlayingOverlay(
                store = store,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

/** Header with back-to-grid plus tabs so every mode stays reachable. */
@Composable
private fun SecondaryModeFrame(
    current: SecondaryMode,
    store: DualScreenStore,
    content: @Composable () -> Unit
) {
    val tabs = listOf(
        SecondaryMode.RunningApps to "Running",
        SecondaryMode.QuickSettings to "System",
        SecondaryMode.Achievements to "Trophies"
    )
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { store.setSecondaryMode(SecondaryMode.GameGrid) }) {
                Text("< Games")
            }
            tabs.forEach { (mode, label) ->
                TextButton(onClick = { store.setSecondaryMode(mode) }) {
                    Text(
                        text = label,
                        color = if (mode == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun BlackoutScreen(onTap: () -> Unit) {
    // Pure black; on OLED-class panels this effectively turns the display off
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = null, indication = null, onClick = onTap)
    )
}

@Composable
private fun NowPlayingScreen(packageName: String?, gameName: String?) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = gameName ?: packageName ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Soft-dims the secondary surface after [idleMs] with no local interaction.
 * Touch or key on this host wakes immediately and restarts the timer.
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun IdleDimHost(
    enabled: Boolean,
    idleMs: Long = 10_000L,
    content: @Composable () -> Unit
) {
    var lastInteractionMs by remember {
        mutableLongStateOf(Clock.System.now().toEpochMilliseconds())
    }
    var dimmed by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, lastInteractionMs) {
        if (!enabled) {
            dimmed = false
            return@LaunchedEffect
        }
        dimmed = false
        val remaining = idleMs - (Clock.System.now().toEpochMilliseconds() - lastInteractionMs)
        if (remaining > 0) delay(remaining)
        if (enabled) dimmed = true
    }

    val dimAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.92f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "nowPlayingDim"
    )

    fun bump() {
        lastInteractionMs = Clock.System.now().toEpochMilliseconds()
        dimmed = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                bump()
                false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        bump()
                        tryAwaitRelease()
                    }
                )
            }
    ) {
        content()
        if (dimAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { bump() }
                    )
            )
        }
    }
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
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = now,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun currentTimeText(): String {
    val dateTime = kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val h = dateTime.hour.toString().padStart(2, '0')
    val m = dateTime.minute.toString().padStart(2, '0')
    return "$h:$m"
}

/** Cocoon-style: quiet bottom screen while setup talks on the top display. */
@Composable
private fun SecondarySetupWaiting() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Bottom Screen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Getting ready…",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Finish the quick tour on the top screen.\n" +
                    "Your library will land here when you're done.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
