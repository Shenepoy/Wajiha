package com.wajiha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.LocalSettingRowMinHeight
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Reusable Confirm + yellow revert for critical staged edits.
 * Both controls are touch targets and gamepad-focusable.
 */
@Composable
fun CriticalChangeActions(
    hasChanges: Boolean,
    onRevert: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    confirmText: String = "Confirm",
    revertFocusRequester: FocusRequester? = null,
    confirmFocusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onRevert,
            enabled = hasChanges,
            shape = WajihaShapes.button,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = WajihaColors.Tertiary,
                    disabledContentColor =
                        WajihaColors.Tertiary.copy(alpha = WajihaAlphas.outlineMuted),
                ),
            modifier =
                Modifier
                    .defaultMinSize(minHeight = LocalSettingRowMinHeight.current)
                    .then(
                        if (revertFocusRequester != null) {
                            Modifier.focusRequester(revertFocusRequester)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Text("↩")
        }
        GamepadButton(
            text = confirmText,
            onClick = onConfirm,
            enabled = hasChanges && confirmEnabled,
            focusRequester = confirmFocusRequester,
        )
    }
}
