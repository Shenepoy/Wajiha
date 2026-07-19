package com.wajiha.input

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadOverlayLayer(
    layerId: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Boolean)? = null,
    onToggleKey: ((KeyEvent) -> Boolean)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusContinuity = LocalFocusContinuityController.current

    DisposableEffect(layerId, focusContinuity) {
        GamepadLayers.stack.push(layerId)
        focusContinuity?.pushLayer(layerId)
        onDispose {
            GamepadLayers.stack.pop(layerId)
            focusContinuity?.popLayer(layerId)
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier =
            Modifier
                .dismissKeyboardOnOutsideTap()
                .onPreviewKeyEvent { event ->
                    when {
                        onToggleKey?.invoke(event) == true -> {
                            true
                        }

                        GamepadKeys.isBack(event.type, event.key) -> {
                            if (dismissTextEdit(focusManager, keyboard)) {
                                true
                            } else {
                                onDismiss()
                                true
                            }
                        }

                        onConfirm != null && GamepadKeys.isConfirm(event.type, event.key) -> {
                            onConfirm()
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        CompositionLocalProvider(LocalFocusLayerId provides layerId) {
            content()
        }
    }
}
