package com.wajiha.ui.components.gamepad

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wajiha.input.FocusAnchor
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.ui.theme.FocusBorderStyle
import com.wajiha.ui.theme.FocusPlacement
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalFocusIndicatorStyle
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.showGamepadChrome
import kotlin.math.min

/**
 * Draws Wajiha custom focus chrome around focused bounds.
 * Suppresses default Compose indication — pair with [com.wajiha.input.wajihaGamepadFocus].
 *
 * [FocusPlacement.Outside] and expanding styles (Glow/Neon/Aura/…) register with
 * [LocalFocusRingOverlay] so chrome is painted at screen root past LazyGrid / clip parents.
 * Simple Inside strokes still draw locally.
 */
fun Modifier.wajihaFocusIndicator(
    highlighted: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    selected: Boolean = false,
    focusAnchor: FocusAnchor? = null,
): Modifier =
    composed {
        if (!showGamepadChrome(highlighted)) return@composed this
        val style = LocalFocusIndicatorStyle.current
        val color = style.color ?: WajihaFocus.borderColor()
        val chromeScope = LocalGamepadFocusChromeScope.current
        // Dense grids need a crisp ring — skip the +1dp selected bump and wide glow.
        val compact =
            chromeScope == GamepadFocusChromeScope.GameGrid ||
                chromeScope == GamepadFocusChromeScope.Menu
        val thickness =
            when {
                compact -> style.thickness.coerceAtMost(2.5.dp)
                selected -> style.selectedThickness
                else -> style.thickness
            }
        // Dense tile grids: always paint Inside so Outside/Glow never eats gutters
        // or clips against the screen edge (polyscreen / bottom launcher).
        val placement = if (compact) FocusPlacement.Inside else style.placement
        val borderStyle =
            if (compact && borderStyleNeedsCompactRemap(style.borderStyle)) {
                FocusBorderStyle.Solid
            } else {
                style.borderStyle
            }
        val overlayState = LocalFocusRingOverlay.current
        val needsOverlay =
            placement == FocusPlacement.Outside ||
                borderStyle == FocusBorderStyle.Glow ||
                borderStyle == FocusBorderStyle.Neon ||
                borderStyle == FocusBorderStyle.Aura ||
                borderStyle == FocusBorderStyle.SoftPulse ||
                borderStyle == FocusBorderStyle.GradientPulse ||
                borderStyle == FocusBorderStyle.Double

        if (needsOverlay && overlayState != null) {
            return@composed outsideFocusViaOverlay(
                overlay = overlayState,
                color = color,
                thickness = thickness,
                borderStyle = borderStyle,
                shape = shape,
                focusAnchor = focusAnchor,
                // Keep the user's placement; expanding styles may still halo outward
                // from Inside strokes without forcing Outside geometry.
                placementOutside = placement == FocusPlacement.Outside,
                compact = compact,
            )
        }

        when (borderStyle) {
            FocusBorderStyle.Solid -> {
                placementBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.Pulsing -> {
                pulsingBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.SoftPulse -> {
                softPulseBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.Double -> {
                doubleBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.Glow -> {
                glowBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.Aura -> {
                auraBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.CornerBrackets -> {
                cornerBracketsBorder(color, thickness, placement)
            }

            FocusBorderStyle.GradientPulse -> {
                gradientPulseBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.Neon -> {
                neonBorder(color, thickness, shape, placement)
            }

            FocusBorderStyle.Dotted,
            FocusBorderStyle.Dashed,
            FocusBorderStyle.MarchingAnts,
            -> {
                styledStrokeBorder(
                    color = color,
                    thickness = thickness,
                    placement = placement,
                    borderStyle = borderStyle,
                    shape = shape,
                )
            }
        }
    }

/** Wide glow/aura styles read as muddy blobs in dense tile grids — use Solid there. */
private fun borderStyleNeedsCompactRemap(style: FocusBorderStyle): Boolean =
    style == FocusBorderStyle.Glow ||
        style == FocusBorderStyle.Neon ||
        style == FocusBorderStyle.Aura ||
        style == FocusBorderStyle.SoftPulse ||
        style == FocusBorderStyle.GradientPulse

private fun Modifier.outsideFocusViaOverlay(
    overlay: FocusRingOverlayState,
    color: Color,
    thickness: Dp,
    borderStyle: FocusBorderStyle,
    shape: Shape,
    focusAnchor: FocusAnchor?,
    placementOutside: Boolean = true,
    compact: Boolean = false,
): Modifier =
    composed {
        val token = remember { Any() }
        var boundsInRoot by remember { mutableStateOf<Rect?>(null) }
        val viewportBoundsInRoot =
            LocalFocusRingClipViewport.current?.boundsInRoot
                ?: LocalSettingSectionScroll.current?.viewportBoundsInRoot()
        val continuity = LocalFocusContinuityController.current
        val layerId = LocalFocusLayerId.current
        val resolvedAnchor =
            focusAnchor
                ?: continuity?.takeIf { layerId.isNotEmpty() }?.anchor(
                    targetId = token,
                    layerId = layerId,
                )

        FocusRingOverlayRegistrationEffect(
            state = overlay,
            token = token,
            anchor = resolvedAnchor,
            active = true,
            boundsInRoot = boundsInRoot,
            viewportBoundsInRoot = viewportBoundsInRoot,
            color = color,
            thickness = thickness,
            borderStyle = borderStyle,
            shape = shape,
            placementOutside = placementOutside,
            compact = compact,
        )

        onGloballyPositioned { coords ->
            boundsInRoot = coords.unclippedBoundsInRoot()
        }
    }

internal data class StrokeBounds(
    val topLeft: Offset,
    val size: Size,
    val cornerRadius: CornerRadius,
)

internal fun strokeBounds(
    drawSize: Size,
    strokeWidth: Float,
    outside: Boolean,
    cornerRadiusPx: Float,
): StrokeBounds =
    if (!outside) {
        val inset = strokeWidth / 2f
        StrokeBounds(
            topLeft = Offset(inset, inset),
            size =
                Size(
                    (drawSize.width - strokeWidth).coerceAtLeast(0f),
                    (drawSize.height - strokeWidth).coerceAtLeast(0f),
                ),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        )
    } else {
        val outset = strokeWidth / 2f
        StrokeBounds(
            topLeft = Offset(-outset, -outset),
            size =
                Size(
                    drawSize.width + strokeWidth,
                    drawSize.height + strokeWidth,
                ),
            cornerRadius =
                CornerRadius(
                    cornerRadiusPx + outset,
                    cornerRadiusPx + outset,
                ),
        )
    }

internal fun cornerRadiusPx(
    shape: Shape,
    size: Size,
    density: Density,
): Float =
    when (shape) {
        is RoundedCornerShape -> {
            val topStart = shape.topStart.toPx(size, density)
            val topEnd = shape.topEnd.toPx(size, density)
            val bottomEnd = shape.bottomEnd.toPx(size, density)
            val bottomStart = shape.bottomStart.toPx(size, density)
            maxOf(topStart, topEnd, bottomEnd, bottomStart)
        }

        else -> {
            0f
        }
    }

/**
 * Paints focus chrome for [itemSize] with origin at [itemTopLeft] in the current draw scope.
 * Used by both local Inside drawing and the screen-level Outside overlay.
 */
internal fun DrawScope.drawFocusChrome(
    itemTopLeft: Offset,
    itemSize: Size,
    color: Color,
    thickness: Dp,
    borderStyle: FocusBorderStyle,
    shape: Shape,
    placementOutside: Boolean,
    pulseAlpha: Float = 1f,
    marchPhase: Float = 0f,
    gradientPhase: Float = 0f,
    compact: Boolean = false,
) {
    val strokeWidth = thickness.toPx()
    val radius = cornerRadiusPx(shape, itemSize, this)
    val tinted = color.copy(alpha = (color.alpha * pulseAlpha).coerceIn(0f, 1f))
    val glowSpreadFactor = if (compact) 0.55f else 1f

    when (borderStyle) {
        FocusBorderStyle.Solid,
        FocusBorderStyle.Pulsing,
        -> {
            val bounds = strokeBounds(itemSize, strokeWidth, placementOutside, radius)
            drawRoundRect(
                color = tinted,
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = strokeWidth),
            )
        }

        FocusBorderStyle.SoftPulse -> {
            val breathe = 0.75f + gradientPhase * 0.55f
            val softWidth = strokeWidth * breathe
            val bounds = strokeBounds(itemSize, softWidth, placementOutside, radius)
            drawRoundRect(
                color = color.copy(alpha = (0.35f + pulseAlpha * 0.55f).coerceIn(0f, 1f)),
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = softWidth),
            )
            val haloSpread = softWidth * 2.2f * glowSpreadFactor
            val haloOutset = haloSpread / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.12f * pulseAlpha * glowSpreadFactor),
                topLeft = itemTopLeft + Offset(-haloOutset, -haloOutset),
                size = Size(itemSize.width + haloSpread, itemSize.height + haloSpread),
                cornerRadius = CornerRadius(radius + haloOutset, radius + haloOutset),
                style = Stroke(width = haloSpread),
            )
        }

        FocusBorderStyle.Double -> {
            val outerPx = strokeWidth
            val innerPx = (strokeWidth * 0.55f).coerceAtLeast(1f)
            val gap = outerPx * 0.75f
            val outerBounds = strokeBounds(itemSize, outerPx, placementOutside, radius)
            drawRoundRect(
                color = tinted,
                topLeft = itemTopLeft + outerBounds.topLeft,
                size = outerBounds.size,
                cornerRadius = outerBounds.cornerRadius,
                style = Stroke(width = outerPx),
            )
            val innerOffset =
                if (placementOutside) {
                    -(outerPx + gap + innerPx / 2f)
                } else {
                    outerPx + gap + innerPx / 2f
                }
            val rect =
                if (placementOutside) {
                    Size(
                        (itemSize.width + innerOffset * 2f).coerceAtLeast(0f),
                        (itemSize.height + innerOffset * 2f).coerceAtLeast(0f),
                    )
                } else {
                    Size(
                        (itemSize.width - innerOffset * 2f).coerceAtLeast(0f),
                        (itemSize.height - innerOffset * 2f).coerceAtLeast(0f),
                    )
                }
            val topLeft =
                if (placementOutside) {
                    Offset(-innerOffset, -innerOffset)
                } else {
                    Offset(innerOffset, innerOffset)
                }
            val cornerAdj = if (placementOutside) gap else -gap
            drawRoundRect(
                color = tinted.copy(alpha = tinted.alpha * 0.85f),
                topLeft = itemTopLeft + topLeft,
                size = rect,
                cornerRadius =
                    CornerRadius(
                        (radius + cornerAdj).coerceAtLeast(0f),
                        (radius + cornerAdj).coerceAtLeast(0f),
                    ),
                style = Stroke(width = innerPx),
            )
        }

        FocusBorderStyle.Glow -> {
            val bounds = strokeBounds(itemSize, strokeWidth, placementOutside, radius)
            drawRoundRect(
                color = tinted,
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = strokeWidth),
            )
            // Halo always expands outward so Inside Glow is not an inset frame.
            listOf(2.5f to 0.18f, 4f to 0.10f, 6f to 0.05f).forEach { (spread, alpha) ->
                val spreadPx = strokeWidth * spread * glowSpreadFactor
                val outset = spreadPx / 2f
                drawRoundRect(
                    color = color.copy(alpha = alpha * pulseAlpha * glowSpreadFactor),
                    topLeft = itemTopLeft + Offset(-outset, -outset),
                    size = Size(itemSize.width + spreadPx, itemSize.height + spreadPx),
                    cornerRadius = CornerRadius(radius + outset, radius + outset),
                    style = Stroke(width = spreadPx),
                )
            }
        }

        FocusBorderStyle.Aura -> {
            val bounds = strokeBounds(itemSize, strokeWidth, placementOutside, radius)
            drawRoundRect(
                color = tinted,
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = strokeWidth),
            )
            listOf(1.8f to 0.22f, 3.4f to 0.12f, 5.2f to 0.06f).forEach { (spread, alpha) ->
                val spreadPx = strokeWidth * spread * (0.85f + pulseAlpha * 0.25f) * glowSpreadFactor
                val outset = spreadPx / 2f
                drawRoundRect(
                    color = color.copy(alpha = alpha * pulseAlpha * glowSpreadFactor),
                    topLeft = itemTopLeft + Offset(-outset, -outset),
                    size = Size(itemSize.width + spreadPx, itemSize.height + spreadPx),
                    cornerRadius = CornerRadius(radius + outset, radius + outset),
                    style = Stroke(width = spreadPx * 0.85f),
                )
            }
        }

        FocusBorderStyle.CornerBrackets -> {
            val arm = min(itemSize.width, itemSize.height) * 0.22f
            val edgeInset = if (placementOutside) -strokeWidth / 2f else strokeWidth / 2f
            val corners =
                listOf(
                    Offset(edgeInset, edgeInset),
                    Offset(itemSize.width - edgeInset, edgeInset),
                    Offset(edgeInset, itemSize.height - edgeInset),
                    Offset(itemSize.width - edgeInset, itemSize.height - edgeInset),
                )
            corners.forEachIndexed { index, origin ->
                val horizontalEnd = if (index % 2 == 0) origin.x + arm else origin.x - arm
                val verticalEnd = if (index < 2) origin.y + arm else origin.y - arm
                val start = itemTopLeft + origin
                drawLine(
                    color = tinted,
                    start = start,
                    end = itemTopLeft + Offset(horizontalEnd, origin.y),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = tinted,
                    start = start,
                    end = itemTopLeft + Offset(origin.x, verticalEnd),
                    strokeWidth = strokeWidth,
                )
            }
        }

        FocusBorderStyle.GradientPulse -> {
            val coreAlpha = 0.55f + gradientPhase * 0.45f
            val haloAlpha = 0.08f + gradientPhase * 0.14f
            val bounds = strokeBounds(itemSize, strokeWidth, placementOutside, radius)
            drawRoundRect(
                color = color.copy(alpha = coreAlpha * pulseAlpha),
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = strokeWidth),
            )
            val spreadPx = strokeWidth * 3.5f
            // Halo expands outward for both placements so Inside is not clipped to an inset wash.
            val haloPx = spreadPx * glowSpreadFactor
            val outset = haloPx / 2f
            drawRoundRect(
                color = color.copy(alpha = haloAlpha * pulseAlpha * glowSpreadFactor),
                topLeft = itemTopLeft + Offset(-outset, -outset),
                size = Size(itemSize.width + haloPx, itemSize.height + haloPx),
                cornerRadius = CornerRadius(radius + haloPx / 3f, radius + haloPx / 3f),
                style = Stroke(width = haloPx),
            )
        }

        FocusBorderStyle.Neon -> {
            val bounds = strokeBounds(itemSize, strokeWidth, placementOutside, radius)
            val glowSpread = strokeWidth * 2.8f * glowSpreadFactor
            // Always expand neon halo outward (Inside placement previously drew negative
            // offsets that parents with .clip() shaved off).
            val glowOutset = glowSpread / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.35f * pulseAlpha * glowSpreadFactor),
                topLeft = itemTopLeft + Offset(-glowOutset, -glowOutset),
                size =
                    Size(
                        itemSize.width + glowSpread,
                        itemSize.height + glowSpread,
                    ),
                cornerRadius =
                    CornerRadius(
                        radius + glowOutset,
                        radius + glowOutset,
                    ),
                style = Stroke(width = strokeWidth + glowSpread),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f * pulseAlpha),
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = strokeWidth * 0.35f),
            )
            drawRoundRect(
                color = tinted,
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = strokeWidth),
            )
        }

        FocusBorderStyle.Dotted,
        FocusBorderStyle.Dashed,
        FocusBorderStyle.MarchingAnts,
        -> {
            val dash =
                when (borderStyle) {
                    FocusBorderStyle.Dotted -> floatArrayOf(strokeWidth, strokeWidth * 1.5f)

                    FocusBorderStyle.Dashed -> floatArrayOf(strokeWidth * 3f, strokeWidth * 2f)

                    FocusBorderStyle.MarchingAnts -> floatArrayOf(strokeWidth * 2f, strokeWidth * 2f)

                    FocusBorderStyle.Solid,
                    FocusBorderStyle.Pulsing,
                    FocusBorderStyle.SoftPulse,
                    FocusBorderStyle.Double,
                    FocusBorderStyle.Glow,
                    FocusBorderStyle.Aura,
                    FocusBorderStyle.CornerBrackets,
                    FocusBorderStyle.GradientPulse,
                    FocusBorderStyle.Neon,
                    -> floatArrayOf()
                }
            val pathEffect =
                when (borderStyle) {
                    FocusBorderStyle.MarchingAnts -> {
                        PathEffect.dashPathEffect(dash, marchPhase)
                    }

                    else -> {
                        PathEffect.dashPathEffect(dash, 0f)
                    }
                }
            val bounds = strokeBounds(itemSize, strokeWidth, placementOutside, radius)
            drawRoundRect(
                color = tinted,
                topLeft = itemTopLeft + bounds.topLeft,
                size = bounds.size,
                cornerRadius = bounds.cornerRadius,
                style = Stroke(width = strokeWidth, pathEffect = pathEffect),
            )
        }
    }
}

