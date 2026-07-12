package com.wajiha.android.input

import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.platform.AppActions
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.SystemNotificationStore

/** Shared L2/R2 side effects for digital keys and analog axes. */
class LauncherTriggerActions(
    private val store: DualScreenStore,
    private val notifications: SystemNotificationStore,
    private val appActions: AppActions
) {
    /** @return true when the press was handled. */
    fun onL2(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.SingleDisplay) return false
        if (dualState == DualScreenState.BlackoutSecondary) return false
        store.toggleGamepadOwner()
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            "map: L2($label) → gamepadOwner=${store.gamepadOwner.value}"
        )
        appActions.playSound(UiSound.Navigate)
        return true
    }

    /** @return true when the press was handled. */
    fun onR2(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.GameRunning ||
            dualState == DualScreenState.BlackoutSecondary
        ) {
            return false
        }
        notifications.togglePanel()
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            "map: R2($label) → notifications panelOpen=${notifications.panelOpen.value}"
        )
        appActions.playSound(UiSound.Navigate)
        return true
    }
}
