package com.wajiha.ui.components.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Actions the currently focused setting can truthfully advertise. */
data class SettingHintCapabilities(
    val primaryAction: String? = null,
    val secondaryAction: String? = null,
    val canReset: Boolean = false,
    val canAdjust: Boolean = false,
)

val LocalSettingHintCapabilitiesReporter =
    staticCompositionLocalOf<(SettingHintCapabilities?) -> Unit> { {} }

@Composable
fun ProvideSettingHintCapabilities(
    reporter: (SettingHintCapabilities?) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSettingHintCapabilitiesReporter provides reporter,
        content = content,
    )
}
