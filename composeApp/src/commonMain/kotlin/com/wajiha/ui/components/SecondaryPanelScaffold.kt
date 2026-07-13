package com.wajiha.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wajiha.input.GamepadHint
import com.wajiha.input.MirroredOrLocalGamepadActionBar
import com.wajiha.state.GamepadOwner

@Composable
fun SecondaryPanelScaffold(
    modifier: Modifier = Modifier,
    showGamepadHints: Boolean = true,
    hints: List<GamepadHint> = emptyList(),
    gamepadOwner: GamepadOwner? = GamepadOwner.Secondary,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WajihaPanel(modifier = Modifier.weight(1f)) {
            content()
        }
        if (showGamepadHints && hints.isNotEmpty()) {
            MirroredOrLocalGamepadActionBar(
                publisherId = "secondary_panel",
                hints = hints,
                hostOwner = gamepadOwner,
            )
        }
    }
}
