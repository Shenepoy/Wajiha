package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.components.LocalUiFeedback
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
    shape: Shape = WajihaShapes.focus,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val highlight = navHighlighted || (!useCustomNav && (focused || selected))
    val feedback = LocalUiFeedback.current

    fun performClick() {
        feedback.confirm()
        onClick()
    }

    Box(
        modifier = modifier
            .clip(shape)
            .wajihaPressedFeedback(pressed, shape)
            .wajihaFocusIndicator(
                highlighted = highlight,
                shape = shape,
                selected = selected || navHighlighted
            )
            .then(
                if (!useCustomNav) {
                    Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .wajihaGamepadFocus()
                        .onPreviewKeyEvent { event ->
                            if (GamepadKeys.isConfirm(event.type, event.key)) {
                                performClick()
                                true
                            } else {
                                false
                            }
                        }
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) performClick()
                    }
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        content()
    }
}
