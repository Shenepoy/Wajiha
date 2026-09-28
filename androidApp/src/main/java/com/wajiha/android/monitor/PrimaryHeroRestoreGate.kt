package com.wajiha.android.monitor

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.view.Display
import com.wajiha.state.DualScreenStore

/**
 * Decides whether Wajiha may pull [com.wajiha.android.MainActivity] over display 0.
 *
 * Thor's ActivityTaskManager often reports Wajiha as the top package while Chrome
 * or an emulator still owns the panel. Prefer DualScreenStore + UsageStats over ATM.
 */
internal object PrimaryHeroRestoreGate {
    /** Long enough to cover false session-end polls (~10–30s) after launch. */
    private const val EVENT_WINDOW_MS = 120_000L

    /**
     * Foreign package that currently owns (or most recently resumed on) display 0,
     * or null when the launcher may safely reclaim the hero.
     *
     * UsageStats alone can prove foreign ownership — Thor often hides other UIDs
     * from process/task APIs while an emu/Chrome still owns the panel. A true quit
     * may delay hero restore for up to [EVENT_WINDOW_MS] until MainActivity resumes.
     */
    fun foreignTopOwnerIfAny(
        context: Context,
        store: DualScreenStore,
    ): String? {
        val own = context.packageName

        fun accept(pkg: String?): String? = pkg?.takeUnless { it == own || isSystemUi(it) }

        // Curated top-display owner (dual-play / usage override). Cleared on session end.
        accept(store.topDisplayForegroundPackage.value)?.let { return it }

        // ATM top — only trust when the package still has a live process/task
        // (ATM can linger on a Recents-dismissed task name).
        accept(
            TopDisplayTaskResolver.topPackageOnDisplay(context, Display.DEFAULT_DISPLAY),
        )?.takeIf { isPackageLive(context, it) }
            ?.let { return it }

        if (!hasUsageAccess(context)) return null
        val usageFg = accept(latestNonLauncherForeground(context, own)) ?: return null
        val usageEvents = usageEventsForPackage(context, usageFg)
        // A resume that is still inside the 120s window used to keep the hero
        // covered after the app had already paused or stopped. Per-activity
        // tracking avoids treating a multi-activity handoff (root stop after
        // child resume) as a quit. Store topDisplayForegroundPackage above
        // still wins for a live session, so an in-game pause does not reclaim.
        if (usageEvents != null &&
            usageEvents.isNotEmpty() &&
            !usageEventsStillOwnDisplay(usageEvents)
        ) {
            return null
        }
        val usageResume = latestResumeTime(context, usageFg)
        val mainResume = latestOwnMainActivityResumeTime(context, own)
        // Fresher foreign resume than MainActivity ⇒ do not steal. Do not require
        // process/task live signals — those miss top-display emus on Thor.
        return usageFg.takeIf { usageResume >= mainResume }
    }

    /** True only when UsageStats/store agree no foreign app owns the top panel. */
    fun canRestorePrimaryHero(
        context: Context,
        store: DualScreenStore,
    ): Boolean = foreignTopOwnerIfAny(context, store) == null

    private fun isPackageLive(
        context: Context,
        packageName: String,
    ): Boolean {
        if (SessionTaskRegistry.hasTask(packageName)) return true
        if (TopDisplayTaskResolver.taskIdForPackage(context, packageName) != null) return true
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.runningAppProcesses?.any { proc ->
                    proc.pkgList?.contains(packageName) == true
                } == true
            ) {
                return true
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun hasUsageAccess(context: Context): Boolean {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode =
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            return false
        }
    }

    private fun usageEventsForPackage(
        context: Context,
        packageName: String,
    ): List<UsageTimelineEvent>? {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - EVENT_WINDOW_MS, end)
            val matched = mutableListOf<UsageTimelineEvent>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                if (event.eventType != UsageTimelineActive.ACTIVITY_RESUMED &&
                    event.eventType != UsageTimelineActive.ACTIVITY_PAUSED &&
                    event.eventType != UsageTimelineActive.ACTIVITY_STOPPED
                ) {
                    continue
                }
                matched +=
                    UsageTimelineEvent(
                        eventType = event.eventType,
                        className = event.className,
                        timeStamp = event.timeStamp,
                    )
            }
            return matched
        } catch (_: Exception) {
            return null
        }
    }

    private fun latestNonLauncherForeground(
        context: Context,
        ownPackage: String,
    ): String? {
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

    private fun latestResumeTime(
        context: Context,
        packageName: String,
    ): Long {
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

    private fun latestOwnMainActivityResumeTime(
        context: Context,
        ownPackage: String,
    ): Long {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - EVENT_WINDOW_MS, end)
            var latestTime = 0L
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != ownPackage) continue
                if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
                val className = event.className ?: continue
                if (!className.endsWith(".MainActivity")) continue
                if (event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                }
            }
            return latestTime
        } catch (_: Exception) {
            return 0L
        }
    }

    private fun isSystemUi(packageName: String): Boolean =
        packageName == "com.android.systemui" ||
            packageName == "android" ||
            packageName.startsWith("com.android.launcher")
}

/**
 * True while any activity in [events] is still resumed.
 * A later pause or stop of that same activity means the package left the panel.
 */
internal fun usageEventsStillOwnDisplay(events: List<UsageTimelineEvent>): Boolean = UsageTimelineActive.isActive(events)
