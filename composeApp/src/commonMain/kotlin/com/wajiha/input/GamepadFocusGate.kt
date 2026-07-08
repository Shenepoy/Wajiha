package com.wajiha.input

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
/**
 * Marks a composable as focusable for gamepad D-pad traversal.
 * Set [enabled] to false for touch-only controls (e.g. header chips).
 */
@Composable
fun Modifier.wajihaGamepadFocus(enabled: Boolean = true): Modifier {
    return if (enabled) {
        focusable()
    } else {
        focusProperties { canFocus = false }
    }
}
