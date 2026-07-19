package com.wajiha.ui.components.gamepad

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
import com.wajiha.platform.UiSound
import com.wajiha.ui.components.LocalUiFeedback
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
    focusRequester: FocusRequester? = null,
    focusId: Any? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    sound: UiSound? = UiSound.Open,
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val feedback = LocalUiFeedback.current
    val interactionSource = rememberPressInteractionSource()
    val localFocusRequester = remember { FocusRequester() }
    val resolvedFocusRequester = focusRequester ?: localFocusRequester
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val resolvedFocusId = focusId ?: text
    val anchor =
        remember(continuity, layerId, resolvedFocusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(resolvedFocusId, layerId)
        }

    fun performClick() {
        if (!enabled) return
        when (sound) {
            UiSound.Navigate -> feedback.navigate()
            UiSound.Open -> feedback.confirm()
            UiSound.Back -> feedback.back()
            UiSound.Launch -> feedback.launch()
            null -> Unit
        }
        onClick()
    }

    val chrome =
        Modifier
            .clip(WajihaShapes.button)
            .wajihaPressedFeedback(interactionSource, WajihaShapes.button)
            .wajihaFocusIndicator(
                highlighted = !useCustomNav && focused,
                shape = WajihaShapes.button,
                focusAnchor = anchor,
            ).defaultMinSize(minHeight = LocalSettingRowMinHeight.current)
            .focusRequester(resolvedFocusRequester)
            .then(
                if (gamepadFocusable) {
                    Modifier.reportSectionVisibleFocus(resolvedFocusRequester)
                } else {
                    Modifier
                },
            ).then(
                if (!useCustomNav) {
                    Modifier
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused && anchor != null) {
                                continuity?.claim(anchor, FocusClaimSource.Compose)
                            }
                            onFocusedChanged?.invoke(it.isFocused)
                        }.wajihaGamepadFocus(enabled && gamepadFocusable)
                } else {
                    Modifier
                },
            ).then(
                if (!useCustomNav) {
                    Modifier.onPreviewKeyEvent { event ->
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
            ).pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures {
                        if (anchor != null) {
                            continuity?.claim(anchor, FocusClaimSource.Touch)
                        }
                        try {
                            resolvedFocusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                        performClick()
                    }
                }
            }

    if (outlined) {
        OutlinedButton(
            onClick = ::performClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = modifier.then(chrome),
            shape = WajihaShapes.button,
        ) { Text(text) }
    } else {
        Button(
            onClick = ::performClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = modifier.then(chrome),
            shape = WajihaShapes.button,
        ) { Text(text) }
    }
}
