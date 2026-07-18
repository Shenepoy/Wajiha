package com.wajiha.android.input

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.ui.window.DialogWindowProvider
import com.wajiha.input.DialogGamepadKeyInstaller
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.state.GamepadOwner

/**
 * Compose Dialog windows swallow keys before [ComponentActivity.dispatchKeyEvent],
 * so dual-display forwarding never runs while scrape-review (etc.) is open.
 * Wrap the dialog Window.Callback with the same [GamepadKeyRouter] path.
 */
class AndroidDialogGamepadKeyInstaller(
    private val router: GamepadKeyRouter,
    private val gate: GamepadGate,
    private val triggerAxisHandler: TriggerAxisHandler,
) : DialogGamepadKeyInstaller {
    override fun install(
        androidView: Any,
        hostOwner: GamepadOwner,
    ): () -> Unit {
        val view = androidView as? View ?: return {}
        val window = findDialogWindow(view) ?: return {}
        val original = window.callback ?: return {}
        if (original is DialogGamepadWindowCallback) {
            return {}
        }
        val wrapped =
            DialogGamepadWindowCallback(
                original = original,
                hostOwner = hostOwner,
                router = router,
                gate = gate,
                triggers = triggerAxisHandler,
            )
        window.callback = wrapped
        val decor = window.decorView
        decor.setOnGenericMotionListener { _, event ->
            !gate.shouldBlockGamepad() && triggerAxisHandler.onGenericMotion(event)
        }
        WajihaLog.d(
            WajihaLogKind.INPUT,
            "DialogGamepadKeyRouting installed host=$hostOwner",
        )
        return {
            if (window.callback === wrapped) {
                window.callback = original
            }
            decor.setOnGenericMotionListener(null)
            WajihaLog.d(
                WajihaLogKind.INPUT,
                "DialogGamepadKeyRouting removed host=$hostOwner",
            )
        }
    }

    private fun findDialogWindow(view: View): Window? {
        var parent: Any? = view.parent
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                return parent.window
            }
            parent = (parent as? View)?.parent
        }
        return null
    }
}

private class DialogGamepadWindowCallback(
    private val original: Window.Callback,
    private val hostOwner: GamepadOwner,
    private val router: GamepadKeyRouter,
    private val gate: GamepadGate,
    private val triggers: TriggerAxisHandler,
) : Window.Callback by original {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gate.shouldBlockGamepad()) {
            return original.dispatchKeyEvent(event)
        }
        return router.dispatch(hostOwner, event) { remapped ->
            original.dispatchKeyEvent(remapped)
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!gate.shouldBlockGamepad() && triggers.onGenericMotion(event)) {
            return true
        }
        return original.dispatchGenericMotionEvent(event)
    }
}
