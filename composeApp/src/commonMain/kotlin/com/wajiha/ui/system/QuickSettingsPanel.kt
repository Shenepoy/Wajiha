package com.wajiha.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.platform.SystemControls
import com.wajiha.platform.SystemStatus
import com.wajiha.state.DualScreenStore
import com.wajiha.state.SettingsHeroKind
import com.wajiha.state.SettingsHeroOption
import com.wajiha.ui.components.gamepad.MultiChoiceOption
import com.wajiha.ui.components.gamepad.WajihaActionSetting
import com.wajiha.ui.components.gamepad.WajihaMultiChoiceSetting
import com.wajiha.ui.components.gamepad.WajihaSettingBlurb
import com.wajiha.ui.components.gamepad.WajihaSettingDivider
import com.wajiha.ui.components.gamepad.WajihaSliderSetting
import com.wajiha.ui.components.gamepad.WajihaToggleSetting
import com.wajiha.ui.settings.clearSettingsHero
import com.wajiha.ui.settings.genericSettingHeroDetail
import com.wajiha.ui.settings.publishSettingsHero
import com.wajiha.ui.settings.settingsToggleHeroFocus
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

private val ScreenTimeoutOptions =
    listOf(
        MultiChoiceOption("30", "30 seconds", description = "Dim quickly when idle."),
        MultiChoiceOption("60", "1 minute"),
        MultiChoiceOption("300", "5 minutes"),
        MultiChoiceOption("1800", "30 minutes", description = "Long idle before sleep."),
    )

/**
 * Quick Settings section content for folder chrome (primary System + secondary
 * System tab): brightness, volume, Wi‑Fi / Bluetooth, torch, screen timeout.
 *
 * Parent owns [com.wajiha.ui.components.gamepad.WajihaSettingPanel].
 */
@Composable
fun QuickSettingsPanel(initialFocusRequester: FocusRequester? = null) {
    val controls = koinInject<SystemControls>()
    val dualStore = koinInject<DualScreenStore>()
    val status by controls.status.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            controls.refreshStatus()
            delay(5000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { clearSettingsHero(dualStore) }
    }

    val batteryBlurb =
        remember(status.batteryPercent, status.charging) {
            when {
                status.batteryPercent < 0 -> {
                    "Device controls for this display."
                }

                status.charging -> {
                    "Battery ${status.batteryPercent}% · charging"
                }

                else -> {
                    "Battery ${status.batteryPercent}%"
                }
            }
        }

    val timeoutSelected =
        remember(status.screenTimeoutSec) {
            listOf(30, 60, 300, 1800)
                .minByOrNull { abs(it - status.screenTimeoutSec) }
                ?.toString()
                ?: "60"
        }

    QuickSettingsSectionContent(
        controls = controls,
        dualStore = dualStore,
        status = status,
        batteryBlurb = batteryBlurb,
        timeoutSelected = timeoutSelected,
        initialFocusRequester = initialFocusRequester,
    )
}

