package com.wajiha.input

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
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
    onAdjustLeft: (() -> Boolean)? = null,
    onAdjustRight: (() -> Boolean)? = null,
    onFocus: (() -> Unit)? = null,
    /** When false, content owns focus chrome (avoids double rings). */
    drawChrome: Boolean = true,
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
        DisposableEffect(
            id,
            enabled,
            onActivate,
            onEnterEdit,
            onExitEdit,
            onAdjustLeft,
            onAdjustRight,
            anchor,
        ) {
            controller.register(
                id = id,
                onActivate = onActivate,
                enabled = enabled,
                onEnterEdit = onEnterEdit,
                onExitEdit = onExitEdit,
                onAdjustLeft = onAdjustLeft,
                onAdjustRight = onAdjustRight,
            )
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
            drawChrome = drawChrome,
            focusAnchor = anchor,
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
    drawChrome: Boolean,
    focusAnchor: FocusAnchor?,
    onActivate: () -> Unit,
    enabled: Boolean,
    onTouchFocus: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (showChrome) WajihaFocus.selectedScale else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "nav_item_focus_scale",
    )
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.then(
                    if (drawChrome && showChrome) {
                        Modifier
                            .wajihaFocusIndicator(
                                highlighted = true,
                                shape = WajihaShapes.focus,
                                selected = true,
                                focusAnchor = focusAnchor,
                            ).background(
                                color = WajihaFocus.selectedBackground(),
                                shape = WajihaShapes.focus,
                            )
                    } else {
                        Modifier
                    },
                ).clip(WajihaShapes.focus)
                .pointerInput(onActivate, onTouchFocus, enabled) {
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
