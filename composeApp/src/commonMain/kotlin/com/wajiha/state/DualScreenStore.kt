package com.wajiha.state

import com.wajiha.input.GamepadLayers
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    BlackoutSecondary,
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
    Off,
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
    val logoPath: String? = null,
    val iconPath: String? = null,
    val squarePath: String? = null,
    val sessionStartedAt: Long = 0,
    /** Accumulated ms on the top display before the current segment. */
    val sessionElapsedMs: Long = 0,
    /** Wall time when this session was last on top (0 = paused / backgrounded). */
    val sessionResumedAt: Long = 0,
    /** true when launched from Wajiha, false when detected externally */
    val launchedByWajiha: Boolean = true,
) {
    fun activeElapsedMs(
        now: Long =
            kotlin.time.Clock.System
                .now()
                .toEpochMilliseconds(),
    ): Long =
        sessionElapsedMs +
            if (sessionResumedAt > 0L) {
                (now - sessionResumedAt).coerceAtLeast(0)
            } else {
                0L
            }
}

/** Prefer game title, then app label, then package name. */
fun sessionDisplayLabel(state: NowPlayingState?): String? = state?.gameName ?: state?.appLabel ?: state?.packageName

data class RunningApp(
    val packageName: String,
    val label: String,
    val lastUsedAt: Long,
    val isGame: Boolean,
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

    private val _activeSessions = MutableStateFlow<List<NowPlayingState>>(emptyList())

    /** All live sessions, sorted by start time — drives the left session rail. */
    val activeSessions: StateFlow<List<NowPlayingState>> = _activeSessions.asStateFlow()

    /** Single-display: App.kt navigates to [Route.NowRunning] when this fires. */
    private val _navigateToNowPlaying = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToNowPlayingRequests = _navigateToNowPlaying.asSharedFlow()

    /** Menu owner (primary or secondary) navigates to Settings when this fires (Start). */
    private val _openSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSettingsRequests = _openSettings.asSharedFlow()

    /** Menu owner navigates to Apps when this fires (L3). */
    private val _openApps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openAppsRequests = _openApps.asSharedFlow()

    /** Apps drawer opens its compact options panel when this fires (Start on Apps). */
    private val _openAppsOptions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openAppsOptionsRequests = _openAppsOptions.asSharedFlow()

    /** @return true when the request was accepted into the channel. */
    fun requestOpenSettings(): Boolean = _openSettings.tryEmit(Unit)

    /** @return true when the request was accepted into the channel. */
    fun requestOpenApps(): Boolean = _openApps.tryEmit(Unit)

    /** @return true when the request was accepted into the channel. */
    fun requestOpenAppsOptions(): Boolean = _openAppsOptions.tryEmit(Unit)

    /**
     * Start button: open Apps options while the menu is on Apps; otherwise open
     * global Settings (main grid / other destinations).
     */
    fun requestStartAction(): Boolean =
        if (_menuRoute.value.destination == MenuDestination.Apps) {
            requestOpenAppsOptions()
        } else {
            requestOpenSettings()
        }

    /**
     * Now Playing UI state: only non-null when the featured session is foreground on
     * the top display (dual) or any session exists (single display).
     */
    val nowPlayingUiState: StateFlow<NowPlayingState?> =
        combine(
            _nowPlaying,
            _topDisplayForegroundPackage,
            _state,
        ) { session, topPackage, dualState ->
            when {
                session == null -> null
                dualState == DualScreenState.SingleDisplay -> session
                topPackage != null && topPackage == session.packageName -> session
                else -> null
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

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

    /**
     * Last menu destination published by whichever display currently (or most
     * recently) owns the launcher menu. On SELECT swap the gaining menu display
     * adopts this so Settings / Apps / … are not dropped when the losing side
     * resets its local route to Home.
     */
    private val _menuRoute = MutableStateFlow(MenuRouteSnapshot())
    val menuRoute: StateFlow<MenuRouteSnapshot> = _menuRoute.asStateFlow()
    private val _secondaryNavigation = MutableStateFlow(SecondaryNavigationState())
    val secondaryNavigation: StateFlow<SecondaryNavigationState> =
        _secondaryNavigation.asStateFlow()
    private val _appsHeroCount = MutableStateFlow(0)
    private val _appsHeroFocusedLabel = MutableStateFlow<String?>(null)
    private val _systemHeroSnapshot = MutableStateFlow(HeroContext.System())

    /** Manual scrape review open — top shows slot candidate grid / hint. */
    private val _scrapeReviewActive = MutableStateFlow(false)

    /** Slot picker visible on hero display — gamepad follows the top grid. */
    private val _scrapeReviewPicking = MutableStateFlow(false)

    /** Focused settings row / section snapshot for the dual-screen hero. */
    private val _settingsHeroDetail = MutableStateFlow<SettingsHeroDetail?>(null)
    val settingsHeroDetail: StateFlow<SettingsHeroDetail?> = _settingsHeroDetail.asStateFlow()

    /** Settings hero option/action picking — gamepad follows the hero display. */
    private val _settingsHeroPicking = MutableStateFlow(false)

    /** Free-form hero layout editor — gamepad follows the hero display. */
    private val _heroLayoutEditing = MutableStateFlow(false)
    val heroLayoutEditing: StateFlow<Boolean> = _heroLayoutEditing.asStateFlow()

    private val _heroLayoutEditDraft = MutableStateFlow<com.wajiha.data.prefs.HeroLayout?>(null)
    val heroLayoutEditDraft = _heroLayoutEditDraft.asStateFlow()

    private val _heroLayoutEditSlot =
        MutableStateFlow(com.wajiha.data.prefs.HeroDisplaySlot.Primary)
    val heroLayoutEditSlot = _heroLayoutEditSlot.asStateFlow()

    private val _heroLayoutEditSelected =
        MutableStateFlow(com.wajiha.data.prefs.HeroElementId.Title)
    val heroLayoutEditSelected = _heroLayoutEditSelected.asStateFlow()

    /** Resolved hero for whichever display is showing [TopScreen]. */
    val heroContext: StateFlow<HeroContext> =
        combine(
            combine(
                _primaryPanel,
                _secondaryPanel,
                _settingsSectionLabel,
                _appsHeroCount,
                _appsHeroFocusedLabel,
            ) { primary, secondary, section, appCount, focusedApp ->
                HeroInputsPartial(primary, secondary, section, appCount, focusedApp)
            },
            _gameDetailId,
            _systemHeroSnapshot,
            _scrapeReviewActive,
        ) { partial, detailId, system, scrapeReview ->
            if (scrapeReview) return@combine HeroContext.ScrapeReview
            val inputs =
                HeroInputs(
                    primary = partial.primary,
                    secondary = partial.secondary,
                    section = partial.section,
                    appCount = partial.appCount,
                    focusedApp = partial.focusedApp,
                    gameDetailId = detailId,
                )
            val panel =
                when {
                    inputs.primary != LauncherPanel.GameLibrary -> inputs.primary
                    inputs.secondary != LauncherPanel.GameLibrary -> inputs.secondary
                    else -> LauncherPanel.GameLibrary
                }
            when (panel) {
                LauncherPanel.GameLibrary -> {
                    HeroContext.GameLibrary
                }

                LauncherPanel.Settings -> {
                    HeroContext.Settings(sectionLabel = inputs.section)
                }

                LauncherPanel.Apps -> {
                    HeroContext.Apps(
                        appCount = inputs.appCount,
                        focusedLabel = inputs.focusedApp,
                    )
                }

                LauncherPanel.System -> {
                    system
                }

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

    /**
     * Bumps whenever [gamepadOwner] changes so the gaining screen can restore
     * Compose focus onto its content (L2 toggle, touch claim, route recompute).
     */
    private val _gamepadFocusEpoch = MutableStateFlow(0L)
    val gamepadFocusEpoch: StateFlow<Long> = _gamepadFocusEpoch.asStateFlow()

    /**
     * L2 / touch claim sticky override. While set, [recomputeGamepadOwner] keeps
     * this owner instead of route heuristics. Cleared on single-display.
     */
    private var stickyGamepadOwner: GamepadOwner? = null

    /** Debounce digital + analog L2 firing for the same physical press. */
    private var lastL2ToggleAtMs: Long = 0L

    /** Primary (top) is showing onboarding or a non-home route (Settings/Apps/…). */
    private var primaryHoldsInput: Boolean = false

    /** Secondary is showing Settings/Scraper or a non-grid mode (Apps/System/…). */
    private var secondaryHoldsInput: Boolean = false

    /** When true, the games grid lives on the primary display instead of secondary. */
    private var gamesMenuOnPrimary: Boolean = false

    /** Which activity hosts the launcher menu (grid / settings / review dialog). */
    fun menuGamepadOwner(): GamepadOwner = if (gamesMenuOnPrimary) GamepadOwner.Primary else GamepadOwner.Secondary

    /** Which activity hosts the hero / top preview. */
    fun heroGamepadOwner(): GamepadOwner = if (gamesMenuOnPrimary) GamepadOwner.Secondary else GamepadOwner.Primary

    /**
     * Hero has real focusable UI (scrape slot grid or settings/detail actions).
     * Idle artwork / Apps / System heroes do not count.
     */
    fun isHeroInteractive(): Boolean = _scrapeReviewPicking.value || _settingsHeroPicking.value || _heroLayoutEditing.value

    /**
     * L2 may flip ownership: dual layout, not blackout, and the hero is interactive
     * (menu is always a valid return target).
     */
    fun isL2SwitchAvailable(): Boolean {
        val dualState = _state.value
        if (dualState == DualScreenState.SingleDisplay) return false
        if (dualState == DualScreenState.BlackoutSecondary) return false
        return isHeroInteractive()
    }

    /**
     * Display that should show the L2 "Focus screen" hint — the unfocused owner
     * while [isL2SwitchAvailable]. Null when L2 is inactive.
     */
    val l2HintOwner: StateFlow<GamepadOwner?> =
        combine(
            _state,
            _gamepadOwner,
            _scrapeReviewPicking,
            _settingsHeroPicking,
            _heroLayoutEditing,
        ) { dualState, owner, scrapePicking, settingsPicking, layoutEditing ->
            if (dualState == DualScreenState.SingleDisplay ||
                dualState == DualScreenState.BlackoutSecondary
            ) {
                return@combine null
            }
            if (!scrapePicking && !settingsPicking && !layoutEditing) return@combine null
            when (owner) {
                GamepadOwner.Primary -> GamepadOwner.Secondary
                GamepadOwner.Secondary -> GamepadOwner.Primary
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    // Options (mirrored from settings so state transitions can use them synchronously)
    var blackoutOnLaunch: Boolean = false
    var preferredGameMode: SecondaryMode = SecondaryMode.NowPlaying
    var nowPlayingDisplay: NowPlayingDisplayMode = NowPlayingDisplayMode.Both
    var gameDimEnabled: Boolean = false
    var gameDimOnlyOnNowPlaying: Boolean = true
    var gameDimPercent: Int = 90

    /** Seconds before dim applies and before re-dimming after idle (0 = immediate / stay lifted). */
    var gameplayDimTimeoutSeconds: Int = 10

    /**
     * Primary-only launcher (Settings → Screens → Single screen). When true,
     * [state] stays [DualScreenState.SingleDisplay] even if hardware secondary exists.
     */
    @Volatile
    var forceSingleScreen: Boolean = false
        private set

    /** True when UI should use dual layout roles (not forced single / no secondary). */
    fun isDualLayout(): Boolean = _state.value != DualScreenState.SingleDisplay

    /**
     * Apply the Single screen pref. Re-evaluates display state from the last
     * known secondary id so toggling does not require a display reconnect.
     */
    fun setForceSingleScreen(enabled: Boolean) {
        if (forceSingleScreen == enabled) return
        forceSingleScreen = enabled
        WajihaLog.i(WajihaTags.DISPLAY, "forceSingleScreen=$enabled")
        onDisplaysChanged(_secondaryDisplayId.value)
        if (enabled) {
            stickyGamepadOwner = null
            recomputeGamepadOwner()
        }
    }

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
        val previous = _state.value
        _secondaryDisplayId.value = secondaryDisplayId
        _state.update { current ->
            when {
                forceSingleScreen || secondaryDisplayId == null -> DualScreenState.SingleDisplay
                current == DualScreenState.SingleDisplay -> DualScreenState.DualBrowsing
                current == DualScreenState.AppOnSecondary -> current
                else -> current
            }
        }
        val next = _state.value
        if (previous != next) {
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "dualScreen: secondaryDisplayId=$secondaryDisplayId " +
                    "forceSingle=$forceSingleScreen $previous→$next",
            )
        } else {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "dualScreen: secondaryDisplayId=$secondaryDisplayId " +
                    "forceSingle=$forceSingleScreen state=$next",
            )
        }
        recomputeGamepadOwner()
    }

    fun getSession(packageName: String): NowPlayingState? = sessionCache[packageName]

    fun activeSessions(): Collection<NowPlayingState> = sessionCache.values

    fun hasActiveSessions(): Boolean = sessionCache.isNotEmpty()

    private fun publishActiveSessions() {
        _activeSessions.value = sessionCache.values.sortedBy { it.sessionStartedAt }
    }

    /** Feature a cached session for the Now Running panel (e.g. rail confirm before focus). */
    fun featureSession(packageName: String) {
        val cached = sessionCache[packageName] ?: return
        if (_nowPlaying.value?.packageName != cached.packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "featureSession: ${_nowPlaying.value?.packageName ?: "none"} -> ${cached.packageName}",
            )
            _nowPlaying.value = cached
        }
    }

    /**
     * Task-switcher: bring [packageName] to the top display without ending other sessions.
     * Pauses elapsed time on background sessions and resumes the selected one.
     */
    fun switchToSession(packageName: String) {
        if (!sessionCache.containsKey(packageName)) return
        applyTopDisplaySessionClocks(packageName)
        _topDisplayForegroundPackage.value = packageName
        featureSession(packageName)
        publishActiveSessions()
        WajihaLog.i(WajihaTags.NOW_PLAYING, "switchToSession: $packageName")
    }

    /**
     * Launch intent or first external detection — sets session immediately.
     *
     * @param deferSecondaryUi when true, keep the current bottom UI (usually GameGrid)
     * through [startActivity]; call [applyDeferredSecondaryModeAfterLaunch] after the
     * top-display task attaches so the bottom screen does not flash.
     */
    fun beginGameSession(
        nowPlaying: NowPlayingState,
        deferSecondaryUi: Boolean = false,
    ) {
        GamepadLayers.stack.deactivateAll()
        val now = nowMs()
        val wasEmpty = sessionCache.isEmpty()
        sessionCache.keys.toList().forEach { pkg ->
            sessionCache[pkg]?.let { sessionCache[pkg] = pauseSessionClock(it, now) }
        }
        val started =
            nowPlaying.copy(
                sessionStartedAt = nowPlaying.sessionStartedAt.takeIf { it > 0L } ?: now,
                sessionResumedAt = now,
                sessionElapsedMs = nowPlaying.sessionElapsedMs,
            )
        sessionCache[started.packageName] = started
        _nowPlaying.value = started
        _topDisplayForegroundPackage.value = started.packageName
        publishActiveSessions()
        if (wasEmpty) {
            if (deferSecondaryUi && !blackoutOnLaunch) {
                // Keep GameGrid (or current mode) through startActivity — switching
                // secondary UI in the same frame as the top-display launch flashes black.
                if (_state.value != DualScreenState.SingleDisplay) {
                    _state.value = DualScreenState.GameRunning
                }
            } else {
                enterGameRunningStateIfNeeded()
            }
        } else if (_state.value == DualScreenState.DualBrowsing) {
            _state.value = DualScreenState.GameRunning
        }
        if (deferSecondaryUi) {
            if (_state.value == DualScreenState.SingleDisplay && nowPlaying.launchedByWajiha) {
                _navigateToNowPlaying.tryEmit(Unit)
            }
            return
        }
        // Dual: enterGameRunningStateIfNeeded already applied preferredGameMode / blackout.
        // Do not force NowPlaying over the user's "Bottom screen while a game runs" setting.
        // Single-display still needs the in-app Now Running route.
        when {
            _state.value == DualScreenState.SingleDisplay && nowPlaying.launchedByWajiha -> {
                _navigateToNowPlaying.tryEmit(Unit)
            }

            !wasEmpty && nowPlaying.launchedByWajiha && !blackoutOnLaunch -> {
                when (preferredGameMode) {
                    SecondaryMode.NowPlaying -> requestNavigateToNowPlaying()
                    SecondaryMode.Off -> setSecondaryMode(SecondaryMode.Off)
                    else -> setSecondaryMode(preferredGameMode)
                }
            }

            !nowPlaying.launchedByWajiha && !blackoutOnLaunch -> {
                showGridDuringSession()
            }
        }
    }

    /**
     * Apply [preferredGameMode] / blackout after the top-display task has attached.
     * Called from [com.wajiha.android.launch.GameLauncher] post-launch.
     */
    fun applyDeferredSecondaryModeAfterLaunch() {
        if (!hasActiveSessions()) return
        if (_state.value == DualScreenState.SingleDisplay) {
            _navigateToNowPlaying.tryEmit(Unit)
            return
        }
        if (blackoutOnLaunch) {
            _secondaryMode.value = SecondaryMode.Off
            _state.value = DualScreenState.BlackoutSecondary
            return
        }
        _state.value = DualScreenState.GameRunning
        when (preferredGameMode) {
            SecondaryMode.NowPlaying -> {
                // Open Now Running on the bottom when a game lands on the top
                // panel. Live Overlay + mode crossfade cover the transition.
                _secondaryMode.value = SecondaryMode.NowPlaying
            }

            SecondaryMode.GameGrid -> {
                if (_secondaryMode.value != SecondaryMode.GameGrid) {
                    _secondaryMode.value = SecondaryMode.GameGrid
                }
            }

            else -> {
                setSecondaryMode(preferredGameMode)
            }
        }
    }

    /** Update metadata without changing [secondaryMode]. */
    fun updateGameSession(state: NowPlayingState) {
        sessionCache[state.packageName] = state
        if (_nowPlaying.value?.packageName == state.packageName) {
            _nowPlaying.value = state
        }
        publishActiveSessions()
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
        val gamingTop = packageName?.takeIf { sessionCache.containsKey(it) }
        applyTopDisplaySessionClocks(gamingTop)
        _topDisplayForegroundPackage.value = packageName
        val cached = packageName?.let { sessionCache[it] } ?: return
        if (_nowPlaying.value?.packageName != cached.packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "featureTopSession: ${_nowPlaying.value?.packageName ?: "none"} -> ${cached.packageName}",
            )
            _nowPlaying.value = cached
        }
        publishActiveSessions()
    }

    /** Confirmed process exit or explicit kill — clears one session. */
    fun endGameSession(packageName: String? = null) {
        val cleared = packageName ?: _nowPlaying.value?.packageName ?: return
        sessionCache.remove(cleared)
        publishActiveSessions()
        WajihaLog.i(WajihaTags.NOW_PLAYING, "endGameSession: clear $cleared")
        if (sessionCache.isEmpty()) {
            _nowPlaying.value = null
            _topDisplayForegroundPackage.value = null
            if (_state.value != DualScreenState.SingleDisplay && !forceSingleScreen) {
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
        val mode =
            when {
                blackoutOnLaunch -> SecondaryMode.Off
                else -> preferredGameMode
            }
        _secondaryMode.value = mode
        _state.value =
            if (mode == SecondaryMode.Off) {
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

    /**
     * Open the Now Playing screen — dual: [SecondaryMode.NowPlaying];
     * single: emits [navigateToNowPlayingRequests] for App.kt [Route.NowRunning].
     */
    fun requestNavigateToNowPlaying() {
        if (!hasActiveSessions()) return
        when (_state.value) {
            DualScreenState.SingleDisplay -> {
                _navigateToNowPlaying.tryEmit(Unit)
            }

            else -> {
                _state.value = DualScreenState.GameRunning
                _secondaryMode.value = SecondaryMode.NowPlaying
            }
        }
        WajihaLog.i(WajihaTags.NOW_PLAYING, "requestNavigateToNowPlaying")
    }

    fun onAppSentToSecondary() {
        if (_state.value == DualScreenState.SingleDisplay) return
        // Foreign app owns the bottom panel — keep this even during GameRunning
        // so SecondaryHome reclaim cannot cover Chrome/Settings launches.
        if (_state.value != DualScreenState.AppOnSecondary) {
            _state.value = DualScreenState.AppOnSecondary
        }
    }

    fun onSecondaryAppDismissed() {
        if (_state.value != DualScreenState.AppOnSecondary) return
        _state.value =
            if (hasActiveSessions()) {
                DualScreenState.GameRunning
            } else {
                DualScreenState.DualBrowsing
            }
    }

    fun setSecondaryMode(mode: SecondaryMode) {
        val resolved = mode
        _secondaryMode.value = resolved
        _state.update { current ->
            when {
                resolved == SecondaryMode.Off && current == DualScreenState.GameRunning -> {
                    DualScreenState.BlackoutSecondary
                }

                resolved != SecondaryMode.Off && current == DualScreenState.BlackoutSecondary -> {
                    DualScreenState.GameRunning
                }

                else -> {
                    current
                }
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

    /** Publish the active menu route while this display owns the menu. */
    fun publishMenuRoute(snapshot: MenuRouteSnapshot) {
        if (_menuRoute.value == snapshot) return
        _menuRoute.value = snapshot
    }

    fun updateSecondaryNavigation(transform: (SecondaryNavigationState) -> SecondaryNavigationState) {
        _secondaryNavigation.update(transform)
    }

    fun setSettingsSectionLabel(label: String?) {
        _settingsSectionLabel.value = label
    }

    fun setGameDetailGameId(gameId: Long?) {
        _gameDetailId.value = gameId
    }

    /** Top screen shows scrape-review slot grid while Manual review is open. */
    fun setScrapeReviewActive(active: Boolean) {
        _scrapeReviewActive.value = active
        if (active) {
            _settingsHeroDetail.value = null
            _settingsHeroPicking.value = false
            if (_heroLayoutEditing.value) setHeroLayoutEditing(false)
            SettingsHeroActionBridge.clear()
        }
        if (!active) {
            _scrapeReviewPicking.value = false
        }
        // Drop L2 sticky on open (menu overview) and close (underlying host).
        stickyGamepadOwner = null
        recomputeGamepadOwner()
        // Owner may be unchanged — still refocus overview or the host underneath.
        _gamepadFocusEpoch.value = _gamepadFocusEpoch.value + 1L
    }

    /** Hero display owns the slot candidate grid (gamepad → primary). */
    fun setScrapeReviewPicking(picking: Boolean) {
        if (_scrapeReviewPicking.value == picking) return
        _scrapeReviewPicking.value = picking
        // Drop L2 sticky so the slot grid on the hero display receives keys.
        if (picking) stickyGamepadOwner = null
        recomputeGamepadOwner()
        // Owner may already match — still bump so hero/overview content refocuses.
        _gamepadFocusEpoch.value = _gamepadFocusEpoch.value + 1L
    }

    fun setSettingsHeroDetail(detail: SettingsHeroDetail?) {
        if (_settingsHeroDetail.value == detail) return
        _settingsHeroDetail.value = detail
        if (detail == null) {
            SettingsHeroActionBridge.clear()
        }
    }

    /** Hero display owns settings option/action picking (gamepad → primary). */
    fun setSettingsHeroPicking(picking: Boolean) {
        if (_settingsHeroPicking.value == picking) return
        _settingsHeroPicking.value = picking
        if (picking) {
            stickyGamepadOwner = null
            if (_heroLayoutEditing.value) setHeroLayoutEditing(false)
        }
        recomputeGamepadOwner()
        _gamepadFocusEpoch.value = _gamepadFocusEpoch.value + 1L
    }

    /** Hero layout editor active — gamepad follows the hero display. */
    fun setHeroLayoutEditing(editing: Boolean) {
        if (_heroLayoutEditing.value == editing) return
        if (editing && _scrapeReviewPicking.value) return
        _heroLayoutEditing.value = editing
        if (editing) {
            stickyGamepadOwner = null
            _settingsHeroPicking.value = false
            _settingsHeroDetail.value = null
            SettingsHeroActionBridge.clear()
        }
        // Keep the draft until Settings controls dispose/save (and the next
        // beginHeroLayoutEdit overwrites). Clearing here raced B-on-canvas /
        // swap teardown and could drop an unsaved layout.
        recomputeGamepadOwner()
        _gamepadFocusEpoch.value = _gamepadFocusEpoch.value + 1L
    }

    fun beginHeroLayoutEdit(
        slot: com.wajiha.data.prefs.HeroDisplaySlot,
        layout: com.wajiha.data.prefs.HeroLayout,
    ) {
        if (_scrapeReviewPicking.value) return
        _heroLayoutEditSlot.value = slot
        _heroLayoutEditDraft.value = layout.copy(configured = true)
        _heroLayoutEditSelected.value =
            layout.elements
                .firstOrNull { it.visible && it.id != com.wajiha.data.prefs.HeroElementId.Backdrop }
                ?.id
                ?: com.wajiha.data.prefs.HeroElementId.Title
        setHeroLayoutEditing(true)
    }

    fun updateHeroLayoutEditDraft(layout: com.wajiha.data.prefs.HeroLayout) {
        _heroLayoutEditDraft.value = layout.copy(configured = true)
    }

    fun clearHeroLayoutEditDraft() {
        _heroLayoutEditDraft.value = null
    }

    fun setHeroLayoutEditSelected(id: com.wajiha.data.prefs.HeroElementId) {
        _heroLayoutEditSelected.value = id
    }

    fun setAppsHeroDetail(
        appCount: Int,
        focusedLabel: String? = null,
    ) {
        _appsHeroCount.value = appCount
        _appsHeroFocusedLabel.value = focusedLabel
    }

    fun setSystemHeroSnapshot(
        batteryPercent: Int = -1,
        charging: Boolean = false,
        wifiEnabled: Boolean = false,
    ) {
        _systemHeroSnapshot.value =
            HeroContext.System(
                batteryPercent = batteryPercent,
                charging = charging,
                wifiEnabled = wifiEnabled,
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

    /**
     * SELECT / Settings role swap moves the menu to the other activity. Focus
     * follows that menu even when a prior touch or L2 left a sticky owner behind.
     *
     * When the secondary gains the menu, apply [menuRoute] to secondary nav/mode
     * **before** the swap pref recomposes UI so AppDock/Settings are not painted
     * as a one-frame GameGrid flash.
     */
    fun onScreenRolesSwapped(menuOnPrimary: Boolean) {
        if (_state.value == DualScreenState.SingleDisplay) return
        // Exit layout editor on swap so drag/nudge cannot span displays.
        // Use the setter so draft clear / gamepad recompute stay consistent;
        // Settings controls DisposableEffect still saves the last draft.
        if (_heroLayoutEditing.value) setHeroLayoutEditing(false)
        gamesMenuOnPrimary = menuOnPrimary
        stickyGamepadOwner = null
        if (!menuOnPrimary) {
            adoptMenuSnapshotOntoSecondary(_menuRoute.value)
        }
        val menuOwner =
            if (menuOnPrimary) {
                GamepadOwner.Primary
            } else {
                GamepadOwner.Secondary
            }
        if (_gamepadOwner.value == menuOwner) {
            // The destination composition is still being replaced; force its
            // owner-focus effect to restore the semantic anchor after mounting.
            _gamepadFocusEpoch.value = _gamepadFocusEpoch.value + 1L
        } else {
            setGamepadOwner(menuOwner)
        }
    }

    /** Apply shared menu snapshot to secondary navigation + mode (idempotent). */
    fun adoptMenuSnapshotOntoSecondary(snap: MenuRouteSnapshot = _menuRoute.value) {
        val adoptedRoute =
            when (snap.destination) {
                MenuDestination.Home,
                MenuDestination.Apps,
                MenuDestination.System,
                MenuDestination.NowRunning,
                -> SecondaryRoute.Modes

                MenuDestination.Settings -> SecondaryRoute.Settings

                MenuDestination.PlatformPicker -> SecondaryRoute.PlatformPicker

                MenuDestination.PlatformDetail -> SecondaryRoute.PlatformDetail

                MenuDestination.Scraper -> SecondaryRoute.Scraper

                MenuDestination.GameDetail -> SecondaryRoute.GameDetail
            }
        updateSecondaryNavigation {
            it.copy(
                route = adoptedRoute,
                platformDetailId = snap.platformDetailId,
                platformDetailFromPicker = snap.platformDetailFromPicker,
                gameDetailId = snap.gameDetailId,
            )
        }
        when (snap.destination) {
            MenuDestination.Home -> setSecondaryMode(SecondaryMode.GameGrid)
            MenuDestination.Apps -> setSecondaryMode(SecondaryMode.AppDock)
            MenuDestination.System -> setSecondaryMode(SecondaryMode.QuickSettings)
            MenuDestination.NowRunning -> setSecondaryMode(SecondaryMode.NowPlaying)
            else -> Unit
        }
    }

    /**
     * Explicit claim — touch / key on a display. Sticky until L2 toggles or
     * single-display clears it.
     *
     * Only steals **key routing**. Does not bump [gamepadFocusEpoch], so the
     * gaining screen keeps its current selection/anchor (touching bottom must
     * control bottom without yanking focus). L2 / SELECT still use
     * [setGamepadOwner] when a full focus restore is intended.
     */
    fun claimGamepad(owner: GamepadOwner) {
        if (_state.value == DualScreenState.SingleDisplay) {
            stickyGamepadOwner = null
            setGamepadOwner(GamepadOwner.Primary)
            return
        }
        stickyGamepadOwner = owner
        if (_gamepadOwner.value != owner) {
            _gamepadOwner.value = owner
        }
    }

    /** L2 — flip gamepad between top (Primary) and bottom (Secondary). */
    fun toggleGamepadOwner() {
        if (!isL2SwitchAvailable()) return
        val now = nowMs()
        // Digital BUTTON_L2 and analog AXIS_LTRIGGER can both fire for one press.
        if (now - lastL2ToggleAtMs < 280L) return
        lastL2ToggleAtMs = now
        val next =
            when (_gamepadOwner.value) {
                GamepadOwner.Primary -> GamepadOwner.Secondary
                GamepadOwner.Secondary -> GamepadOwner.Primary
            }
        stickyGamepadOwner = next
        setGamepadOwner(next)
    }

    private fun setGamepadOwner(owner: GamepadOwner) {
        if (_gamepadOwner.value == owner) return
        _gamepadOwner.value = owner
        _gamepadFocusEpoch.value = _gamepadFocusEpoch.value + 1L
    }

    private fun nowMs(): Long =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()

    private fun pauseSessionClock(
        session: NowPlayingState,
        now: Long,
    ): NowPlayingState {
        if (session.sessionResumedAt <= 0L) return session
        return session.copy(
            sessionElapsedMs =
                session.sessionElapsedMs +
                    (now - session.sessionResumedAt).coerceAtLeast(0),
            sessionResumedAt = 0L,
        )
    }

    private fun resumeSessionClock(
        session: NowPlayingState,
        now: Long,
    ): NowPlayingState = if (session.sessionResumedAt > 0L) session else session.copy(sessionResumedAt = now)

    /** Pause every session except [topPackage] (null pauses all). */
    private fun applyTopDisplaySessionClocks(topPackage: String?) {
        val now = nowMs()
        sessionCache.keys.toList().forEach { pkg ->
            val current = sessionCache[pkg] ?: return@forEach
            sessionCache[pkg] =
                when (pkg) {
                    topPackage -> resumeSessionClock(current, now)
                    else -> pauseSessionClock(current, now)
                }
        }
    }

    private fun recomputeGamepadOwner() {
        if (_state.value == DualScreenState.SingleDisplay) {
            stickyGamepadOwner = null
            setGamepadOwner(GamepadOwner.Primary)
            return
        }
        // Re-read sticky at decision time — never pass a stale null into apply that
        // would wipe an L2/claim sticky set concurrently by toggle/claim.
        val sticky = stickyGamepadOwner
        val next =
            when {
                sticky != null -> {
                    sticky
                }

                // Slot grid follows the hero display (primary by default; secondary when swapped).
                _scrapeReviewPicking.value -> {
                    if (gamesMenuOnPrimary) GamepadOwner.Secondary else GamepadOwner.Primary
                }

                // Settings hero option/action picking follows the hero display.
                _settingsHeroPicking.value -> {
                    if (gamesMenuOnPrimary) GamepadOwner.Secondary else GamepadOwner.Primary
                }

                // Hero layout editor follows the hero display.
                _heroLayoutEditing.value -> {
                    if (gamesMenuOnPrimary) GamepadOwner.Secondary else GamepadOwner.Primary
                }

                // Explicit destination overlays take keys first (Settings/Apps/…).
                secondaryHoldsInput -> {
                    GamepadOwner.Secondary
                }

                primaryHoldsInput -> {
                    GamepadOwner.Primary
                }

                // Dual browsing home: keys follow the games menu screen.
                gamesMenuOnPrimary -> {
                    GamepadOwner.Primary
                }

                else -> {
                    GamepadOwner.Secondary
                }
            }
        setGamepadOwner(next)
    }
}

private data class HeroInputsPartial(
    val primary: LauncherPanel,
    val secondary: LauncherPanel,
    val section: String?,
    val appCount: Int,
    val focusedApp: String?,
)

private data class HeroInputs(
    val primary: LauncherPanel,
    val secondary: LauncherPanel,
    val section: String?,
    val appCount: Int,
    val focusedApp: String?,
    val gameDetailId: Long?,
)
