package com.wajiha.ui.components.gamepad

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import com.wajiha.input.FocusAnchor
import com.wajiha.input.FocusContinuityController
import com.wajiha.ui.theme.FocusBorderStyle

/**
 * Focus chrome drawn at screen root so Outside strokes are not clipped by LazyGrid cells,
 * [Modifier.clip], or sibling draws.
 */
@Stable
class FocusRingOverlayState {
    constructor(controller: FocusContinuityController? = null) {
        this.controller = controller
    }

    private var controller: FocusContinuityController? = null
    private val entries = mutableStateMapOf<FocusAnchor, FocusRingOverlayEntry>()

    private var retainedEntry by mutableStateOf<FocusRingOverlayEntry?>(null)

    val entry: FocusRingOverlayEntry?
        get() = controller?.activeAnchor?.let { entries[it] } ?: retainedEntry

    fun publish(entry: FocusRingOverlayEntry) {
        val anchor = entry.anchor
        if (anchor != null) {
            entries[anchor] = entry
        }
        val accepted =
            anchor == null ||
                controller?.let { continuity ->
                    continuity.offer(anchor) && continuity.isActive(anchor)
                } != false
        if (accepted) {
            retainedEntry = entry
        }
    }

    fun clear(token: Any) {
        entries.entries.removeAll { (_, entry) -> entry.token === token }
        // Keep the last valid geometry until its successor publishes. Clearing
        // here caused one-frame ring loss during lazy-item and modal handoffs.
    }

    fun register(anchor: FocusAnchor) {
        controller?.register(anchor)
    }

    fun unregister(anchor: FocusAnchor) {
        controller?.unregister(anchor)
    }
}

data class FocusRingOverlayEntry(
    val token: Any,
    val anchor: FocusAnchor? = null,
    val boundsInRoot: Rect,
    val color: Color,
    val thickness: Dp,
    val borderStyle: FocusBorderStyle,
    val shape: Shape,
)

val LocalFocusRingOverlay = compositionLocalOf<FocusRingOverlayState?> { null }

@Composable
fun rememberFocusRingOverlayState(controller: FocusContinuityController? = null): FocusRingOverlayState =
    remember(controller) { FocusRingOverlayState(controller) }

/**
 * Full-screen host: content first, then Outside focus chrome drawn after children so the ring
 * sits above everything without becoming a hit target.
 */
@Composable
fun FocusRingOverlayHost(
    state: FocusRingOverlayState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val entry = state.entry
    val borderStyle = entry?.borderStyle

    val pulseAlpha = animatedPulseAlpha(enabled = borderStyle == FocusBorderStyle.Pulsing)
    val marchPhase =
        animatedMarchPhase(
            enabled = borderStyle == FocusBorderStyle.MarchingAnts,
        )
    val gradientPhase =
        animatedGradientPhase(
            enabled = borderStyle == FocusBorderStyle.GradientPulse,
        )

    Box(
        modifier =
            modifier
                .onGloballyPositioned { hostCoordinates = it }
                .drawWithContent {
                    drawContent()
                    val current = state.entry ?: return@drawWithContent
                    val host = hostCoordinates?.takeIf { it.isAttached } ?: return@drawWithContent
                    val hostBounds = host.boundsInRoot()
                    val item = current.boundsInRoot
                    if (item.width <= 0f || item.height <= 0f) return@drawWithContent
                    drawFocusChrome(
                        itemTopLeft =
                            Offset(
                                item.left - hostBounds.left,
                                item.top - hostBounds.top,
                            ),
                        itemSize = Size(item.width, item.height),
                        color = current.color,
                        thickness = current.thickness,
                        borderStyle = current.borderStyle,
                        shape = current.shape,
                        placementOutside = true,
                        pulseAlpha = pulseAlpha,
                        marchPhase = marchPhase,
                        gradientPhase = gradientPhase,
                    )
                },
    ) {
        content()
    }
}

@Composable
fun FocusRingOverlayRegistrationEffect(
    state: FocusRingOverlayState,
    token: Any,
    anchor: FocusAnchor? = null,
    active: Boolean,
    boundsInRoot: Rect?,
    color: Color,
    thickness: Dp,
    borderStyle: FocusBorderStyle,
    shape: Shape,
) {
    DisposableEffect(state, token, anchor, active) {
        if (active && anchor != null) {
            state.register(anchor)
        }
        onDispose {
            if (active && anchor != null) {
                state.unregister(anchor)
            }
            state.clear(token)
        }
    }
    SideEffect {
        if (active && boundsInRoot != null && !boundsInRoot.isEmpty) {
            state.publish(
                FocusRingOverlayEntry(
                    token = token,
                    anchor = anchor,
                    boundsInRoot = boundsInRoot,
                    color = color,
                    thickness = thickness,
                    borderStyle = borderStyle,
                    shape = shape,
                ),
            )
        } else {
            state.clear(token)
        }
    }
}

@Composable
private fun animatedPulseAlpha(enabled: Boolean): Float {
    if (!enabled) return 1f
    val transition = rememberInfiniteTransition(label = "focus_overlay_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "focus_overlay_pulse_alpha",
    )
    return alpha
}

@Composable
private fun animatedMarchPhase(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "focus_overlay_march")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "focus_overlay_march_phase",
    )
    return phase
}

@Composable
private fun animatedGradientPhase(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "focus_overlay_gradient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "focus_overlay_gradient_phase",
    )
    return phase
}
