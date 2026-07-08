package com.wajiha.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wajiha.platform.SystemControls
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Quick Settings — a secondary-screen mode and swipe-up panel: brightness,
 * volume, battery, wifi/bt shortcuts, screen timeout, torch.
 */
@Composable
fun QuickSettingsPanel(modifier: Modifier = Modifier) {
    val controls = koinInject<SystemControls>()
    val status by controls.status.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            controls.refreshStatus()
            delay(5000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Settings", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (status.batteryPercent >= 0) {
                    "${status.batteryPercent}%" + if (status.charging) " ⚡" else ""
                } else {
                    ""
                },
                style = MaterialTheme.typography.titleSmall
            )
        }

        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = status.brightness.coerceIn(0f, 1f),
            onValueChange = { controls.setBrightness(it) },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Volume", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = status.volume.coerceIn(0f, 1f),
            onValueChange = { controls.setVolume(it) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = controls::openWifiSettings) {
                Text("Wi-Fi" + if (status.wifiEnabled) " (on)" else " (off)")
            }
            TextButton(onClick = controls::openBluetoothSettings) {
                Text("Bluetooth" + if (status.bluetoothEnabled) " (on)" else " (off)")
            }
            TextButton(onClick = controls::toggleTorch) {
                Text("Torch" + if (status.torchOn) " (on)" else "")
            }
        }

        Text("Screen timeout", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(30 to "30s", 60 to "1m", 300 to "5m", 1800 to "30m").forEach { (sec, label) ->
                TextButton(onClick = { controls.setScreenTimeout(sec) }) {
                    Text(
                        text = label,
                        color = if (status.screenTimeoutSec == sec) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
