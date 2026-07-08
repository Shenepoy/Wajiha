package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wajiha.ui.theme.WajihaSpacing

fun defaultGamepadHints(): List<Pair<String, String>> = listOf(
    "A" to "Confirm",
    "B" to "Back",
    "L1/R1" to "Section",
    "SELECT" to "Swap screens"
)

val quickSettingsGamepadHints: List<Pair<String, String>> = listOf(
    "A" to "Toggle",
    "←→" to "Adjust slider"
)

val runningAppsGamepadHints: List<Pair<String, String>> = listOf(
    "A" to "Action",
    "B" to "Back to grid"
)

val achievementsGamepadHints: List<Pair<String, String>> = listOf(
    "B" to "Back"
)

val settingsGamepadHints: List<Pair<String, String>> = listOf(
    "A" to "Select/Toggle",
    "B" to "Back",
    "Y" to "Reset",
    "L1/R1" to "Section"
)

val gameDetailGamepadHints: List<Pair<String, String>> = listOf(
    "A" to "Select/Launch",
    "B" to "Back",
    "Y" to "Reset",
    "L1/R1" to "Tab"
)

val secondaryModeTabGamepadHints: List<Pair<String, String>> = listOf(
    "A" to "Select tab",
    "B" to "Games",
    "L1/R1" to "Tab"
)

@Composable
fun GamepadActionBar(
    modifier: Modifier = Modifier,
    hints: List<Pair<String, String>> = defaultGamepadHints()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WajihaSpacing.md, vertical = WajihaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.lg)
    ) {
        hints.forEach { (button, action) ->
            Text(
                text = "$button  $action",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
