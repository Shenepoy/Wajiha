package com.wajiha.android.input

import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.platform.AppActions
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.MenuDestination
import com.wajiha.state.SystemNotificationStore

/** Shared L2/R2 side effects for digital keys and analog axes. */
class LauncherTriggerActions(
    private val store: DualScreenStore,
    private val notifications: SystemNotificationStore,
    private val appActions: AppActions,
) {
    /**
     * Cross-display focus switch. Only acts when the other screen has real
     * interactive UI ([DualScreenStore.isL2SwitchAvailable]); otherwise the
     * press is eaten quietly so idle hero artwork does not beep / steal focus.
     *
     * @return true when the press was handled (including no-op absorb).
     */
    fun onL2(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.SingleDisplay) return false
        if (dualState == DualScreenState.BlackoutSecondary) return false
        if (!store.isL2SwitchAvailable()) {
            WajihaLog.i(
                WajihaTags.GAMEPAD,
                "map: L2($label) ignored (no interactive cross-display target)",
            )
            return true
        }
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

    /**
     * L3 (left stick click) → Apps on the display that currently owns the menu.
     */
    fun onL3(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.GameRunning ||
            dualState == DualScreenState.BlackoutSecondary
        ) {
            return false
        }
        val applied = store.requestOpenApps()
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            if (applied) {
                "map: L3($label) → open Apps"
            } else {
                "map: L3($label) dropped (buffer full)"
            },
        )
        return true
    }

    /**
     * Start → Apps options while the menu is on Apps; otherwise Settings on the
     * display that currently owns the menu (main grid).
     */
    fun onStart(label: String): Boolean {
        val dualState = store.state.value
        if (dualState == DualScreenState.GameRunning ||
            dualState == DualScreenState.BlackoutSecondary
        ) {
            return false
        }
        val onApps = store.menuRoute.value.destination == MenuDestination.Apps
        val applied = store.requestStartAction()
        WajihaLog.i(
            WajihaTags.GAMEPAD,
            if (applied) {
                if (onApps) {
                    "map: Start($label) → open Apps options"
                } else {
                    "map: Start($label) → open Settings"
                }
            } else {
                "map: Start($label) dropped (buffer full)"
            },
        )
        // Sound plays in the menu-owner / Apps collector.
        return true
    }
}