@Composable
private fun QuickSettingsSectionContent(
    controls: SystemControls,
    dualStore: DualScreenStore,
    status: SystemStatus,
    batteryBlurb: String,
    timeoutSelected: String,
    initialFocusRequester: FocusRequester?,
) {
    WajihaSettingBlurb(batteryBlurb)

    WajihaSliderSetting(
        label = "Brightness",
        description = "Display backlight level.",
        value = status.brightness.coerceIn(0f, 1f),
        onValueChange = { controls.setBrightness(it) },
        valueLabel = "${(status.brightness.coerceIn(0f, 1f) * 100f).roundToInt()}%",
        focusId = "qs_brightness",
        focusRequester = initialFocusRequester,
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualStore,
                    genericSettingHeroDetail(
                        title = "Brightness",
                        subtitle = "Display backlight level.",
                        valueText =
                            "${(status.brightness.coerceIn(0f, 1f) * 100f).roundToInt()}%",
                        kind = SettingsHeroKind.Generic,
                    ),
                )
            } else {
                clearSettingsHero(dualStore)
            }
        },
    )

    WajihaSettingDivider()

    WajihaSliderSetting(
        label = "Volume",
        description = "Media playback volume.",
        value = status.volume.coerceIn(0f, 1f),
        onValueChange = controls::setVolume,
        valueLabel = "${(status.volume.coerceIn(0f, 1f) * 100f).roundToInt()}%",
        focusId = "qs_volume",
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualStore,
                    genericSettingHeroDetail(
                        title = "Volume",
                        subtitle = "Media playback volume.",
                        valueText =
                            "${(status.volume.coerceIn(0f, 1f) * 100f).roundToInt()}%",
                        kind = SettingsHeroKind.Generic,
                    ),
                )
            } else {
                clearSettingsHero(dualStore)
            }
        },
    )

    WajihaSettingDivider()

    WajihaActionSetting(
        label = "Wi‑Fi",
        description = "Opens Android Wi‑Fi settings.",
        labelMeta = if (status.wifiEnabled) "On" else "Off",
        actionLabel = "Open",
        onClick = controls::openWifiSettings,
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualStore,
                    genericSettingHeroDetail(
                        title = "Wi‑Fi",
                        subtitle = "Opens Android Wi‑Fi settings.",
                        valueText = if (status.wifiEnabled) "On" else "Off",
                        kind = SettingsHeroKind.Generic,
                    ),
                    onPrimaryAction = controls::openWifiSettings,
                )
            } else {
                clearSettingsHero(dualStore)
            }
        },
    )

    WajihaActionSetting(
        label = "Bluetooth",
        description = "Opens Android Bluetooth settings.",
        labelMeta = if (status.bluetoothEnabled) "On" else "Off",
        actionLabel = "Open",
        onClick = controls::openBluetoothSettings,
        onFocusedChanged = { focused ->
            if (focused) {
                publishSettingsHero(
                    dualStore,
                    genericSettingHeroDetail(
                        title = "Bluetooth",
                        subtitle = "Opens Android Bluetooth settings.",
                        valueText = if (status.bluetoothEnabled) "On" else "Off",
                        kind = SettingsHeroKind.Generic,
                    ),
                    onPrimaryAction = controls::openBluetoothSettings,
                )
            } else {
                clearSettingsHero(dualStore)
            }
        },
    )

    WajihaToggleSetting(
        label = "Torch",
        description = "Camera flash / flashlight.",
        checked = status.torchOn,
        onCheckedChange = { wantOn ->
            if (wantOn != status.torchOn) {
                controls.toggleTorch()
            }
        },
        defaultChecked = false,
        onReset = {
            if (status.torchOn) {
                controls.toggleTorch()
            }
        },
        onFocusedChanged =
            settingsToggleHeroFocus(
                store = dualStore,
                title = "Torch",
                subtitle = "Camera flash / flashlight.",
                checked = status.torchOn,
            ),
    )

    WajihaSettingDivider()

    WajihaMultiChoiceSetting(
        label = "Screen timeout",
        description = "Idle time before the display sleeps.",
        choiceOptions = ScreenTimeoutOptions,
        selected = timeoutSelected,
        onSelect = { value ->
            value.toIntOrNull()?.let { controls.setScreenTimeout(it) }
        },
        defaultValue = "60",
        onReset = { controls.setScreenTimeout(60) },
        onFocusedChanged = { focused ->
            if (focused) {
                val label =
                    ScreenTimeoutOptions
                        .firstOrNull { it.value == timeoutSelected }
                        ?.label
                        ?: timeoutSelected
                publishSettingsHero(
                    dualStore,
                    genericSettingHeroDetail(
                        title = "Screen timeout",
                        subtitle = "Idle time before the display sleeps.",
                        valueText = label,
                        options =
                            ScreenTimeoutOptions.map {
                                SettingsHeroOption(
                                    value = it.value,
                                    label = it.label,
                                    selected = it.value == timeoutSelected,
                                )
                            },
                        kind = SettingsHeroKind.Generic,
                    ),
                    onSelectOption = { value ->
                        value.toIntOrNull()?.let { controls.setScreenTimeout(it) }
                    },
                )
            } else {
                clearSettingsHero(dualStore)
            }
        },
    )
}
