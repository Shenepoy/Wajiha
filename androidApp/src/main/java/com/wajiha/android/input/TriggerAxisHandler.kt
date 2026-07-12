package com.wajiha.android.input

import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.platform.AppActions
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.SystemNotificationStore

/**
 * Thor / Xbox expose L2/R2 as analog axes — typically [MotionEvent.AXIS_BRAKE] /
 * [MotionEvent.AXIS_GAS] (axis 23 / 22), sometimes also LTRIGGER/RTRIGGER.
 * Edge-detect presses so L2 toggles gamepad ownership and R2 the notification panel.
 */
class TriggerAxisHandler(
    private val store: DualScreenStore,
    private val notifications: SystemNotificationStore,
    private val appActions: AppActions,
) {
    private val triggers = LauncherTriggerActions(store, notifications, appActions)
    private var l2Pressed = false
    private var r2Pressed = false
    private var lastLoggedBucket: Int = -1

    /**
     * Install before Compose can swallow joystick MOVE events.
     * Uses the decor view listener (survives Compose window.callback swaps).
     */
    fun installOn(
        activity: ComponentActivity,
        gate: GamepadGate,
    ) {
        val decor = activity.window?.decorView ?: return
        decor.setOnGenericMotionListener { _, event ->
            !gate.shouldBlockGamepad() && onGenericMotion(event)
        }
        // Also wrap window callback in case the view listener is skipped.
        val window = activity.window ?: return
        val original = window.callback
        if (original != null && original !is TriggerWindowCallback) {
            window.callback = TriggerWindowCallback(original, gate, this)
        }
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            "TriggerAxisHandler installed on ${activity.javaClass.simpleName}",
        )
    }

    /**
     * @return true when a trigger edge was handled (caller should consume the motion).
     */
    fun onGenericMotion(event: MotionEvent): Boolean {
        val l2 =
            maxOf(
                event.getAxisValue(MotionEvent.AXIS_BRAKE),
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            )
        val r2 =
            maxOf(
                event.getAxisValue(MotionEvent.AXIS_GAS),
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            )

        val hasTriggerSignal = l2 >= 0.05f || r2 >= 0.05f
        if (!hasTriggerSignal && !isJoystickOrGamepad(event)) return false

        val dualState = store.state.value
        if (dualState == DualScreenState.SingleDisplay) return false

        if (hasTriggerSignal) {
            val bucket = ((l2 * 20).toInt() shl 8) or (r2 * 20).toInt()
            if (bucket != lastLoggedBucket) {
                lastLoggedBucket = bucket
                WajihaLog.i(
                    WajihaTags.GAMEPAD,
                    "axis: BRAKE/L2=$l2 GAS/R2=$r2 action=${event.actionMasked} " +
                        "src=0x${Integer.toHexString(event.source)} dev=${event.deviceId}",
                )
            }
        }

        var handled = false

        val l2Down = l2 >= PRESS_THRESHOLD
        if (l2Down != l2Pressed) {
            l2Pressed = l2Down
            if (l2Down) {
                handled = triggers.onL2("axis")
            }
        }

        val r2Down = r2 >= PRESS_THRESHOLD
        if (r2Down != r2Pressed) {
            r2Pressed = r2Down
            if (r2Down) {
                handled = triggers.onR2("axis") || handled
            }
        }

        return handled
    }

    private fun isJoystickOrGamepad(event: MotionEvent): Boolean {
        val sources = event.source
        return sources and InputDevice.SOURCE_CLASS_JOYSTICK != 0 ||
            sources and InputDevice.SOURCE_GAMEPAD != 0 ||
            sources and InputDevice.SOURCE_JOYSTICK != 0
    }

    companion object {
        /** Thor analog triggers often peak below 1.0 — fire a bit early. */
        private const val PRESS_THRESHOLD = 0.35f
    }
}

private class TriggerWindowCallback(
    private val original: Window.Callback,
    private val gate: GamepadGate,
    private val triggers: TriggerAxisHandler,
) : Window.Callback by original {
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!gate.shouldBlockGamepad() && triggers.onGenericMotion(event)) {
            return true
        }
        return original.dispatchGenericMotionEvent(event)
    }
}
