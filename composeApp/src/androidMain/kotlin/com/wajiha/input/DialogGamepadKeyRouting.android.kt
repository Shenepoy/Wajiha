package com.wajiha.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.wajiha.state.GamepadOwner
import org.koin.compose.koinInject

@Composable
actual fun RememberDialogGamepadKeyRouting(hostOwner: GamepadOwner) {
    val installer = koinInject<DialogGamepadKeyInstaller>()
    val view = LocalView.current
    DisposableEffect(hostOwner, view) {
        var uninstall: (() -> Unit)? = null
        val install =
            Runnable {
                uninstall = installer.install(view, hostOwner)
            }
        // DialogWindowProvider is attached after the first layout pass.
        if (view.isAttachedToWindow) {
            install.run()
        } else {
            view.post(install)
        }
        onDispose {
            view.removeCallbacks(install)
            uninstall?.invoke()
        }
    }
}
