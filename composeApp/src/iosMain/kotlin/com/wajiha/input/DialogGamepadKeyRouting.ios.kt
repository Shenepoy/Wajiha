package com.wajiha.input

import androidx.compose.runtime.Composable
import com.wajiha.state.GamepadOwner

@Composable
actual fun RememberDialogGamepadKeyRouting(hostOwner: GamepadOwner) {
    // No dual-display gamepad routing on iOS.
}
