package com.wajiha.input

import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as AndroidKeyEvent

/**
 * Dispatches screen-level preview keys to the top [GamepadLayers] handler
 * before Compose focus routing — needed when nothing in the subtree is focused
 * (e.g. home library after touch-scroll disposed the selected tile).
 *
 * Includes face buttons A/X/Y so library confirm and context menus still work
 * on the secondary display when Compose window focus is absent.
 * B stays on the activity back dispatcher (menu [BackHandler]s) so a bridged
 * DOWN dismiss is not followed by a second back on UP.
 */
fun tryDispatchTopLayerPreviewKey(event: AndroidKeyEvent): Boolean {
    if (!isFocusIndependentPreviewKey(event.keyCode)) return false
    return GamepadLayers.stack.dispatchTopPreviewKey(event.toComposeKeyEvent())
}

private fun isFocusIndependentPreviewKey(keyCode: Int): Boolean =
    keyCode == AndroidKeyEvent.KEYCODE_BUTTON_L1 ||
        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_R1 ||
        keyCode == AndroidKeyEvent.KEYCODE_PAGE_UP ||
        keyCode == AndroidKeyEvent.KEYCODE_PAGE_DOWN ||
        keyCode == AndroidKeyEvent.KEYCODE_MOVE_HOME ||
        keyCode == AndroidKeyEvent.KEYCODE_MOVE_END ||
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A ||
        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_X ||
        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_Y

private fun AndroidKeyEvent.toComposeKeyEvent(): KeyEvent = KeyEvent(this)
