package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.theme.WajihaShapes

@Composable
fun GamepadChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gamepadFocusable: Boolean = true,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        shape = WajihaShapes.chip,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(WajihaShapes.chip)
            .wajihaFocusIndicator(
                highlighted = !useCustomNav && focused,
                shape = WajihaShapes.chip
            )
            .then(
                if (!useCustomNav) {
                    Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .wajihaGamepadFocus(gamepadFocusable)
                        .onPreviewKeyEvent { event ->
                            if (GamepadKeys.isConfirm(event.type, event.key)) {
                                onClick()
                                true
                            } else {
                                false
                            }
                        }
                } else {
                    Modifier
                }
            )
            .pointerInput(onClick) {
                detectTapGestures { onClick() }
            },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
