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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
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
    focusId: Any? = null,
    shape: Shape = WajihaShapes.focus,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val highlight = navHighlighted || (!useCustomNav && (focused || selected))
    val feedback = LocalUiFeedback.current
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val resolvedFocusId = focusId ?: remember { Any() }
    val anchor =
        remember(continuity, layerId, resolvedFocusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(resolvedFocusId, layerId)
        }

    fun performClick() {
        feedback.confirm()
        onClick()
    }

    Box(
        modifier =
            modifier
                .wajihaFocusIndicator(
                    highlighted = highlight,
                    shape = shape,
                    selected = selected || navHighlighted,
                    focusAnchor = anchor,
                ).clip(shape)
                .wajihaPressedFeedback(pressed, shape)
                .then(
                    if (!useCustomNav) {
                        Modifier
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused && anchor != null) {
                                    continuity?.claim(anchor, FocusClaimSource.Compose)
                                }
                            }.wajihaGamepadFocus()
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
                    },
                ).pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (anchor != null) {
                                continuity?.claim(anchor, FocusClaimSource.Touch)
                            }
                            pressed = true
                            val released = tryAwaitRelease()
                            pressed = false
                            if (released) performClick()
                        },
                    )
                },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        content()
    }
}