private fun Modifier.placementBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    when (placement) {
        FocusPlacement.Inside -> {
            border(width = thickness, color = color, shape = shape)
        }

        FocusPlacement.Outside -> {
            drawBehind {
                drawFocusChrome(
                    itemTopLeft = Offset.Zero,
                    itemSize = size,
                    color = color,
                    thickness = thickness,
                    borderStyle = FocusBorderStyle.Solid,
                    shape = shape,
                    placementOutside = true,
                )
            }
        }
    }

private fun Modifier.pulsingBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "focus_pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "focus_pulse_alpha",
        )
        when (placement) {
            FocusPlacement.Inside -> {
                border(width = thickness, color = color.copy(alpha = alpha), shape = shape)
            }

            FocusPlacement.Outside -> {
                drawBehind {
                    drawFocusChrome(
                        itemTopLeft = Offset.Zero,
                        itemSize = size,
                        color = color,
                        thickness = thickness,
                        borderStyle = FocusBorderStyle.Solid,
                        shape = shape,
                        placementOutside = true,
                        pulseAlpha = alpha,
                    )
                }
            }
        }
    }

private fun Modifier.softPulseBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "focus_soft_pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "focus_soft_pulse_alpha",
        )
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "focus_soft_pulse_phase",
        )
        drawBehind {
            drawFocusChrome(
                itemTopLeft = Offset.Zero,
                itemSize = size,
                color = color,
                thickness = thickness,
                borderStyle = FocusBorderStyle.SoftPulse,
                shape = shape,
                placementOutside = placement == FocusPlacement.Outside,
                pulseAlpha = alpha,
                gradientPhase = phase,
            )
        }
    }

