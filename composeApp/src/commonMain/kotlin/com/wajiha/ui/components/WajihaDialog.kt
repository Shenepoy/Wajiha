package com.wajiha.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.ui.components.gamepad.GamepadButton

@Composable
fun WajihaDialog(
    visible: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "OK",
    dismissText: String = "Cancel"
) {
    if (!visible) return
    val layerId = "dialog_${title.hashCode()}"

    GamepadOverlayLayer(
        layerId = layerId,
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm()
            true
        }
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                GamepadButton(text = confirmText, onClick = onConfirm)
            },
            dismissButton = {
                GamepadButton(text = dismissText, onClick = onDismiss, outlined = true)
            }
        )
    }
}
