package com.wajiha.android.display

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.wajiha.android.MainActivity
import com.wajiha.android.SecondaryHomeActivity
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
    private val store: DualScreenStore
) : DisplayManager.DisplayListener {

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listenerRegistered = false

    /** True while a display-0 [MainActivity] instance is in the resumed lifecycle. */
    @Volatile
    private var primaryMainOnDisplay0 = false

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
                    "displayId=$currentDisplayId taskId=${from.taskId} — no redirect"
            )
            return false
        }

        WajihaLog.i(
            WajihaTags.DISPLAY,
            "redirectMainToPrimaryIfNeeded: ${from.javaClass.simpleName} " +
                "displayId=$currentDisplayId taskId=${from.taskId} — " +
                "redirecting to primary (display 0)"
        )
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
        val intent = Intent(from, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(flags)
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "launchPrimaryMain: from=${from.javaClass.simpleName} " +
                "targetDisplayId=${Display.DEFAULT_DISPLAY} " +
                "flags=$flagLabel reusePrimary=$reusePrimary"
        )
        from.startActivity(intent, options.toBundle())
        // Secondary home is started from MainActivity.onResume once display 0
        // has settled — posting here raced ahead of primary and let display 4
        // steal topDisplayFocusedRootTask (Thor bottom drawer symptom).
    }

    /**
     * After the bottom screen is alive, pull the primary HOME task back to the
     * top of the multi-display stack so drawer launches land on the top panel.
     */
    fun focusPrimaryMain(main: Activity) {
        if (main !is MainActivity) return
        if (main.display?.displayId != Display.DEFAULT_DISPLAY) return
        mainHandler.postDelayed({
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                WajihaLog.i(
                    WajihaTags.DISPLAY,
                    "focusPrimaryMain: moveTaskToFront taskId=${main.taskId}"
                )
                am.moveTaskToFront(main.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            } catch (e: Exception) {
                WajihaLog.w(
                    WajihaTags.DISPLAY,
                    "focusPrimaryMain: moveTaskToFront failed — ${e.message}; " +
                        "falling back to REORDER_TO_FRONT on display 0"
                )
                val intent = Intent(main, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                val options = ActivityOptions.makeBasic()
                    .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                main.startActivity(intent, options.toBundle())
            }
        }, 150)
    }

    /** Delay secondary launch until primary has claimed display-0 focus. */
    fun scheduleSecondaryHome(main: MainActivity) {
        focusPrimaryMain(main)
        mainHandler.postDelayed({
            ensureSecondaryHome()
            // Secondary onCreate resumes on display 4 and can steal
            // topDisplayFocusedRootTask — reclaim display 0 afterwards.
            focusPrimaryMain(main)
        }, 300)
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
        if (visibleId == display.displayId) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "ensureSecondaryHome: already visible on displayId=${display.displayId}"
            )
            return
        }
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "ensureSecondaryHome: launching SecondaryHome on displayId=${display.displayId} " +
                "(visibleDisplayId=$visibleId)"
        )
        val reclaim = visibleId != null && visibleId != display.displayId
        launchSecondaryHomeOn(display.displayId, reclaim = reclaim)
    }

    fun launchSecondaryHomeOn(displayId: Int, reclaim: Boolean = false) {
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        if (reclaim) flags = flags or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        val intent = Intent(context, SecondaryHomeActivity::class.java).addFlags(flags)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        val flagLabel = if (reclaim) {
            "NEW_TASK|REORDER_TO_FRONT|RESET_TASK_IF_NEEDED"
        } else {
            "NEW_TASK|RESET_TASK_IF_NEEDED"
        }
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "launchSecondaryHomeOn: targetDisplayId=$displayId reclaim=$reclaim flags=$flagLabel"
        )
        try {
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "launchSecondaryHomeOn: failed on displayId=$displayId — ${e.message}"
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

    /** Launch an app on a specific display (running-apps "move to display"). */
    fun launchOnDisplay(packageName: String, displayId: Int): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
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
