package com.wajiha.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wajiha.android.monitor.ForegroundAppMonitor
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Debug-build test hooks for agents (`adb shell am broadcast -a com.wajiha.DEBUG_*`).
 * Compiled only in debug variants — not present in release APKs.
 */
class DebugBroadcastReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val monitor: ForegroundAppMonitor by inject()
    private val dualStore: DualScreenStore by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_DUMP_SESSIONS -> {
                monitor.debugDumpState()
            }

            ACTION_REFRESH_SESSIONS -> {
                monitor.debugForceRefresh()
            }

            ACTION_CLEAR_SUPPRESS -> {
                monitor.debugClearSuppressList()
            }

            ACTION_TOGGLE_GAMEPAD_OWNER -> {
                dualStore.toggleGamepadOwner()
                WajihaLog.i(
                    WajihaTags.DEBUG,
                    "toggleGamepadOwner → owner=${dualStore.gamepadOwner.value} " +
                        "state=${dualStore.state.value} epoch=${dualStore.gamepadFocusEpoch.value}",
                )
            }

            ACTION_DUMP_GAMEPAD -> {
                WajihaLog.i(
                    WajihaTags.DEBUG,
                    "gamepad: owner=${dualStore.gamepadOwner.value} " +
                        "state=${dualStore.state.value} epoch=${dualStore.gamepadFocusEpoch.value} " +
                        "menu=${dualStore.menuGamepadOwner()} hero=${dualStore.heroGamepadOwner()}",
                )
            }

            ACTION_SIMULATE_FOREGROUND -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE)
                if (pkg.isNullOrBlank()) {
                    WajihaLog.w(
                        WajihaTags.DEBUG,
                        "simulateForeground: missing --es package <pkg>",
                    )
                } else {
                    WajihaLog.i(WajihaTags.DEBUG, "simulateForeground: $pkg")
                    monitor.onForegroundPackage(pkg)
                }
            }

            else -> {
                WajihaLog.w(WajihaTags.DEBUG, "unknown action: ${intent.action}")
            }
        }
    }

    companion object {
        const val ACTION_DUMP_SESSIONS = "com.wajiha.DEBUG_DUMP_SESSIONS"
        const val ACTION_REFRESH_SESSIONS = "com.wajiha.DEBUG_REFRESH_SESSIONS"
        const val ACTION_CLEAR_SUPPRESS = "com.wajiha.DEBUG_CLEAR_SUPPRESS"
        const val ACTION_SIMULATE_FOREGROUND = "com.wajiha.DEBUG_SIMULATE_FOREGROUND"
        const val ACTION_TOGGLE_GAMEPAD_OWNER = "com.wajiha.DEBUG_TOGGLE_GAMEPAD_OWNER"
        const val ACTION_DUMP_GAMEPAD = "com.wajiha.DEBUG_DUMP_GAMEPAD"
        const val EXTRA_PACKAGE = "package"
    }
}
