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
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.showGamepadChrome

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
    content: @Composable (highlighted: Boolean) -> Unit,
) {
    val controller = LocalGamepadNavController.current
    val id = itemId ?: remember { Any() }
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val anchor =
        remember(continuity, layerId, id) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(id, layerId)
        }

    if (controller != null) {
        DisposableEffect(id, enabled, onActivate, onEnterEdit, onExitEdit, anchor) {
            controller.register(id, onActivate, enabled, onEnterEdit, onExitEdit)
            if (enabled && anchor != null) {
                continuity?.register(anchor)
            }
            onDispose {
                controller.unregister(id)
                if (anchor != null) {
                    continuity?.unregister(anchor)
                }
            }
        }
        // Observe index changes so highlight updates on D-pad moves.
        val focusedIndex = controller.focusState.focusedIndex
        val highlighted = controller.isSlotFocused(id)
        if (onFocus != null) {
            androidx.compose.runtime.LaunchedEffect(highlighted) {
                if (highlighted) onFocus()
            }
        }
        androidx.compose.runtime.LaunchedEffect(highlighted, anchor) {
            if (highlighted && anchor != null) {
                continuity?.claim(anchor, FocusClaimSource.Gamepad)
            }
        }
        val showChrome = showGamepadChrome(highlighted)
        NavItemChrome(
            modifier = modifier,
            showChrome = showChrome,
            onActivate = onActivate,
            enabled = enabled,
            onTouchFocus = {
                if (anchor != null) {
                    continuity?.claim(anchor, FocusClaimSource.Touch)
                }
            },
        ) {
            content(showChrome)
        }
    } else {
        Box(
            modifier =
                modifier.pointerInput(onActivate, enabled) {
                    if (enabled) detectTapGestures { onActivate() }
                },
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
    onTouchFocus: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = if (showChrome) WajihaFocus.selectedScale else 1f
    Box(
        modifier =
            modifier
                .scale(scale)
                .clip(WajihaShapes.focus)
                .then(
                    if (showChrome) {
                        Modifier
                            .background(
                                color = WajihaFocus.selectedBackground(),
                                shape = WajihaShapes.focus,
                            ).border(
                                width = WajihaFocus.selectedBorderWidth,
                                color = WajihaFocus.selectedBorderColor(),
                                shape = WajihaShapes.focus,
                            )
                    } else {
                        Modifier
                    },
                ).pointerInput(onActivate, onTouchFocus, enabled) {
                    if (enabled) {
                        detectTapGestures {
                            onTouchFocus()
                            onActivate()
                        }
                    }
                },
    ) {
        content()
    }
}
