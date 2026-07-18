package com.wajiha.input

import androidx.compose.runtime.Composable
import com.wajiha.state.GamepadOwner

/**
 * Compose [androidx.compose.ui.window.Dialog] uses its own Window, so keys never
 * reach Activity.dispatchKeyEvent — and dual-display [GamepadKeyRouter] cannot
 * forward them. Call this inside Dialog content when the host display is known.
 */
@Composable
expect fun RememberDialogGamepadKeyRouting(hostOwner: GamepadOwner)

/**
 * Android host installs a Window.Callback that routes dialog keys through the
 * same-process gamepad router. [androidView] is LocalView.current from Dialog.
 */
interface DialogGamepadKeyInstaller {
    fun install(
        androidView: Any,
        hostOwner: GamepadOwner,
    ): () -> Unit
}

object NoOpDialogGamepadKeyInstaller : DialogGamepadKeyInstaller {
    override fun install(
        androidView: Any,
        hostOwner: GamepadOwner,
    ): () -> Unit = {}
}
