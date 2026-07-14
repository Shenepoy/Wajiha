package com.wajiha.ui.components.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Minimum height for settings-family rows/buttons/tabs.
 * Default matches [WajihaSpacing.touchMin]; settings scaffolds provide 44.dp.
 */
val LocalSettingRowMinHeight = compositionLocalOf { WajihaSpacing.touchMin }

val SettingsCompactRowMinHeight = 44.dp

@Composable
fun ProvideSettingsDensity(
    rowMinHeight: Dp = SettingsCompactRowMinHeight,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSettingRowMinHeight provides rowMinHeight) {
        content()
    }
}
