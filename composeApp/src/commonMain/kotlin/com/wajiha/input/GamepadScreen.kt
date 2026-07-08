package com.wajiha.input

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.theme.LocalInputMode
import com.wajiha.ui.theme.LocalInputModeController
import com.wajiha.ui.theme.rememberInputModeController

/**
 * Registers a gamepad layer and optional screen-level key handler.
 * Touch down claims gamepad for [owner] when [onClaimGamepad] is provided.
 * Tracks touch vs gamepad input mode for chrome visibility app-wide.
 */
@Composable
fun GamepadScreen(
    layerId: String,
    modifier: Modifier = Modifier,
    owner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    onPreviewKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    val processor = remember { GamepadInputProcessor() }
    val inputModeController = rememberInputModeController()

    DisposableEffect(layerId) {
        GamepadLayers.stack.push(layerId)
        processor.onLayerPushed()
        onDispose {
            GamepadLayers.stack.pop(layerId)
            processor.reset()
        }
    }

    CompositionLocalProvider(
        LocalInputModeController provides inputModeController,
        LocalInputMode provides inputModeController.mode
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .pointerInput(inputModeController) {
                    detectTapGestures(
                        onPress = {
                            inputModeController.onTouch()
                            tryAwaitRelease()
                        }
                    )
                }
                .onPreviewKeyEvent { event ->
                    if (GamepadKeys.switchesToGamepadMode(event)) {
                        inputModeController.onGamepadKey()
                    }
                    if (
                        onClaimGamepad != null &&
                        owner != null &&
                        event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        isGamepadClaimKey(event.key)
                    ) {
                        onClaimGamepad(owner)
                    }
                    // Screen handlers (L1/R1 tab cycle, X, etc.) before grace/repeat throttle.
                    if (onPreviewKey?.invoke(event) == true) return@onPreviewKeyEvent true
                    processor.shouldConsume(event.type, event.key)
                }
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
        key == androidx.compose.ui.input.key.Key.DirectionCenter ||
        key == androidx.compose.ui.input.key.Key.Enter
