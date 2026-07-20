package com.wajiha.ui.components.gamepad

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun GamepadSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    /** Discrete Material slider notches; 0 = continuous track. */
    steps: Int = 0,
    valueLabel: String? = null,
    description: String? = null,
    focusId: Any? = null,
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // When steps is 0 the track is continuous; still use ~5% gamepad nudges.
    val nudgeCount = if (steps > 0) steps + 1 else 20
    val step = (valueRange.endInclusive - valueRange.start) / nudgeCount.toFloat()
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val resolvedFocusId = focusId ?: label
    val anchor =
        remember(continuity, layerId, resolvedFocusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(resolvedFocusId, layerId)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .wajihaFocusIndicator(
                    highlighted = focused,
                    focusAnchor = anchor,
                ).clip(WajihaShapes.focus)
                .onFocusChanged {
                    val nowFocused = it.isFocused
                    focused = nowFocused
                    onFocusedChanged?.invoke(nowFocused)
                    if (nowFocused && anchor != null) {
                        continuity?.claim(anchor, FocusClaimSource.Compose)
                    }
                }.focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onValueChange((value - step).coerceIn(valueRange))
                            true
                        }

                        Key.DirectionRight -> {
                            onValueChange((value + step).coerceIn(valueRange))
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.padding(vertical = WajihaSpacing.xs, horizontal = WajihaSpacing.xs),
    ) {
        Text(
            text = if (valueLabel != null) "$label · $valueLabel" else label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
