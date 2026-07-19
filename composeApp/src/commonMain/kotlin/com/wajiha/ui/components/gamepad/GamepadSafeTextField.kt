package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadTextEditRegistry
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

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
    navItemId: Any? = null,
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
) {
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val controller = LocalGamepadNavController.current
    val useCustomNav = controller != null
    val id = navItemId ?: remember { Any() }
    var highlighted by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun exitEditing(): Boolean {
        if (!editing) return false
        return if (controller != null) {
            controller.exitEdit()
        } else {
            editing = false
            keyboard?.hide()
            true
        }
    }

    val dismissHandler =
        remember(controller, keyboard) {
            { exitEditing() }
        }

    DisposableEffect(editing) {
        if (editing) {
            GamepadTextEditRegistry.register(dismissHandler)
        }
        onDispose {
            GamepadTextEditRegistry.unregister(dismissHandler)
        }
    }

    fun enterEditing() {
        if (controller != null) {
            controller.enterEditFor(id)
        } else {
            editing = true
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
            keyboard?.show()
        }
    }

    val fieldModifier =
        modifier
            .clip(WajihaShapes.focus)
            .then(
                if (highlighted) {
                    Modifier.border(
                        width = WajihaFocus.borderWidth,
                        color = WajihaFocus.borderColor(),
                        shape = WajihaShapes.focus,
                    )
                } else {
                    Modifier
                },
            )

    if (useCustomNav) {
        com.wajiha.input.GamepadNavItem(
            onActivate = { enterEditing() },
            itemId = id,
            onEnterEdit = {
                editing = true
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {
                }
                keyboard?.show()
                true
            },
            onExitEdit = {
                editing = false
                keyboard?.hide()
                true
            },
            modifier = fieldModifier,
        ) { isHighlighted ->
            highlighted = isHighlighted
            TextFieldBody(
                value = value,
                onValueChange = onValueChange,
                label = label,
                secret = secret,
                singleLine = singleLine,
                editing = editing,
                overridden = overridden,
                overrideHint = overrideHint,
                focusRequester = focusRequester,
                onTap = { enterEditing() },
                onFocusChanged = { focused ->
                    if (!focused && editing) {
                        editing = false
                        controller.focusState.editing = false
                        keyboard?.hide()
                    }
                },
                modifier = Modifier,
            )
        }
    } else {
        LegacyGamepadSafeTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            secret = secret,
            singleLine = singleLine,
            overridden = overridden,
            overrideHint = overrideHint,
            modifier = fieldModifier,
        )
    }
}

@Composable
private fun TextFieldLabel(
    label: String,
    overridden: Boolean,
    overrideHint: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        Text(label)
        if (overridden) {
            PlatformOverrideIndicator(hint = overrideHint)
        }
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
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onTap: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { TextFieldLabel(label, overridden, overrideHint) },
        singleLine = singleLine,
        readOnly = !editing,
        visualTransformation =
            if (secret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
        modifier =
            modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (onFocusChanged != null) {
                        Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                    } else {
                        Modifier
                    },
                ).then(
                    if (onTap != null) {
                        Modifier.pointerInput(onTap, editing) {
                            detectTapGestures {
                                onTap()
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
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
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focusRequester =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }

    fun exitEditing(): Boolean {
        if (!editing) return false
        editing = false
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        return true
    }

    val dismissHandler = remember(keyboard) { { exitEditing() } }

    DisposableEffect(editing) {
        if (editing) {
            GamepadTextEditRegistry.register(dismissHandler)
        }
        onDispose {
            GamepadTextEditRegistry.unregister(dismissHandler)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { TextFieldLabel(label, overridden, overrideHint) },
        singleLine = singleLine,
        readOnly = !editing,
        visualTransformation =
            if (secret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
        modifier =
            modifier
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    when {
                        editing && GamepadKeys.isBack(event.type, event.key) -> {
                            exitEditing()
                        }

                        editing && event.key == androidx.compose.ui.input.key.Key.DirectionUp -> {
                            exitEditing()
                        }

                        !editing -> {
                            val direction =
                                when (event.key) {
                                    androidx.compose.ui.input.key.Key.DirectionDown -> {
                                        androidx.compose.ui.focus.FocusDirection.Down
                                    }

                                    androidx.compose.ui.input.key.Key.DirectionUp -> {
                                        androidx.compose.ui.focus.FocusDirection.Up
                                    }

                                    androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                        androidx.compose.ui.focus.FocusDirection.Left
                                    }

                                    androidx.compose.ui.input.key.Key.DirectionRight -> {
                                        androidx.compose.ui.focus.FocusDirection.Right
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            if (direction != null) {
                                focusManager.moveFocus(direction)
                                true
                            } else if (com.wajiha.input.GamepadKeys
                                    .isConfirm(event.type, event.key)
                            ) {
                                editing = true
                                keyboard?.show()
                                true
                            } else {
                                false
                            }
                        }

                        else -> {
                            false
                        }
                    }
                }.onFocusChanged { state ->
                    if (state.isFocused && !editing) {
                        keyboard?.hide()
                    }
                    if (!state.isFocused && editing) {
                        editing = false
                        keyboard?.hide()
                    }
                }.pointerInput(Unit) {
                    detectTapGestures {
                        editing = true
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                        keyboard?.show()
                    }
                },
    )
}

/** Alias for platform picker and list search. */
@Composable
fun GamepadSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Search",
    modifier: Modifier = Modifier,
    navItemId: Any? = null,
) {
    GamepadSafeTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        navItemId = navItemId,
    )
}
