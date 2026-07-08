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
 * Detects the foreground app via UsageStatsManager event polling (the
 * "better than Cocoon" requirement): when a known emulator or game package
 * comes to the foreground — even if launched manually from another launcher
 * or app drawer — the secondary screen is switched to Now Playing.
 *
 * Polls every [POLL_INTERVAL_MS] with `queryEvents` over a short sliding
 * window; near-zero cost when nothing changed. The optional
 * [GameDetectAccessibilityService] provides event-driven detection when the
 * user grants accessibility access.
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val store: DualScreenStore,
    private val platformRepository: PlatformRepository,
    private val sessionTracker: PlaySessionTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

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

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var lastPackagesRefresh = 0L
            while (isActive) {
                // Re-read periodically: config imports/seeding change the list
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

    /** Also called by the accessibility service for instant detection. */
    fun onForegroundPackage(packageName: String) {
        if (packageName == lastForeground) return
        lastForeground = packageName
        if (!detectionEnabled) return
        if (packageName == ownPackage) return
        if (isSystemUi(packageName)) return

        if (isGamingPackage(packageName)) {
            applyGamingForeground(packageName)
        }
    }

    private fun poll() {
        try {
            val foreground = queryLatestForegroundPackage()
            foreground?.let { pkg ->
                if (pkg == ownPackage) {
                    reconcileBackgroundGamingApp(trigger = "poll/wajiha")
                } else {
                    onForegroundPackage(pkg)
                }
            }
            verifyNowPlayingStillAlive(trigger = "poll")
        } catch (_: Exception) {
        }
    }

    /**
     * Called when the launcher activity resumes. Keeps [NowPlayingState] alive
     * if a gaming app is still in recent usage; otherwise clears it.
     */
    fun onLauncherForegrounded() {
        scope.launch {
            refreshKnownPackages()
            if (hasUsageAccess()) {
                refreshRunningApps(force = true)
            }
            reconcileBackgroundGamingApp(trigger = "launcherResumed")
            verifyNowPlayingStillAlive(trigger = "launcherResumed")
        }
    }

    private fun reconcileBackgroundGamingApp(trigger: String) {
        val gaming = findMostRecentGamingApp()
        val existing = store.nowPlaying.value
        if (gaming != null) {
            if (existing?.packageName == gaming.packageName) {
                WajihaLog.i(
                    WajihaTags.NOW_PLAYING,
                    "reconcile($trigger): keep ${gaming.packageName} (still running)"
                )
                rememberProcessId(gaming.packageName)
                store.returnToGridWhileGaming()
            } else {
                WajihaLog.i(
                    WajihaTags.NOW_PLAYING,
                    "reconcile($trigger): switch ${existing?.packageName ?: "none"} " +
                        "-> ${gaming.packageName}"
                )
                applyGamingForeground(gaming.packageName)
                store.returnToGridWhileGaming()
            }
        } else if (existing != null) {
            WajihaLog.i(
                WajihaTags.NOW_PLAYING,
                "reconcile($trigger): clear ${existing.packageName} (no gaming running)"
            )
            clearNowPlaying(existing.packageName)
        } else {
            WajihaLog.d(WajihaTags.NOW_PLAYING, "reconcile($trigger): idle (no nowPlaying)")
        }
    }

    private fun applyGamingForeground(packageName: String) {
        val existing = store.nowPlaying.value
        if (existing?.packageName == packageName) {
            if (existing.appLabel == labelOf(packageName) &&
                sessionTracker.active.value?.packageName == packageName
            ) {
                return
            }
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
                "foreground switch ${existing?.packageName ?: "none"} -> $packageName"
            )
        }
        rememberProcessId(packageName)
        sessionTracker.onGameDetected(null, packageName)

        if (existing == null) {
            store.onGameDetected(newState)
        } else {
            store.updateNowPlaying(newState)
        }
        KeepAliveService.start(context)
    }

    private fun verifyNowPlayingStillAlive(trigger: String) {
        val playing = store.nowPlaying.value
        if (playing == null) {
            pendingClearPackage = null
            return
        }
        if (isGamingAppAlive(playing.packageName)) {
            pendingClearPackage = null
            return
        }

        val now = System.currentTimeMillis()
        if (pendingClearPackage != playing.packageName) {
            pendingClearPackage = playing.packageName
            pendingClearSince = now
            WajihaLog.d(
                WajihaTags.NOW_PLAYING,
                "verify($trigger): ${playing.packageName} looks dead; confirming"
            )
            return
        }
        if (now - pendingClearSince < CLEAR_CONFIRM_MS) return

        if (isPackageProcessAlive(playing.packageName)) {
            pendingClearPackage = null
            return
        }

        WajihaLog.i(
            WajihaTags.NOW_PLAYING,
            "verify($trigger): clear ${playing.packageName} (confirmed dead)"
        )
        clearNowPlaying(playing.packageName)
    }

    private fun clearNowPlaying(packageName: String) {
        store.onGameEnded()
        pendingClearPackage = null
        dropCachedPid(packageName)
        if (lastForeground == packageName) {
            lastForeground = null
        }
    }

    private fun findMostRecentGamingApp(): RunningApp? {
        val runningGaming = findRunningGamingPackages()
        if (runningGaming.isEmpty()) return null

        val timelines = queryPackageTimelines(RECENT_GAMING_WINDOW_MS)
        val aliveGaming = runningGaming.filter { pkg ->
            isPackageProcessAlive(pkg) ||
                timelines[pkg]?.let { isPackageTimelineAlive(it, pkg) } == true
        }
        if (aliveGaming.isEmpty()) return null

        val best = aliveGaming.maxByOrNull { pkg ->
            timelines[pkg]?.lastResume
                ?: store.runningApps.value.find { it.packageName == pkg }?.lastUsedAt
                ?: 0L
        } ?: return null

        return RunningApp(
            packageName = best,
            label = labelOf(best) ?: best,
            lastUsedAt = timelines[best]?.lastResume ?: System.currentTimeMillis(),
            isGame = true
        )
    }

    private fun findRunningGamingPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        packages.addAll(findRunningGamingPackagesFromProcesses())
        packages.addAll(findRunningGamingPackagesFromTasks())
        packages.addAll(findRunningGamingPackagesFromProcessIds())

        val timelines = queryPackageTimelines(RECENT_GAMING_WINDOW_MS)
        timelines.forEach { (pkg, timeline) ->
            if (pkg != ownPackage &&
                !isSystemUi(pkg) &&
                isGamingPackage(pkg) &&
                timeline.lastResume > 0L &&
                isPackageTimelineAlive(timeline, pkg)
            ) {
                packages.add(pkg)
            }
        }

        if (packages.isNotEmpty()) {
            WajihaLog.d(
                WajihaTags.NOW_PLAYING,
                "alive gaming packages: ${packages.joinToString()}"
            )
        }
        return packages
    }

    private fun findRunningGamingPackagesFromProcesses(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.forEach { proc ->
                if (proc.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) {
                    proc.pkgList?.forEach { pkg ->
                        if (pkg != ownPackage && !isSystemUi(pkg) && isGamingPackage(pkg)) {
                            packages.add(pkg)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return packages
    }

    @Suppress("DEPRECATION")
    private fun findRunningGamingPackagesFromTasks(): Set<String> {
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

    private fun findRunningGamingPackagesFromProcessIds(): Set<String> {
        val candidates = buildSet {
            store.nowPlaying.value?.packageName?.let { add(it) }
            store.runningApps.value.filter { it.isGame }.forEach { add(it.packageName) }
            queryPackageTimelines(RECENT_GAMING_WINDOW_MS).forEach { (pkg, timeline) ->
                if (timeline.lastResume > 0L) add(pkg)
            }
        }
        return candidates.filter { pkg ->
            pkg != ownPackage && !isSystemUi(pkg) && isGamingPackage(pkg) &&
                isPackageProcessAlive(pkg)
        }.toSet()
    }

    private fun isPackageInTaskStack(packageName: String): Boolean {
        if (findRunningGamingPackagesFromTasks().contains(packageName)) return true
        return isPackageProcessAlive(packageName)
    }

    private fun isPackageProcessAlive(packageName: String): Boolean {
        if (isPackageRunning(packageName)) {
            rememberProcessId(packageName)
            return true
        }
        cachedPid(packageName)?.let { pid ->
            if (isPidAlive(pid)) return true
            dropCachedPid(packageName)
        }
        val pid = queryProcessId(packageName)?.toIntOrNull()
        if (pid != null && isPidAlive(pid)) {
            storeCachedPid(packageName, pid)
            return true
        }
        return false
    }

    private fun rememberProcessId(packageName: String) {
        if (isPackageRunning(packageName)) return
        queryProcessId(packageName)?.toIntOrNull()?.let { storeCachedPid(packageName, it) }
    }

    private fun isPidAlive(pid: Int): Boolean = java.io.File("/proc/$pid").exists()

    private fun queryProcessId(packageName: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/pidof", packageName))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val pid = output.split(Regex("\\s+")).firstOrNull { it.isNotEmpty() }
            pid?.toIntOrNull()?.let { storeCachedPid(packageName, it) }
            pid ?: cachedPid(packageName)?.toString()
        } catch (_: Exception) {
            cachedPid(packageName)?.toString()
        }
    }

    private fun isPackageTimelineAlive(timeline: PackageActivityTimeline, packageName: String): Boolean {
        if (isPackageProcessAlive(packageName)) return true
        if (timeline.lastResume == 0L) return false
        if (timeline.lastStop > timeline.lastPause && timeline.lastStop > timeline.lastResume) {
            return false
        }
        return timeline.lastPause >= timeline.lastResume || timeline.lastStop <= timeline.lastResume
    }

    private fun isGamingAppAlive(packageName: String): Boolean {
        if (isPackageInTaskStack(packageName)) return true
        val timeline = queryPackageTimelines(ALIVE_CHECK_WINDOW_MS)[packageName] ?: return false
        return isPackageTimelineAlive(timeline, packageName)
    }

    private fun isPackageRunning(packageName: String): Boolean {
        if (packageName == ownPackage) return true
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.forEach { proc ->
                if (proc.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED &&
                    proc.pkgList?.contains(packageName) == true
                ) {
                    storeCachedPid(packageName, proc.pid)
                    return true
                }
            }
        } catch (_: Exception) {
        }
        return false
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

    private data class PackageActivityTimeline(
        val lastResume: Long = 0L,
        val lastPause: Long = 0L,
        val lastStop: Long = 0L
    )

    private fun queryPackageTimelines(windowMs: Long): Map<String, PackageActivityTimeline> {
        val timelines = mutableMapOf<String, PackageActivityTimeline>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - windowMs, end)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val current = timelines.getOrPut(pkg) { PackageActivityTimeline() }
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (event.timeStamp >= current.lastResume) {
                            timelines[pkg] = current.copy(lastResume = event.timeStamp)
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (event.timeStamp >= current.lastPause) {
                            timelines[pkg] = current.copy(lastPause = event.timeStamp)
                        }
                    }
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (event.timeStamp >= current.lastStop) {
                            timelines[pkg] = current.copy(lastStop = event.timeStamp)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return timelines
    }

    private var lastRunningRefresh = 0L
    private var pendingClearPackage: String? = null
    private var pendingClearSince: Long = 0L
    private val cachedProcessIds = mutableMapOf<String, Int>()

    @Synchronized
    private fun cachedPid(packageName: String): Int? = cachedProcessIds[packageName]

    @Synchronized
    private fun storeCachedPid(packageName: String, pid: Int) {
        cachedProcessIds[packageName] = pid
    }

    @Synchronized
    private fun dropCachedPid(packageName: String) {
        cachedProcessIds.remove(packageName)
    }

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
                WajihaLog.i(WajihaTags.NOW_PLAYING, "killApp: clear $packageName")
                clearNowPlaying(packageName)
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
        const val ALIVE_CHECK_WINDOW_MS = 30 * 60 * 1000L
        const val RECENT_GAMING_WINDOW_MS = 30 * 60 * 1000L
        const val CLEAR_CONFIRM_MS = 4_000L
        const val RUNNING_APPS_REFRESH_MS = 10_000L
        const val RUNNING_APPS_WINDOW_MS = 6 * 60 * 60 * 1000L
        const val PACKAGES_REFRESH_MS = 60_000L
    }
}
