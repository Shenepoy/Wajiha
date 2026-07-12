package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Boxy "- value +" stepper for gamepad settings rows.
 * Value changes via [onValueChange]; minus/plus segments are touch targets only
 * (row-level focus handles D-pad Left/Right).
 */
@Composable
fun GamepadNumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..100,
    step: Int = 1,
    valueLabel: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val canDecrease = enabled && value > range.first
    val canIncrease = enabled && value < range.last
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val segmentShape = WajihaShapes.chip

    fun adjust(delta: Int) {
        if (!enabled) return
        val next = (value + delta).coerceIn(range)
        if (next != value) onValueChange(next)
    }

    Row(
        modifier =
            modifier
                .clip(WajihaShapes.chip)
                .background(containerColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperSegment(
            text = "−",
            enabled = canDecrease,
            shape = segmentShape,
            modifier =
                Modifier.pointerInput(canDecrease, value, step) {
                    if (canDecrease) detectTapGestures { adjust(-step) }
                },
        )
        Box(
            modifier =
                Modifier
                    .widthIn(min = 44.dp)
                    .defaultMinSize(minHeight = WajihaSpacing.touchMin - WajihaSpacing.xs)
                    .padding(horizontal = WajihaSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        StepperSegment(
            text = "+",
            enabled = canIncrease,
            shape = segmentShape,
            modifier =
                Modifier.pointerInput(canIncrease, value, step) {
                    if (canIncrease) detectTapGestures { adjust(step) }
                },
        )
    }
}

@Composable
private fun StepperSegment(
    text: String,
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    val bg =
        if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            Color.Transparent
        }
    val fg =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        }
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 36.dp, minHeight = WajihaSpacing.touchMin - WajihaSpacing.xs)
                .clip(shape)
                .background(bg)
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs / 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}
