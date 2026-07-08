package com.wajiha.input

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent

/**
 * Dispatches shoulder / tab keys to the top [GamepadLayers] preview handler
 * before Compose focus routing — fixes L1/R1 on Settings/Info when nothing
 * in the screen subtree is focused yet.
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
        keyCode == AndroidKeyEvent.KEYCODE_MOVE_END

private fun AndroidKeyEvent.toComposeKeyEvent(): KeyEvent = KeyEvent(this)
