package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.theme.showGamepadChrome
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes

/**
 * Base focusable surface with consistent focus chrome.
 * Touch uses [pointerInput] tap — never combine with launch-target clickable.
 */
@Composable
fun GamepadFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    navHighlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val highlight = showGamepadChrome(navHighlighted || (!useCustomNav && (focused || selected)))
    Box(
        modifier = modifier
            .clip(WajihaShapes.focus)
            .then(
                if (highlight) {
                    Modifier.border(
                        width = WajihaFocus.borderWidth,
                        color = WajihaFocus.borderColor(),
                        shape = WajihaShapes.focus
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (!useCustomNav) {
                    Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .wajihaGamepadFocus()
                        .onPreviewKeyEvent { event ->
                            if (GamepadKeys.isConfirm(event.type, event.key)) {
                                onClick()
                                true
                            } else {
                                false
                            }
                        }
                } else {
                    Modifier
                }
            )
            .pointerInput(onClick) {
                detectTapGestures { onClick() }
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        content()
    }
}
