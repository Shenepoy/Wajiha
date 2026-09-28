package com.wajiha.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.input.LocalGamepadOwner
import com.wajiha.input.RememberDialogGamepadKeyRouting
import com.wajiha.ui.components.gamepad.WajihaGlyphAction

@Composable
fun WajihaDialog(
    visible: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "OK",
    dismissText: String = "Cancel",
) {
    if (!visible) return
    val layerId = "dialog_${title.hashCode()}"
    val confirmFocus = remember(layerId) { FocusRequester() }

    LaunchedEffect(layerId) {
        withFrameNanos { }
        try {
            confirmFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    GamepadOverlayLayer(
        layerId = layerId,
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm()
            true
        },
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                LocalGamepadOwner.current?.let { hostOwner ->
                    RememberDialogGamepadKeyRouting(hostOwner)
                }
                Text(title)
            },
            text = { Text(message) },
            confirmButton = {
                WajihaGlyphAction(
                    button = GamepadHintButton.A,
                    label = confirmText,
                    onClick = onConfirm,
                    glyphAtEnd = true,
                    outlined = false,
                    focusRequester = confirmFocus,
                    focusId = "$layerId:confirm",
                )
            },
            dismissButton = {
                WajihaGlyphAction(
                    button = GamepadHintButton.B,
                    label = dismissText,
                    onClick = onDismiss,
                    outlined = true,
                )
            },
        )
    }
}
