package com.wajiha.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.wajiha.platform.SystemControls
import com.wajiha.ui.components.SecondaryPanelScaffold
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadForm
import com.wajiha.ui.components.gamepad.GamepadSlider
import com.wajiha.ui.components.gamepad.quickSettingsGamepadHints
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Quick Settings — a secondary-screen mode and swipe-up panel: brightness,
 * volume, battery, wifi/bt shortcuts, screen timeout, torch.
 */
@Composable
fun QuickSettingsPanel(
    modifier: Modifier = Modifier,
    showGamepadHints: Boolean = true
) {
    val controls = koinInject<SystemControls>()
    val status by controls.status.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            controls.refreshStatus()
            delay(5000)
        }
    }

    SecondaryPanelScaffold(
        modifier = modifier,
        showGamepadHints = showGamepadHints,
        hints = quickSettingsGamepadHints
    ) {
        GamepadForm(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    "Quick Settings",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                androidx.compose.material3.Text(
                    text = if (status.batteryPercent >= 0) {
                        "${status.batteryPercent}%" + if (status.charging) " ⚡" else ""
                    } else {
                        ""
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                )
            }

            GamepadSlider(
                label = "Brightness",
                value = status.brightness.coerceIn(0f, 1f),
                onValueChange = controls::setBrightness
            )

            GamepadSlider(
                label = "Volume",
                value = status.volume.coerceIn(0f, 1f),
                onValueChange = controls::setVolume
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
            ) {
                GamepadButton(
                    text = "Wi-Fi" + if (status.wifiEnabled) " (on)" else " (off)",
                    onClick = controls::openWifiSettings,
                    outlined = true
                )
                GamepadButton(
                    text = "Bluetooth" + if (status.bluetoothEnabled) " (on)" else " (off)",
                    onClick = controls::openBluetoothSettings,
                    outlined = true
                )
                GamepadButton(
                    text = "Torch" + if (status.torchOn) " (on)" else "",
                    onClick = controls::toggleTorch,
                    outlined = true
                )
            }

            androidx.compose.material3.Text(
                "Screen timeout",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
                listOf(30 to "30s", 60 to "1m", 300 to "5m", 1800 to "30m").forEach { (sec, label) ->
                    GamepadButton(
                        text = label,
                        onClick = { controls.setScreenTimeout(sec) },
                        outlined = status.screenTimeoutSec != sec
                    )
                }
            }
        }
    }
}