private fun Modifier.auraBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "focus_aura")
        val alpha by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "focus_aura_alpha",
        )
        drawBehind {
            drawFocusChrome(
                itemTopLeft = Offset.Zero,
                itemSize = size,
                color = color,
                thickness = thickness,
                borderStyle = FocusBorderStyle.Aura,
                shape = shape,
                placementOutside = placement == FocusPlacement.Outside,
                pulseAlpha = alpha,
            )
        }
    }

private fun Modifier.doubleBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    drawBehind {
        drawFocusChrome(
            itemTopLeft = Offset.Zero,
            itemSize = size,
            color = color,
            thickness = thickness,
            borderStyle = FocusBorderStyle.Double,
            shape = shape,
            placementOutside = placement == FocusPlacement.Outside,
        )
    }

private fun Modifier.glowBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    drawBehind {
        drawFocusChrome(
            itemTopLeft = Offset.Zero,
            itemSize = size,
            color = color,
            thickness = thickness,
            borderStyle = FocusBorderStyle.Glow,
            shape = shape,
            placementOutside = placement == FocusPlacement.Outside,
        )
    }

private fun Modifier.cornerBracketsBorder(
    color: Color,
    thickness: Dp,
    placement: FocusPlacement,
): Modifier =
    drawBehind {
        drawFocusChrome(
            itemTopLeft = Offset.Zero,
            itemSize = size,
            color = color,
            thickness = thickness,
            borderStyle = FocusBorderStyle.CornerBrackets,
            shape = RoundedCornerShape(0.dp),
            placementOutside = placement == FocusPlacement.Outside,
        )
    }

