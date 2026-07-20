package com.wajiha.ui.components.gamepad

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) 0.97f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            label = "pressed_scale",
        )
        then(
            Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.then(
                    if (pressed) {
                        if (shape != null) {
                            Modifier.background(WajihaFocus.pressedOverlay(), shape)
                        } else {
                            Modifier.background(WajihaFocus.pressedOverlay())
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    }

@Composable
fun Modifier.wajihaPressedFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape? = null,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    return wajihaPressedFeedback(pressed, shape)
}
