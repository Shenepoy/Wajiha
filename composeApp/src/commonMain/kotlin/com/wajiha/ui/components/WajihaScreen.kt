package com.wajiha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.key.KeyEvent
import com.wajiha.input.GamepadScreen
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.gamepad.GamepadActionBar
import com.wajiha.ui.components.gamepad.defaultGamepadHints
import com.wajiha.ui.theme.WajihaSpacing

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WajihaScreen(
    layerId: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    showActionBar: Boolean = false,
    gamepadHints: List<Pair<String, String>>? = null,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    onPreviewKey: ((KeyEvent) -> Boolean)? = null,
    onOwnerGainedFocus: (suspend () -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable () -> Unit
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    GamepadScreen(
        layerId = layerId,
        modifier = modifier.fillMaxSize(),
        owner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onPreviewKey = onPreviewKey,
        onOwnerGainedFocus = onOwnerGainedFocus
    ) {
        WajihaSnackbarScaffold(
            snackbarHostState = snackbarHostState ?: rememberWajihaSnackbarHostState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (title != null) {
                    androidx.compose.material3.Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(WajihaSpacing.md)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    content()
                }
                if (showActionBar) {
                    GamepadActionBar(hints = gamepadHints ?: defaultGamepadHints())
                }
            }
        }
    }
}
