package com.wajiha.android.input

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import com.wajiha.input.GamepadBackPreview
import com.wajiha.input.dispatchTopLayerPreviewKey
import com.wajiha.input.tryDispatchTopLayerPreviewKey
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
    dispatch: (KeyEvent) -> Boolean,
): Boolean {
    if (tryDispatchTopLayerPreviewKey(event)) return true
    return when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> {
            if (event.action == KeyEvent.ACTION_DOWN) {
                WajihaLog.d(
                    WajihaTags.GAMEPAD,
                    "map: BUTTON_A → DPAD_CENTER (confirm)",
                )
            }
            dispatch(
                KeyEvent(
                    event.downTime,
                    event.eventTime,
                    event.action,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    event.repeatCount,
                ),
            )
            true
        }

        KeyEvent.KEYCODE_BUTTON_B -> {
            // Let screen preview handle B-down first (e.g. dock → games). If consumed,
            // skip the UP back so we don't also fire activity BackHandler.
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                event.repeatCount == 0 &&
                dispatchTopLayerPreviewKey(event)
            ) {
                GamepadBackPreview.consumeNextBUp = true
                WajihaLog.d(WajihaTags.GAMEPAD, "map: BUTTON_B → preview consumed")
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                if (GamepadBackPreview.consumeNextBUp) {
                    GamepadBackPreview.consumeNextBUp = false
                    return true
                }
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

        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN,
        -> {
            if (event.action == KeyEvent.ACTION_DOWN) {
                WajihaLog.d(
                    WajihaTags.GAMEPAD,
                    "map: shoulder/Y keyCode=${event.keyCode} → pass-through",
                )
            }
            dispatch(event)
            true
        }

        else -> {
            false
        }
    }
}
