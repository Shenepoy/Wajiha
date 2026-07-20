package com.wajiha.ui.components.gamepad

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import com.wajiha.ui.theme.showGamepadChrome
import kotlinx.coroutines.launch

/**
 * Grid tile: touch first tap selects (and takes gamepad focus), second tap launches.
 * Gamepad: index selection via [GamepadNavItem]; launch via screen-level confirm.
 */
@Composable
fun GamepadTile(
    selected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusId: Any? = null,
    gamepadFocusable: Boolean = true,
    navHighlighted: Boolean = false,
    /** When true, touch always invokes [onLaunch] (session switcher tiles). */
    touchSwitchMode: Boolean = false,
    /**
     * When true (default), gaining Compose focus calls [onSelect].
     * Home library drives selection via D-pad/touch only — focus restore during
     * scroll must not rewrite [onSelect] or selection jumps backward mid-scroll.
     */
    selectOnFocus: Boolean = true,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val localRequester = remember { FocusRequester() }
    val requester = focusRequester ?: localRequester
    val scope = rememberCoroutineScope()
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val resolvedFocusId = focusId ?: remember { Any() }
    val anchor =
        remember(continuity, layerId, resolvedFocusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(resolvedFocusId, layerId)
        }
    val rawHighlight =
        navHighlighted ||
            (
                !useCustomNav &&
                    if (selectOnFocus) {
                        focused || selected
                    } else {
                        // Library drives chrome via selection only — stray Compose
                        // focus on a peek tile must not light up the wrong game.
                        selected
                    }
            )
    val navChrome = showGamepadChrome(navHighlighted)
    // Keep grid pop subtle — 1.05 scaled tiles collide with neighbors and clip edges.
    val targetScale =
        if (navChrome || showGamepadChrome(rawHighlight)) {
            WajihaFocus.selectedScale
        } else {
            1f
        }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "gamepad_tile_focus_scale",
    )

    fun claimFocusFromTouch() {
        if (useCustomNav) return
        scope.launch {
            // After the pointer gesture ends / recomposition, so Android touch-mode
            // doesn't immediately drop the focus we just requested.
            withFrameNanos { }
            try {
                requester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = WajihaSpacing.touchMin, minHeight = WajihaSpacing.touchMin)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.then(
                    if (navChrome) {
                        Modifier
                            .wajihaFocusIndicator(
                                highlighted = true,
                                shape = WajihaShapes.tile,
                                selected = true,
                                focusAnchor = anchor,
                            ).background(
                                color = WajihaFocus.selectedBackground(),
                                shape = WajihaShapes.tile,
                            )
                    } else {
                        Modifier.wajihaFocusIndicator(
                            highlighted = rawHighlight,
                            shape = WajihaShapes.tile,
                            selected = selected,
                            focusAnchor = anchor,
                        )
                    },
                ).clip(WajihaShapes.tile)
                .then(
                    if (!useCustomNav) {
                        Modifier
                            .focusRequester(requester)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused && anchor != null) {
                                    continuity?.claim(anchor, FocusClaimSource.Compose)
                                }
                                onFocusChanged(it.isFocused)
                                if (it.isFocused && selectOnFocus) onSelect()
                            }.wajihaGamepadFocus(
                                enabled = gamepadFocusable,
                                // Home/app grids own smooth scroll; skip default bring-into-view.
                                bringIntoView = false,
                            )
                    } else {
                        Modifier
                    },
                ).pointerInput(selected, focused, onSelect, onLaunch, onLongPress, touchSwitchMode, useCustomNav) {
                    detectTapGestures(
                        onLongPress = {
                            if (anchor != null) {
                                continuity?.claim(anchor, FocusClaimSource.Touch)
                            }
                            onLongPress?.invoke()
                        },
                        onTap = {
                            if (anchor != null) {
                                continuity?.claim(anchor, FocusClaimSource.Touch)
                            }
                            when {
                                touchSwitchMode -> {
                                    onLaunch()
                                }

                                // Second tap launches only when this tile already owns focus.
                                selected && focused -> {
                                    onLaunch()
                                }

                                else -> {
                                    onSelect()
                                    claimFocusFromTouch()
                                }
                            }
                        },
                    )
                },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        content()
    }
}
