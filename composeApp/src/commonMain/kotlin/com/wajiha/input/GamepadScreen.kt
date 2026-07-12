package com.wajiha.input

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.gamepad.FocusRingOverlayHost
import com.wajiha.ui.components.gamepad.LocalFocusRingOverlay
import com.wajiha.ui.components.gamepad.rememberFocusRingOverlayState
import com.wajiha.ui.theme.LocalInputMode
import com.wajiha.ui.theme.LocalInputModeController
import com.wajiha.ui.theme.rememberInputModeController

/**
 * Registers a gamepad layer and optional screen-level key handler.
 * Touch down claims gamepad for [owner] when [onClaimGamepad] is provided.
 * Tracks touch vs gamepad input mode for chrome visibility app-wide.
 *
 * Text edit defaults: A enters edit on [GamepadSafeTextField], B / system back
 * dismisses the keyboard, tap outside hides the keyboard.
 *
 * [onPreviewKey] is registered on [GamepadLayers] for focus-independent dispatch
 * (Android activity bridge) and mirrored on [onPreviewKeyEvent] when the focus
 * tree tunnels through this root — no root focus target (that would steal indicators).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadScreen(
    layerId: String,
    modifier: Modifier = Modifier,
    owner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    onPreviewKey: ((KeyEvent) -> Boolean)? = null,
    /** Called when [owner] gains gamepad ownership — restore content focus. */
    onOwnerGainedFocus: (suspend () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val processor = remember { GamepadInputProcessor() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    DisposableEffect(layerId, onPreviewKey) {
        GamepadLayers.stack.push(layerId)
        GamepadLayers.stack.setPreviewHandler(layerId, onPreviewKey)
        processor.onLayerPushed()
        onDispose {
            GamepadLayers.stack.setPreviewHandler(layerId, null)
            GamepadLayers.stack.pop(layerId)
            processor.reset()
        }
    }

    if (onOwnerGainedFocus != null) {
        RememberGamepadOwnerFocus(owner = owner, onGained = onOwnerGainedFocus)
    }

    val inputModeController = rememberInputModeController()
    val focusRingOverlay = rememberFocusRingOverlayState()
    val textFieldEditing = GamepadTextEditRegistry.isEditing

    BackHandler(enabled = textFieldEditing) {
        dismissTextEdit(focusManager, keyboard)
    }

    CompositionLocalProvider(
        LocalInputModeController provides inputModeController,
        LocalInputMode provides inputModeController.mode,
        LocalFocusRingOverlay provides focusRingOverlay,
    ) {
        FocusRingOverlayHost(
            state = focusRingOverlay,
            modifier =
                modifier
                    .fillMaxSize()
                    .pointerInput(inputModeController, focusManager, keyboard) {
                        detectTapGestures(
                            onPress = {
                                inputModeController.onTouch()
                                tryAwaitRelease()
                            },
                            onTap = {
                                if (GamepadTextEditRegistry.isEditing) {
                                    dismissTextEdit(focusManager, keyboard)
                                }
                            },
                        )
                    }.onPreviewKeyEvent { event ->
                        if (GamepadKeys.switchesToGamepadMode(event)) {
                            inputModeController.onGamepadKey()
                        }
                        if (
                            GamepadKeys.isBack(event.type, event.key) &&
                            dismissTextEdit(focusManager, keyboard)
                        ) {
                            return@onPreviewKeyEvent true
                        }
                        if (
                            onClaimGamepad != null &&
                            owner != null &&
                            event.type == KeyEventType.KeyDown &&
                            isGamepadClaimKey(event.key)
                        ) {
                            onClaimGamepad(owner)
                        }
                        // Screen handlers (L1/R1 tab cycle, X, etc.) before grace/repeat throttle.
                        if (onPreviewKey?.invoke(event) == true) return@onPreviewKeyEvent true
                        processor.shouldConsume(event.type, event.key)
                    },
        ) {
            content()
        }
    }
}

private fun isGamepadClaimKey(key: androidx.compose.ui.input.key.Key): Boolean =
    key == androidx.compose.ui.input.key.Key.DirectionUp ||
        key == androidx.compose.ui.input.key.Key.DirectionDown ||
        key == androidx.compose.ui.input.key.Key.DirectionLeft ||
        key == androidx.compose.ui.input.key.Key.DirectionRight ||
        key == androidx.compose.ui.input.key.Key.ButtonA ||
        key == androidx.compose.ui.input.key.Key.ButtonB ||
        key == androidx.compose.ui.input.key.Key.ButtonX ||
        key == androidx.compose.ui.input.key.Key.ButtonL1 ||
        key == androidx.compose.ui.input.key.Key.ButtonR1 ||
        // L2/R2 are global (owner toggle / notifications) — never claim via Compose.
        key == androidx.compose.ui.input.key.Key.DirectionCenter ||
        key == androidx.compose.ui.input.key.Key.Enter
