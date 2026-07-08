package com.wajiha.input

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Marks a composable as focusable for gamepad D-pad traversal.
 * Default Compose focus indication is suppressed — use [com.wajiha.ui.components.gamepad.wajihaFocusIndicator].
 * Set [enabled] to false for touch-only controls (e.g. header chips).
 */
@Composable
fun Modifier.wajihaGamepadFocus(enabled: Boolean = true): Modifier {
    return if (enabled) {
        val interactionSource = remember { MutableInteractionSource() }
        focusable(interactionSource = interactionSource)
    } else {
        focusProperties { canFocus = false }
    }
}
