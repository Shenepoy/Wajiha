package com.wajiha.ui.components.gamepad

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
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
    val scale =
        when {
            navChrome -> WajihaFocus.selectedScale
            showGamepadChrome(rawHighlight) -> 1.05f
            else -> 1f
        }

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
                .scale(scale)
                .clip(WajihaShapes.tile)
                .then(
                    if (navChrome) {
                        Modifier
                            .background(
                                color = WajihaFocus.selectedBackground(),
                                shape = WajihaShapes.tile,
                            ).wajihaFocusIndicator(
                                highlighted = true,
                                shape = WajihaShapes.tile,
                                selected = true,
                            )
                    } else {
                        Modifier.wajihaFocusIndicator(
                            highlighted = rawHighlight,
                            shape = WajihaShapes.tile,
                            selected = selected,
                        )
                    },
                ).then(
                    if (!useCustomNav) {
                        Modifier
                            .focusRequester(requester)
                            .onFocusChanged {
                                focused = it.isFocused
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
                        onLongPress = { onLongPress?.invoke() },
                        onTap = {
                            when {
                                touchSwitchMode -> onLaunch()
                                // Second tap launches only when this tile already owns focus.
                                selected && focused -> onLaunch()
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
