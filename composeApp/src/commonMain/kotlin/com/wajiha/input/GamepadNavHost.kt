package com.wajiha.input

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.ui.theme.LocalInputModeController

/**
 * Screen-level gamepad navigation host.
 * Intercepts D-pad / A / B and disables reliance on Compose focus traversal.
 */
@Composable
fun GamepadNavHost(
    controller: GamepadNavController,
    modifier: Modifier = Modifier,
    coordinator: GamepadNavCoordinator? = null,
    zoneId: String = "default",
    zoneIndex: Int = 0,
    interceptKeys: Boolean = true,
    onGamepadKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    val inputModeController = LocalInputModeController.current
    val effectiveCoordinator = coordinator ?: LocalGamepadNavCoordinator.current

    controller.zoneId = zoneId

    DisposableEffect(controller, effectiveCoordinator, zoneIndex) {
        effectiveCoordinator?.registerZone(zoneIndex, controller)
        onDispose {
            effectiveCoordinator?.unregisterZone(zoneIndex)
        }
    }

    CompositionLocalProvider(LocalGamepadNavController provides controller) {
        Box(
            modifier = modifier
                .pointerInput(inputModeController) {
                    detectTapGestures(
                        onPress = {
                            inputModeController?.onTouch()
                            tryAwaitRelease()
                        }
                    )
                }
                .then(
                    if (interceptKeys) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (GamepadKeys.switchesToGamepadMode(event)) {
                                inputModeController?.onGamepadKey()
                            }
                            if (onGamepadKey?.invoke(event) == true) return@onPreviewKeyEvent true
                            val handled = effectiveCoordinator?.handleKeyEvent(event)
                                ?: controller.handleKeyEvent(event)
                            handled
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}
