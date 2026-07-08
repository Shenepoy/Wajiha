package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.wajiha.ui.theme.showGamepadChrome
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun GamepadSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    val step = (valueRange.endInclusive - valueRange.start) / (steps + 1).coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(WajihaShapes.focus)
            .then(
                if (showGamepadChrome(focused)) {
                    Modifier.border(
                        width = WajihaFocus.borderWidth,
                        color = WajihaFocus.borderColor(),
                        shape = WajihaShapes.focus
                    )
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.ButtonL1 -> {
                        onValueChange((value - step).coerceIn(valueRange))
                        true
                    }
                    Key.DirectionRight, Key.ButtonR1 -> {
                        onValueChange((value + step).coerceIn(valueRange))
                        true
                    }
                    else -> false
                }
            }
            .padding(WajihaSpacing.sm)
    ) {
        Text(
            text = if (valueLabel != null) "$label: $valueLabel" else label,
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
