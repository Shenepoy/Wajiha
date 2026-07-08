package com.wajiha.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.showGamepadChrome
import com.wajiha.ui.theme.WajihaShapes

/**
 * Registers a navigable slot with the nav controller.
 * Visual highlight comes from [focusedIndex], not Compose focus.
 */
@Composable
fun GamepadNavItem(
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemId: Any? = null,
    onEnterEdit: (() -> Boolean)? = null,
    onExitEdit: (() -> Boolean)? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable (highlighted: Boolean) -> Unit
) {
    val controller = LocalGamepadNavController.current
    val id = itemId ?: remember { Any() }

    if (controller != null) {
        DisposableEffect(id, enabled, onActivate, onEnterEdit, onExitEdit) {
            controller.register(id, onActivate, enabled, onEnterEdit, onExitEdit)
            onDispose { controller.unregister(id) }
        }
        // Observe index changes so highlight updates on D-pad moves.
        val focusedIndex = controller.focusState.focusedIndex
        val highlighted = controller.isSlotFocused(id)
        if (onFocus != null) {
            androidx.compose.runtime.LaunchedEffect(highlighted) {
                if (highlighted) onFocus()
            }
        }
        val showChrome = showGamepadChrome(highlighted)
        NavItemChrome(
            modifier = modifier,
            showChrome = showChrome,
            onActivate = onActivate,
            enabled = enabled
        ) {
            content(showChrome)
        }
    } else {
        Box(
            modifier = modifier.pointerInput(onActivate, enabled) {
                if (enabled) detectTapGestures { onActivate() }
            }
        ) {
            content(false)
        }
    }
}

@Composable
private fun NavItemChrome(
    showChrome: Boolean,
    onActivate: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale = if (showChrome) WajihaFocus.selectedScale else 1f
    Box(
        modifier = modifier
            .scale(scale)
            .clip(WajihaShapes.focus)
            .then(
                if (showChrome) {
                    Modifier
                        .background(
                            color = WajihaFocus.selectedBackground(),
                            shape = WajihaShapes.focus
                        )
                        .border(
                            width = WajihaFocus.selectedBorderWidth,
                            color = WajihaFocus.selectedBorderColor(),
                            shape = WajihaShapes.focus
                        )
                } else {
                    Modifier
                }
            )
            .pointerInput(onActivate, enabled) {
                if (enabled) detectTapGestures { onActivate() }
            }
    ) {
        content()
    }
}
