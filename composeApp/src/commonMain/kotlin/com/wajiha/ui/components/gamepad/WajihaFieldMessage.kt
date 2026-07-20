package com.wajiha.ui.components.gamepad

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class WajihaFieldMessageSeverity {
    Supporting,
    Info,
    Warning,
    Error,
}

data class WajihaFieldMessageState(
    val text: String,
    val severity: WajihaFieldMessageSeverity = WajihaFieldMessageSeverity.Supporting,
) {
    val isError: Boolean
        get() = severity == WajihaFieldMessageSeverity.Error
}

@Composable
fun WajihaFieldMessage(
    message: WajihaFieldMessageState,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message.text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = message.severity.color(),
    )
}

@Composable
private fun WajihaFieldMessageSeverity.color(): Color =
    when (this) {
        WajihaFieldMessageSeverity.Supporting -> MaterialTheme.colorScheme.onSurfaceVariant
        WajihaFieldMessageSeverity.Info -> MaterialTheme.colorScheme.primary
        WajihaFieldMessageSeverity.Warning -> MaterialTheme.colorScheme.tertiary
        WajihaFieldMessageSeverity.Error -> MaterialTheme.colorScheme.error
    }
