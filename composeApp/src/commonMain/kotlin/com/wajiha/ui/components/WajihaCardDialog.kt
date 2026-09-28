package com.wajiha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.input.LocalGamepadOwner
import com.wajiha.input.RememberDialogGamepadKeyRouting
import com.wajiha.ui.components.gamepad.WajihaGlyphAction
import com.wajiha.ui.theme.WajihaElevation
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Picker card. [WajihaDialog] stays the confirm.
 * B leaves the card. A is the optional create or apply action, and it does not
 * steal A from rows inside [content].
 */
@Composable
fun WajihaCardDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Done",
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val layerId = "card_${title.hashCode()}"
    GamepadOverlayLayer(
        layerId = layerId,
        onDismiss = onDismiss,
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = modifier,
                shape = WajihaShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = WajihaElevation.low,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(WajihaSpacing.md),
                ) {
                    LocalGamepadOwner.current?.let { hostOwner ->
                        RememberDialogGamepadKeyRouting(hostOwner)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = WajihaSpacing.sm),
                    )
                    content()
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = WajihaSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WajihaGlyphAction(
                            button = GamepadHintButton.B,
                            label = dismissText,
                            onClick = onDismiss,
                            outlined = true,
                        )
                        if (confirmText != null && onConfirm != null) {
                            WajihaGlyphAction(
                                button = GamepadHintButton.A,
                                label = confirmText,
                                onClick = onConfirm,
                                glyphAtEnd = true,
                                outlined = false,
                                enabled = confirmEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}
