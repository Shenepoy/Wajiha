package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.wajiha.ui.theme.WajihaFocus

@Composable
fun rememberPressInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Brief scale-down and tint while a control is pressed. */
fun Modifier.wajihaPressedFeedback(
    pressed: Boolean,
    shape: Shape? = null,
): Modifier =
    composed {
        val overlay =
            if (pressed) {
                Modifier
                    .graphicsLayer {
                        scaleX = 0.97f
                        scaleY = 0.97f
                    }.then(
                        if (shape != null) {
                            Modifier.background(WajihaFocus.pressedOverlay(), shape)
                        } else {
                            Modifier.background(WajihaFocus.pressedOverlay())
                        },
                    )
            } else {
                Modifier
            }
        then(overlay)
    }

@Composable
fun Modifier.wajihaPressedFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape? = null,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    return wajihaPressedFeedback(pressed, shape)
}
