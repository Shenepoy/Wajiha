package com.wajiha.android

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.input.GamepadKeyRouter
import com.wajiha.android.input.handleGamepadKey
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.secondary.SecondaryApp
import org.koin.android.ext.android.inject

/**
 * HOME activity for the secondary built-in display (AYN Thor bottom screen).
 * The system launches this automatically on the second display when Wajiha
 * is the default launcher (SECONDARY_HOME category — RetroHrai/iiSU model).
 * When Wajiha is not the default home, [com.wajiha.android.display.DisplayCoordinator]
 * launches it explicitly.
 */
class SecondaryHomeActivity : ComponentActivity() {

    private val displayCoordinator: DisplayCoordinator by inject()
    private val gamepadKeyRouter: GamepadKeyRouter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val displayId = display?.displayId
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "onCreate: displayId=$displayId taskId=$taskId"
        )
        gamepadKeyRouter.attach(GamepadOwner.Secondary, this)
        // Home surface: BACK must never dismiss it (in-app screens register
        // their own Compose BackHandlers on top of this).
        onBackPressedDispatcher.addCallback(this) { }
        setContent {
            SecondaryApp()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return gamepadKeyRouter.dispatch(GamepadOwner.Secondary, event) { remappedOrRaw ->
            handleGamepadKey(this, remappedOrRaw) { super.dispatchKeyEvent(it) } ||
                super.dispatchKeyEvent(remappedOrRaw)
        }
    }

    override fun onDestroy() {
        gamepadKeyRouter.detach(GamepadOwner.Secondary, this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        visibleDisplayId = display?.displayId
        WajihaLog.d(
            WajihaTags.DISPLAY,
            "onResume: displayId=$visibleDisplayId taskId=$taskId"
        )
        // If the stock launcher took the bottom screen, reclaim it when we
        // regain focus (covers non-default-home until the user sets Wajiha).
        displayCoordinator.ensureSecondaryHome()
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
    }
}
