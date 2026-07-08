package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.ui.theme.InputMode
import com.wajiha.ui.theme.LocalInputMode
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes

/**
 * Text field integrated with custom gamepad nav.
 * Highlighted in nav index; A enters edit, B exits — no focus trap.
 */
@Composable
fun GamepadSafeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    singleLine: Boolean = true,
    navItemId: Any? = null
) {
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val useCustomNav = LocalGamepadNavController.current != null
    val inputMode = LocalInputMode.current
    val id = navItemId ?: remember { Any() }
    var highlighted by remember { mutableStateOf(false) }

    val fieldModifier = modifier
        .clip(WajihaShapes.focus)
        .then(
            if (highlighted && inputMode == InputMode.Gamepad) {
                Modifier.border(
                    width = WajihaFocus.borderWidth,
                    color = WajihaFocus.borderColor(),
                    shape = WajihaShapes.focus
                )
            } else {
                Modifier
            }
        )

    if (useCustomNav) {
        com.wajiha.input.GamepadNavItem(
            onActivate = { },
            itemId = id,
            onEnterEdit = {
                editing = true
                keyboard?.show()
                true
            },
            onExitEdit = {
                editing = false
                keyboard?.hide()
                true
            },
            modifier = fieldModifier
        ) { isHighlighted ->
            highlighted = isHighlighted
            TextFieldBody(
                value = value,
                onValueChange = onValueChange,
                label = label,
                secret = secret,
                singleLine = singleLine,
                editing = editing,
                modifier = Modifier
            )
        }
    } else {
        LegacyGamepadSafeTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            secret = secret,
            singleLine = singleLine,
            modifier = fieldModifier
        )
    }
}

@Composable
private fun TextFieldBody(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean,
    singleLine: Boolean,
    editing: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        readOnly = !editing,
        visualTransformation = if (secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = modifier
    )
}

/** Legacy focus-based text field for screens not yet on custom nav. */
@Composable
private fun LegacyGamepadSafeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        readOnly = !editing,
        visualTransformation = if (secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when {
                    editing && event.key == androidx.compose.ui.input.key.Key.DirectionUp -> {
                        editing = false
                        keyboard?.hide()
                        true
                    }
                    !editing -> {
                        val direction = when (event.key) {
                            androidx.compose.ui.input.key.Key.DirectionDown ->
                                androidx.compose.ui.focus.FocusDirection.Down
                            androidx.compose.ui.input.key.Key.DirectionUp ->
                                androidx.compose.ui.focus.FocusDirection.Up
                            androidx.compose.ui.input.key.Key.DirectionLeft ->
                                androidx.compose.ui.focus.FocusDirection.Left
                            androidx.compose.ui.input.key.Key.DirectionRight ->
                                androidx.compose.ui.focus.FocusDirection.Right
                            else -> null
                        }
                        if (direction != null) {
                            focusManager.moveFocus(direction)
                            true
                        } else if (com.wajiha.input.GamepadKeys.isConfirm(event.type, event.key)) {
                            editing = true
                            keyboard?.show()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .onFocusChanged { state ->
                if (state.isFocused && !editing) {
                    keyboard?.hide()
                }
                if (!state.isFocused) {
                    editing = false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    editing = true
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {
                    }
                    keyboard?.show()
                }
            }
    )
}

/** Alias for platform picker and list search. */
@Composable
fun GamepadSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Search",
    modifier: Modifier = Modifier,
    navItemId: Any? = null
) {
    GamepadSafeTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        navItemId = navItemId
    )
}
