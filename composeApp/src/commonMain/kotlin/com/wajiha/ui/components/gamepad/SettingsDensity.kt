package com.wajiha.ui.components.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Canonical min height for settings-family chrome: Back, folder tabs, setting
 * rows, and [GamepadButton]. Matches Settings (44.dp), not [com.wajiha.ui.theme.WajihaSpacing.touchMin].
 */
val SettingsCompactRowMinHeight = 44.dp

/**
 * Minimum height for settings-family rows/buttons/tabs.
 * Default is [SettingsCompactRowMinHeight] so System, Apps, and Settings match.
 */
val LocalSettingRowMinHeight = compositionLocalOf { SettingsCompactRowMinHeight }

/**
 * Optional density override. Default already matches Settings; keep only when
 * a screen needs a different row height.
 */
@Composable
fun ProvideSettingsDensity(
    rowMinHeight: Dp = SettingsCompactRowMinHeight,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSettingRowMinHeight provides rowMinHeight) {
        content()
    }
}
