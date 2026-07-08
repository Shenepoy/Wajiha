package com.wajiha.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.GamepadTextEditRegistry
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.theme.WajihaSpacing

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

    DisposableEffect(layerId) {
        GamepadLayers.stack.push(layerId)
        onDispose { GamepadLayers.stack.pop(layerId) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            GamepadButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            GamepadButton(text = dismissText, onClick = onDismiss, outlined = true)
        },
        modifier = Modifier.onPreviewKeyEvent { event ->
            when {
                GamepadKeys.isBack(event.type, event.key) -> {
                    if (GamepadTextEditRegistry.dismissIfEditing()) {
                        true
                    } else {
                        onDismiss()
                        true
                    }
                }
                GamepadKeys.isConfirm(event.type, event.key) -> {
                    onConfirm()
                    true
                }
                else -> false
            }
        }
    )
}

@Composable
fun GamepadModal(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!visible) return
    val layerId = "modal_${title.hashCode()}"

    DisposableEffect(layerId) {
        GamepadLayers.stack.push(layerId)
        onDispose { GamepadLayers.stack.pop(layerId) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(WajihaSpacing.lg)
            .onPreviewKeyEvent { event ->
                if (GamepadKeys.isBack(event.type, event.key)) {
                    if (GamepadTextEditRegistry.dismissIfEditing()) {
                        true
                    } else {
                        onDismiss()
                        true
                    }
                } else {
                    false
                }
            }
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
