package com.wajiha.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import org.koin.compose.koinInject

/**
 * When this display gains gamepad ownership (L2 toggle / route change),
 * run [onGained] so content focus lands on the page (not chrome).
 *
 * Only reacts to [DualScreenStore.gamepadFocusEpoch] bumps — not to sticky
 * claims that leave the owner unchanged (those must not yank D-pad focus).
 */
@Composable
fun RememberGamepadOwnerFocus(
    owner: GamepadOwner?,
    onGained: suspend () -> Unit,
) {
    if (owner == null) return
    val dualStore = koinInject<DualScreenStore>()
    val currentOwner by dualStore.gamepadOwner.collectAsState()
    val epoch by dualStore.gamepadFocusEpoch.collectAsState()
    LaunchedEffect(owner, currentOwner, epoch) {
        if (currentOwner != owner) return@LaunchedEffect
        // Skip the initial epoch=0 composition unless we already own keys —
        // still restore once so first paint lands on content.
        // Wait for the owner composition to attach; logical focus is restored
        // immediately and Compose focus follows on the next frame.
        withFrameNanos { }
        onGained()
    }
}

/** Request [FocusRequester] safely after ownership changes. */
suspend fun FocusRequester.requestContentFocus() {
    try {
        requestFocus()
    } catch (_: Exception) {
    }
}
