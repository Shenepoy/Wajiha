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
    private val _appsHeroCount = MutableStateFlow(0)
    private val _appsHeroFocusedLabel = MutableStateFlow<String?>(null)
    private val _systemHeroSnapshot = MutableStateFlow(HeroContext.System())

    /** Manual scrape review open — top shows slot candidate grid / hint. */
    private val _scrapeReviewActive = MutableStateFlow(false)

    /** Slot picker visible on hero display — gamepad follows the top grid. */
    private val _scrapeReviewPicking = MutableStateFlow(false)

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

    // Options (mirrored from settings so state transitions can use them synchronously)
    var blackoutOnLaunch: Boolean = false
    var preferredGameMode: SecondaryMode = SecondaryMode.NowPlaying
    var nowPlayingDisplay: NowPlayingDisplayMode = NowPlayingDisplayMode.Both
    var gameDimEnabled: Boolean = false
    var gameDimOnlyOnNowPlaying: Boolean = true
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

    /** Launch intent or first external detection — sets session immediately. */
    fun beginGameSession(nowPlaying: NowPlayingState) {
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
            enterGameRunningStateIfNeeded()
        } else if (_state.value == DualScreenState.DualBrowsing) {
            _state.value = DualScreenState.GameRunning
        }
        when {
            nowPlaying.launchedByWajiha && !blackoutOnLaunch -> {
                requestNavigateToNowPlaying()
            }

            else -> {
                showGridDuringSession()
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
                mode == SecondaryMode.Off && current == DualScreenState.GameRunning -> {
                    DualScreenState.BlackoutSecondary
                }

                mode != SecondaryMode.Off && current == DualScreenState.BlackoutSecondary -> {
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

    fun setSettingsSectionLabel(label: String?) {
        _settingsSectionLabel.value = label
    }

    fun setGameDetailGameId(gameId: Long?) {
        _gameDetailId.value = gameId
    }

    /** Top screen shows scrape-review slot grid while Manual review is open. */
    fun setScrapeReviewActive(active: Boolean) {
        _scrapeReviewActive.value = active
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
     * Explicit claim — touch / key on a display. Sticky until L2 toggles or
     * single-display clears it.
     */
    fun claimGamepad(owner: GamepadOwner) {
        if (_state.value == DualScreenState.SingleDisplay) {
            stickyGamepadOwner = null
            setGamepadOwner(GamepadOwner.Primary)
            return
        }
        stickyGamepadOwner = owner
        setGamepadOwner(owner)
    }

    /** L2 — flip gamepad between top (Primary) and bottom (Secondary). */
    fun toggleGamepadOwner() {
        if (_state.value == DualScreenState.SingleDisplay) return
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
