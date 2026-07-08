package com.wajiha.android.input

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/**
 * Console-style face button mapping for the whole UI:
 * A confirms (arrives as DPAD_CENTER so Compose focus "clicks"),
 * B navigates back (through the activity's back dispatcher, which
 * Compose BackHandlers hook into),
 * X passes through to screen-level handlers (e.g. open game settings).
 *
 * Call from [ComponentActivity.dispatchKeyEvent]; returns true when the
 * event was consumed/remapped.
 */
fun handleGamepadKey(
    activity: ComponentActivity,
    event: KeyEvent,
    dispatch: (KeyEvent) -> Boolean
): Boolean = when (event.keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> {
        if (event.action == KeyEvent.ACTION_DOWN) {
            WajihaLog.d(
                WajihaTags.GAMEPAD,
                "map: BUTTON_A → DPAD_CENTER (confirm)"
            )
        }
        dispatch(
            KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                KeyEvent.KEYCODE_DPAD_CENTER,
                event.repeatCount
            )
        )
        true
    }
    KeyEvent.KEYCODE_BUTTON_B -> {
        if (event.action == KeyEvent.ACTION_UP) {
            WajihaLog.d(WajihaTags.GAMEPAD, "map: BUTTON_B → back")
            activity.onBackPressedDispatcher.onBackPressed()
        }
        true
    }
    KeyEvent.KEYCODE_BUTTON_X -> {
        if (event.action == KeyEvent.ACTION_DOWN) {
            WajihaLog.d(WajihaTags.GAMEPAD, "map: BUTTON_X → pass-through (settings)")
        }
        dispatch(event)
        true
    }
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_PAGE_UP,
    KeyEvent.KEYCODE_PAGE_DOWN -> {
        if (event.action == KeyEvent.ACTION_DOWN) {
            WajihaLog.d(
                WajihaTags.GAMEPAD,
                "map: shoulder keyCode=${event.keyCode} → pass-through (L1/R1)"
            )
        }
        dispatch(event)
        true
    }
    else -> false
}
