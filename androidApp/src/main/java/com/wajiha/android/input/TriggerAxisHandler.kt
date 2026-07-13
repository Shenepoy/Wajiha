package com.wajiha.android.input

import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import com.wajiha.input.ControllerGlyphStore
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.platform.AppActions
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.SystemNotificationStore

/**
 * Thor / Xbox expose L2/R2 as analog axes — typically [MotionEvent.AXIS_BRAKE] /
 * [MotionEvent.AXIS_GAS] (axis 23 / 22), sometimes also LTRIGGER/RTRIGGER.
 * Edge-detect presses so L2 toggles gamepad ownership and R2 the notification panel.
 *
 * Trigger pressed state is tracked **per [MotionEvent.getDeviceId]** so multiple
 * controllers cannot race a single global edge detector.
 */
class TriggerAxisHandler(
    private val store: DualScreenStore,
    private val notifications: SystemNotificationStore,
    private val appActions: AppActions,
    private val glyphStore: ControllerGlyphStore,
    private val deviceRegistry: GamepadDeviceRegistry,
) {
    private val triggers = LauncherTriggerActions(store, notifications, appActions)
    private val byDevice = mutableMapOf<Int, DeviceTriggerState>()
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
        WajihaLog.d(
            WajihaLogKind.INPUT,
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
                WajihaLog.d(
                    WajihaLogKind.INPUT,
                    "axis: L2=$l2 R2=$r2 device=${event.deviceId}",
                    minIntervalMs = 100L,
                )
            }
        }

        val state = byDevice.getOrPut(event.deviceId) { DeviceTriggerState() }
        var handled = false

        val l2Down = l2 >= PRESS_THRESHOLD
        if (l2Down != state.l2Pressed) {
            state.l2Pressed = l2Down
            WajihaLog.d(
                WajihaLogKind.INPUT,
                "trigger: L2 ${if (l2Down) "DOWN" else "UP"} src=axis device=${event.deviceId}",
            )
            if (l2Down) {
                noteGlyphInput(event.deviceId)
                handled = triggers.onL2("axis")
            }
        }

        val r2Down = r2 >= PRESS_THRESHOLD
        if (r2Down != state.r2Pressed) {
            state.r2Pressed = r2Down
            WajihaLog.d(
                WajihaLogKind.INPUT,
                "trigger: R2 ${if (r2Down) "DOWN" else "UP"} src=axis device=${event.deviceId}",
            )
            if (r2Down) {
                noteGlyphInput(event.deviceId)
                handled = triggers.onR2("axis") || handled
            }
        }

        return handled
    }

    private fun noteGlyphInput(deviceId: Int) {
        val controller = deviceRegistry.controllerForDeviceId(deviceId)
        if (controller != null) {
            glyphStore.noteInput(controller.stableId, controller.type)
        } else {
            glyphStore.noteInputByDeviceId(deviceId)
        }
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

    private class DeviceTriggerState {
        var l2Pressed = false
        var r2Pressed = false
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
