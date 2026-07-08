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
import com.wajiha.input.GamepadLayers
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

    /** Foreground package on display 0 (top / game panel). Updated by [ForegroundAppMonitor]. */
    private val _topDisplayForegroundPackage = MutableStateFlow<String?>(null)
    val topDisplayForegroundPackage: StateFlow<String?> = _topDisplayForegroundPackage.asStateFlow()

    /** Active sessions keyed by package — survives when a game moves off the top display. */
    private val sessionCache = mutableMapOf<String, NowPlayingState>()

    /**
     * Now Playing UI state: only non-null when the featured session is foreground on
     * the top display (dual) or any session exists (single display).
     */
    val nowPlayingUiState: StateFlow<NowPlayingState?> = combine(
        _nowPlaying,
        _topDisplayForegroundPackage,
        _state
    ) { session, topPackage, dualState ->
        when {
            session == null -> null
            dualState == DualScreenState.SingleDisplay -> session
            topPackage != null && topPackage == session.packageName -> session
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val nowPlayingOnTopScreen: StateFlow<Boolean> = combine(
        nowPlayingUiState,
        _nowPlaying
    ) { uiSession, session ->
        uiSession != null && session != null
    }.stateIn(scope, SharingStarted.Eagerly, false)

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
    private val _gameDetailId = MutableStateFlow<Long?>(null)
    val gameDetailId: StateFlow<Long?> = _gameDetailId.asStateFlow()
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
            HeroInputsPartial(primary, secondary, section, appCount, focusedApp)
        },
        _gameDetailId,
        _systemHeroSnapshot
    ) { partial, detailId, system ->
        val inputs = HeroInputs(
            primary = partial.primary,
            secondary = partial.secondary,
            section = partial.section,
            appCount = partial.appCount,
            focusedApp = partial.focusedApp,
            gameDetailId = detailId
        )
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
            LauncherPanel.GameDetail -> {
                val id = inputs.gameDetailId
                if (id != null) HeroContext.GameDetail(id) else HeroContext.GameLibrary
            }
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
    var gameDimEnabled: Boolean = false
    var gameDimPercent: Int = 90
    /** Seconds before dim applies and before re-dimming after idle (0 = immediate / stay lifted). */
    var gameplayDimTimeoutSeconds: Int = 10

    // ---- Game session ([sessionCache] + featured [nowPlaying]) ----
    //
    // State machine:
    //   IDLE ──beginGameSession()──► ACTIVE ──endGameSession()──► IDLE
    //
    // [nowPlayingUiState] drives chip / Now Running tab — only set when the
    // featured session is foreground on the top display (display 0 on Thor).
    //
    // [secondaryMode] is independent UI during ACTIVE:
    //   GameGrid + NowPlayingOverlay chip (when [nowPlayingUiState] != null)
    //   NowPlaying full screen (user taps chip)
    //   Off (blackout)
    //
    // Dual-display Thor: game on top + launcher on bottom is NORMAL — never
    // treat launcher foreground as session end; only [endGameSession] clears.

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

    fun getSession(packageName: String): NowPlayingState? = sessionCache[packageName]

    fun activeSessions(): Collection<NowPlayingState> = sessionCache.values

    fun hasActiveSessions(): Boolean = sessionCache.isNotEmpty()

    /** Launch intent or first external detection — sets session immediately. */
    fun beginGameSession(nowPlaying: NowPlayingState) {
        GamepadLayers.stack.deactivateAll()
        val wasEmpty = sessionCache.isEmpty()
        sessionCache[nowPlaying.packageName] = nowPlaying
        _nowPlaying.value = nowPlaying
        if (wasEmpty) {
            enterGameRunningStateIfNeeded()
        } else if (_state.value == DualScreenState.DualBrowsing) {
            _state.value = DualScreenState.GameRunning
        }
        when {
            nowPlaying.launchedByWajiha && !blackoutOnLaunch -> showNowPlayingDuringSession()
            else -> showGridDuringSession()
        }
    }

    /** Update metadata without changing [secondaryMode]. */
    fun updateGameSession(state: NowPlayingState) {
        val previous = _nowPlaying.value
        sessionCache[state.packageName] = state
        _nowPlaying.value = state
        if (previous?.packageName != state.packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "updateGameSession: ${previous?.packageName ?: "none"} -> ${state.packageName} " +
                    "(${state.appLabel ?: state.gameName ?: "unlabeled"})"
            )
        }
        if (_state.value == DualScreenState.SingleDisplay) return
        if (_state.value == DualScreenState.DualBrowsing) {
            _state.value = DualScreenState.GameRunning
        }
    }

    /**
     * Top display foreground changed (from [ForegroundAppMonitor]).
     * When the top panel shows a cached gaming session, feature it for UI.
     */
    fun setTopDisplayForeground(packageName: String?) {
        if (_topDisplayForegroundPackage.value == packageName) return
        _topDisplayForegroundPackage.value = packageName
        val cached = packageName?.let { sessionCache[it] } ?: return
        if (_nowPlaying.value?.packageName != cached.packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "featureTopSession: ${_nowPlaying.value?.packageName ?: "none"} -> ${cached.packageName}"
            )
            _nowPlaying.value = cached
        }
    }

    /** Confirmed process exit or explicit kill — clears one session. */
    fun endGameSession(packageName: String? = null) {
        val cleared = packageName ?: _nowPlaying.value?.packageName ?: return
        sessionCache.remove(cleared)
        WajihaLog.i(WajihaTags.NOW_PLAYING, "endGameSession: clear $cleared")
        if (sessionCache.isEmpty()) {
            _nowPlaying.value = null
            _topDisplayForegroundPackage.value = null
            if (_state.value != DualScreenState.SingleDisplay) {
                _state.value = DualScreenState.DualBrowsing
                _secondaryMode.value = SecondaryMode.GameGrid
            }
            return
        }
        val topPkg = _topDisplayForegroundPackage.value
        _nowPlaying.value = topPkg?.let { sessionCache[it] } ?: sessionCache.values.firstOrNull()
    }

    private fun enterGameRunningStateIfNeeded() {
        if (_state.value == DualScreenState.SingleDisplay) return
        val mode = if (blackoutOnLaunch) SecondaryMode.Off else preferredGameMode
        _secondaryMode.value = mode
        _state.value = if (mode == SecondaryMode.Off) {
            DualScreenState.BlackoutSecondary
        } else {
            DualScreenState.GameRunning
        }
    }

    /**
     * Launcher has focus on the bottom display while a session is still active.
     * Keeps [nowPlaying] (chip + Y hint) but shows the games grid instead of
     * the full Now Playing screen or blackout.
     */
    fun showGridDuringSession() {
        if (!hasActiveSessions() || _state.value == DualScreenState.SingleDisplay) return
        _state.value = DualScreenState.GameRunning
        // Match legacy returnToGridWhileGaming: NowPlaying/Off are launch transitions;
        // other preferred modes (Quick Settings, Running Apps, …) stay as configured.
        if (_secondaryMode.value == SecondaryMode.NowPlaying ||
            _secondaryMode.value == SecondaryMode.Off
        ) {
            _secondaryMode.value = SecondaryMode.GameGrid
        }
    }

    /** Wajiha-initiated launch: show the Now Running panel on the bottom screen. */
    private fun showNowPlayingDuringSession() {
        if (!hasActiveSessions() || _state.value == DualScreenState.SingleDisplay) return
        _state.value = DualScreenState.GameRunning
        _secondaryMode.value = SecondaryMode.NowPlaying
        WajihaLog.i(WajihaTags.NOW_PLAYING, "showNowPlayingDuringSession")
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

    fun setGameDetailGameId(gameId: Long?) {
        _gameDetailId.value = gameId
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

private data class HeroInputsPartial(
    val primary: LauncherPanel,
    val secondary: LauncherPanel,
    val section: String?,
    val appCount: Int,
    val focusedApp: String?
)

private data class HeroInputs(
    val primary: LauncherPanel,
    val secondary: LauncherPanel,
    val section: String?,
    val appCount: Int,
    val focusedApp: String?,
    val gameDetailId: Long?
)
