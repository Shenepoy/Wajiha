package com.wajiha.android.monitor

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.provider.Settings
import com.wajiha.android.detect.ExternalGameResolver
import com.wajiha.android.detect.ResolvedGame
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.launch.PlaySessionTracker
import com.wajiha.android.service.KeepAliveService
import com.wajiha.android.util.PackageKiller
import com.wajiha.domain.GamingAppCatalog
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import com.wajiha.state.RunningApp
import com.wajiha.state.sessionDisplayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Detects externally launched games and verifies when a session ends.
 *
 * Session lifecycle:
 * - **Start**: [onSessionStarted] from [com.wajiha.android.launch.GameLauncher] (immediate)
 *   or [onForegroundPackage] when [detectionEnabled] sees a gaming package.
 * - **UI**: [DualScreenStore.nowPlaying] is the single source of truth; [DualScreenStore.secondaryMode]
 *   is independent navigation (grid + chip vs full Now Playing screen).
 * - **End**: only after [GameSessionController.END_CONFIRM_POLLS] consecutive polls find
 *   no process/task/usage alive signals — process/task APIs miss top-display emulators
 *   on Thor; Wajiha foreground on the bottom display is never a session end.
 *
 * Polls every [POLL_INTERVAL_MS] with `UsageStatsManager.queryEvents` for external
 * detection; active sessions use a shorter interval but still throttle UsageStats lists.
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val store: DualScreenStore,
    private val platformRepository: PlatformRepository,
    private val sessionTracker: PlaySessionTracker,
    private val externalGameResolver: ExternalGameResolver,
    private val displayCoordinator: DisplayCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val sessionController = GameSessionController()

    private var knownEmulatorPackages: Set<String> = emptySet()
    private var lastForeground: String? = null

    /** Latest window-state / usage-stats sighting per package (accessibility + poll). */
    private val lastSeenForegroundAt = mutableMapOf<String, Long>()

    /** Debounce alt-tab away from game before hiding Now Playing chip. */
    private var pendingTopHidePackage: String? = null
    private var pendingTopHideSince: Long = 0L

    /** Mirrored from settings (detectManualLaunches). */
    @Volatile
    var detectionEnabled: Boolean = true

    /** Mirrored from settings (memoryGuardEnabled). Default on. */
    @Volatile
    var memoryGuardEnabled: Boolean = true

    /**
     * Packages the user explicitly closed — block [discoverAdditionalGamingSessions]
     * while the emulator process survives [killBackgroundProcesses].
     */
    private val suppressRediscoveryUntil = mutableMapOf<String, Long>()

    private val lastMemoryGuardAtMs = AtomicLong(0L)
    private var lastRssProbeAtMs: Long = 0L
    private var cachedMemTotalBytes: Long = 0L
    private var loggedRssBlind: Boolean = false

    private val ownPackage = context.packageName

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode =
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent() = android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Task-switcher: feature session, pause background timers, keep all processes alive. */
    fun switchToSession(packageName: String) {
        store.activeSessions().map { it.packageName }.forEach { pkg ->
            sessionController.markSiblingLaunchGrace(pkg)
        }
        sessionController.onSessionStarted(packageName)
        if (store.getSession(packageName) != null) {
            store.switchToSession(packageName)
        } else {
            WajihaLog.w(
                WajihaTags.NOW_PLAYING,
                "switchToSession: no cache for $packageName (task switch only)",
            )
        }
        store.setTopDisplayForeground(packageName)
        lastForeground = packageName
        pendingTopHidePackage = null
        lastSeenForegroundAt[packageName] = System.currentTimeMillis()
        SessionTaskRegistry.captureTaskId(context, packageName)
        WajihaLog.i(WajihaTags.NOW_PLAYING, "switchToSession: $packageName")
    }

    /** After task switch, refresh top-display foreground so verify poll keeps siblings alive. */
    fun scheduleTopDisplayRefresh() {
        scope.launch {
            delay(600)
            refreshTopDisplayForeground()
            store.activeSessions().forEach { session ->
                if (sessionProcessAlive(session)) {
                    sessionController.onSessionStarted(session.packageName)
                }
            }
        }
    }

    /** Call from [com.wajiha.android.launch.GameLauncher] right after [DualScreenStore.beginGameSession]. */
    fun onSessionStarted(packageName: String) {
        val siblings =
            store
                .activeSessions()
                .map { it.packageName }
                .filter { it != packageName }
        if (siblings.isNotEmpty()) {
            sessionController.markSiblingLaunchGrace(*siblings.toTypedArray())
        }
        clearRediscoverySuppression(packageName)
        sessionController.onSessionStarted(packageName)
        lastForeground = packageName
        pendingTopHidePackage = null
        store.setTopDisplayForeground(packageName)
        lastSeenForegroundAt[packageName] = System.currentTimeMillis()
        SessionTaskRegistry.captureTaskId(context, packageName)
        WajihaLog.i(WajihaTags.NOW_PLAYING, "onSessionStarted: $packageName")
    }

    fun start() {
        if (job?.isActive == true) return
        WajihaLog.i(WajihaTags.NOW_PLAYING, "monitor: start")
        job =
            scope.launch {
                var lastPackagesRefresh = 0L
                var pollCount = 0
                while (isActive) {
                    val now = System.currentTimeMillis()
                    if (now - lastPackagesRefresh > PACKAGES_REFRESH_MS) {
                        lastPackagesRefresh = now
                        refreshKnownPackages()
                    }
                    if (hasUsageAccess()) {
                        poll()
                        pollCount++
                        if (pollCount % 8 == 0) {
                            WajihaLog.d(
                                WajihaLogKind.WORK,
                                "poll: sessions=${store.activeSessions().size} " +
                                    "top=${store.topDisplayForegroundPackage.value}",
                                minIntervalMs = 2_000L,
                            )
                        }
                        // Idle path: poll skips running-apps refresh; keep the 10s throttle.
                        if (!store.hasActiveSessions()) {
                            refreshRunningApps(force = false)
                        }
                        store.nowPlaying.value?.let { maybeResolveExternalSession(it) }
                    }
                    val interval =
                        if (store.hasActiveSessions()) {
                            ACTIVE_POLL_INTERVAL_MS
                        } else {
                            POLL_INTERVAL_MS
                        }
                    delay(interval)
                }
            }
    }

    fun stop() {
        WajihaLog.i(WajihaTags.NOW_PLAYING, "monitor: stop")
        job?.cancel()
        job = null
    }

    suspend fun refreshKnownPackages() {
        knownEmulatorPackages = platformRepository.knownEmulatorPackages()
    }

    /** Also called by the accessibility service for instant external detection. */
    fun onForegroundPackage(packageName: String) {
        lastSeenForegroundAt[packageName] = System.currentTimeMillis()

        // Cached multi-session: accessibility / usage resume on display 0 updates top chip.
        if (store.getSession(packageName) != null && isGamingPackage(packageName)) {
            sessionController.onSessionStarted(packageName)
            applyTopDisplayForeground(packageName, reason = "foreground-event")
        }

        val missingSession = isGamingPackage(packageName) && store.getSession(packageName) == null
        // After Y-close the same package may still be lastForeground from poll; recents
        // re-focus must still create a session even when the package name is unchanged.
        if (packageName == lastForeground && !missingSession) return
        if (packageName != lastForeground) lastForeground = packageName
        if (!detectionEnabled) return
        if (packageName == ownPackage) return
        if (isSystemUi(packageName)) return

        if (isGamingPackage(packageName)) {
            if (missingSession) clearRediscoverySuppression(packageName)
            beginOrUpdateExternalSession(packageName, allowSuppressed = false)
        }
    }

    /**
     * Called when MainActivity or SecondaryHomeActivity resumes.
     * Dual-display: bottom launcher foreground is expected — keep sessions alive.
     */
    fun onLauncherForegrounded() {
        scope.launch {
            refreshKnownPackages()
            if (hasUsageAccess()) {
                refreshRunningApps(force = true)
            }
            refreshTopDisplayForeground()
            onLauncherHasFocus(trigger = "launcherResumed")
        }
    }

    private fun poll() {
        try {
            queryLatestForegroundPackage()?.let { pkg ->
                if (pkg != ownPackage && !isSystemUi(pkg)) {
                    lastSeenForegroundAt[pkg] = System.currentTimeMillis()
                }
            }
            refreshTopDisplayForeground()
            if (store.hasActiveSessions()) {
                // Throttle UsageStats refresh (10s) even while active — force only on resume.
                refreshRunningApps(force = false)
                enforceMemoryGuard(trigger = "poll")
                verifyActiveSessions(trigger = "poll")
                if (detectionEnabled) {
                    discoverAdditionalGamingSessions(trigger = "poll")
                }
                return
            }

            if (!detectionEnabled) return

            val foreground = queryLatestForegroundPackage()
            when {
                foreground == null -> Unit
                foreground == ownPackage -> onLauncherHasFocus(trigger = "poll/wajiha")
                isSystemUi(foreground) -> Unit
                else -> onForegroundPackage(foreground)
            }
        } catch (e: Exception) {
            WajihaLog.w(WajihaTags.NOW_PLAYING, "poll failed — ${e.message}")
        }
    }

    private fun refreshTopDisplayForeground() {
        val resolved = TopDisplayForegroundResolver.foregroundPackage(context)
        val previous = store.topDisplayForegroundPackage.value

        when {
            resolved == ownPackage -> {
                // Use the task on display 0, not the grid-featured session (nowPlaying).
                val topSession = topDisplaySession()
                if (topSession != null && isGamingPackage(topSession.packageName)) {
                    val overlay = queryLatestNonLauncherForeground()
                    val gameResume = queryLatestResumeTime(topSession.packageName)
                    val overlayResume = overlay?.let { queryLatestResumeTime(it) } ?: 0L
                    if (overlay != null && overlay != ownPackage &&
                        overlay != topSession.packageName && !isSystemUi(overlay) &&
                        overlayResume > gameResume
                    ) {
                        applyTopDisplayForeground(
                            overlay,
                            reason = "overlay (resolver=$ownPackage)",
                        )
                        return
                    }
                    if (sessionProcessAlive(topSession)) {
                        applyTopDisplayForeground(
                            topSession.packageName,
                            reason = "dual-play (resolver=$ownPackage)",
                        )
                        return
                    }
                }
                if (!store.hasActiveSessions()) {
                    applyTopDisplayForeground(ownPackage, reason = "launcher on top")
                }
                return
            }

            resolved != null && !isSystemUi(resolved) -> {
                val changed = resolved != previous
                applyTopDisplayForeground(resolved, reason = "resolved")
                if (!changed) return
                if (!isGamingPackage(resolved)) return
                if (store.getSession(resolved) != null) return
                if (detectionEnabled) {
                    clearRediscoverySuppression(resolved)
                    WajihaLog.i(WajihaTags.NOW_PLAYING, "topDisplay: detect $resolved")
                    beginOrUpdateExternalSession(resolved, allowSuppressed = false)
                }
            }

            else -> {
                inferTopDisplayGamingPackage()?.let { pkg ->
                    applyTopDisplayForeground(pkg, reason = "infer")
                }
            }
        }
    }

    /**
     * Publishes top-display foreground with hysteresis so [DualScreenStore.nowPlayingUiState]
     * does not flicker when the resolver alternates game ↔ launcher during alt-tab.
     *
     * - Game on top: commit immediately (show chip).
     * - Alt-tab to another app: debounce hide briefly so transient resolver noise is ignored.
     * - Launcher resolved under an alive game: keep the gaming package, never publish wajiha.
     */
    private fun applyTopDisplayForeground(
        desired: String,
        reason: String,
    ) {
        val topSession = topDisplaySession()
        val previous = store.topDisplayForegroundPackage.value
        val normalized =
            when {
                desired == ownPackage && topSession != null &&
                    isGamingPackage(topSession.packageName) &&
                    sessionProcessAlive(topSession) -> topSession.packageName

                else -> desired
            }

        if (topSession != null && normalized == topSession.packageName) {
            pendingTopHidePackage = null
            commitTopDisplayForeground(normalized, previous, reason)
            return
        }

        if (topSession != null && previous == topSession.packageName &&
            normalized != topSession.packageName && normalized != ownPackage
        ) {
            val now = System.currentTimeMillis()
            if (pendingTopHidePackage != normalized) {
                pendingTopHidePackage = normalized
                pendingTopHideSince = now
                return
            }
            if (now - pendingTopHideSince < TOP_GAME_HIDE_DEBOUNCE_MS) return
        } else {
            pendingTopHidePackage = null
        }

        commitTopDisplayForeground(normalized, previous, reason)
    }

    private fun commitTopDisplayForeground(
        packageName: String,
        previous: String?,
        reason: String,
    ) {
        if (packageName == previous) return
        store.setTopDisplayForeground(packageName)
        WajihaLog.d(
            WajihaTags.NOW_PLAYING,
            "topDisplay: ${previous ?: "none"} -> $packageName ($reason)",
        )
    }

    /** Session actually on display 0 — distinct from grid-featured [DualScreenStore.nowPlaying]. */
    private fun topDisplaySession(): NowPlayingState? =
        store.topDisplayForegroundPackage.value?.let { store.getSession(it) }
            ?: store.activeSessions().firstOrNull { session ->
                isGamingPackage(session.packageName) && sessionProcessAlive(session)
            }

    /** Infer top-display gaming package when resolver is blocked during an active session. */
    private fun inferTopDisplayGamingPackage(): String? {
        val active =
            store
                .activeSessions()
                .filter { isGamingPackage(it.packageName) && isSessionStillActive(it) }
        if (active.isEmpty()) return null
        val topPkg = store.topDisplayForegroundPackage.value
        return active.firstOrNull { it.packageName == topPkg }?.packageName
            ?: active.maxByOrNull { it.sessionStartedAt }?.packageName
    }

    private fun onLauncherHasFocus(trigger: String) {
        refreshTopDisplayForeground()
        val sessions = store.activeSessions()
        if (sessions.isNotEmpty()) {
            val multiSession = sessions.size > 1
            sessions.forEach { session ->
                if (sessionProcessAlive(session)) {
                    sessionController.onSessionStarted(session.packageName)
                    return@forEach
                }
                if (sessionController.isWithinLaunchGrace(
                        session.sessionStartedAt,
                        session.packageName,
                    )
                ) {
                    WajihaLog.d(
                        WajihaTags.NOW_PLAYING,
                        "launcherFocus($trigger): keep ${session.packageName} (launch grace)",
                    )
                    return@forEach
                }
                if (!isSessionStillActive(session)) {
                    if (sessionController.recordAliveCheck(
                            session.packageName,
                            processAlive = false,
                            multiSession = multiSession,
                        )
                    ) {
                        if (sessionProcessAlive(session)) {
                            sessionController.onSessionStarted(session.packageName)
                            WajihaLog.d(
                                WajihaTags.NOW_PLAYING,
                                "launcherFocus($trigger): keep ${session.packageName} " +
                                    "(alive recheck)",
                            )
                            return@forEach
                        }
                        endSession(session.packageName, trigger = "$trigger/confirmed-end")
                    } else {
                        WajihaLog.d(
                            WajihaTags.NOW_PLAYING,
                            "launcherFocus($trigger): ${session.packageName} end pending " +
                                "(awaiting confirm)",
                        )
                    }
                    return@forEach
                }
                sessionController.recordAliveCheck(
                    session.packageName,
                    processAlive = true,
                    multiSession = multiSession,
                )
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "launcherFocus($trigger): keep ${session.packageName}",
                )
                maybeResolveExternalSession(session)
            }
            if (detectionEnabled) {
                discoverAdditionalGamingSessions(trigger)
            }
            return
        }

        if (!detectionEnabled) return
        findExternalGamingPackage()?.let { pkg ->
            if (isRediscoverySuppressed(pkg)) return
            WajihaLog.i(WajihaTags.NOW_PLAYING, "launcherFocus($trigger): detect $pkg")
            beginOrUpdateExternalSession(pkg)
        }
    }

    private fun verifyActiveSessions(trigger: String) {
        val sessions = store.activeSessions().toList()
        val multiSession = sessions.size > 1
        sessions.forEach { session ->
            if (sessionProcessAlive(session)) {
                sessionController.onSessionStarted(session.packageName)
                return@forEach
            }
            if (sessionController.isWithinLaunchGrace(
                    session.sessionStartedAt,
                    session.packageName,
                )
            ) {
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "verify($trigger): keep ${session.packageName} (launch grace)",
                )
                return@forEach
            }
            val overlay = overlayCoveringGame(session)
            if (overlay != null) {
                sessionController.recordAliveCheck(
                    session.packageName,
                    processAlive = true,
                    multiSession = multiSession,
                )
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "verify($trigger): keep ${session.packageName} (overlay=$overlay)",
                )
                return@forEach
            }
            if (isSessionStillActive(session)) {
                sessionController.recordAliveCheck(
                    session.packageName,
                    processAlive = true,
                    multiSession = multiSession,
                )
                maybeResolveExternalSession(session)
                return@forEach
            }
            if (sessionController.recordAliveCheck(
                    session.packageName,
                    processAlive = false,
                    multiSession = multiSession,
                )
            ) {
                if (sessionProcessAlive(session)) {
                    sessionController.onSessionStarted(session.packageName)
                    WajihaLog.d(
                        WajihaTags.NOW_PLAYING,
                        "verify($trigger): keep ${session.packageName} (alive recheck)",
                    )
                    return@forEach
                }
                endSession(session.packageName, trigger = "$trigger/confirmed-end")
            } else {
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "verify($trigger): ${session.packageName} end pending (awaiting confirm)",
                )
            }
        }
    }

    /** Non-game app on the top display while this session is backgrounded (alt-tab). */
    private fun overlayCoveringGame(session: NowPlayingState): String? {
        val top = store.topDisplayForegroundPackage.value ?: return null
        if (top == ownPackage || top == session.packageName || isGamingPackage(top)) return null
        return top
    }

    /**
     * Session is active while the game process/task survives, even when another emulator
     * owns the top display (multi-session on Thor).
     */
    private fun isSessionStillActive(session: NowPlayingState): Boolean {
        val pkg = session.packageName
        val since = session.sessionStartedAt
        if (sessionProcessAlive(session)) return true
        if (overlayCoveringGame(session) != null) return true
        if (isPackageOnTopDisplay(pkg)) return true
        return false
    }

    /**
     * Session alive while usage stats, accessibility, launch grace, or a cached task say so.
     * Prefer [store.runningApps] + UsageStats over getRunningTasks (blocked for other packages on Thor).
     */
    private fun sessionProcessAlive(session: NowPlayingState): Boolean = sessionProcessAlive(session.packageName, session.sessionStartedAt)

    private fun sessionProcessAlive(
        packageName: String,
        sessionStartedAt: Long = 0L,
    ): Boolean {
        if (sessionController.isWithinLaunchGrace(sessionStartedAt, packageName)) return true
        // Process / live task first — recent usage alone must not keep a Recents-dismissed
        // session alive (that path used to cold-start the emulator via display reclaim).
        if (hasRunningProcess(packageName)) return true
        if (hasRunningTask(packageName)) return true
        if (SessionTaskRegistry.hasTask(packageName)) {
            // Resolver cannot see the task and the process is gone → Recents dismissed;
            // drop the stale launch-window id so we do not pin/refocus forever.
            SessionTaskRegistry.clear(packageName)
        }
        if (sessionStartedAt > 0L && isUsageTimelineActive(packageName, sessionStartedAt)) return true
        return false
    }

    /** [RunningAppsPanel] source — UsageStats lastTimeUsed within [SESSION_RECENT_USAGE_MS]. */
    private fun isPackageRecentlyUsed(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        store.runningApps.value.firstOrNull { it.packageName == packageName }?.let { app ->
            if (app.lastUsedAt > now - SESSION_RECENT_USAGE_MS) return true
        }
        val lastUsed = queryUsageLastTimeUsed(packageName) ?: return false
        return lastUsed > now - SESSION_RECENT_USAGE_MS
    }

    private fun queryUsageLastTimeUsed(packageName: String): Long? {
        if (!hasUsageAccess()) return null
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats =
                usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    now - SESSION_RECENT_USAGE_MS,
                    now,
                )
            return stats.find { it.packageName == packageName }?.lastTimeUsed?.takeIf { it > 0L }
        } catch (_: Exception) {
            return null
        }
    }

    private fun wasRecentlySeenForeground(packageName: String): Boolean {
        val seenAt = lastSeenForegroundAt[packageName] ?: return false
        return System.currentTimeMillis() - seenAt < RECENT_FOREGROUND_MS
    }

    /** True when the latest usage event for [packageName] is a resume, not a stop. */
    private fun isUsageTimelineActive(
        packageName: String,
        since: Long,
    ): Boolean {
        if (!hasUsageAccess()) return false
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(since, System.currentTimeMillis())
            val event = UsageEvents.Event()
            var lastResume = 0L
            var lastStop = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    -> {
                        if (event.timeStamp >= lastResume) lastResume = event.timeStamp
                    }

                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (event.timeStamp >= lastStop) lastStop = event.timeStamp
                    }
                }
            }
            return lastResume > lastStop
        } catch (_: Exception) {
        }
        return false
    }

    private fun isPackageOnTopDisplay(packageName: String): Boolean {
        val resolved = TopDisplayForegroundResolver.foregroundPackage(context)
        if (resolved == packageName) return true
        if (resolved != null && resolved != ownPackage && resolved != packageName) return false
        if (resolved == null || resolved == ownPackage) return hasRunningTask(packageName)
        return false
    }

    private fun beginOrUpdateExternalSession(
        packageName: String,
        allowSuppressed: Boolean = true,
    ) {
        if (allowSuppressed && isRediscoverySuppressed(packageName)) {
            WajihaLog.d(
                WajihaTags.NOW_PLAYING,
                "discover: skip $packageName (user closed)",
            )
            return
        }
        val existing = store.getSession(packageName)
        if (existing?.packageName == packageName &&
            existing.appLabel == labelOf(packageName) &&
            sessionTracker.isTracking(packageName)
        ) {
            return
        }

        val preserve = existing
        val newState =
            NowPlayingState(
                packageName = packageName,
                appLabel = labelOf(packageName),
                gameId = preserve?.gameId,
                gameName = preserve?.gameName,
                platformId = preserve?.platformId,
                boxartPath = preserve?.boxartPath,
                heroPath = preserve?.heroPath,
                sessionStartedAt = preserve?.sessionStartedAt ?: System.currentTimeMillis(),
                sessionElapsedMs = preserve?.sessionElapsedMs ?: 0L,
                sessionResumedAt = preserve?.sessionResumedAt ?: 0L,
                launchedByWajiha = preserve?.launchedByWajiha ?: false,
            )

        if (existing?.packageName != packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "external session ${existing?.packageName ?: "none"} -> $packageName",
            )
        }

        sessionController.onSessionStarted(packageName)
        sessionTracker.onGameDetected(null, packageName)
        if (existing == null) {
            store.beginGameSession(newState.copy(launchedByWajiha = false))
        } else {
            store.updateGameSession(newState)
        }
        maybeResolveExternalSession(newState.copy(launchedByWajiha = false))
        KeepAliveService.start(context)
    }

    private fun maybeResolveExternalSession(session: NowPlayingState) {
        if (session.launchedByWajiha || session.gameId != null) return
        scope.launch {
            val resolved =
                externalGameResolver.resolve(session.packageName, session.sessionStartedAt)
                    ?: return@launch
            enrichNowPlaying(session, resolved)
        }
    }

    private fun enrichNowPlaying(
        session: NowPlayingState,
        resolved: ResolvedGame,
    ) {
        val cached = store.getSession(session.packageName) ?: return
        if (cached.gameId != null && resolved.gameId == null) return

        val enriched =
            cached.copy(
                gameId = resolved.gameId ?: cached.gameId,
                gameName = resolved.displayName.takeIf { it.isNotBlank() } ?: cached.gameName,
                platformId = resolved.platformId ?: cached.platformId,
                boxartPath = resolved.boxartPath ?: cached.boxartPath,
                heroPath = resolved.heroPath ?: cached.heroPath,
            )
        if (enriched == cached) return
        store.updateGameSession(enriched)
        resolved.gameId?.let { gameId ->
            sessionTracker.updateGameId(gameId, session.packageName)
        }
    }

    private fun endSession(
        packageName: String,
        trigger: String,
    ) {
        WajihaLog.i(WajihaTags.NOW_PLAYING, "endSession: $packageName ($trigger)")
        // Match Y-close: block rediscovery while a dismissed process lingers.
        suppressRediscovery(packageName)
        SessionTaskRegistry.clear(packageName)
        lastSeenForegroundAt.remove(packageName)
        externalGameResolver.clearSession(packageName)
        sessionController.onSessionEnded(packageName)
        sessionTracker.onGameEnded(packageName)
        store.endGameSession(packageName)
        if (lastForeground == packageName) {
            lastForeground = null
        }
        if (!store.hasActiveSessions()) {
            displayCoordinator.restorePrimaryHero()
        }
    }

    private fun findExternalGamingPackage(): String? {
        val alive = findAliveGamingPackages().filter { hasEmulatorProcessOrTask(it) }
        if (alive.isEmpty()) return null
        return alive
            .maxByOrNull { pkg ->
                store.runningApps.value
                    .find { it.packageName == pkg }
                    ?.lastUsedAt ?: 0L
            }?.takeUnless { isRediscoverySuppressed(it) }
    }

    /** Register any live emulator not yet in [DualScreenStore.sessionCache]. */
    private fun discoverAdditionalGamingSessions(trigger: String) {
        findAliveGamingPackages().forEach { pkg ->
            if (store.getSession(pkg) != null) return@forEach
            if (!hasEmulatorProcessOrTask(pkg)) return@forEach
            if (isRediscoverySuppressed(pkg)) {
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "discover($trigger): skip $pkg (user closed)",
                )
                return@forEach
            }
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "discover($trigger): additional session $pkg",
            )
            beginOrUpdateExternalSession(pkg)
        }
    }

    private fun findAliveGamingPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        packages.addAll(findGamingPackagesFromProcesses())
        packages.addAll(findGamingPackagesFromTasks())
        packages.addAll(
            store.runningApps.value
                .filter { it.isGame }
                .map { it.packageName },
        )
        return packages
            .filter { pkg ->
                pkg != ownPackage && !isSystemUi(pkg) && isGamingPackage(pkg) &&
                    !isRediscoverySuppressed(pkg) &&
                    (
                        isPackageRecentlyUsed(pkg) || hasRunningProcess(pkg) ||
                            SessionTaskRegistry.hasTask(pkg)
                    )
            }.toSet()
    }

    private fun findGamingPackagesFromProcesses(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.forEach { proc ->
                proc.pkgList?.forEach { pkg ->
                    if (pkg != ownPackage && !isSystemUi(pkg) && isGamingPackage(pkg)) {
                        packages.add(pkg)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return packages
    }

    private fun findGamingPackagesFromTasks(): Set<String> =
        TopDisplayTaskResolver
            .packagesWithTasks(context)
            .filter { pkg ->
                pkg != ownPackage && !isSystemUi(pkg) && isGamingPackage(pkg)
            }.toSet()

    /**
     * True when [packageName] has any running process, including IMPORTANCE_CACHED.
     * Thor demotes top-display emulators to cached while Wajiha owns the bottom launcher.
     */
    private fun hasRunningProcess(packageName: String): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val listed =
                am.runningAppProcesses?.any { proc ->
                    proc.pkgList?.contains(packageName) == true
                } ?: false
            if (listed) return true
        } catch (_: Exception) {
        }
        return pidOf(packageName) != null
    }

    private fun hasRunningTask(packageName: String): Boolean = TopDisplayTaskResolver.taskIdForPackage(context, packageName) != null

    /**
     * Process or top-display task — excludes usage-stats-only hits after force-stop
     * that otherwise recreate ghost tiles on a clean launcher resume.
     */
    private fun hasEmulatorProcessOrTask(packageName: String): Boolean =
        hasRunningProcess(packageName) ||
            SessionTaskRegistry.hasTask(packageName) ||
            hasRunningTask(packageName)

    private fun queryLatestForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - EVENT_WINDOW_MS, end)
        var latestPackage: String? = null
        var latestTime = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                event.timeStamp >= latestTime
            ) {
                latestTime = event.timeStamp
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    /** Latest non-launcher resume — catches Chrome overlay when resolver returns wajiha. */
    private fun queryLatestNonLauncherForeground(): String? {
        if (!hasUsageAccess()) return null
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - EVENT_WINDOW_MS, end)
            var latestPackage: String? = null
            var latestTime = 0L
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
                if (event.timeStamp < latestTime) continue
                val pkg = event.packageName
                if (pkg == ownPackage || isSystemUi(pkg)) continue
                latestTime = event.timeStamp
                latestPackage = pkg
            }
            return latestPackage
        } catch (_: Exception) {
            return null
        }
    }

    private fun queryLatestResumeTime(packageName: String): Long {
        if (!hasUsageAccess()) return 0L
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - EVENT_WINDOW_MS, end)
            var latestTime = 0L
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.timeStamp >= latestTime
                ) {
                    latestTime = event.timeStamp
                }
            }
            return latestTime
        } catch (_: Exception) {
            return 0L
        }
    }

    private var lastRunningRefresh = 0L

    private fun refreshRunningApps(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRunningRefresh < RUNNING_APPS_REFRESH_MS) return
        lastRunningRefresh = now
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats =
                usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - RUNNING_APPS_WINDOW_MS,
                    now,
                )
            val pm = context.packageManager
            val apps =
                stats
                    .asSequence()
                    .filter { it.lastTimeUsed > now - RUNNING_APPS_WINDOW_MS }
                    .filter { it.packageName != ownPackage && !isSystemUi(it.packageName) }
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .sortedByDescending { it.lastTimeUsed }
                    .distinctBy { it.packageName }
                    .take(12)
                    .map { stat ->
                        RunningApp(
                            packageName = stat.packageName,
                            label = labelOf(stat.packageName) ?: stat.packageName,
                            lastUsedAt = stat.lastTimeUsed,
                            isGame = isGamingPackage(stat.packageName),
                        )
                    }.toList()
            store.setRunningApps(apps)
        } catch (_: Exception) {
        }
    }

    /** Debug adb: log active sessions, top display, and rediscovery suppression. */
    fun debugDumpState() {
        val now = System.currentTimeMillis()
        val top = store.topDisplayForegroundPackage.value
        val featured = store.nowPlaying.value
        val sessions = store.activeSessions.value
        WajihaLog.i(WajihaTags.DEBUG, "=== session dump ===")
        WajihaLog.i(WajihaTags.DEBUG, "topDisplay: ${top ?: "none"}")
        WajihaLog.i(
            WajihaTags.DEBUG,
            "featured(nowPlaying): ${featured?.packageName ?: "none"}",
        )
        WajihaLog.i(
            WajihaTags.DEBUG,
            "detectionEnabled=$detectionEnabled lastForeground=${lastForeground ?: "none"}",
        )
        WajihaLog.i(WajihaTags.DEBUG, "usageAccess=${hasUsageAccess()}")
        if (sessions.isEmpty()) {
            WajihaLog.i(WajihaTags.DEBUG, "activeSessions: (none)")
        } else {
            sessions.forEachIndexed { index, session ->
                WajihaLog.i(
                    WajihaTags.DEBUG,
                    "session[$index]: pkg=${session.packageName} " +
                        "game=${session.gameName ?: session.appLabel ?: "?"} " +
                        "onTop=${session.packageName == top} " +
                        "wajihaLaunch=${session.launchedByWajiha} " +
                        "elapsedMs=${session.activeElapsedMs(now)}",
                )
            }
        }
        val suppress =
            suppressRediscoveryUntil.entries
                .map { (pkg, until) ->
                    val remaining = (until - now).coerceAtLeast(0)
                    "$pkg(${remaining}ms)"
                }.sorted()
        WajihaLog.i(
            WajihaTags.DEBUG,
            "suppressRediscovery: ${if (suppress.isEmpty()) "(none)" else suppress.joinToString()}",
        )
        WajihaLog.i(WajihaTags.DEBUG, "=== end dump ===")
    }

    /** Debug adb: run the same refresh path as launcher resume / active poll. */
    fun debugForceRefresh() {
        scope.launch {
            refreshKnownPackages()
            if (hasUsageAccess()) {
                refreshRunningApps(force = true)
            }
            refreshTopDisplayForeground()
            if (store.hasActiveSessions()) {
                verifyActiveSessions(trigger = "debug-refresh")
                if (detectionEnabled) {
                    discoverAdditionalGamingSessions(trigger = "debug-refresh")
                }
            } else if (detectionEnabled && hasUsageAccess()) {
                queryLatestForegroundPackage()?.let { onForegroundPackage(it) }
            }
            WajihaLog.i(WajihaTags.DEBUG, "debugForceRefresh: complete")
        }
    }

    /** Debug adb: clear all Y-close rediscovery suppression entries. */
    fun debugClearSuppressList() {
        val cleared = suppressRediscoveryUntil.keys.sorted()
        suppressRediscoveryUntil.clear()
        WajihaLog.i(
            WajihaTags.DEBUG,
            "debugClearSuppressList: cleared ${if (cleared.isEmpty()) "(none)" else cleared}",
        )
    }

    fun killApp(packageName: String) {
        WajihaLog.i(WajihaTags.NOW_PLAYING, "killApp: $packageName")
        suppressRediscovery(packageName)
        try {
            PackageKiller.forceStopPackageBestEffort(context, packageName)
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            store.setRunningApps(store.runningApps.value.filterNot { it.packageName == packageName })
            SessionTaskRegistry.clear(packageName)
            lastSeenForegroundAt.remove(packageName)
            if (store.getSession(packageName) != null) {
                endSession(packageName, trigger = "killApp")
            }
        } catch (e: Exception) {
            WajihaLog.w(WajihaTags.NOW_PLAYING, "killApp: failed $packageName — ${e.message}")
        }
    }

    /**
     * Force-stop runaway emulator sessions under memory pressure.
     *
     * Primary signal is system-wide [ActivityManager.MemoryInfo] (documented for
     * third-party apps). Per-session RSS is best-effort only: since Android Q,
     * [ActivityManager.getProcessMemoryInfo] returns zeros for other UIDs and is
     * rate-limited (~5 min); `/proc/<pid>/status` is typically SELinux-blocked.
     */
    fun enforceMemoryGuard(trigger: String = "trim") {
        if (!memoryGuardEnabled) return
        if (!store.hasActiveSessions()) return
        val now = System.currentTimeMillis()
        if (now - lastMemoryGuardAtMs.get() < MEMORY_GUARD_DEBOUNCE_MS) return

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memTotal = memTotalBytes()

        // Best-effort RSS probe (infrequent — API is throttled + usually zeros for others).
        if (now - lastRssProbeAtMs >= RSS_PROBE_INTERVAL_MS) {
            lastRssProbeAtMs = now
            val rssCap =
                minOf(SESSION_RSS_CAP_BYTES, (memTotal * SESSION_RSS_CAP_FRACTION).toLong())
                    .coerceAtLeast(1L)
            val fatOffenders = findOversizedSessions(am, rssCap)
            if (fatOffenders.isNotEmpty()) {
                lastMemoryGuardAtMs.set(now)
                val labels =
                    fatOffenders.map { (pkg, rss) ->
                        val label = sessionLabel(pkg)
                        killApp(pkg)
                        WajihaLog.w(
                            WajihaTags.NOW_PLAYING,
                            "memoryGuard[$trigger]: RSS kill $pkg rss=${formatBytes(rss)} " +
                                "cap=${formatBytes(rssCap)}",
                        )
                        label to rss
                    }
                val reason =
                    labels.joinToString("; ") { (label, rss) ->
                        "$label RSS ${formatBytes(rss)}"
                    }
                finishMemoryGuard(
                    killedLabels = labels.map { it.first },
                    reason = reason,
                )
                return
            }
        }

        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availCritical = maxOf(memInfo.threshold * 3, memTotal / 10)
        val systemCritical = memInfo.lowMemory || memInfo.availMem < availCritical
        if (!systemCritical) return

        lastMemoryGuardAtMs.set(now)

        val topPkg = store.topDisplayForegroundPackage.value
        val background =
            store
                .activeSessions()
                .map { it.packageName }
                .filter { it != topPkg }
                .distinct()
        val killedLabels = mutableListOf<String>()
        background.forEach { pkg ->
            killedLabels += sessionLabel(pkg)
            killApp(pkg)
            WajihaLog.w(
                WajihaTags.NOW_PLAYING,
                "memoryGuard[$trigger]: system kill background $pkg " +
                    "avail=${formatBytes(memInfo.availMem)} low=${memInfo.lowMemory}",
            )
        }

        am.getMemoryInfo(memInfo)
        val stillCritical = memInfo.lowMemory || memInfo.availMem < availCritical
        if (stillCritical) {
            store.activeSessions().map { it.packageName }.distinct().forEach { pkg ->
                killedLabels += sessionLabel(pkg)
                killApp(pkg)
                WajihaLog.w(
                    WajihaTags.NOW_PLAYING,
                    "memoryGuard[$trigger]: system kill top/remaining $pkg " +
                        "avail=${formatBytes(memInfo.availMem)}",
                )
            }
        }

        if (killedLabels.isEmpty()) return
        finishMemoryGuard(
            killedLabels = killedLabels.distinct(),
            reason = "low system memory (avail ${formatBytes(memInfo.availMem)})",
        )
    }

    private fun finishMemoryGuard(
        killedLabels: List<String>,
        reason: String,
    ) {
        displayCoordinator.deferReclaimForMemoryGuard()
        MemoryGuardNotifier.notifyClosed(context, killedLabels, reason)
    }

    private fun sessionLabel(packageName: String): String {
        val session = store.getSession(packageName)
        return sessionDisplayLabel(session)
            ?: labelOf(packageName)
            ?: packageName
    }

    private fun findOversizedSessions(
        am: ActivityManager,
        rssCapBytes: Long,
    ): List<Pair<String, Long>> {
        val sessions = store.activeSessions().map { it.packageName }.distinct()
        if (sessions.isEmpty()) return emptyList()
        val pidByPackage = mutableMapOf<String, Int>()
        try {
            am.runningAppProcesses?.forEach { proc ->
                proc.pkgList?.forEach { pkg ->
                    if (pkg in sessions && proc.pid > 0) {
                        pidByPackage.putIfAbsent(pkg, proc.pid)
                    }
                }
            }
        } catch (_: Exception) {
        }
        sessions.forEach { pkg ->
            if (pkg !in pidByPackage) {
                pidOf(pkg)?.let { pidByPackage[pkg] = it }
            }
        }
        if (pidByPackage.isEmpty()) return emptyList()

        val packages = pidByPackage.keys.toList()
        val pids = IntArray(packages.size) { pidByPackage.getValue(packages[it]) }
        val memInfos =
            try {
                am.getProcessMemoryInfo(pids)
            } catch (_: Exception) {
                return emptyList()
            }
        val offenders = mutableListOf<Pair<String, Long>>()
        packages.forEachIndexed { index, pkg ->
            val info = memInfos.getOrNull(index) ?: return@forEachIndexed
            val pid = pids[index]
            val rssKb = processRssKb(info, pid)
            val rssBytes = rssKb * 1024L
            if (rssBytes >= rssCapBytes) {
                offenders += pkg to rssBytes
            }
        }
        if (offenders.isEmpty() && !loggedRssBlind) {
            val anyReadable =
                packages.indices.any { index ->
                    processRssKb(memInfos.getOrNull(index) ?: return@any false, pids[index]) > 0
                }
            if (!anyReadable) {
                loggedRssBlind = true
                WajihaLog.i(
                    WajihaTags.NOW_PLAYING,
                    "memoryGuard: per-app RSS unavailable " +
                        "(Android Q+ zeros /proc blocked) — using system MemoryInfo only",
                )
            }
        }
        return offenders
    }

    private fun processRssKb(
        info: Debug.MemoryInfo,
        pid: Int,
    ): Int {
        // Prefer /proc VmRSS — matches LMK's view of native emulator balloons
        // better than summary.total-rss / PSS (which under-count EE guest RAM).
        val fromProc = readProcRssKb(pid)
        if (fromProc > 0) return fromProc
        val fromStat = info.getMemoryStat("summary.total-rss")?.toIntOrNull()
        if (fromStat != null && fromStat > 0) return fromStat
        return info.totalPss
    }

    private fun readProcRssKb(pid: Int): Int {
        if (pid <= 0) return 0
        return try {
            File("/proc/$pid/status").useLines { lines ->
                val line = lines.firstOrNull { it.startsWith("VmRSS:") } ?: return@useLines 0
                line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun pidOf(packageName: String): Int? =
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/pidof", packageName))
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            process.waitFor()
            output
                .split(Regex("\\s+"))
                .firstOrNull { it.isNotEmpty() }
                ?.toIntOrNull()
        } catch (_: Exception) {
            null
        }

    private fun memTotalBytes(): Long {
        if (cachedMemTotalBytes > 0L) return cachedMemTotalBytes
        val fromAm =
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val info = ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                info.totalMem
            } catch (_: Exception) {
                0L
            }
        if (fromAm > 0L) {
            cachedMemTotalBytes = fromAm
            return fromAm
        }
        val fromProc =
            try {
                File("/proc/meminfo").useLines { lines ->
                    val line = lines.firstOrNull { it.startsWith("MemTotal:") } ?: return@useLines 0L
                    val kb = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: return@useLines 0L
                    kb * 1024L
                }
            } catch (_: Exception) {
                0L
            }
        cachedMemTotalBytes = fromProc.coerceAtLeast(8L * 1024 * 1024 * 1024)
        return cachedMemTotalBytes
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format("%.1fGB", gb)
        } else {
            String.format("%.0fMB", bytes / (1024.0 * 1024.0))
        }
    }

    /** User closed a session — suppress rediscovery until process dies or timeout. */
    private fun suppressRediscovery(packageName: String) {
        suppressRediscoveryUntil[packageName] =
            System.currentTimeMillis() + SUPPRESS_REDISCOVERY_MS
        WajihaLog.i(
            WajihaTags.NOW_PLAYING,
            "suppressRediscovery: $packageName for ${SUPPRESS_REDISCOVERY_MS}ms",
        )
    }

    private fun clearRediscoverySuppression(packageName: String) {
        if (suppressRediscoveryUntil.remove(packageName) != null) {
            WajihaLog.d(WajihaTags.NOW_PLAYING, "clearRediscoverySuppression: $packageName")
        }
    }

    private fun isRediscoverySuppressed(packageName: String): Boolean {
        val until = suppressRediscoveryUntil[packageName] ?: return false
        if (System.currentTimeMillis() < until) return true
        suppressRediscoveryUntil.remove(packageName)
        return false
    }

    private fun labelOf(packageName: String): String? =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            null
        }

    private fun isSystemUi(packageName: String): Boolean =
        packageName == "com.android.systemui" ||
            packageName == "android" ||
            packageName.startsWith("com.android.launcher")

    private fun isGamingPackage(packageName: String): Boolean =
        GamingAppCatalog.isKnownGamingPackage(
            packageName = packageName,
            emulatorPackages = knownEmulatorPackages,
            isPlayStoreGame = isGameApp(packageName),
        )

    private fun isGameApp(packageName: String): Boolean =
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
        } catch (_: Exception) {
            false
        }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L

        /** Faster verification while a session is active. */
        const val ACTIVE_POLL_INTERVAL_MS = 750L
        const val EVENT_WINDOW_MS = 10_000L
        const val RUNNING_APPS_REFRESH_MS = 10_000L
        const val RUNNING_APPS_WINDOW_MS = 6 * 60 * 60 * 1000L

        /** Matches [refreshRunningApps] — cached emulators stay in session grid while recently used. */
        const val SESSION_RECENT_USAGE_MS = RUNNING_APPS_WINDOW_MS

        /** Accessibility / poll RESUMED events — sibling background sessions stay alive. */
        const val RECENT_FOREGROUND_MS = 30 * 60 * 1000L
        const val PACKAGES_REFRESH_MS = 60_000L

        /** Ignore brief resolver flips when alt-tabbing away from the game. */
        const val TOP_GAME_HIDE_DEBOUNCE_MS = 400L

        /** After explicit Y-close, block rediscovery while emulator process lingers. */
        const val SUPPRESS_REDISCOVERY_MS = 60_000L

        /** At most one memory-guard escalation pass this often. */
        const val MEMORY_GUARD_DEBOUNCE_MS = 5_000L

        /**
         * How often to attempt per-session RSS probes. [getProcessMemoryInfo] is
         * throttled (~5 min) and returns zeros for other UIDs on Android Q+.
         */
        const val RSS_PROBE_INTERVAL_MS = 30_000L

        /** Absolute per-session RSS cap (~4 GB) — only if readings are non-zero. */
        const val SESSION_RSS_CAP_BYTES = 4L * 1024 * 1024 * 1024

        /** Relative per-session RSS cap vs device MemTotal. */
        const val SESSION_RSS_CAP_FRACTION = 0.35
    }
}
