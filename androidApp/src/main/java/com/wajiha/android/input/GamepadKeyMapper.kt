package com.wajiha.android.input

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/**
 * Console-style face button mapping for the whole UI:
 * A confirms (arrives as DPAD_CENTER so Compose focus "clicks"),
 * B navigates back (through the activity's back dispatcher, which
 * Compose BackHandlers hook into).
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
    else -> false
}
