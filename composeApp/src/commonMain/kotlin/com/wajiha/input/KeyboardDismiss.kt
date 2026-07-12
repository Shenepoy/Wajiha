package com.wajiha.input

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * App-wide text-edit dismiss: exit [GamepadSafeTextField] edit mode, hide IME, clear focus.
 * Used by B / system back and tap-outside.
 */
fun dismissTextEdit(
    focusManager: FocusManager? = null,
    keyboard: SoftwareKeyboardController? = null,
): Boolean {
    val wasEditing = GamepadTextEditRegistry.isEditing
    GamepadTextEditRegistry.dismissIfEditing()
    keyboard?.hide()
    focusManager?.clearFocus(force = true)
    return wasEditing
}

/**
 * Tap empty / non-consuming areas to hide the keyboard and leave text-edit mode.
 * Child controls that consume the pointer (including the text field itself) are unaffected.
 */
@Composable
fun Modifier.dismissKeyboardOnOutsideTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return pointerInput(focusManager, keyboard) {
        detectTapGestures {
            if (GamepadTextEditRegistry.isEditing) {
                dismissTextEdit(focusManager, keyboard)
            }
        }
    }
}
