package com.wajiha.android.input

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import com.wajiha.state.GamepadOwner

/** Shared gamepad key dispatch for launcher activities. */
fun ComponentActivity.dispatchLauncherKeyEvent(
    owner: GamepadOwner,
    event: KeyEvent,
    gamepadGate: GamepadGate,
    gamepadKeyRouter: GamepadKeyRouter,
    fallback: (KeyEvent) -> Boolean,
): Boolean {
    if (gamepadGate.shouldBlockGamepad()) return false
    return gamepadKeyRouter.dispatch(owner, event) { remappedOrRaw ->
        handleGamepadKey(this, remappedOrRaw) { fallback(remappedOrRaw) } ||
            fallback(remappedOrRaw)
    }
}
