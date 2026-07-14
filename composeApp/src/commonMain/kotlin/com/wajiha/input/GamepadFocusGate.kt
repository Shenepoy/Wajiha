package com.wajiha.input

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget

/**
 * Marks a composable as focusable for gamepad D-pad traversal.
 * Default Compose focus indication is suppressed — use [com.wajiha.ui.components.gamepad.wajihaFocusIndicator].
 * Set [enabled] to false for touch-only controls (e.g. header chips).
 *
 * When [bringIntoView] is false, uses [focusTarget] so focus changes do not trigger
 * Compose’s default bring-into-view scroll (grids drive their own smooth scroll).
 */
@Composable
fun Modifier.wajihaGamepadFocus(
    enabled: Boolean = true,
    bringIntoView: Boolean = true,
): Modifier =
    when {
        !enabled -> {
            focusProperties { canFocus = false }
        }

        bringIntoView -> {
            val interactionSource = remember { MutableInteractionSource() }
            focusable(interactionSource = interactionSource)
        }

        else -> {
            focusTarget()
        }
    }
