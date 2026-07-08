package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
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
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun GamepadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    gamepadFocusable: Boolean = true,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val chrome = Modifier
        .clip(WajihaShapes.focus)
        .then(
            if (showGamepadChrome(!useCustomNav && focused)) {
                Modifier.border(
                    width = WajihaFocus.borderWidth,
                    color = WajihaFocus.borderColor(),
                    shape = WajihaShapes.focus
                )
            } else {
                Modifier
            }
        )
        .defaultMinSize(minHeight = WajihaSpacing.touchMin)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .then(
            if (!useCustomNav) {
                Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .wajihaGamepadFocus(enabled && gamepadFocusable)
            } else {
                Modifier
            }
        )
        .then(
            if (!useCustomNav) {
                Modifier.onPreviewKeyEvent { event ->
                    if (GamepadKeys.isConfirm(event.type, event.key)) {
                        if (enabled) onClick()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            }
        )
        .pointerInput(enabled, onClick) {
            if (enabled) detectTapGestures { onClick() }
        }

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.then(chrome)
        ) { Text(text) }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.then(chrome)
        ) { Text(text) }
    }
}
