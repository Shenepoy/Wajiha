package com.wajiha.android.monitor

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import com.wajiha.android.launch.PlaySessionTracker
import com.wajiha.android.service.KeepAliveService
import com.wajiha.domain.GamingAppCatalog
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
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
    private val sessionTracker: PlaySessionTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val sessionController = GameSessionController()

    private var knownEmulatorPackages: Set<String> = emptySet()
    private var lastForeground: String? = null

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
        sessionController.onSessionStarted()
        lastForeground = packageName
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
                }
                delay(POLL_INTERVAL_MS)
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
            onLauncherHasFocus(trigger = "launcherResumed")
        }
    }

    private fun poll() {
        try {
            val session = store.nowPlaying.value
            if (session != null) {
                verifySessionEnd(trigger = "poll")
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

    private fun onLauncherHasFocus(trigger: String) {
        val session = store.nowPlaying.value
        if (session != null) {
            if (sessionController.isWithinLaunchGrace(session.sessionStartedAt)) {
                WajihaLog.d(
                    WajihaTags.NOW_PLAYING,
                    "launcherFocus($trigger): keep ${session.packageName} (launch grace)"
                )
                return
            }
            if (!isSessionStillActive(session)) {
                if (sessionController.recordAliveCheck(processAlive = false)) {
                    endSession(session.packageName, trigger = "$trigger/confirmed-end")
                } else {
                    WajihaLog.d(
                        WajihaTags.NOW_PLAYING,
                        "launcherFocus($trigger): ${session.packageName} end pending " +
                            "(awaiting confirm)"
                    )
                }
                return
            }
            sessionController.recordAliveCheck(processAlive = true)
            WajihaLog.d(
                WajihaTags.NOW_PLAYING,
                "launcherFocus($trigger): keep ${session.packageName} " +
                    sessionAliveReason(session)
            )
            store.showGridDuringSession()
            return
        }

        if (!detectionEnabled) return
        findExternalGamingPackage()?.let { pkg ->
            WajihaLog.i(WajihaTags.NOW_PLAYING, "launcherFocus($trigger): detect $pkg")
            beginOrUpdateExternalSession(pkg)
        }
    }

    private fun verifySessionEnd(trigger: String) {
        val session = store.nowPlaying.value ?: return
        if (sessionController.isWithinLaunchGrace(session.sessionStartedAt)) {
            WajihaLog.d(
                WajihaTags.NOW_PLAYING,
                "verify($trigger): keep ${session.packageName} (launch grace)"
            )
            return
        }
        if (isSessionStillActive(session)) {
            sessionController.recordAliveCheck(processAlive = true)
            return
        }
        if (sessionController.recordAliveCheck(processAlive = false)) {
            endSession(session.packageName, trigger = "$trigger/confirmed-end")
        } else {
            WajihaLog.d(
                WajihaTags.NOW_PLAYING,
                "verify($trigger): ${session.packageName} end pending (awaiting confirm)"
            )
        }
    }

    /**
     * Process/task lists are unreliable on Thor dual-display (top emulator hidden from
     * [ActivityManager]). Treat the session as active until UsageStats reports an explicit
     * [UsageEvents.Event.ACTIVITY_STOPPED] for the package after session start.
     */
    private fun isSessionStillActive(session: NowPlayingState): Boolean {
        if (isProcessAlive(session.packageName)) return true
        return !hasExplicitSessionEndEvent(session.packageName, session.sessionStartedAt)
    }

    private fun sessionAliveReason(session: NowPlayingState): String =
        if (isProcessAlive(session.packageName)) {
            "(process alive)"
        } else {
            "(no stop event — process hidden)"
        }

    private fun hasExplicitSessionEndEvent(packageName: String, since: Long): Boolean {
        if (!hasUsageAccess()) return false
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(since, System.currentTimeMillis())
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == packageName &&
                    event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                ) {
                    return true
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun beginOrUpdateExternalSession(packageName: String) {
        val existing = store.nowPlaying.value
        if (existing?.packageName == packageName &&
            existing.appLabel == labelOf(packageName) &&
            sessionTracker.active.value?.packageName == packageName
        ) {
            return
        }

        val preserve = existing?.takeIf { it.packageName == packageName }
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

        sessionController.onSessionStarted()
        sessionTracker.onGameDetected(null, packageName)
        if (existing == null) {
            store.beginGameSession(newState.copy(launchedByWajiha = false))
        } else {
            store.updateGameSession(newState)
        }
        KeepAliveService.start(context)
    }

    private fun endSession(packageName: String, trigger: String) {
        WajihaLog.i(WajihaTags.NOW_PLAYING, "endSession: $packageName ($trigger)")
        sessionController.onSessionEnded()
        sessionTracker.onGameEnded(packageName)
        store.endGameSession()
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
            return am.runningAppProcesses?.any { proc ->
                proc.pkgList?.contains(packageName) == true
            } ?: false
        } catch (_: Exception) {
            return false
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
            if (store.nowPlaying.value?.packageName == packageName) {
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
        const val EVENT_WINDOW_MS = 10_000L
        const val RUNNING_APPS_REFRESH_MS = 10_000L
        const val RUNNING_APPS_WINDOW_MS = 6 * 60 * 60 * 1000L
        const val PACKAGES_REFRESH_MS = 60_000L
    }
}
