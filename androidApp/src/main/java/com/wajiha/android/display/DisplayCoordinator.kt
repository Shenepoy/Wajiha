package com.wajiha.android.display

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.wajiha.android.MainActivity
import com.wajiha.android.SecondaryHomeActivity
import com.wajiha.android.monitor.SessionTaskRegistry
import com.wajiha.android.monitor.TopDisplayTaskResolver
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.SecondaryMode

/**
 * Watches connected displays and keeps [DualScreenStore] in sync.
 *
 * Bottom-screen launch follows Cocoon's ExternalDisplayActivity model:
 * a dedicated SECONDARY_HOME activity on its own taskAffinity, started
 * explicitly when Wajiha is not yet the default home (system only auto-
 * starts SECONDARY_HOME for the default launcher).
 */
class DisplayCoordinator(
    private val context: Context,
    private val store: DualScreenStore,
) : DisplayManager.DisplayListener {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listenerRegistered = false
    private var reclaimRunnable: Runnable? = null
    private var secondaryWatchdogRunning = false
    private var fastReclaimRunnable: Runnable? = null

    @Volatile
    private var fastReclaimDisplayId: Int? = null

    /** True while a display-0 [MainActivity] instance is in the resumed lifecycle. */
    @Volatile
    private var primaryMainOnDisplay0 = false

    /** Skip secondary reclaim briefly after OOM-guard kills. */
    @Volatile
    private var memoryGuardReclaimDeferUntil: Long = 0L
    private var lastFocusGameAt = 0L
    private var lastFocusGamePkg: String? = null

    fun start() {
        if (!listenerRegistered) {
            WajihaLog.i(WajihaTags.DISPLAY, "start: registering display listener")
            displayManager.registerDisplayListener(this, mainHandler)
            listenerRegistered = true
        } else {
            WajihaLog.d(WajihaTags.DISPLAY, "start: display listener already registered")
        }
        refresh()
        ensureSecondaryWatchdog()
    }

    fun onPrimaryMainResumed() {
        primaryMainOnDisplay0 = true
    }

    fun onPrimaryMainStopped() {
        primaryMainOnDisplay0 = false
    }

    fun secondaryDisplay(): Display? =
        displayManager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY && display.isValid
        }

    /**
     * RetroHrai/Cocoon trampoline: drawer and HOME intents can land
     * [MainActivity] on the Thor bottom display. `singleTask` + CLEAR_TOP
     * ignores [ActivityOptions.setLaunchDisplayId]; CLEAR_TASK forces a fresh
     * task on display 0.
     *
     * @return true if [from] was finished and a primary launch was scheduled
     */
    fun redirectMainToPrimaryIfNeeded(from: Activity): Boolean {
        val currentDisplayId = from.display?.displayId ?: Display.DEFAULT_DISPLAY
        if (currentDisplayId == Display.DEFAULT_DISPLAY) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "redirectMainToPrimaryIfNeeded: ${from.javaClass.simpleName} " +
                    "displayId=$currentDisplayId taskId=${from.taskId} — no redirect",
            )
            return false
        }

        WajihaLog.i(
            WajihaTags.DISPLAY,
            "redirectMainToPrimaryIfNeeded: ${from.javaClass.simpleName} " +
                "displayId=$currentDisplayId taskId=${from.taskId} — " +
                "redirecting to primary (display 0)",
        )
        // HOME on the bottom display can land CATEGORY_HOME on MainActivity;
        // reclaim SecondaryHome on this display before hopping primary.
        if (from.intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            launchSecondaryHomeOn(currentDisplayId, reclaim = true)
        }
        launchPrimaryMain(from)
        from.finish()
        from.overridePendingTransition(0, 0)
        return true
    }

    /** Start [MainActivity] on the default display (display 0). */
    fun launchPrimaryMain(from: Activity) {
        // When HOME already auto-started on display 0, CLEAR_TASK destroys that
        // instance; its onDestroy used to cancel scheduleSecondaryHome for the
        // replacement (Thor drawer + default-home race).
        val reusePrimary = primaryMainOnDisplay0
        val flags: Int
        val flagLabel: String
        if (reusePrimary) {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            flagLabel = "NEW_TASK|REORDER_TO_FRONT|SINGLE_TOP"
        } else {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            flagLabel = "NEW_TASK|CLEAR_TASK"
        }
        val intent =
            Intent(from, MainActivity::class.java)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(flags)
        val options =
            ActivityOptions
                .makeBasic()
                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "launchPrimaryMain: from=${from.javaClass.simpleName} " +
                "targetDisplayId=${Display.DEFAULT_DISPLAY} " +
                "flags=$flagLabel reusePrimary=$reusePrimary",
        )
        from.startActivity(intent, options.toBundle())
        // Secondary home is started from MainActivity.onResume once display 0
        // has settled — posting here raced ahead of primary and let display 4
        // steal topDisplayFocusedRootTask (Thor bottom drawer symptom).
    }

    /**
     * After the bottom screen is alive, pull the primary HOME task back to the
     * top of display 0 so Launcher3 Recents does not cover the hero panel.
     *
     * Do **not** use [ActivityManager.MOVE_TASK_WITH_HOME] — on QuickStep that
     * surfaces RecentsActivity; Wajiha is excludeFromRecents so the user sees
     * "No recent items" instead of the hero.
     */
    fun focusPrimaryMain(main: Activity) {
        if (main !is MainActivity) return
        if (main.display?.displayId != Display.DEFAULT_DISPLAY) return
        restorePrimaryHero(main)
    }

    /** Bring [MainActivity] above Recents on display 0 (browsing / hero mode). */
    fun restorePrimaryHero(main: MainActivity? = null) {
        if (store.hasActiveSessions()) {
            val topGame = store.topDisplayForegroundPackage.value
            if (topGame != null && topGame != context.packageName && store.getSession(topGame) != null) {
                focusGameOnPrimary(topGame)
                return
            }
        }
        mainHandler.postDelayed({
            val taskId =
                main?.taskId
                    ?: TopDisplayTaskResolver.taskIdForActivityClass(context, MainActivity::class.java)
            if (taskId != null) {
                try {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    WajihaLog.i(
                        WajihaTags.DISPLAY,
                        "restorePrimaryHero: moveTaskToFront taskId=$taskId",
                    )
                    am.moveTaskToFront(taskId, 0)
                } catch (e: Exception) {
                    WajihaLog.w(
                        WajihaTags.DISPLAY,
                        "restorePrimaryHero: moveTaskToFront failed — ${e.message}",
                    )
                    launchPrimaryMainReorder(main)
                }
            } else {
                launchPrimaryMainReorder(main)
            }
        }, 50)
    }

    private fun launchPrimaryMainReorder(main: MainActivity?) {
        WajihaLog.i(WajihaTags.DISPLAY, "restorePrimaryHero: REORDER_TO_FRONT fallback")
        val intent =
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        if (main != null) {
            main.startActivity(intent, options.toBundle())
        } else {
            context.startActivity(intent, options.toBundle())
        }
    }

    /** Delay secondary launch until primary has claimed display-0 focus. */
    fun scheduleSecondaryHome(main: MainActivity) {
        restorePrimaryHero(main)
        mainHandler.postDelayed({
            ensureSecondaryHome()
            restorePrimaryHero(main)
        }, 300)
    }

    /**
     * Thor ROM (uid 0) hard-starts Launcher3's SECONDARY_HOME task on bottom
     * HOME, bypassing preferred-activity resolution. Reclaim must beat its first
     * frame — delayed posts caused the visible Launcher3 flash.
     */
    fun scheduleSecondaryHomeReclaim(
        displayId: Int? = null,
        delayMs: Long = 0,
    ) {
        val targetDisplayId = displayId ?: secondaryDisplay()?.displayId ?: return
        if (delayMs <= 0L) {
            reclaimRunnable?.let { mainHandler.removeCallbacks(it) }
            reclaimSecondaryHomeOnDisplay(targetDisplayId)
            return
        }
        reclaimRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { reclaimSecondaryHomeOnDisplay(targetDisplayId) }
        reclaimRunnable = runnable
        WajihaLog.d(
            WajihaTags.DISPLAY,
            "scheduleSecondaryHomeReclaim: displayId=$targetDisplayId delayMs=$delayMs",
        )
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * Frame-paced reclaim loop after bottom HOME — runs every ~16 ms until
     * [SecondaryHomeActivity] is resumed on the target display again.
     */
    fun beginFastSecondaryReclaim(displayId: Int) {
        if (store.state.value == DualScreenState.AppOnSecondary) return
        if (shouldDeferReclaimForMemoryGuard()) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "beginFastSecondaryReclaim: defer displayId=$displayId — memory-guard grace",
            )
            scheduleSecondaryHomeReclaim(displayId, delayMs = MEMORY_GUARD_RECLAIM_DEFER_MS)
            return
        }
        if (shouldDeferReclaimForGameLaunch()) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "beginFastSecondaryReclaim: defer displayId=$displayId — game launch grace",
            )
            scheduleSecondaryHomeReclaim(displayId, delayMs = GAME_LAUNCH_RECLAIM_DEFER_MS)
            return
        }
        fastReclaimDisplayId = displayId
        reclaimSecondaryHomeOnDisplay(displayId)
        fastReclaimRunnable?.let { mainHandler.removeCallbacks(it) }
        val loop =
            object : Runnable {
                override fun run() {
                    val targetId = fastReclaimDisplayId ?: return
                    if (store.state.value == DualScreenState.AppOnSecondary) {
                        stopFastSecondaryReclaim()
                        return
                    }
                    val onScreen =
                        SecondaryHomeActivity.isResumed &&
                            SecondaryHomeActivity.visibleDisplayId == targetId
                    if (onScreen) {
                        stopFastSecondaryReclaim()
                        return
                    }
                    reclaimSecondaryHomeOnDisplay(targetId, launchIfNeeded = false)
                    mainHandler.postDelayed(this, FAST_RECLAIM_INTERVAL_MS)
                }
            }
        fastReclaimRunnable = loop
        mainHandler.postAtFrontOfQueue(loop)
    }

    fun stopFastSecondaryReclaim() {
        fastReclaimDisplayId = null
        fastReclaimRunnable?.let { mainHandler.removeCallbacks(it) }
        fastReclaimRunnable = null
    }

    /**
     * After a game launches on the top display, pull its task above [MainActivity].
     * Without this, Thor keeps the HOME task resumed while the emulator sits invisible.
     */
    fun focusGameOnPrimary(packageName: String) {
        val now = System.currentTimeMillis()
        if (packageName == lastFocusGamePkg && now - lastFocusGameAt < FOCUS_DEBOUNCE_MS) {
            return
        }
        lastFocusGamePkg = packageName
        lastFocusGameAt = now
        focusGameOnPrimary(packageName, attempt = 0)
    }

    private fun focusGameOnPrimary(
        packageName: String,
        attempt: Int,
    ) {
        val delayMs =
            when (attempt) {
                0 -> 100L
                1 -> 400L
                2 -> 900L
                else -> 1_500L
            }
        mainHandler.postDelayed({
            val moved = SessionTaskRegistry.moveToFront(context, packageName)
            if (moved) {
                WajihaLog.i(
                    WajihaTags.DISPLAY,
                    "focusGameOnPrimary: moveTaskToFront pkg=$packageName attempt=$attempt",
                )
            } else if (attempt < 3) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "focusGameOnPrimary: retry pkg=$packageName attempt=$attempt",
                )
                focusGameOnPrimary(packageName, attempt + 1)
            } else {
                val reordered = SessionTaskRegistry.moveToFront(context, packageName)
                if (reordered) {
                    WajihaLog.i(
                        WajihaTags.DISPLAY,
                        "focusGameOnPrimary: reorderToFront pkg=$packageName",
                    )
                } else {
                    WajihaLog.w(
                        WajihaTags.DISPLAY,
                        "focusGameOnPrimary: no task for pkg=$packageName",
                    )
                }
            }
        }, delayMs)
    }

    /** Bottom leave-hint during startActivity must not beat the top-display game task. */
    private fun shouldDeferReclaimForGameLaunch(): Boolean {
        if (!store.hasActiveSessions()) return false
        val session = store.nowPlaying.value ?: return true
        val elapsed = System.currentTimeMillis() - session.sessionStartedAt
        return elapsed < GAME_LAUNCH_RECLAIM_DEFER_MS
    }

    /** After OOM-guard kills, pause reclaim so recovery does not thrash bottom HOME. */
    fun deferReclaimForMemoryGuard() {
        memoryGuardReclaimDeferUntil =
            System.currentTimeMillis() + MEMORY_GUARD_RECLAIM_DEFER_MS
        stopFastSecondaryReclaim()
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "deferReclaimForMemoryGuard: ${MEMORY_GUARD_RECLAIM_DEFER_MS}ms",
        )
    }

    private fun shouldDeferReclaimForMemoryGuard(): Boolean = System.currentTimeMillis() < memoryGuardReclaimDeferUntil

    fun reclaimSecondaryHomeOnDisplay(
        displayId: Int,
        launchIfNeeded: Boolean = true,
    ) {
        if (shouldDeferReclaimForMemoryGuard()) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "reclaimSecondaryHomeOnDisplay: skip — memory-guard grace",
            )
            return
        }
        if (store.state.value == DualScreenState.AppOnSecondary) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "reclaimSecondaryHomeOnDisplay: skip — AppOnSecondary",
            )
            return
        }
        val taskId = SecondaryHomeActivity.taskIdForDisplay(displayId)
        var moved = false
        if (taskId != null) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                WajihaLog.i(
                    WajihaTags.DISPLAY,
                    "reclaimSecondaryHomeOnDisplay: moveTaskToFront taskId=$taskId " +
                        "displayId=$displayId",
                )
                am.moveTaskToFront(taskId, 0)
                moved = true
            } catch (e: Exception) {
                WajihaLog.w(
                    WajihaTags.DISPLAY,
                    "reclaimSecondaryHomeOnDisplay: moveTaskToFront failed — ${e.message}",
                )
            }
        }
        if (!moved && launchIfNeeded) {
            launchSecondaryHomeOn(displayId, reclaim = true)
        }
        // Fast-reclaim ticks use launchIfNeeded=false — only re-focus after real work.
        if (launchIfNeeded || moved) {
            refreshTopDisplayAfterSecondaryReclaim()
        }
    }

    /** Keep display 0 on the active game or Wajiha hero — never Recents. */
    private fun refreshTopDisplayAfterSecondaryReclaim() {
        val topGame = store.topDisplayForegroundPackage.value
        if (topGame != null && topGame != context.packageName && store.getSession(topGame) != null) {
            focusGameOnPrimary(topGame)
        } else if (!store.hasActiveSessions()) {
            restorePrimaryHero()
        }
    }

    /**
     * While a secondary display is connected, keep Wajiha's bottom-screen task
     * resumed. Covers bottom-screen HOME when Launcher3 already owns display 4
     * (no lifecycle callback reaches a paused SecondaryHomeActivity).
     */
    private fun ensureSecondaryWatchdog() {
        if (secondaryWatchdogRunning) return
        secondaryWatchdogRunning = true
        val tick =
            object : Runnable {
                override fun run() {
                    val display = secondaryDisplay()
                    if (display == null) {
                        secondaryWatchdogRunning = false
                        return
                    }
                    if (store.state.value != DualScreenState.AppOnSecondary) {
                        if (shouldDeferReclaimForMemoryGuard()) {
                            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                            return
                        }
                        val onScreen =
                            SecondaryHomeActivity.isResumed &&
                                SecondaryHomeActivity.visibleDisplayId == display.displayId
                        if (!onScreen) {
                            WajihaLog.d(
                                WajihaTags.DISPLAY,
                                "watchdog: secondary not resumed on displayId=" +
                                    "${display.displayId} — reclaiming",
                            )
                            reclaimSecondaryHomeOnDisplay(display.displayId)
                        }
                    }
                    mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                }
            }
        mainHandler.post(tick)
    }

    private fun refresh() {
        store.onDisplaysChanged(secondaryDisplay()?.displayId)
    }

    /**
     * Ensures the bottom-screen activity is alive on the secondary display.
     * Safe to call from MainActivity / SecondaryHomeActivity onResume.
     */
    fun ensureSecondaryHome() {
        val display = secondaryDisplay()
        if (display == null) {
            WajihaLog.d(WajihaTags.DISPLAY, "ensureSecondaryHome: no secondary display")
            return
        }
        if (store.state.value == DualScreenState.DualBrowsing) {
            store.setSecondaryMode(SecondaryMode.GameGrid)
        }
        val visibleId = SecondaryHomeActivity.visibleDisplayId
        val onScreen = visibleId == display.displayId && SecondaryHomeActivity.isResumed
        if (onScreen) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "ensureSecondaryHome: already resumed on displayId=${display.displayId}",
            )
            return
        }
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "ensureSecondaryHome: launching SecondaryHome on displayId=${display.displayId} " +
                "(visibleDisplayId=$visibleId resumed=${SecondaryHomeActivity.isResumed})",
        )
        launchSecondaryHomeOn(display.displayId, reclaim = true)
    }

    fun launchSecondaryHomeOn(
        displayId: Int,
        reclaim: Boolean = false,
    ) {
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        if (reclaim) {
            flags = flags or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val component = ComponentName(context, SecondaryHomeActivity::class.java)
        val intent =
            Intent(Intent.ACTION_MAIN)
                .setComponent(component)
                .addCategory(SECONDARY_HOME_CATEGORY)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(context.packageName)
                .addFlags(flags)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        val flagLabel =
            if (reclaim) {
                "NEW_TASK|REORDER_TO_FRONT|CLEAR_TOP|RESET_TASK_IF_NEEDED"
            } else {
                "NEW_TASK|RESET_TASK_IF_NEEDED"
            }
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "launchSecondaryHomeOn: targetDisplayId=$displayId reclaim=$reclaim flags=$flagLabel",
        )
        try {
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "launchSecondaryHomeOn: failed on displayId=$displayId — ${e.message}",
            )
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        WajihaLog.i(WajihaTags.DISPLAY, "onDisplayAdded: displayId=$displayId")
        refresh()
        mainHandler.postDelayed({ ensureSecondaryHome() }, 200)
    }

    override fun onDisplayRemoved(displayId: Int) {
        WajihaLog.i(WajihaTags.DISPLAY, "onDisplayRemoved: displayId=$displayId")
        refresh()
    }

    override fun onDisplayChanged(displayId: Int) {
        WajihaLog.d(WajihaTags.DISPLAY, "onDisplayChanged: displayId=$displayId")
        refresh()
    }

    private companion object {
        /** minSdk 30 — [Intent.CATEGORY_SECONDARY_HOME] is API 33+. */
        const val SECONDARY_HOME_CATEGORY = "android.intent.category.SECONDARY_HOME"
        private const val WATCHDOG_INTERVAL_MS = 200L
        private const val FAST_RECLAIM_INTERVAL_MS = 16L
        private const val FOCUS_DEBOUNCE_MS = 400L

        /** Let top-display emulator win before bottom HOME reclaim runs. */
        private const val GAME_LAUNCH_RECLAIM_DEFER_MS = 800L

        /** After memory-guard kills, avoid reclaim thrashing during recovery. */
        private const val MEMORY_GUARD_RECLAIM_DEFER_MS = 10_000L
    }

    /** Launch an app on a specific display (running-apps "move to display"). */
    fun launchOnDisplay(
        packageName: String,
        displayId: Int,
    ): Boolean {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        return try {
            context.startActivity(launchIntent, options.toBundle())
            if (displayId != Display.DEFAULT_DISPLAY) store.onAppSentToSecondary()
            true
        } catch (_: Exception) {
            false
        }
    }
}
