package com.wajiha.android.monitor

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import com.wajiha.android.detect.ExternalGameResolver
import com.wajiha.android.detect.ResolvedGame
import com.wajiha.android.launch.PlaySessionTracker
import com.wajiha.android.service.KeepAliveService
import com.wajiha.domain.GamingAppCatalog
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import com.wajiha.state.RunningApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects externally launched games and verifies when a session ends.
 *
 * Session lifecycle:
 * - **Start**: [onSessionStarted] from [com.wajiha.android.launch.GameLauncher] (immediate)
 *   or [onForegroundPackage] when [detectionEnabled] sees a gaming package.
 * - **UI**: [DualScreenStore.nowPlaying] is the single source of truth; [DualScreenStore.secondaryMode]
 *   is independent navigation (grid + chip vs full Now Playing screen).
 * - **End**: only after [GameSessionController.END_CONFIRM_POLLS] consecutive polls find
 *   an explicit UsageStats ACTIVITY_STOPPED — process/task APIs miss top-display emulators
 *   on Thor; Wajiha foreground on the bottom display is never a session end.
 *
 * Polls every [POLL_INTERVAL_MS] with `UsageStatsManager.queryEvents` for external
 * detection; near-zero cost when a session is already active.
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val store: DualScreenStore,
    private val platformRepository: PlatformRepository,
    private val sessionTracker: PlaySessionTracker,
    private val externalGameResolver: ExternalGameResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val sessionController = GameSessionController()

    private var knownEmulatorPackages: Set<String> = emptySet()
    private var lastForeground: String? = null

    /** Debounce alt-tab away from game before hiding Now Playing chip. */
    private var pendingTopHidePackage: String? = null
    private var pendingTopHideSince: Long = 0L

    /** Mirrored from settings (detectManualLaunches). */
    @Volatile
    var detectionEnabled: Boolean = true

    private val ownPackage = context.packageName

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent() =
        android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Call from [com.wajiha.android.launch.GameLauncher] right after [DualScreenStore.beginGameSession]. */
    fun onSessionStarted(packageName: String) {
        sessionController.onSessionStarted(packageName)
        lastForeground = packageName
        pendingTopHidePackage = null
        store.setTopDisplayForeground(packageName)
        WajihaLog.i(WajihaTags.NOW_PLAYING, "onSessionStarted: $packageName")
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var lastPackagesRefresh = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now - lastPackagesRefresh > PACKAGES_REFRESH_MS) {
                    lastPackagesRefresh = now
                    refreshKnownPackages()
                }
                if (hasUsageAccess()) {
                    poll()
                    refreshRunningApps(force = false)
                    store.nowPlaying.value?.let { maybeReprobeActiveSession(it) }
                }
                val interval = if (store.hasActiveSessions()) {
                    ACTIVE_POLL_INTERVAL_MS
                } else {
                    POLL_INTERVAL_MS
                }
                delay(interval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun refreshKnownPackages() {
        knownEmulatorPackages = platformRepository.knownEmulatorPackages()
    }

    /** Also called by the accessibility service for instant external detection. */
    fun onForegroundPackage(packageName: String) {
        if (packageName == lastForeground) return
        lastForeground = packageName
        if (!detectionEnabled) return
        if (packageName == ownPackage) return
        if (isSystemUi(packageName)) return

        if (isGamingPackage(packageName)) {
            beginOrUpdateExternalSession(packageName)
        }
    }

    /**
     * Called when MainActivity or SecondaryHomeActivity resumes.
     * Dual-display: bottom launcher foreground is expected — keep session, show grid + chip.
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
            refreshTopDisplayForeground()
            if (store.hasActiveSessions()) {
                verifyActiveSessions(trigger = "poll")
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
        } catch (_: Exception) {
        }
    }

    private fun refreshTopDisplayForeground() {
        val resolved = TopDisplayForegroundResolver.foregroundPackage(context)
        val previous = store.topDisplayForegroundPackage.value

        when {
            resolved == ownPackage -> {
                val session = store.nowPlaying.value
                if (session != null && isGamingPackage(session.packageName)) {
                    val overlay = queryLatestNonLauncherForeground()
                    val gameResume = queryLatestResumeTime(session.packageName)
                    val overlayResume = overlay?.let { queryLatestResumeTime(it) } ?: 0L
                    if (overlay != null && overlay != ownPackage &&
                        overlay != session.packageName && !isSystemUi(overlay) &&
                        overlayResume > gameResume
                    ) {
                        applyTopDisplayForeground(
                            overlay,
                            reason = "overlay (resolver=$ownPackage)"
                        )
                        return
                    }
                    if (gamingProcessLikelyAlive(session)) {
                        applyTopDisplayForeground(
                            session.packageName,
                            reason = "dual-play (resolver=$ownPackage)"
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
                    WajihaLog.i(WajihaTags.NOW_PLAYING, "topDisplay: detect $resolved")
                    beginOrUpdateExternalSession(resolved)
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
    private fun applyTopDisplayForeground(desired: String, reason: String) {
        val session = store.nowPlaying.value
        val previous = store.topDisplayForegroundPackage.value
        val normalized = when {
            desired == ownPackage && session != null && isGamingPackage(session.packageName) &&
                gamingProcessLikelyAlive(session) -> session.packageName
            else -> desired
        }

        if (session != null && normalized == session.packageName) {
            pendingTopHidePackage = null
            commitTopDisplayForeground(normalized, previous, reason)
            return
        }

        if (session != null && previous == session.packageName &&
            normalized != session.packageName && normalized != ownPackage
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
        reason: String
    ) {
        if (packageName == previous) return
        store.setTopDisplayForeground(packageName)
        WajihaLog.d(
            WajihaTags.NOW_PLAYING,
            "topDisplay: ${previous ?: "none"} -> $packageName ($reason)"
        )
    }

    private fun gamingProcessLikelyAlive(session: NowPlayingState): Boolean =
        sessionController.isWithinLaunchGrace(session.sessionStartedAt) ||
            hasRunningProcess(session.packageName) ||
            hasRunningTask(session.packageName) ||
            isUsageTimelineActive(session.packageName, session.sessionStartedAt)

    /** Infer top-display gaming package when resolver is blocked during an active session. */
    private fun inferTopDisplayGamingPackage(): String? {
        val session = store.nowPlaying.value ?: return null
        if (!isGamingPackage(session.packageName)) return null
        return if (isSessionStillActive(session)) session.packageName else null
    }

    private fun onLauncherHasFocus(trigger: String) {
        refreshTopDisplayForeground()
        val sessions = store.activeSessions()
        if (sessions.isNotEmpty()) {
            sessions.forEach { session ->
                if (sessionController.isWithinLaunchGrace(session.sessionStartedAt)) {
                    WajihaLog.d(
                        WajihaTags.NOW_PLAYING,
                        "launcherFocus($trigger): keep ${session.packageName} (launch grace)"
                    )
                    return@forEach
                }
                if (!isSessionStillActive(session)) {
                    if (sessionController.recordAliveCheck(session.packageName, processAlive = false)) {
                        endSession(session.packageName, trigger = "$trigger/confirmed-end")
                    } else {
                        WajihaLog.d(
                            WajihaTags.NOW_PLAYING,
                            "launcherFocus($trigger): ${session.packageName} end pending " +
                                "(awaiting confirm)"
                        )
                    }
                    return@forEach
                }
                sessionController.recordAliveCheck(session.packageName, processAlive = true)
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "launcherFocus($trigger): keep ${session.packageName} " +
                        sessionAliveReason(session)
                )
                maybeResolveExternalSession(session)
            }
            store.showGridDuringSession()
            return
        }

        if (!detectionEnabled) return
        findExternalGamingPackage()?.let { pkg ->
            WajihaLog.i(WajihaTags.NOW_PLAYING, "launcherFocus($trigger): detect $pkg")
            beginOrUpdateExternalSession(pkg)
        }
    }

    private fun verifyActiveSessions(trigger: String) {
        store.activeSessions().forEach { session ->
            if (sessionController.isWithinLaunchGrace(session.sessionStartedAt)) {
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "verify($trigger): keep ${session.packageName} (launch grace)"
                )
                return@forEach
            }
            val overlay = overlayCoveringGame(session)
            if (overlay != null) {
                sessionController.recordAliveCheck(session.packageName, processAlive = true)
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "verify($trigger): keep ${session.packageName} (overlay=$overlay)"
                )
                return@forEach
            }
            if (isSessionStillActive(session)) {
                sessionController.recordAliveCheck(session.packageName, processAlive = true)
                maybeReprobeActiveSession(session)
                return@forEach
            }
            if (sessionController.recordAliveCheck(session.packageName, processAlive = false)) {
                endSession(session.packageName, trigger = "$trigger/confirmed-end")
            } else {
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "verify($trigger): ${session.packageName} end pending (awaiting confirm)"
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
     * Session is active while the game has foreground importance or a visible top-display
     * task. Cached-only processes after ACTIVITY_STOPPED / MOVE_TO_BACKGROUND count as ended.
     */
    private fun isSessionStillActive(session: NowPlayingState): Boolean {
        val pkg = session.packageName
        val since = session.sessionStartedAt
        if (sessionController.isWithinLaunchGrace(since)) return true
        if (overlayCoveringGame(session) != null) return true
        // Process/task outlive usage STOPPED during alt-tab — never end on timeline alone.
        if (hasRunningProcess(pkg) || hasRunningTask(pkg)) return true
        if (isUsageTimelineActive(pkg, since)) return true
        return isPackageOnTopDisplay(pkg)
    }

    /** True when the latest usage event for [packageName] is a resume, not a stop. */
    private fun isUsageTimelineActive(packageName: String, since: Long): Boolean {
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
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
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

    private fun sessionAliveReason(session: NowPlayingState): String = when {
        sessionController.isWithinLaunchGrace(session.sessionStartedAt) -> "(launch grace)"
        isUsageTimelineActive(session.packageName, session.sessionStartedAt) -> "(usage timeline active)"
        isProcessForeground(session.packageName) -> "(foreground process)"
        isPackageOnTopDisplay(session.packageName) -> "(top-display task)"
        hasRunningProcess(session.packageName) -> "(process listed)"
        hasRunningTask(session.packageName) -> "(background task)"
        else -> "(gone)"
    }

    private fun isPackageOnTopDisplay(packageName: String): Boolean {
        val resolved = TopDisplayForegroundResolver.foregroundPackage(context)
        if (resolved == packageName) return true
        if (resolved != null && resolved != ownPackage && resolved != packageName) return false
        if (resolved == null || resolved == ownPackage) return hasRunningTask(packageName)
        return false
    }

    private fun isProcessForeground(packageName: String): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.runningAppProcesses?.any { proc ->
                proc.pkgList?.contains(packageName) == true &&
                    proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            } ?: false
        } catch (_: Exception) {
            return false
        }
    }

    private fun hasUsageSessionEndEvent(packageName: String, since: Long): Boolean =
        hasActivityStoppedEvent(packageName, since)

    private fun hasActivityStoppedEvent(packageName: String, since: Long): Boolean {
        if (!hasUsageAccess()) return false
        val now = System.currentTimeMillis()
        if (now - since < STOP_EVENT_MIN_AGE_MS) return false
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
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (event.timeStamp >= lastResume) lastResume = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (event.timeStamp >= lastStop) lastStop = event.timeStamp
                    }
                }
            }
            return lastStop > 0L && lastStop > lastResume
        } catch (_: Exception) {
        }
        return false
    }

    private fun beginOrUpdateExternalSession(packageName: String) {
        val existing = store.getSession(packageName)
        if (existing?.packageName == packageName &&
            existing.appLabel == labelOf(packageName) &&
            sessionTracker.active.value?.packageName == packageName
        ) {
            return
        }

        val preserve = existing
        val newState = NowPlayingState(
            packageName = packageName,
            appLabel = labelOf(packageName),
            gameId = preserve?.gameId,
            gameName = preserve?.gameName,
            platformId = preserve?.platformId,
            boxartPath = preserve?.boxartPath,
            heroPath = preserve?.heroPath,
            sessionStartedAt = preserve?.sessionStartedAt ?: System.currentTimeMillis(),
            launchedByWajiha = preserve?.launchedByWajiha ?: false
        )

        if (existing?.packageName != packageName) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "external session ${existing?.packageName ?: "none"} -> $packageName"
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
            val resolved = externalGameResolver.resolve(session.packageName, session.sessionStartedAt)
                ?: return@launch
            enrichNowPlaying(session, resolved)
        }
    }

    private fun maybeReprobeActiveSession(session: NowPlayingState) {
        if (session.launchedByWajiha || session.gameId != null) return
        maybeResolveExternalSession(session)
    }

    private fun enrichNowPlaying(session: NowPlayingState, resolved: ResolvedGame) {
        val current = store.nowPlaying.value ?: return
        if (current.packageName != session.packageName) return
        if (current.gameId != null && resolved.gameId == null) return

        val enriched = current.copy(
            gameId = resolved.gameId ?: current.gameId,
            gameName = resolved.displayName,
            platformId = resolved.platformId ?: current.platformId,
            boxartPath = resolved.boxartPath ?: current.boxartPath,
            heroPath = resolved.heroPath ?: current.heroPath
        )
        store.updateGameSession(enriched)
        resolved.gameId?.let { gameId ->
            sessionTracker.updateGameId(gameId, session.packageName)
        }
    }

    private fun endSession(packageName: String, trigger: String) {
        WajihaLog.i(WajihaTags.NOW_PLAYING, "endSession: $packageName ($trigger)")
        externalGameResolver.clearSession(packageName)
        sessionController.onSessionEnded(packageName)
        sessionTracker.onGameEnded(packageName)
        store.endGameSession(packageName)
        if (lastForeground == packageName) {
            lastForeground = null
        }
    }

    private fun findExternalGamingPackage(): String? {
        val alive = findAliveGamingPackages()
        if (alive.isEmpty()) return null
        return alive.maxByOrNull { pkg ->
            store.runningApps.value.find { it.packageName == pkg }?.lastUsedAt ?: 0L
        }
    }

    private fun findAliveGamingPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        packages.addAll(findGamingPackagesFromProcesses())
        packages.addAll(findGamingPackagesFromTasks())
        return packages.filter { pkg ->
            pkg != ownPackage && !isSystemUi(pkg) && isGamingPackage(pkg) && isProcessAlive(pkg)
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

    @Suppress("DEPRECATION")
    private fun findGamingPackagesFromTasks(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getRunningTasks(25).forEach { task ->
                val pkg = task.baseActivity?.packageName ?: task.topActivity?.packageName ?: return@forEach
                if (pkg != ownPackage && !isSystemUi(pkg) && isGamingPackage(pkg)) {
                    packages.add(pkg)
                }
            }
        } catch (_: Exception) {
        }
        return packages
    }

    /**
     * True when [packageName] has any running process, including IMPORTANCE_CACHED.
     * Thor demotes top-display emulators to cached while Wajiha owns the bottom launcher.
     */
    private fun isProcessAlive(packageName: String): Boolean {
        if (packageName == ownPackage) return true
        if (hasRunningProcess(packageName)) return true
        return hasRunningTask(packageName)
    }

    private fun hasRunningProcess(packageName: String): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val listed = am.runningAppProcesses?.any { proc ->
                proc.pkgList?.contains(packageName) == true
            } ?: false
            if (listed) return true
        } catch (_: Exception) {
        }
        return isProcessListedByPidof(packageName)
    }

    private fun isProcessListedByPidof(packageName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/pidof", packageName))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output.split(Regex("\\s+")).any { it.isNotEmpty() }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun hasRunningTask(packageName: String): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.getRunningTasks(25).any { task ->
                val pkg = task.baseActivity?.packageName ?: task.topActivity?.packageName
                pkg == packageName
            }
        } catch (_: Exception) {
            return false
        }
    }

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
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - RUNNING_APPS_WINDOW_MS,
                now
            )
            val pm = context.packageManager
            val apps = stats
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
                        isGame = isGamingPackage(stat.packageName)
                    )
                }
                .toList()
            store.setRunningApps(apps)
        } catch (_: Exception) {
        }
    }

    fun killApp(packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            store.setRunningApps(store.runningApps.value.filterNot { it.packageName == packageName })
            if (store.getSession(packageName) != null) {
                endSession(packageName, trigger = "killApp")
            }
        } catch (_: Exception) {
        }
    }

    private fun labelOf(packageName: String): String? = try {
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
            isPlayStoreGame = isGameApp(packageName)
        )

    private fun isGameApp(packageName: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        /** Faster verification while a session is active. */
        const val ACTIVE_POLL_INTERVAL_MS = 750L
        /** Ignore splash/transition STOPPED events right after launch. */
        const val STOP_EVENT_MIN_AGE_MS = 12_000L
        const val EVENT_WINDOW_MS = 10_000L
        const val RUNNING_APPS_REFRESH_MS = 10_000L
        const val RUNNING_APPS_WINDOW_MS = 6 * 60 * 60 * 1000L
        const val PACKAGES_REFRESH_MS = 60_000L
        /** Ignore brief resolver flips when alt-tabbing away from the game. */
        const val TOP_GAME_HIDE_DEBOUNCE_MS = 400L
    }
}
