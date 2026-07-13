package com.wajiha.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wajiha.input.GamepadHint
import com.wajiha.ui.components.gamepad.GamepadActionBar

@Composable
fun SecondaryPanelScaffold(
    modifier: Modifier = Modifier,
    showGamepadHints: Boolean = true,
    hints: List<GamepadHint> = emptyList(),
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WajihaPanel(modifier = Modifier.weight(1f)) {
            content()
        }
        if (showGamepadHints && hints.isNotEmpty()) {
            GamepadActionBar(hints = hints)
        }
    }
}
