package com.wajiha.android

import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.display.SecondaryDisplayHost
import com.wajiha.android.display.SecondaryRenderSurface
import com.wajiha.android.input.GamepadGate
import com.wajiha.android.input.GamepadKeyRouter
import com.wajiha.android.input.TriggerAxisHandler
import com.wajiha.android.input.dispatchLauncherKeyEvent
import com.wajiha.android.monitor.ForegroundAppMonitor
import com.wajiha.android.ui.hideSystemStatusBar
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.secondary.SecondaryApp
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * HOME activity for the secondary built-in display (AYN Thor bottom screen).
 * The system launches this automatically on the second display when Wajiha
 * is the default launcher (SECONDARY_HOME category — RetroHrai/iiSU model).
 * When Wajiha is not the default home, [com.wajiha.android.display.DisplayCoordinator]
 * launches it explicitly.
 *
 * On Thor, bottom-screen HOME is routed to Launcher3's established SECONDARY_HOME
 * task; [DisplayCoordinator] restores this task with bounded recovery.
 */
class SecondaryHomeActivity : ComponentActivity() {
    private val displayCoordinator: DisplayCoordinator by inject()
    private val secondaryDisplayHost: SecondaryDisplayHost by inject()
    private val dualScreenStore: DualScreenStore by inject()
    private val gamepadKeyRouter: GamepadKeyRouter by inject()
    private val triggerAxisHandler: TriggerAxisHandler by inject()
    private val gamepadGate: GamepadGate by inject()
    private val foregroundAppMonitor: ForegroundAppMonitor by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val displayId = display?.displayId
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "onCreate: displayId=$displayId taskId=$taskId",
        )
        if (dualScreenStore.forceSingleScreen) {
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "onCreate: forceSingleScreen — finishing SecondaryHome",
            )
            finish()
            overridePendingTransition(0, 0)
            return
        }
        if (displayId == Display.DEFAULT_DISPLAY) {
            val secondary = displayCoordinator.secondaryDisplay()
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "onCreate: landed on display 0 — redirecting to displayId=" +
                    "${secondary?.displayId ?: "none"}",
            )
            if (secondary != null) {
                displayCoordinator.launchSecondaryHomeOn(secondary.displayId, reclaim = true)
            }
            finish()
            overridePendingTransition(0, 0)
            return
        }
        if (displayId != null) {
            registerTask(displayId, taskId)
        }
        displayCoordinator.handleSecondaryHomeIntent(this, intent)
        instanceRef = WeakReference(this)
        secondaryDisplayHost.attach(
            activity = this,
            gamepadKeyRouter = gamepadKeyRouter,
            triggerAxisHandler = triggerAxisHandler,
            gamepadGate = gamepadGate,
        )
        gamepadKeyRouter.attach(GamepadOwner.Secondary, this)
        // Home surface: BACK must never dismiss it (in-app screens register
        // their own Compose BackHandlers on top of this).
        onBackPressedDispatcher.addCallback(this) { }
        hideSystemStatusBar()
        setContent {
            val renderSurface by secondaryDisplayHost.surface.collectAsState()
            if (renderSurface == SecondaryRenderSurface.Activity) {
                SecondaryApp()
            }
        }
        window.decorView.post {
            triggerAxisHandler.installOn(this, gamepadGate)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        dispatchLauncherKeyEvent(
            owner = GamepadOwner.Secondary,
            event = event,
            gamepadGate = gamepadGate,
            gamepadKeyRouter = gamepadKeyRouter,
        ) { super.dispatchKeyEvent(it) }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!gamepadGate.shouldBlockGamepad() && triggerAxisHandler.onGenericMotion(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!gamepadGate.shouldBlockGamepad() && triggerAxisHandler.onGenericMotion(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        display?.displayId?.let { unregisterTask(it, taskId) }
        gamepadKeyRouter.detach(GamepadOwner.Secondary, this)
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        secondaryDisplayHost.detach(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val displayId = display?.displayId
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "onNewIntent: displayId=$displayId taskId=$taskId " +
                "intent=${intent.action ?: intent.categories?.joinToString()}",
        )
        if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            registerTask(displayId, taskId)
        }
        displayCoordinator.handleSecondaryHomeIntent(this, intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val displayId = display?.displayId
        if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            displayCoordinator.onSecondaryHomeUserLeave(this)
        }
        WajihaLog.d(
            WajihaTags.DISPLAY,
            "onUserLeaveHint: displayId=$displayId taskId=$taskId",
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val displayId = display?.displayId
        if (hasFocus) {
            hideSystemStatusBar()
        } else if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            WajihaLog.d(
                WajihaLogKind.WINDOW,
                "SecondaryHome.onWindowFocusChanged: lost focus displayId=$displayId",
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemStatusBar()
        isResumed = true
        gamepadGate.onLauncherForegrounded()
        visibleDisplayId = display?.displayId
        display?.displayId?.let { registerTask(it, taskId) }
        visibleDisplayId?.let { displayCoordinator.onSecondaryHomeResumed(this) }
        foregroundAppMonitor.onSecondaryLauncherForegrounded()
        WajihaLog.d(
            WajihaLogKind.WINDOW,
            "SecondaryHome.onResume: displayId=$visibleDisplayId taskId=$taskId",
        )
    }

    override fun onPause() {
        val displayId = display?.displayId
        super.onPause()
        isResumed = false
        if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            WajihaLog.d(
                WajihaLogKind.WINDOW,
                "SecondaryHome.onPause: displayId=$displayId taskId=$taskId",
            )
        }
    }

    override fun onStart() {
        super.onStart()
        visibleDisplayId = display?.displayId
    }

    override fun onStop() {
        val displayId = display?.displayId
        super.onStop()
        if (visibleDisplayId == displayId) visibleDisplayId = null
        if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            WajihaLog.d(
                WajihaLogKind.WINDOW,
                "SecondaryHome.onStop: displayId=$displayId taskId=$taskId",
            )
            displayCoordinator.onSecondaryHomeStopped(displayId)
        }
    }

    companion object {
        /** Which display this activity is currently visible on, null if none. */
        @Volatile
        var visibleDisplayId: Int? = null

        /** True while the secondary home activity is in the resumed lifecycle. */
        @Volatile
        var isResumed: Boolean = false

        private val displayTaskIds = ConcurrentHashMap<Int, Int>()

        @Volatile
        private var instanceRef: WeakReference<SecondaryHomeActivity>? = null

        fun taskIdForDisplay(displayId: Int): Int? = displayTaskIds[displayId]

        fun registerTask(
            displayId: Int,
            taskId: Int,
        ) {
            displayTaskIds[displayId] = taskId
        }

        private fun unregisterTask(
            displayId: Int,
            taskId: Int,
        ) {
            if (displayTaskIds[displayId] == taskId) {
                displayTaskIds.remove(displayId)
            }
        }

        /** Finish the live secondary home activity if any (Single screen mode). */
        fun finishIfRunning() {
            val activity = instanceRef?.get() ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                    activity.overridePendingTransition(0, 0)
                }
            }
        }
    }
}