private fun Modifier.gradientPulseBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "focus_gradient_pulse")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "focus_gradient_phase",
        )
        drawBehind {
            drawFocusChrome(
                itemTopLeft = Offset.Zero,
                itemSize = size,
                color = color,
                thickness = thickness,
                borderStyle = FocusBorderStyle.GradientPulse,
                shape = shape,
                placementOutside = placement == FocusPlacement.Outside,
                gradientPhase = phase,
            )
        }
    }

private fun Modifier.neonBorder(
    color: Color,
    thickness: Dp,
    shape: Shape,
    placement: FocusPlacement,
): Modifier =
    drawBehind {
        drawFocusChrome(
            itemTopLeft = Offset.Zero,
            itemSize = size,
            color = color,
            thickness = thickness,
            borderStyle = FocusBorderStyle.Neon,
            shape = shape,
            placementOutside = placement == FocusPlacement.Outside,
        )
    }

private fun Modifier.styledStrokeBorder(
    color: Color,
    thickness: Dp,
    placement: FocusPlacement,
    borderStyle: FocusBorderStyle,
    shape: Shape,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "focus_march")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 24f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "focus_march_phase",
        )
        drawBehind {
            drawFocusChrome(
                itemTopLeft = Offset.Zero,
                itemSize = size,
                color = color,
                thickness = thickness,
                borderStyle = borderStyle,
                shape = shape,
                placementOutside = placement == FocusPlacement.Outside,
                marchPhase = phase,
            )
        }
    }
