package com.wajiha.android.display

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import androidx.activity.ComponentActivity
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
    private val secondaryDisplayHost: SecondaryDisplayHost,
) : DisplayManager.DisplayListener {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val secondaryTransitionCover = SecondaryTransitionCover()
    private var listenerRegistered = false
    private var secondaryAppPackage: String? = null
    private var exhaustedRecoveryRetryUsed = false
    private var lastSecondaryProbeAt = 0L
    private var lastPrimaryHomeRequestAt = 0L

    /** Skip secondary reclaim briefly after OOM-guard kills. */
    @Volatile
    private var memoryGuardReclaimDeferUntil: Long = 0L
    private var lastFocusGameAt = 0L
    private var lastFocusGamePkg: String? = null
    private val secondaryRecovery: SecondaryRecoveryController =
        SecondaryRecoveryController(
            scheduler =
                SecondaryRecoveryScheduler { delayMs, action ->
                    mainHandler.postDelayed(action, delayMs)
                },
            effects =
                object : SecondaryRecoveryEffects {
                    override fun gate(): SecondaryRecoveryGate = secondaryRecoveryGate()

                    override fun isHealthy(displayId: Int): Boolean = isSecondaryHealthy(displayId)

                    override fun recover(
                        displayId: Int,
                        allowLaunch: Boolean,
                    ) {
                        reclaimSecondaryHomeOnDisplay(displayId, launchIfNeeded = allowLaunch)
                    }

                    override fun onRecovered(displayId: Int) {
                        exhaustedRecoveryRetryUsed = false
                        mainHandler.postDelayed(
                            { reconcilePrimaryAfterSecondaryRecovery() },
                            PRIMARY_RECONCILE_SETTLE_MS,
                        )
                    }

                    override fun onExhausted(displayId: Int) {
                        if (exhaustedRecoveryRetryUsed) return
                        exhaustedRecoveryRetryUsed = true
                        mainHandler.postDelayed(
                            {
                                val powerManager =
                                    context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                if (powerManager.isInteractive &&
                                    secondaryDisplay()?.displayId == displayId &&
                                    !isSecondaryHealthy(displayId)
                                ) {
                                    secondaryRecovery.probe(displayId, "exhausted-retry")
                                }
                            },
                            EXHAUSTED_RECOVERY_RETRY_MS,
                        )
                    }

                    override fun log(message: String) {
                        WajihaLog.i(WajihaTags.DISPLAY, "secondaryRecovery: $message")
                    }
                },
        )

    fun start() {
        if (!listenerRegistered) {
            WajihaLog.i(WajihaTags.DISPLAY, "start: registering display listener")
            displayManager.registerDisplayListener(this, mainHandler)
            listenerRegistered = true
        } else {
            WajihaLog.d(WajihaTags.DISPLAY, "start: display listener already registered")
        }
        refresh()
    }

    fun secondaryDisplay(): Display? =
        displayManager.displays.firstOrNull { display ->
            isPhysicalSecondaryDisplay(
                displayId = display.displayId,
                displayFlags = display.flags,
                isValid = display.isValid,
            )
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
        // HOME on the bottom display can land CATEGORY_HOME on MainActivity.
        // Queue one bounded recovery while this misplaced instance finishes.
        if (from.intent?.hasCategory(Intent.CATEGORY_HOME) == true &&
            !store.forceSingleScreen
        ) {
            requestSecondaryRecovery(currentDisplayId, "wrong-display-home")
        }
        launchPrimaryMain(from)
        from.finish()
        from.overridePendingTransition(0, 0)
        return true
    }

    /** Start [MainActivity] on the default display (display 0). */
    fun launchPrimaryMain(from: Activity) {
        val primaryTaskId =
            TopDisplayTaskResolver.taskIdForActivityClassOnDisplay(
                context,
                MainActivity::class.java,
                Display.DEFAULT_DISPLAY,
            ) ?: MainActivity.primaryTaskId
        val strategy = primaryLaunchStrategy(primaryTaskId != null)
        val flags: Int
        val flagLabel: String
        when (strategy) {
            PrimaryLaunchStrategy.Reorder -> {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                flagLabel = "NEW_TASK|REORDER_TO_FRONT|SINGLE_TOP"
            }

            PrimaryLaunchStrategy.Recreate -> {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                flagLabel = "NEW_TASK|CLEAR_TASK"
            }
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
                "flags=$flagLabel primaryTaskId=$primaryTaskId",
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
                // Only refocus a live task — never cold-start a Recents-dismissed emulator.
                if (TopDisplayTaskResolver.taskIdForPackage(context, topGame) != null ||
                    SessionTaskRegistry.hasTask(topGame)
                ) {
                    focusGameOnPrimary(topGame)
                    return
                }
                SessionTaskRegistry.clear(topGame)
            }
        }
        // Never pull the hero over a normal app that owns the top display.
        val topPkg = store.topDisplayForegroundPackage.value
        if (topPkg != null && topPkg != context.packageName) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "restorePrimaryHero: skip — foreign top pkg=$topPkg",
            )
            return
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
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        val options =
            ActivityOptions
                .makeCustomAnimation(context, 0, 0)
                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        if (main != null) {
            main.startActivity(intent, options.toBundle())
        } else {
            context.startActivity(intent, options.toBundle())
        }
    }

    fun requestSecondaryAvailability(reason: String) {
        runOnMain {
            val displayId = secondaryDisplay()?.displayId ?: return@runOnMain
            exhaustedRecoveryRetryUsed = false
            secondaryRecovery.request(displayId, reason)
        }
    }

    /**
     * A real SECONDARY_HOME request means the user pressed Home while another
     * app may still own display 0. Bring Wajiha Home forward there as well.
     * This stops that activity, allowing apps such as Azahar to dismiss their
     * display-4 Presentation. Internal recovery launches must not do this.
     */
    fun handleSecondaryHomeIntent(
        activity: Activity,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MAIN ||
            !intent.hasCategory(SECONDARY_HOME_CATEGORY) ||
            intent.getBooleanExtra(EXTRA_INTERNAL_SECONDARY_HOME_LAUNCH, false)
        ) {
            return
        }
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "secondaryHome: user HOME — bringing primary launcher forward",
        )
        bringPrimaryLauncherForward(activity)
    }

    /**
     * Thor routes bottom-screen Home through its built-in Launcher3 task. Act
     * from onUserLeaveHint while Wajiha is still visible; Android rejects this
     * cross-display Home launch after SecondaryHome has stopped.
     */
    fun onSecondaryHomeUserLeave(activity: ComponentActivity) {
        if (store.state.value == DualScreenState.AppOnSecondary) return
        if (secondaryDisplayHost.surface.value == SecondaryRenderSurface.Overlay) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — live overlay already owns secondary",
            )
            return
        }

        secondaryTransitionCover.show(activity)
        secondaryDisplayHost.showOverlay(activity)
        mainHandler.postDelayed(
            { secondaryTransitionCover.hide() },
            SECONDARY_COVER_TIMEOUT_MS,
        )

        val topPackage = store.topDisplayForegroundPackage.value
        if (topPackage == null || topPackage == context.packageName) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — secondary reclaimed; primary already Wajiha or unknown",
            )
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastPrimaryHomeRequestAt < PRIMARY_HOME_MIN_INTERVAL_MS) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — primary HOME request throttled",
            )
            return
        }
        lastPrimaryHomeRequestAt = now
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "secondaryHome: user leave — bringing primary launcher over $topPackage",
        )
        bringPrimaryLauncherForward(activity)
        releaseForeignSecondarySurface(activity, topPackage)
    }

    /**
     * SurfaceView-based emulator Presentations can retain Thor's display-4
     * sideband layer after their host Activity stops. While that layer exists,
     * HWC ignores every normal Android layer on the panel and outputs black.
     * Android does not let a regular app kill that foreign process on Thor.
     * Clearing its task through its exported launcher destroys the stale
     * Presentation, then Wajiha immediately reclaims display 0.
     */
    private fun releaseForeignSecondarySurface(
        activity: Activity,
        packageName: String,
    ) {
        mainHandler.postDelayed(
            {
                val clearIntent =
                    context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: return@postDelayed
                clearIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                val options =
                    ActivityOptions
                        .makeCustomAnimation(context, 0, 0)
                        .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                runCatching { context.startActivity(clearIntent, options.toBundle()) }
                    .onSuccess {
                        WajihaLog.i(
                            WajihaTags.DISPLAY,
                            "secondaryHome: cleared stale secondary surface owner=$packageName",
                        )
                        mainHandler.postDelayed(
                            { bringPrimaryLauncherForward(activity) },
                            PRIMARY_RECLAIM_AFTER_SURFACE_RELEASE_MS,
                        )
                    }.onFailure {
                        WajihaLog.w(
                            WajihaTags.DISPLAY,
                            "secondaryHome: surface release failed owner=$packageName — ${it.message}",
                        )
                    }
            },
            SECONDARY_SURFACE_RELEASE_DELAY_MS,
        )
    }

    fun onSecondaryHomeStopped(displayId: Int) {
        requestSecondaryRecovery(displayId, "stop")
    }

    private fun bringPrimaryLauncherForward(activity: Activity) {
        val primaryTaskId = MainActivity.primaryTaskId
        try {
            val homeIntent =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(context.packageName)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    )
            val options =
                ActivityOptions
                    .makeCustomAnimation(context, 0, 0)
                    .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            val pendingHome =
                PendingIntent.getActivity(
                    context,
                    PRIMARY_HOME_REQUEST_CODE,
                    homeIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    options.toBundle(),
                )
            pendingHome.send()
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHome: requested primary HOME transition",
            )
            return
        } catch (error: Exception) {
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "secondaryHome: primary HOME request failed — ${error.message}",
            )
        }
        if (primaryTaskId != null) {
            try {
                val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.moveTaskToFront(
                    primaryTaskId,
                    ActivityManager.MOVE_TASK_WITH_HOME or
                        ActivityManager.MOVE_TASK_NO_USER_ACTION,
                )
                WajihaLog.i(
                    WajihaTags.DISPLAY,
                    "secondaryHome: moved primary taskId=$primaryTaskId to front",
                )
                return
            } catch (error: Exception) {
                WajihaLog.w(
                    WajihaTags.DISPLAY,
                    "secondaryHome: primary move failed — ${error.message}",
                )
            }
        }
        launchPrimaryMain(activity)
    }

    fun requestSecondaryRecovery(
        displayId: Int,
        reason: String,
    ) {
        runOnMain {
            val physicalDisplayId = secondaryDisplay()?.displayId
            if (displayId != physicalDisplayId) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "secondaryRecovery: ignore non-physical displayId=$displayId " +
                        "physicalDisplayId=$physicalDisplayId reason=$reason",
                )
                return@runOnMain
            }
            if (reason != "exhausted-retry") {
                exhaustedRecoveryRetryUsed = false
            }
            secondaryRecovery.request(displayId, reason)
        }
    }

    /** Low-frequency safety net for ROM starts that produce no fresh lifecycle callback. */
    fun probeSecondaryHome(reason: String) {
        mainHandler.post {
            val now = System.currentTimeMillis()
            if (now - lastSecondaryProbeAt < SECONDARY_PROBE_MIN_INTERVAL_MS) return@post
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isInteractive) return@post
            val displayId = secondaryDisplay()?.displayId ?: return@post
            lastSecondaryProbeAt = now
            exhaustedRecoveryRetryUsed = false
            secondaryRecovery.probe(displayId, reason)
        }
    }

    fun onSecondaryHomeResumed(activity: Activity) {
        val displayId = activity.display?.displayId ?: return
        activity.window.decorView.postDelayed(
            { secondaryTransitionCover.hide() },
            SECONDARY_COVER_REMOVE_DELAY_MS,
        )
        runOnMain {
            secondaryAppPackage = null
            store.onSecondaryAppDismissed()
            secondaryRecovery.onResumed(displayId)
        }
    }

    fun cancelSecondaryRecovery(reason: String) {
        runOnMain { secondaryRecovery.cancel(reason) }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun isSecondaryHealthy(displayId: Int): Boolean =
        SecondaryHomeActivity.visibleDisplayId == displayId &&
            SecondaryHomeActivity.taskIdForDisplay(displayId) != null

    private fun secondaryRecoveryGate(): SecondaryRecoveryGate {
        if (store.forceSingleScreen) {
            return SecondaryRecoveryGate(suppressReason = "single-screen")
        }
        if (store.state.value == DualScreenState.AppOnSecondary) {
            val packageName = secondaryAppPackage
            val appStillPresent =
                packageName != null &&
                    secondaryDisplay()?.displayId?.let { displayId ->
                        TopDisplayTaskResolver.isPackageOnDisplay(context, packageName, displayId)
                    } == true
            if (appStillPresent) {
                return SecondaryRecoveryGate(suppressReason = "app-on-secondary:$packageName")
            }
            secondaryAppPackage = null
            store.onSecondaryAppDismissed()
        }
        val now = System.currentTimeMillis()
        val memoryDelay = (memoryGuardReclaimDeferUntil - now).coerceAtLeast(0L)
        if (memoryDelay > 0L) {
            return SecondaryRecoveryGate(deferMs = memoryDelay)
        }
        val session = store.nowPlaying.value
        if (store.hasActiveSessions()) {
            val launchDelay =
                if (session == null) {
                    GAME_LAUNCH_RECLAIM_DEFER_MS
                } else {
                    (GAME_LAUNCH_RECLAIM_DEFER_MS - (now - session.sessionStartedAt))
                        .coerceAtLeast(0L)
                }
            if (launchDelay > 0L) {
                return SecondaryRecoveryGate(deferMs = launchDelay)
            }
        }
        return SecondaryRecoveryGate()
    }

    /**
     * After a game launches on the top display, pull its task above [MainActivity].
     * Without this, Thor keeps the HOME task resumed while the emulator sits invisible.
     */
    fun focusGameOnPrimary(packageName: String) {
        secondaryDisplayHost.showActivity("focus-game:$packageName")
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
            // Automatic focus never cold-starts (allowColdStart=false).
            val moved = SessionTaskRegistry.moveToFront(context, packageName, allowColdStart = false)
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
                SessionTaskRegistry.clear(packageName)
                WajihaLog.w(
                    WajihaTags.DISPLAY,
                    "focusGameOnPrimary: no live task for pkg=$packageName (skip cold-start)",
                )
            }
        }, delayMs)
    }

    /** After OOM-guard kills, pause reclaim so recovery does not thrash bottom HOME. */
    fun deferReclaimForMemoryGuard() {
        memoryGuardReclaimDeferUntil =
            System.currentTimeMillis() + MEMORY_GUARD_RECLAIM_DEFER_MS
        cancelSecondaryRecovery("memory-guard")
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "deferReclaimForMemoryGuard: ${MEMORY_GUARD_RECLAIM_DEFER_MS}ms",
        )
    }

    private fun reclaimSecondaryHomeOnDisplay(
        displayId: Int,
        launchIfNeeded: Boolean = true,
    ) {
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
    }

    /** One post-recovery check; retry attempts never manipulate display 0. */
    private fun reconcilePrimaryAfterSecondaryRecovery() {
        val actualTop =
            TopDisplayTaskResolver.topPackageOnDisplay(
                context,
                Display.DEFAULT_DISPLAY,
            )
        if (actualTop == null) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "reconcilePrimaryAfterSecondaryRecovery: skip — top task unavailable",
            )
            return
        }
        val topGame = store.topDisplayForegroundPackage.value
        if (topGame != null && topGame != context.packageName && store.getSession(topGame) != null) {
            if (actualTop == topGame) return
            if (TopDisplayTaskResolver.taskIdForPackage(context, topGame) != null ||
                SessionTaskRegistry.hasTask(topGame)
            ) {
                focusGameOnPrimary(topGame)
            } else {
                SessionTaskRegistry.clear(topGame)
                // Do not restorePrimaryHero here — a non-session app may own display 0.
            }
        } else if (!store.hasActiveSessions()) {
            if (actualTop == context.packageName) return
            restorePrimaryHero()
        }
    }

    private fun refresh() {
        val secondaryDisplayId = secondaryDisplay()?.displayId
        store.onDisplaysChanged(secondaryDisplayId)
    }

    /**
     * Ensures the bottom-screen activity is alive on the secondary display.
     * Safe to call from MainActivity / SecondaryHomeActivity onResume.
     */
    fun ensureSecondaryHome() {
        if (store.forceSingleScreen) {
            WajihaLog.d(WajihaTags.DISPLAY, "ensureSecondaryHome: skip — forceSingleScreen")
            return
        }
        val display = secondaryDisplay()
        if (display == null) {
            WajihaLog.d(WajihaTags.DISPLAY, "ensureSecondaryHome: no secondary display")
            return
        }
        if (store.state.value == DualScreenState.DualBrowsing) {
            store.setSecondaryMode(SecondaryMode.GameGrid)
        }
        val visibleId = SecondaryHomeActivity.visibleDisplayId
        val onScreen = isSecondaryHealthy(display.displayId)
        if (onScreen) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "ensureSecondaryHome: already visible on displayId=${display.displayId}",
            )
            return
        }
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "ensureSecondaryHome: requesting SecondaryHome on displayId=${display.displayId} " +
                "(visibleDisplayId=$visibleId resumed=${SecondaryHomeActivity.isResumed})",
        )
        requestSecondaryRecovery(display.displayId, "ensure")
    }

    fun launchSecondaryHomeOn(
        displayId: Int,
        reclaim: Boolean = false,
    ) {
        if (store.forceSingleScreen) {
            WajihaLog.d(WajihaTags.DISPLAY, "launchSecondaryHomeOn: skip — forceSingleScreen")
            return
        }
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        if (reclaim) {
            flags = flags or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        flags = flags or Intent.FLAG_ACTIVITY_NO_ANIMATION
        val component = ComponentName(context, SecondaryHomeActivity::class.java)
        val intent =
            Intent(Intent.ACTION_MAIN)
                .setComponent(component)
                .addCategory(SECONDARY_HOME_CATEGORY)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(EXTRA_INTERNAL_SECONDARY_HOME_LAUNCH, true)
                .setPackage(context.packageName)
                .addFlags(flags)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        val flagLabel =
            if (reclaim) {
                "NEW_TASK|REORDER_TO_FRONT|RESET_TASK_IF_NEEDED|NO_ANIMATION"
            } else {
                "NEW_TASK|RESET_TASK_IF_NEEDED|NO_ANIMATION"
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

    /**
     * Tear down SecondaryHome when Single screen is enabled. Stops recovery work
     * and finishes any live secondary activity (including system SECONDARY_HOME starts).
     */
    fun dismissSecondaryHome() {
        WajihaLog.i(WajihaTags.DISPLAY, "dismissSecondaryHome")
        cancelSecondaryRecovery("dismiss-secondary")
        secondaryDisplayHost.hideOverlay()
        secondaryTransitionCover.hide()
        secondaryAppPackage = null
        SecondaryHomeActivity.finishIfRunning()
    }

    /** Apply Single screen pref transitions from [WajihaApplication] settings mirror. */
    fun onForceSingleScreenChanged(enabled: Boolean) {
        if (enabled) {
            dismissSecondaryHome()
        } else if (secondaryDisplay() != null) {
            refresh()
            ensureSecondaryHome()
        } else {
            refresh()
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        WajihaLog.i(WajihaTags.DISPLAY, "onDisplayAdded: displayId=$displayId")
        refresh()
        if (displayId != secondaryDisplay()?.displayId) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "onDisplayAdded: ignore non-physical secondary displayId=$displayId",
            )
            return
        }
        mainHandler.postDelayed(
            { requestSecondaryRecovery(displayId, "display-added") },
            DISPLAY_ADDED_SETTLE_MS,
        )
    }

    override fun onDisplayRemoved(displayId: Int) {
        WajihaLog.i(WajihaTags.DISPLAY, "onDisplayRemoved: displayId=$displayId")
        if (displayId != store.secondaryDisplayId.value) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "onDisplayRemoved: ignore non-physical secondary displayId=$displayId",
            )
            return
        }
        cancelSecondaryRecovery("display-removed")
        refresh()
    }

    override fun onDisplayChanged(displayId: Int) {
        WajihaLog.d(WajihaTags.DISPLAY, "onDisplayChanged: displayId=$displayId")
        if (displayId == Display.DEFAULT_DISPLAY || displayId == store.secondaryDisplayId.value) {
            refresh()
        }
    }

    private companion object {
        /** minSdk 30 — [Intent.CATEGORY_SECONDARY_HOME] is API 33+. */
        const val SECONDARY_HOME_CATEGORY = "android.intent.category.SECONDARY_HOME"
        private const val EXTRA_INTERNAL_SECONDARY_HOME_LAUNCH =
            "com.wajiha.extra.INTERNAL_SECONDARY_HOME_LAUNCH"
        private const val DISPLAY_ADDED_SETTLE_MS = 200L
        private const val PRIMARY_RECONCILE_SETTLE_MS = 60L
        private const val EXHAUSTED_RECOVERY_RETRY_MS = 2_000L
        private const val SECONDARY_PROBE_MIN_INTERVAL_MS = 5_000L
        private const val PRIMARY_HOME_MIN_INTERVAL_MS = 2_000L
        private const val SECONDARY_COVER_REMOVE_DELAY_MS = 120L
        private const val SECONDARY_COVER_TIMEOUT_MS = 2_000L
        private const val SECONDARY_SURFACE_RELEASE_DELAY_MS = 350L
        private const val PRIMARY_RECLAIM_AFTER_SURFACE_RELEASE_MS = 180L
        private const val PRIMARY_HOME_REQUEST_CODE = 4_004
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
        if (displayId != Display.DEFAULT_DISPLAY) {
            secondaryDisplayHost.showActivity("app-on-secondary:$packageName")
        }
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options =
            ActivityOptions
                .makeCustomAnimation(context, 0, 0)
                .setLaunchDisplayId(displayId)
        return try {
            context.startActivity(launchIntent, options.toBundle())
            if (displayId != Display.DEFAULT_DISPLAY && !store.forceSingleScreen) {
                secondaryAppPackage = packageName
                store.onAppSentToSecondary()
                cancelSecondaryRecovery("app-on-secondary")
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

internal fun isPhysicalSecondaryDisplay(
    displayId: Int,
    displayFlags: Int,
    isValid: Boolean,
): Boolean =
    displayId != Display.DEFAULT_DISPLAY &&
        isValid &&
        displayFlags and Display.FLAG_PRIVATE == 0
