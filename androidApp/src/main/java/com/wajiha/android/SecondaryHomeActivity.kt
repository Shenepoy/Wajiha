package com.wajiha.android

import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.input.GamepadGate
import com.wajiha.android.input.GamepadKeyRouter
import com.wajiha.android.input.handleGamepadKey
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.secondary.SecondaryApp
import org.koin.android.ext.android.inject
import java.util.concurrent.ConcurrentHashMap

/**
 * HOME activity for the secondary built-in display (AYN Thor bottom screen).
 * The system launches this automatically on the second display when Wajiha
 * is the default launcher (SECONDARY_HOME category — RetroHrai/iiSU model).
 * When Wajiha is not the default home, [com.wajiha.android.display.DisplayCoordinator]
 * launches it explicitly.
 *
 * On Thor, bottom-screen HOME is routed to Launcher3's established SECONDARY_HOME
 * task; [DisplayCoordinator.scheduleSecondaryHomeReclaim] fights that from here.
 */
class SecondaryHomeActivity : ComponentActivity() {

    private val displayCoordinator: DisplayCoordinator by inject()
    private val gamepadKeyRouter: GamepadKeyRouter by inject()
    private val gamepadGate: GamepadGate by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val displayId = display?.displayId
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "onCreate: displayId=$displayId taskId=$taskId"
        )
        if (displayId == Display.DEFAULT_DISPLAY) {
            val secondary = displayCoordinator.secondaryDisplay()
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "onCreate: landed on display 0 — redirecting to displayId=" +
                    "${secondary?.displayId ?: "none"}"
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
        gamepadKeyRouter.attach(GamepadOwner.Secondary, this)
        // Home surface: BACK must never dismiss it (in-app screens register
        // their own Compose BackHandlers on top of this).
        onBackPressedDispatcher.addCallback(this) { }
        setContent {
            SecondaryApp()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            val displayId = display?.displayId
            if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
                WajihaLog.i(
                    WajihaTags.DISPLAY,
                    "dispatchKeyEvent: HOME consumed on displayId=$displayId"
                )
                displayCoordinator.beginFastSecondaryReclaim(displayId)
            }
            return true
        }
        if (gamepadGate.shouldBlockGamepad()) return false
        return gamepadKeyRouter.dispatch(GamepadOwner.Secondary, event) { remappedOrRaw ->
            handleGamepadKey(this, remappedOrRaw) { super.dispatchKeyEvent(it) } ||
                super.dispatchKeyEvent(remappedOrRaw)
        }
    }

    override fun onDestroy() {
        display?.displayId?.let { unregisterTask(it, taskId) }
        gamepadKeyRouter.detach(GamepadOwner.Secondary, this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val displayId = display?.displayId
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "onNewIntent: displayId=$displayId taskId=$taskId " +
                "intent=${intent.action ?: intent.categories?.joinToString()}"
        )
        if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            registerTask(displayId, taskId)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val displayId = display?.displayId
        WajihaLog.d(
            WajihaTags.DISPLAY,
            "onUserLeaveHint: displayId=$displayId taskId=$taskId"
        )
        if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            displayCoordinator.beginFastSecondaryReclaim(displayId)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val displayId = display?.displayId
        if (!hasFocus && displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "onWindowFocusChanged: lost focus displayId=$displayId — fast reclaim"
            )
            displayCoordinator.beginFastSecondaryReclaim(displayId)
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        gamepadGate.onLauncherForegrounded()
        visibleDisplayId = display?.displayId
        display?.displayId?.let { registerTask(it, taskId) }
        displayCoordinator.stopFastSecondaryReclaim()
        WajihaLog.d(
            WajihaTags.DISPLAY,
            "onResume: displayId=$visibleDisplayId taskId=$taskId"
        )
    }

    override fun onPause() {
        val displayId = display?.displayId
        if (displayId != null && displayId != Display.DEFAULT_DISPLAY) {
            WajihaLog.d(
                WajihaTags.DISPLAY,
                "onPause: displayId=$displayId taskId=$taskId — fast reclaim"
            )
            displayCoordinator.beginFastSecondaryReclaim(displayId)
        }
        super.onPause()
        isResumed = false
        if (visibleDisplayId == displayId) visibleDisplayId = null
    }

    override fun onStart() {
        super.onStart()
        visibleDisplayId = display?.displayId
    }

    override fun onStop() {
        super.onStop()
        if (visibleDisplayId == display?.displayId) visibleDisplayId = null
    }

    companion object {
        /** Which display this activity is currently visible on, null if none. */
        @Volatile
        var visibleDisplayId: Int? = null

        /** True while the secondary home activity is in the resumed lifecycle. */
        @Volatile
        var isResumed: Boolean = false

        private val displayTaskIds = ConcurrentHashMap<Int, Int>()

        fun taskIdForDisplay(displayId: Int): Int? = displayTaskIds[displayId]

        fun registerTask(displayId: Int, taskId: Int) {
            displayTaskIds[displayId] = taskId
        }

        private fun unregisterTask(displayId: Int, taskId: Int) {
            if (displayTaskIds[displayId] == taskId) {
                displayTaskIds.remove(displayId)
            }
        }
    }
}
