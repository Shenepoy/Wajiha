package com.wajiha.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/** Overall engine state (see plan state diagram). */
enum class DualScreenState {
    /** Only one display — combined layout. */
    SingleDisplay,

    /** Two displays, browsing the launcher. */
    DualBrowsing,

    /** A game/emulator is in the foreground (launched by us or detected). */
    GameRunning,

    /** User sent a regular app to the secondary display. */
    AppOnSecondary,

    /** Game running and the unused display is blacked out. */
    BlackoutSecondary
}

/** What the secondary (bottom) screen shows, user-selectable per state. */
enum class SecondaryMode {
    GameGrid,
    NowPlaying,
    AppDock,
    RunningApps,
    QuickSettings,
    Achievements,
    Clock,
    Off
}

/** Now Playing info pushed to the secondary screen (even for manual launches). */
data class NowPlayingState(
    val packageName: String,
    val appLabel: String? = null,
    val gameId: Long? = null,
    val gameName: String? = null,
    val platformId: String? = null,
    val boxartPath: String? = null,
    val heroPath: String? = null,
    val sessionStartedAt: Long = 0,
    /** true when launched from Wajiha, false when detected externally */
    val launchedByWajiha: Boolean = true
)

data class RunningApp(
    val packageName: String,
    val label: String,
    val lastUsedAt: Long,
    val isGame: Boolean
)

/**
 * Single source of truth for the dual-screen engine. Both activities run in
 * one process and observe these flows — no IPC needed (unlike NeoStation's
 * dual Flutter engines).
 */
class DualScreenStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(DualScreenState.SingleDisplay)
    val state: StateFlow<DualScreenState> = _state.asStateFlow()

    private val _secondaryMode = MutableStateFlow(SecondaryMode.GameGrid)
    val secondaryMode: StateFlow<SecondaryMode> = _secondaryMode.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
    val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying.asStateFlow()

    private val _runningApps = MutableStateFlow<List<RunningApp>>(emptyList())
    val runningApps: StateFlow<List<RunningApp>> = _runningApps.asStateFlow()

    private val _secondaryDisplayId = MutableStateFlow<Int?>(null)
    val secondaryDisplayId: StateFlow<Int?> = _secondaryDisplayId.asStateFlow()

    /** Focused game on the primary grid (drives top-screen hero). */
    private val _focusedGameId = MutableStateFlow<Long?>(null)
    val focusedGameId: StateFlow<Long?> = _focusedGameId.asStateFlow()

    private val _primaryPanel = MutableStateFlow(LauncherPanel.GameLibrary)
    private val _secondaryPanel = MutableStateFlow(LauncherPanel.GameLibrary)
    private val _settingsSectionLabel = MutableStateFlow<String?>(null)
    private val _appsHeroCount = MutableStateFlow(0)
    private val _appsHeroFocusedLabel = MutableStateFlow<String?>(null)
    private val _systemHeroSnapshot = MutableStateFlow(HeroContext.System())

    /** Resolved hero for whichever display is showing [TopScreen]. */
    val heroContext: StateFlow<HeroContext> = combine(
        combine(
            _primaryPanel,
            _secondaryPanel,
            _settingsSectionLabel,
            _appsHeroCount,
            _appsHeroFocusedLabel
        ) { primary, secondary, section, appCount, focusedApp ->
            HeroInputs(primary, secondary, section, appCount, focusedApp)
        },
        _systemHeroSnapshot
    ) { inputs, system ->
        val panel = when {
            inputs.primary != LauncherPanel.GameLibrary -> inputs.primary
            inputs.secondary != LauncherPanel.GameLibrary -> inputs.secondary
            else -> LauncherPanel.GameLibrary
        }
        when (panel) {
            LauncherPanel.GameLibrary -> HeroContext.GameLibrary
            LauncherPanel.Settings -> HeroContext.Settings(sectionLabel = inputs.section)
            LauncherPanel.Apps -> HeroContext.Apps(
                appCount = inputs.appCount,
                focusedLabel = inputs.focusedApp
            )
            LauncherPanel.System -> system
        }
    }.stateIn(scope, SharingStarted.Eagerly, HeroContext.GameLibrary)

    /**
     * Activity that should receive gamepad keys while dual-display is active.
     * Default [GamepadOwner.Secondary] — games menu is normally on the bottom screen.
     */
    private val _gamepadOwner = MutableStateFlow(GamepadOwner.Secondary)
    val gamepadOwner: StateFlow<GamepadOwner> = _gamepadOwner.asStateFlow()

    /** Primary (top) is showing onboarding or a non-home route (Settings/Apps/…). */
    private var primaryHoldsInput: Boolean = false

    /** Secondary is showing Settings/Scraper or a non-grid mode (Apps/System/…). */
    private var secondaryHoldsInput: Boolean = false

    /** When true, the games grid lives on the primary display instead of secondary. */
    private var gamesMenuOnPrimary: Boolean = false

    // Options (mirrored from settings so state transitions can use them synchronously)
    var blackoutOnLaunch: Boolean = false
    var preferredGameMode: SecondaryMode = SecondaryMode.NowPlaying

    // ---- Events (the state machine) ----

    fun onDisplaysChanged(secondaryDisplayId: Int?) {
        _secondaryDisplayId.value = secondaryDisplayId
        _state.update { current ->
            when {
                secondaryDisplayId == null -> DualScreenState.SingleDisplay
                current == DualScreenState.SingleDisplay -> DualScreenState.DualBrowsing
                else -> current
            }
        }
        recomputeGamepadOwner()
    }

    fun onGameStarted(nowPlaying: NowPlayingState) {
        _nowPlaying.value = nowPlaying
        if (_state.value == DualScreenState.SingleDisplay) return
        _state.value =
            if (blackoutOnLaunch) DualScreenState.BlackoutSecondary
            else DualScreenState.GameRunning
        _secondaryMode.value =
            if (blackoutOnLaunch) SecondaryMode.Off else preferredGameMode
    }

    /** Foreground monitor detected an emulator/game not launched by us. */
    fun onGameDetected(nowPlaying: NowPlayingState) {
        onGameStarted(nowPlaying.copy(launchedByWajiha = false))
    }

    /** Update overlay/full-screen metadata without changing [secondaryMode]. */
    fun updateNowPlaying(state: NowPlayingState) {
        val previous = _nowPlaying.value
        _nowPlaying.value = state
        if (previous?.packageName != state.packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "updateNowPlaying: ${previous?.packageName ?: "none"} -> ${state.packageName} " +
                    "(${state.appLabel ?: state.gameName ?: "unlabeled"})"
            )
        }
        if (_state.value == DualScreenState.SingleDisplay) return
        if (_state.value == DualScreenState.DualBrowsing) {
            _state.value = DualScreenState.GameRunning
        }
    }

    fun onGameEnded() {
        val cleared = _nowPlaying.value?.packageName
        _nowPlaying.value = null
        if (cleared != null) {
            WajihaLog.i(WajihaTags.NOW_PLAYING, "onGameEnded: clear $cleared")
        }
        if (_state.value != DualScreenState.SingleDisplay) {
            _state.value = DualScreenState.DualBrowsing
            _secondaryMode.value = SecondaryMode.GameGrid
        }
    }

    /**
     * Launcher regained focus while a game/emulator is still running in the
     * background. Keeps [nowPlaying] for the overlay but returns the bottom
     * screen to the grid (or current non-NowPlaying mode).
     */
    fun retainGamingApp(state: NowPlayingState) {
        val previous = _nowPlaying.value
        _nowPlaying.value = state
        if (previous?.packageName != state.packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "retainGamingApp: ${previous?.packageName ?: "none"} -> ${state.packageName} " +
                    "(${state.appLabel ?: state.gameName ?: "unlabeled"})"
            )
        }
        if (_state.value == DualScreenState.SingleDisplay) return
        _state.value = DualScreenState.GameRunning
        if (_secondaryMode.value == SecondaryMode.NowPlaying ||
            _secondaryMode.value == SecondaryMode.Off
        ) {
            _secondaryMode.value = SecondaryMode.GameGrid
        }
    }

    /** Same as [retainGamingApp] but preserves the existing [NowPlayingState]. */
    fun returnToGridWhileGaming() {
        if (_nowPlaying.value == null || _state.value == DualScreenState.SingleDisplay) return
        _state.value = DualScreenState.GameRunning
        if (_secondaryMode.value == SecondaryMode.NowPlaying ||
            _secondaryMode.value == SecondaryMode.Off
        ) {
            _secondaryMode.value = SecondaryMode.GameGrid
        }
    }

    fun onAppSentToSecondary() {
        if (_state.value == DualScreenState.DualBrowsing) {
            _state.value = DualScreenState.AppOnSecondary
        }
    }

    fun onSecondaryAppDismissed() {
        if (_state.value == DualScreenState.AppOnSecondary) {
            _state.value = DualScreenState.DualBrowsing
        }
    }

    fun setSecondaryMode(mode: SecondaryMode) {
        _secondaryMode.value = mode
        _state.update { current ->
            when {
                mode == SecondaryMode.Off && current == DualScreenState.GameRunning ->
                    DualScreenState.BlackoutSecondary
                mode != SecondaryMode.Off && current == DualScreenState.BlackoutSecondary ->
                    DualScreenState.GameRunning
                else -> current
            }
        }
    }

    fun setRunningApps(apps: List<RunningApp>) {
        _runningApps.value = apps
    }

    fun setFocusedGame(gameId: Long?) {
        _focusedGameId.value = gameId
    }

    fun setPrimaryLauncherPanel(panel: LauncherPanel) {
        _primaryPanel.value = panel
    }

    fun setSecondaryLauncherPanel(panel: LauncherPanel) {
        _secondaryPanel.value = panel
    }

    fun setSettingsSectionLabel(label: String?) {
        _settingsSectionLabel.value = label
    }

    fun setAppsHeroDetail(appCount: Int, focusedLabel: String? = null) {
        _appsHeroCount.value = appCount
        _appsHeroFocusedLabel.value = focusedLabel
    }

    fun setSystemHeroSnapshot(
        batteryPercent: Int = -1,
        charging: Boolean = false,
        wifiEnabled: Boolean = false
    ) {
        _systemHeroSnapshot.value = HeroContext.System(
            batteryPercent = batteryPercent,
            charging = charging,
            wifiEnabled = wifiEnabled
        )
    }

    /**
     * Primary display reports whether it currently owns explicit UI that needs
     * the gamepad (onboarding, Settings, Apps, Scraper, System, or swapped grid).
     */
    fun setPrimaryHoldsGamepad(holds: Boolean) {
        if (primaryHoldsInput == holds) return
        primaryHoldsInput = holds
        recomputeGamepadOwner()
    }

    /**
     * Secondary display reports whether it is on a destination that needs the
     * gamepad (Settings/Scraper, App dock, Quick Settings, …) or the games grid.
     */
    fun setSecondaryHoldsGamepad(holds: Boolean) {
        if (secondaryHoldsInput == holds) return
        secondaryHoldsInput = holds
        recomputeGamepadOwner()
    }

    fun setGamesMenuOnPrimary(onPrimary: Boolean) {
        if (gamesMenuOnPrimary == onPrimary) return
        gamesMenuOnPrimary = onPrimary
        recomputeGamepadOwner()
    }

    /** Explicit claim — used when touch selects the games grid on a display. */
    fun claimGamepad(owner: GamepadOwner) {
        _gamepadOwner.value = owner
    }

    private fun recomputeGamepadOwner() {
        _gamepadOwner.value = when {
            _state.value == DualScreenState.SingleDisplay -> GamepadOwner.Primary
            // Explicit destination overlays take keys first (Settings/Apps/…).
            secondaryHoldsInput -> GamepadOwner.Secondary
            primaryHoldsInput -> GamepadOwner.Primary
            // Dual browsing home: keys follow the games menu screen.
            gamesMenuOnPrimary -> GamepadOwner.Primary
            else -> GamepadOwner.Secondary
        }
    }
}

private data class HeroInputs(
    val primary: LauncherPanel,
    val secondary: LauncherPanel,
    val section: String?,
    val appCount: Int,
    val focusedApp: String?
)
