package com.wajiha.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Deprecated(
    "Moved to com.wajiha.ui.components.gamepad.GamepadSafeTextField",
    ReplaceWith(
        "GamepadSafeTextField(value, onValueChange, label, modifier, secret, singleLine)",
        "com.wajiha.ui.components.gamepad.GamepadSafeTextField"
    )
)
@Composable
fun GamepadSafeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    singleLine: Boolean = true
) = com.wajiha.ui.components.gamepad.GamepadSafeTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    secret = secret,
    singleLine = singleLine
)
