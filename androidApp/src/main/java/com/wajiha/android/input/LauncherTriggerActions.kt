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
    private val appActions: AppActions,
) {
    /** @return true when the press was handled. */
    fun onL2(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.SingleDisplay) return false
        if (dualState == DualScreenState.BlackoutSecondary) return false
        store.toggleGamepadOwner()
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            "map: L2($label) → gamepadOwner=${store.gamepadOwner.value}",
        )
        appActions.playSound(UiSound.Navigate)
        return true
    }

    /**
     * @return true when handled (including debounce absorb). Toggle itself lives in
     * [SystemNotificationStore] so digital + analog paths share one debounce clock.
     */
    fun onR2(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.GameRunning ||
            dualState == DualScreenState.BlackoutSecondary
        ) {
            return false
        }
        val applied = notifications.togglePanel()
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            if (applied) {
                "map: R2($label) → notifications panelOpen=${notifications.panelOpen.value}"
            } else {
                "map: R2($label) debounce absorbed panelOpen=${notifications.panelOpen.value}"
            },
        )
        if (applied) {
            appActions.playSound(UiSound.Navigate)
        }
        return true
    }

    /** Start → open Settings on the display that currently owns the menu. */
    fun onStart(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.GameRunning ||
            dualState == DualScreenState.BlackoutSecondary
        ) {
            return false
        }
        val applied = store.requestOpenSettings()
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            if (applied) {
                "map: Start($label) → open Settings"
            } else {
                "map: Start($label) open Settings dropped (buffer full)"
            },
        )
        // Sound plays in the menu-owner collector (same as the Settings chrome button).
        return true
    }
}
