package com.wajiha.android.display

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.view.Display
import androidx.activity.ComponentActivity
import com.wajiha.android.MainActivity
import com.wajiha.android.SecondaryHomeActivity
import com.wajiha.android.monitor.PrimaryHeroRestoreGate
import com.wajiha.android.monitor.SessionTaskRegistry
import com.wajiha.android.monitor.TopDisplayTaskResolver
import com.wajiha.android.monitor.UsageTimelineActive
import com.wajiha.android.monitor.UsageTimelineEvent
import com.wajiha.android.system.SystemController
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore

/**
 * Watches connected displays and keeps [DualScreenStore] in sync.
 *
 * Bottom-screen launch follows Cocoon's ExternalDisplayActivity model:
 * a dedicated SECONDARY_HOME activity on its own taskAffinity, started
 * explicitly when Wajiha is not yet the default home (system only auto-
 * starts SECONDARY_HOME for the default launcher).
 *
 * When Wajiha is not the default HOME app, never force a CATEGORY_HOME
 * transition onto display 0 or fight the system secondary home after the
 * user presses Home. Yield stays sticky until the user explicitly returns
 * to Wajiha (primary resume / HOME intent) — a timed pause still let
 * recovery and restorePrimaryHero steal both panels.
 */
class DisplayCoordinator(
    private val context: Context,
    private val store: DualScreenStore,
    private val secondaryDisplayHost: SecondaryDisplayHost,
    private val systemController: SystemController,
) : DisplayManager.DisplayListener {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val secondaryTransitionCover = SecondaryTransitionCover()
    private var listenerRegistered = false
    private var secondaryAppPackage: String? = null

    /** Wall-clock when [secondaryAppPackage] was armed — grace against Thor ATM blindness. */
    private var secondaryAppArmedAt = 0L
    private var exhaustedRecoveryRetryUsed = false
    private var lastSecondaryProbeAt = 0L
    private var lastPrimaryHomeRequestAt = 0L

    /**
     * When true, keep the live Overlay until [releaseForeignSecondarySurface]
     * finishes. Resume alone is not enough — Thor can report SecondaryHome
     * healthy while an Azahar-class HWC sideband still blacks the panel.
     */
    private var holdOverlayForForeignSurface = false

    /**
     * Live Overlay armed across [startActivity] for a Wajiha-launched game.
     * Resume must not hand Overlay → Activity until [finishLaunchCoverAfterSecondaryUi].
     */
    @Volatile
    private var holdOverlayForGameLaunch = false
    private var launchCoverReleaseRunnable: Runnable? = null

    /** Skip secondary reclaim briefly after OOM-guard kills. */
    @Volatile
    private var memoryGuardReclaimDeferUntil: Long = 0L

    /**
     * After Home while not the default launcher, suppress secondary reclaim and
     * primary hero restore so stock HOME can own both panels. Cleared only when
     * the user returns to Wajiha ([requestSecondaryAvailability] primary paths).
     */
    @Volatile
    private var yieldSecondaryToSystemHome: Boolean = false
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
        if (yieldSecondaryToSystemHome) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "restorePrimaryHero: skip — yielding to system home",
            )
            return
        }
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
        // Never pull the hero over a live foreign top owner. ATM alone is not enough
        // on Thor (often reports Wajiha while Chrome/emu still own display 0).
        val foreignTop = PrimaryHeroRestoreGate.foreignTopOwnerIfAny(context, store)
        if (foreignTop != null) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "restorePrimaryHero: skip — usage/foreign top pkg=$foreignTop",
            )
            return
        }
        mainHandler.postDelayed({
            // Re-check after the settle delay — a game/app may have taken focus.
            val liveTop = PrimaryHeroRestoreGate.foreignTopOwnerIfAny(context, store)
            if (liveTop != null) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "restorePrimaryHero: skip delayed — usage/foreign top pkg=$liveTop",
                )
                return@postDelayed
            }
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
            // User returned to Wajiha — resume owning the bottom panel.
            if (reason == "primary-resume" || reason == "primary-home-intent") {
                if (yieldSecondaryToSystemHome) {
                    WajihaLog.i(
                        WajihaTags.DISPLAY,
                        "secondaryHome: clear system-home yield reason=$reason",
                    )
                }
                yieldSecondaryToSystemHome = false
            } else if (yieldSecondaryToSystemHome) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "secondaryHome: skip availability — yielding to system home reason=$reason",
                )
                return@runOnMain
            }
            // Foreign app owns bottom — do not reclaim over Chrome/etc.
            if (secondaryAppPackage != null) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "secondaryHome: skip availability — app-on-secondary " +
                        "pkg=$secondaryAppPackage reason=$reason",
                )
                return@runOnMain
            }
            val displayId = secondaryDisplay()?.displayId ?: return@runOnMain
            exhaustedRecoveryRetryUsed = false
            secondaryRecovery.request(displayId, reason)
        }
    }

    /**
     * A real SECONDARY_HOME request means the user pressed Home while another
     * app may still own display 0. Bring Wajiha Home forward there as well —
     * only when Wajiha is the default launcher. Internal recovery launches must
     * not do this.
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
        if (!systemController.isDefaultLauncher()) {
            yieldToSystemHome("secondary-home-intent")
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHome: user HOME ignored — not default launcher",
            )
            return
        }
        // Intentional Home — release sticky bottom-app ownership so reclaim can run.
        clearSecondaryAppOwner("user-secondary-home")
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "secondaryHome: user HOME — bringing primary launcher forward",
        )
        bringPrimaryLauncherForward(activity, userHome = true)
    }

    /**
     * Thor routes bottom-screen Home through its built-in Launcher3 task. Act
     * from onUserLeaveHint while Wajiha is still visible; Android rejects this
     * cross-display Home launch after SecondaryHome has stopped.
     */
    fun onSecondaryHomeUserLeave(activity: ComponentActivity) {
        // Bottom foreign app — leave it alone (do not arm Citra CLEAR_TASK / HOME).
        if (store.state.value == DualScreenState.AppOnSecondary ||
            secondaryAppPackage != null
        ) {
            return
        }

        // Thor fires SecondaryHome onUserLeaveHint synchronously when a top-
        // display game starts. Remounting Overlay / CLEAR_TASK flashes black.
        // Launch already armed the live Overlay (and/or freeze pin) — leave it.
        // Must run before not-default yield so launching from Wajiha still works
        // when another app is the default HOME.
        if (isWithinGameLaunchGrace() ||
            holdOverlayForGameLaunch ||
            secondaryTransitionCover.isPinned()
        ) {
            holdOverlayForForeignSurface = false
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — keep launch cover (grace/pin/live)",
            )
            return
        }

        // Not the default HOME app: never steal either panel after the user
        // leaves for stock HOME. Sticky until primary resume.
        if (!systemController.isDefaultLauncher()) {
            yieldToSystemHome("user-leave")
            holdOverlayForForeignSurface = false
            secondaryTransitionCover.hide(force = true)
            secondaryDisplayHost.hideOverlay()
            val foreignOwner =
                store.topDisplayForegroundPackage.value
                    ?.takeUnless { it == context.packageName }
                    ?.takeIf { it in DUAL_SCREEN_PRESENTATION_PACKAGES }
                    ?: findLiveDualScreenPresentationOwner()
            if (foreignOwner != null) {
                releaseForeignSecondarySurface(activity, foreignOwner)
            }
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — not default launcher; yield HOME",
            )
            return
        }

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

        // Only Citra-family Presentations leave a display-4 HWC sideband.
        // Never CLEAR_TASK a normal single-screen emu (AetherSX2, Eden, …).
        val topPackage =
            store.topDisplayForegroundPackage.value
                ?.takeUnless { it == context.packageName }
        val foreignOwner =
            topPackage?.takeIf { it in DUAL_SCREEN_PRESENTATION_PACKAGES }
                ?: findLiveDualScreenPresentationOwner()
        if (foreignOwner == null) {
            holdOverlayForForeignSurface = false
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — no foreign secondary surface owner",
            )
            return
        }

        // Keep Overlay until CLEAR_TASK tears down the Presentation sideband.
        // onSecondaryHomeResumed alone races HWC and paints black.
        holdOverlayForForeignSurface = true
        val now = System.currentTimeMillis()
        if (now - lastPrimaryHomeRequestAt >= PRIMARY_HOME_MIN_INTERVAL_MS) {
            lastPrimaryHomeRequestAt = now
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — bringing primary launcher over $foreignOwner",
            )
            bringPrimaryLauncherForward(
                activity,
                expectedForeignOwner = foreignOwner,
            )
        } else {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHome: user leave — primary HOME request throttled; " +
                    "still clearing secondary surface owner=$foreignOwner",
            )
        }
        releaseForeignSecondarySurface(activity, foreignOwner)
    }

    private fun yieldToSystemHome(reason: String) {
        yieldSecondaryToSystemHome = true
        cancelSecondaryRecovery("yield-system-home:$reason")
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "secondaryHome: sticky yield to system home reason=$reason",
        )
    }

    /**
     * Primary left while Wajiha is not the default HOME app (Home, Recents,
     * another app). Sticky-yield so background reclaim cannot steal both
     * panels. Skipped during Wajiha-driven game launch cover arming.
     */
    fun onPrimaryUserLeave() {
        if (systemController.isDefaultLauncher()) return
        if (isWithinGameLaunchGrace() ||
            holdOverlayForGameLaunch ||
            secondaryTransitionCover.isPinned()
        ) {
            return
        }
        yieldToSystemHome("primary-user-leave")
        holdOverlayForForeignSurface = false
        secondaryTransitionCover.hide(force = true)
        secondaryDisplayHost.hideOverlay()
    }

    private fun isWithinGameLaunchGrace(): Boolean {
        if (!store.hasActiveSessions()) return false
        val session = store.nowPlaying.value
        val startedAt = session?.sessionStartedAt ?: return true
        return System.currentTimeMillis() - startedAt < GAME_LAUNCH_RECLAIM_DEFER_MS
    }

    /**
     * Arm a live Overlay Presentation across [startActivity]. A bitmap freeze
     * bridges only the Activity → Overlay mount gap; the Overlay then paints
     * Compose continuously (including deferred Now Playing). Timer-dismissing
     * a freeze onto a paused/empty Activity surface was the remaining black flash.
     *
     * @param onReady main thread, once the live Overlay owns the panel
     */
    fun armSecondaryLiveCoverForLaunch(onReady: (() -> Unit)? = null) {
        runOnMain {
            val activity = SecondaryHomeActivity.instance()
            if (activity == null ||
                activity.display?.displayId == null ||
                activity.display?.displayId == Display.DEFAULT_DISPLAY
            ) {
                onReady?.invoke()
                return@runOnMain
            }
            cancelLaunchCoverRelease()
            holdOverlayForGameLaunch = true
            secondaryTransitionCover.pinFor(LAUNCH_COVER_PIN_MS)
            secondaryTransitionCover.show(activity) {
                secondaryDisplayHost.showOverlay(activity)
                warmThen(
                    activity = activity,
                    frames = LAUNCH_OVERLAY_WARM_FRAMES,
                ) {
                    secondaryTransitionCover.hide(force = true)
                    WajihaLog.d(
                        WajihaTags.DISPLAY,
                        "secondaryCover: live Overlay armed for game launch",
                    )
                    onReady?.invoke()
                }
            }
            // Safety: never leave the freeze pin stuck if Overlay mount stalls.
            mainHandler.postDelayed(
                {
                    if (secondaryTransitionCover.isPinned()) {
                        secondaryTransitionCover.hide(force = true)
                    }
                },
                LAUNCH_COVER_PIN_MS,
            )
        }
    }

    /**
     * Apply the deferred bottom mode on the next main turn, then hand
     * Overlay → Activity. Doing that in the [startActivity] frame flashes black.
     */
    fun finishLaunchCoverAfterSecondaryUi() {
        runOnMain {
            mainHandler.post {
                store.applyDeferredSecondaryModeAfterLaunch()
                cancelLaunchCoverRelease()
                val release =
                    Runnable {
                        holdOverlayForGameLaunch = false
                        secondaryTransitionCover.hide(force = true)
                        WajihaLog.d(
                            WajihaTags.DISPLAY,
                            "secondaryCover: launch live handoff Overlay → Activity",
                        )
                        handSecondaryToActivityIfHealthy("launch-settled")
                    }
                launchCoverReleaseRunnable = release
                mainHandler.postDelayed(release, LAUNCH_COVER_AFTER_UI_MS)
            }
        }
    }

    private fun cancelLaunchCoverRelease() {
        launchCoverReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
        launchCoverReleaseRunnable = null
    }

    private fun warmThen(
        activity: Activity,
        frames: Int,
        onDone: () -> Unit,
    ) {
        val decor = activity.window?.decorView
        if (decor == null || frames <= 0) {
            onDone()
            return
        }
        decor.post {
            var left = frames

            fun tick() {
                decor.postOnAnimation {
                    left--
                    if (left <= 0) {
                        onDone()
                    } else {
                        tick()
                    }
                }
            }
            tick()
        }
    }

    /** Citra-family emus that leave a Thor display-4 Presentation sideband. */
    private fun findLiveDualScreenPresentationOwner(): String? {
        for (packageName in DUAL_SCREEN_PRESENTATION_PACKAGES) {
            if (TopDisplayTaskResolver.taskIdForPackage(context, packageName) != null ||
                SessionTaskRegistry.hasTask(packageName)
            ) {
                return packageName
            }
        }
        return null
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
                        holdOverlayForForeignSurface = false
                        mainHandler.postDelayed(
                            {
                                bringPrimaryLauncherForward(
                                    activity,
                                    expectedForeignOwner = packageName,
                                )
                                handSecondaryToActivityIfHealthy("surface-released")
                            },
                            PRIMARY_RECLAIM_AFTER_SURFACE_RELEASE_MS,
                        )
                    }.onFailure {
                        WajihaLog.w(
                            WajihaTags.DISPLAY,
                            "secondaryHome: surface release failed owner=$packageName — ${it.message}",
                        )
                        // Keep Overlay; Activity handoff would black the panel.
                    }
            },
            SECONDARY_SURFACE_RELEASE_DELAY_MS,
        )
    }

    fun onSecondaryHomeStopped(displayId: Int) {
        if (yieldSecondaryToSystemHome) {
            cancelSecondaryRecovery("yield-system-home:secondary-stop")
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHome: stop ignored — yielding to system home " +
                    "displayId=$displayId",
            )
            return
        }
        // Foreign app owns the bottom panel — do not reclaim over it.
        if (store.state.value == DualScreenState.AppOnSecondary ||
            secondaryAppPackage != null
        ) {
            cancelSecondaryRecovery("app-on-secondary:secondary-stop")
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHome: stop ignored — app on secondary " +
                    "pkg=$secondaryAppPackage displayId=$displayId",
            )
            return
        }
        requestSecondaryRecovery(displayId, "stop")
    }

    /**
     * Bring Wajiha Home onto display 0.
     *
     * @param userHome intentional secondary Home — always allowed when default launcher.
     * @param expectedForeignOwner Citra-family package being cleared; proceed only when
     *   no other live foreign top owner is present (or it matches this package).
     */
    private fun bringPrimaryLauncherForward(
        activity: Activity,
        userHome: Boolean = false,
        expectedForeignOwner: String? = null,
    ) {
        if (!systemController.isDefaultLauncher()) {
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHome: skip primary HOME — not default launcher",
            )
            return
        }
        if (!userHome) {
            val foreignTop =
                PrimaryHeroRestoreGate.foreignTopOwnerIfAny(context, store)
            if (foreignTop != null &&
                (expectedForeignOwner == null || foreignTop != expectedForeignOwner)
            ) {
                WajihaLog.i(
                    WajihaTags.DISPLAY,
                    "secondaryHome: skip primary HOME — foreign top pkg=$foreignTop" +
                        " expected=$expectedForeignOwner",
                )
                return
            }
        }
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
            if (yieldSecondaryToSystemHome) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "secondaryRecovery: skip — yielding to system home reason=$reason",
                )
                return@runOnMain
            }
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
            if (yieldSecondaryToSystemHome) return@post
            if (secondaryAppPackage != null) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "secondaryProbe: skip — app-on-secondary pkg=$secondaryAppPackage " +
                        "reason=$reason",
                )
                return@post
            }
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
            {
                // Must not rip off a pinned launch freeze or live-launch Overlay.
                if (!secondaryTransitionCover.isPinned() && !holdOverlayForGameLaunch) {
                    secondaryTransitionCover.hide()
                }
            },
            SECONDARY_COVER_REMOVE_DELAY_MS,
        )
        runOnMain {
            // Do not clear sticky bottom-app ownership on every resume — Thor can
            // briefly resume SecondaryHome during launch / focus flashes and that
            // used to re-arm reclaim over Chrome. Clear only when the foreign app
            // is confirmed gone (or intentional Home already cleared it).
            val foreignPkg = secondaryAppPackage
            if (foreignPkg != null && shouldKeepSecondaryAppOwner(foreignPkg)) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "secondaryHome: resume ignored — keep app-on-secondary pkg=$foreignPkg",
                )
            } else if (foreignPkg != null) {
                clearSecondaryAppOwner("resume-app-gone:$foreignPkg")
            }
            secondaryRecovery.onResumed(displayId)
            // Never tear the live cover down from focusGameOnPrimary. Also wait
            // out foreign Presentation CLEAR_TASK — resume alone races HWC.
            // Game-launch Overlay is released only from finishLaunchCoverAfterSecondaryUi.
            when {
                holdOverlayForForeignSurface -> {
                    WajihaLog.d(
                        WajihaTags.DISPLAY,
                        "secondaryHost: keep Overlay — foreign secondary surface pending",
                    )
                }

                holdOverlayForGameLaunch -> {
                    WajihaLog.d(
                        WajihaTags.DISPLAY,
                        "secondaryHost: keep Overlay — game launch cover active",
                    )
                }

                else -> {
                    handSecondaryToActivityIfHealthy("secondary-healthy")
                }
            }
        }
    }

    /**
     * Dismiss the live secondary Presentation only when the Activity surface is
     * healthy and no Azahar-class sideband clear is in flight. Focusing a
     * top-display game must not call this.
     */
    private fun handSecondaryToActivityIfHealthy(reason: String) {
        if (holdOverlayForForeignSurface) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHost: keep Overlay — foreign secondary surface pending reason=$reason",
            )
            return
        }
        if (holdOverlayForGameLaunch && reason != "launch-settled") {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHost: keep Overlay — game launch cover active reason=$reason",
            )
            return
        }
        val displayId = secondaryDisplay()?.displayId ?: return
        if (!isSecondaryHealthy(displayId)) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "secondaryHost: keep Overlay — secondary not healthy reason=$reason",
            )
            return
        }
        secondaryDisplayHost.showActivity(reason)
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
        if (yieldSecondaryToSystemHome) {
            return SecondaryRecoveryGate(suppressReason = "yield-system-home")
        }
        // Sticky while a foreign app owns the bottom — independent of DualScreenState
        // (GameRunning + bottom Chrome used to fall through and reclaim).
        val foreignPkg = secondaryAppPackage
        if (foreignPkg != null) {
            if (shouldKeepSecondaryAppOwner(foreignPkg)) {
                return SecondaryRecoveryGate(suppressReason = "app-on-secondary:$foreignPkg")
            }
            clearSecondaryAppOwner("gate-app-gone:$foreignPkg")
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

    private fun shouldKeepSecondaryAppOwner(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val withinGrace =
            secondaryAppArmedAt > 0L &&
                now - secondaryAppArmedAt < SECONDARY_APP_OWNER_GRACE_MS
        val stopped =
            withinGrace && packageStoppedSinceArm(packageName, secondaryAppArmedAt)
        return SecondaryOwnerGrace.keepOwner(
            now = now,
            armedAt = secondaryAppArmedAt,
            graceMs = SECONDARY_APP_OWNER_GRACE_MS,
            stoppedSinceArm = stopped,
            aliveAfterGrace = isSecondaryForeignAppAlive(packageName),
        )
    }

    /**
     * True when usage access shows this package stopped after it was armed and
     * no activity has resumed since. Empty or failed queries keep the grace.
     */
    private fun packageStoppedSinceArm(
        packageName: String,
        armedAt: Long,
    ): Boolean {
        val events = usageEventsForPackage(packageName) ?: return false
        if (events.isEmpty()) return false
        val stoppedAfterArm =
            events.any { event ->
                event.eventType == UsageTimelineActive.ACTIVITY_STOPPED &&
                    event.timeStamp >= armedAt
            }
        if (!stoppedAfterArm) return false
        return !UsageTimelineActive.isActive(events)
    }

    private fun usageEventsForPackage(packageName: String): List<UsageTimelineEvent>? {
        if (!hasUsageStatsAccess()) return null
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val windowMs = SECONDARY_APP_OWNER_GRACE_MS + 5_000L
            val events = usm.queryEvents(end - windowMs, end)
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

    private fun hasUsageStatsAccess(): Boolean {
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

    private fun isSecondaryForeignAppAlive(packageName: String): Boolean {
        val displayId = secondaryDisplay()?.displayId
        if (displayId != null &&
            TopDisplayTaskResolver.isPackageOnDisplay(context, packageName, displayId)
        ) {
            return true
        }
        // Thor ATM often cannot see foreign tasks on display 4 — fall back.
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

    private fun clearSecondaryAppOwner(reason: String) {
        val pkg =
            secondaryAppPackage ?: run {
                store.onSecondaryAppDismissed()
                return
            }
        secondaryAppPackage = null
        secondaryAppArmedAt = 0L
        store.onSecondaryAppDismissed()
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "secondaryHome: clear app-on-secondary pkg=$pkg reason=$reason",
        )
    }

    private fun armSecondaryAppOwner(packageName: String) {
        secondaryAppPackage = packageName
        secondaryAppArmedAt = System.currentTimeMillis()
        store.onAppSentToSecondary()
        cancelSecondaryRecovery("app-on-secondary")
        secondaryDisplayHost.showActivity("app-on-secondary:$packageName")
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "secondaryHome: arm app-on-secondary pkg=$packageName",
        )
    }

    /**
     * After a game launches on the top display, pull its task above [MainActivity].
     * Without this, Thor keeps the HOME task resumed while the emulator sits invisible.
     *
     * Does **not** dismiss [SecondaryDisplayHost]'s live Overlay — that cover exists
     * for foreign Presentation sidebands (Azahar/Citra). Gamepad session focus used
     * to call showActivity here and left display 4 black. Handoff is
     * [handSecondaryToActivityIfHealthy] from resume / surface-release only.
     */
    fun focusGameOnPrimary(packageName: String) {
        if (yieldSecondaryToSystemHome) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "focusGameOnPrimary: skip — yielding to system home pkg=$packageName",
            )
            return
        }
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
        if (yieldSecondaryToSystemHome) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "reclaimSecondaryHomeOnDisplay: skip — yielding to system home",
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
    }

    /** One post-recovery check; retry attempts never manipulate display 0. */
    private fun reconcilePrimaryAfterSecondaryRecovery() {
        if (yieldSecondaryToSystemHome) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "reconcilePrimaryAfterSecondaryRecovery: skip — yielding to system home",
            )
            return
        }
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
            // Foreign app on top with no tracked session — leave focus alone.
            // (Previously this called restorePrimaryHero and stole from Turnip/Chrome.)
            if (actualTop != context.packageName) {
                WajihaLog.d(
                    WajihaTags.DISPLAY,
                    "reconcilePrimaryAfterSecondaryRecovery: skip — foreign top pkg=$actualTop",
                )
            }
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
        if (yieldSecondaryToSystemHome) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "ensureSecondaryHome: skip — yielding to system home",
            )
            return
        }
        if (secondaryAppPackage != null) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "ensureSecondaryHome: skip — app-on-secondary pkg=$secondaryAppPackage",
            )
            return
        }
        val display = secondaryDisplay()
        if (display == null) {
            WajihaLog.d(WajihaTags.DISPLAY, "ensureSecondaryHome: no secondary display")
            return
        }
        // Do not force GameGrid — launching an app from Apps/Settings must not
        // yank the bottom route back to Home when SecondaryHome later resumes.
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
        if (yieldSecondaryToSystemHome) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "launchSecondaryHomeOn: skip — yielding to system home",
            )
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
        secondaryTransitionCover.hide(force = true)
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
        if (yieldSecondaryToSystemHome) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "onDisplayAdded: skip recovery — yielding to system home",
            )
            return
        }
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

        /** Max time the bitmap freeze may bridge Activity → live Overlay mount. */
        private const val LAUNCH_COVER_PIN_MS = 1_200L

        /** Frames for Overlay Compose to paint before dropping the bitmap freeze. */
        private const val LAUNCH_OVERLAY_WARM_FRAMES = 3

        /** After launch settle on live Overlay (same Grid UI), hand to Activity. */
        private const val LAUNCH_COVER_AFTER_UI_MS = 400L
        private const val SECONDARY_SURFACE_RELEASE_DELAY_MS = 350L
        private const val PRIMARY_RECLAIM_AFTER_SURFACE_RELEASE_MS = 180L
        private const val PRIMARY_HOME_REQUEST_CODE = 4_004
        private const val FOCUS_DEBOUNCE_MS = 400L

        /** Emulators known to retain a Thor display-4 Presentation sideband. */
        private val DUAL_SCREEN_PRESENTATION_PACKAGES =
            listOf(
                "org.azahar_emu.azahar",
                "io.github.azaharplus.android",
                "io.github.lime3ds.android",
                "org.citra.citra_emu",
                "org.citra.citra_emu.canary",
                "org.citra.emu",
            )

        /** Let top-display emulator win before bottom HOME reclaim runs. */
        private const val GAME_LAUNCH_RECLAIM_DEFER_MS = 800L

        /** After memory-guard kills, avoid reclaim thrashing during recovery. */
        private const val MEMORY_GUARD_RECLAIM_DEFER_MS = 10_000L

        /**
         * Keep sticky bottom-app ownership after launch even if Thor ATM cannot
         * see the foreign task on display 4 yet.
         */
        private const val SECONDARY_APP_OWNER_GRACE_MS = 15_000L
    }

    /** Launch an app on a specific display (running-apps "move to display"). */
    fun launchOnDisplay(
        packageName: String,
        displayId: Int,
    ): Boolean {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
        // Mark AppOnSecondary before startActivity — SecondaryHome onStop races
        // synchronously and used to reclaim over the just-launched app.
        // Do not change secondaryMode / SecondaryRoute — stay on Apps/Settings/…
        // if the user launched from there.
        if (displayId != Display.DEFAULT_DISPLAY && !store.forceSingleScreen) {
            armSecondaryAppOwner(packageName)
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options =
            ActivityOptions
                .makeCustomAnimation(context, 0, 0)
                .setLaunchDisplayId(displayId)
        return try {
            context.startActivity(launchIntent, options.toBundle())
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "launchOnDisplay: pkg=$packageName displayId=$displayId",
            )
            true
        } catch (error: Exception) {
            if (displayId != Display.DEFAULT_DISPLAY && secondaryAppPackage == packageName) {
                clearSecondaryAppOwner("launch-failed")
            }
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "launchOnDisplay: failed pkg=$packageName displayId=$displayId — ${error.message}",
            )
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
