package com.wajiha.input

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier

/**
 * A navigable region within a multi-zone screen (e.g. folder tabs vs form body).
 * Registers with the coordinator but does not intercept keys — root host handles those.
 */
@Composable
fun GamepadNavZone(
    mode: GamepadNavMode,
    gridRows: Int = 2,
    zoneId: String,
    zoneIndex: Int,
    coordinator: GamepadNavCoordinator,
    onBack: (() -> Boolean)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val controller = rememberGamepadNavController(mode, gridRows, onBack)
    DisposableEffect(controller, coordinator, zoneIndex) {
        coordinator.registerZone(zoneIndex, controller)
        onDispose {
            coordinator.unregisterZone(zoneIndex)
        }
    }
    CompositionLocalProvider(LocalGamepadNavController provides controller) {
        Box(modifier = modifier) {
            content()
        }
    }
}
