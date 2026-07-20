package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.UiSound
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.theme.WajihaShapes

private const val DisabledContentAlpha = 0.38f

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
    GamepadButtonScaffold(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        outlined = outlined,
        gamepadFocusable = gamepadFocusable,
        focusRequester = focusRequester,
        focusId = focusId ?: text,
        onFocusedChanged = onFocusedChanged,
        sound = sound,
        content = { Text(text) },
    )
}

/** Compact square button with a vector glyph (e.g. Apps Options). */
@Composable
fun GamepadIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = true,
    gamepadFocusable: Boolean = true,
    focusRequester: FocusRequester? = null,
    focusId: Any? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    sound: UiSound? = UiSound.Open,
) {
    GamepadButtonScaffold(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
        enabled = enabled,
        outlined = outlined,
        gamepadFocusable = gamepadFocusable,
        focusRequester = focusRequester,
        focusId = focusId ?: contentDescription,
        onFocusedChanged = onFocusedChanged,
        sound = sound,
        contentPadding = PaddingValues(10.dp),
        content = {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
private fun GamepadButtonScaffold(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    outlined: Boolean,
    gamepadFocusable: Boolean,
    focusRequester: FocusRequester?,
    focusId: Any,
    onFocusedChanged: ((Boolean) -> Unit)?,
    sound: UiSound?,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val feedback = LocalUiFeedback.current
    val interactionSource = rememberPressInteractionSource()
    val localFocusRequester = remember { FocusRequester() }
    val resolvedFocusRequester = focusRequester ?: localFocusRequester
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val anchor =
        remember(continuity, layerId, focusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(focusId, layerId)
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

    // Keep Material Button enabled so Compose can focus it; dim when logically disabled.
    // Focus chrome still paints while disabled so D-pad can land on min/max steppers.
    val chrome =
        Modifier
            .alpha(if (enabled) 1f else DisabledContentAlpha)
            .wajihaFocusIndicator(
                highlighted = !useCustomNav && focused,
                shape = WajihaShapes.button,
                focusAnchor = anchor,
            ).clip(WajihaShapes.button)
            .wajihaPressedFeedback(interactionSource, WajihaShapes.button)
            .defaultMinSize(minHeight = LocalSettingRowMinHeight.current)
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
                        }
                        // Focusable even when logically disabled (min/max steppers).
                        .wajihaGamepadFocus(gamepadFocusable)
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

    if (outlined) {
        OutlinedButton(
            onClick = ::performClick,
            enabled = true,
            interactionSource = interactionSource,
            contentPadding = contentPadding,
            modifier = modifier.then(chrome),
            shape = WajihaShapes.button,
        ) { content() }
    } else {
        Button(
            onClick = ::performClick,
            enabled = true,
            interactionSource = interactionSource,
            contentPadding = contentPadding,
            modifier = modifier.then(chrome),
            shape = WajihaShapes.button,
        ) { content() }
    }
}
