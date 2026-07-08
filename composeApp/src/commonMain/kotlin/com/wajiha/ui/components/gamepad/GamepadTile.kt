package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Grid tile: touch first tap selects, second tap launches.
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
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val localRequester = remember { FocusRequester() }
    val requester = focusRequester ?: localRequester
    val rawHighlight = navHighlighted || (!useCustomNav && (focused || selected))
    val navChrome = showGamepadChrome(navHighlighted)
    val scale = when {
        navChrome -> WajihaFocus.selectedScale
        showGamepadChrome(rawHighlight) -> 1.05f
        else -> 1f
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = WajihaSpacing.touchMin, minHeight = WajihaSpacing.touchMin)
            .scale(scale)
            .clip(WajihaShapes.tile)
            .then(
                if (navChrome) {
                    Modifier
                        .background(
                            color = WajihaFocus.selectedBackground(),
                            shape = WajihaShapes.tile
                        )
                        .wajihaFocusIndicator(
                            highlighted = true,
                            shape = WajihaShapes.tile,
                            selected = true
                        )
                } else {
                    Modifier.wajihaFocusIndicator(
                        highlighted = rawHighlight,
                        shape = WajihaShapes.tile,
                        selected = selected
                    )
                }
            )
            .then(
                if (!useCustomNav) {
                    Modifier
                        .focusRequester(requester)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChanged(it.isFocused)
                            if (it.isFocused) onSelect()
                        }
                        .wajihaGamepadFocus(gamepadFocusable)
                } else {
                    Modifier
                }
            )
            .pointerInput(selected, onSelect, onLaunch, onLongPress) {
                detectTapGestures(
                    onLongPress = { onLongPress?.invoke() },
                    onTap = {
                        if (selected) onLaunch() else onSelect()
                    }
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        content()
    }
}
