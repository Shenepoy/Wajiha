package com.wajiha.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadKeys
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.theme.FocusBorderStyle
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalFocusIndicatorStyle
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaIconSize
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MenuAnimMs = 160
private const val MenuMarqueeIdleMs = 1500
private const val MenuMarqueePxPerSec = 40f

/**
 * Title card plus action card for a tile context menu.
 * Callers own which rows appear and place [WajihaContextMenuScrim].
 * The panel owns the marquee title and the menu focus scope.
 */
@Composable
fun WajihaContextMenuPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.width(WajihaContextMenuMetrics.width)) {
        Surface(
            shape = WajihaShapes.chip,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            WajihaContextMenuTitle(
                text = title,
                modifier =
                    Modifier.padding(
                        horizontal = WajihaSpacing.sm,
                        vertical = WajihaSpacing.xs,
                    ),
            )
        }
        Spacer(modifier = Modifier.height(WajihaContextMenuMetrics.titleGap))
        Surface(
            shape = WajihaShapes.chip,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            CompositionLocalProvider(
                LocalGamepadFocusChromeScope provides GamepadFocusChromeScope.Menu,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = WajihaSpacing.xs),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun WajihaContextMenuTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.titleSmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(WajihaShapes.focus),
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val textLayoutResult =
            remember(text, containerWidthPx) {
                textMeasurer.measure(
                    text = text,
                    style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        val textWidthPx = textLayoutResult.size.width.toFloat()
        val scrollDistance = (textWidthPx - containerWidthPx).coerceAtLeast(0f)
        val overflow = scrollDistance > 0f
        val offsetX = remember(text) { Animatable(0f) }

        LaunchedEffect(text, overflow, scrollDistance) {
            if (!overflow) {
                offsetX.snapTo(0f)
                return@LaunchedEffect
            }
            val durationMs =
                ((scrollDistance / MenuMarqueePxPerSec) * 1000f)
                    .roundToInt()
                    .coerceIn(1500, 10_000)
            while (true) {
                offsetX.snapTo(0f)
                delay(MenuMarqueeIdleMs.toLong())
                offsetX.animateTo(-scrollDistance, tween(durationMs))
                delay(MenuMarqueeIdleMs.toLong())
            }
        }

        Text(
            text = text,
            style = textStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) },
        )
    }
}

@Composable
fun WajihaContextMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = WajihaSpacing.sm),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        thickness = 0.5.dp,
    )
}

@Composable
fun WajihaContextMenuExpansion(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(MenuAnimMs)) + fadeIn(tween(MenuAnimMs)),
        exit = shrinkVertically(animationSpec = tween(MenuAnimMs)) + fadeOut(tween(MenuAnimMs)),
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun WajihaContextMenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    nested: Boolean = false,
    destructive: Boolean = false,
    expandable: Boolean = false,
    expanded: Boolean = false,
    shortcutHint: String? = null,
    focusRequester: FocusRequester? = null,
    forceHighlight: Boolean = false,
    onFocusGained: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused || forceHighlight
    val rowHeight = if (nested) WajihaContextMenuMetrics.nestedRowHeight else WajihaContextMenuMetrics.rowHeight
    val startPad =
        when {
            nested && icon != null -> WajihaSpacing.sm
            nested -> WajihaSpacing.sm + WajihaContextMenuMetrics.iconSlot + WajihaSpacing.xs
            else -> WajihaSpacing.sm
        }
    val labelColor =
        when {
            destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    val iconColor =
        when {
            destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
            nested -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = rowHeight)
                .clip(WajihaShapes.focus)
                .wajihaFocusIndicator(highlighted = highlighted, shape = WajihaShapes.focus)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) onFocusGained()
                }.wajihaGamepadFocus()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        expandable -> {
                            when {
                                !expanded && (
                                    event.key == Key.DirectionRight ||
                                        GamepadKeys.isConfirm(event.type, event.key)
                                ) -> {
                                    onClick()
                                    true
                                }

                                expanded && (
                                    event.key == Key.DirectionLeft ||
                                        event.key == Key.DirectionUp
                                ) -> {
                                    onClick()
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }

                        GamepadKeys.isConfirm(event.type, event.key) -> {
                            onClick()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.pointerInput(onClick) { detectTapGestures { onClick() } }
                .padding(
                    start = startPad,
                    end = WajihaSpacing.sm,
                    top = 2.dp,
                    bottom = 2.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier =
                    Modifier.size(
                        if (nested) WajihaIconSize.xs else WajihaContextMenuMetrics.iconSlot,
                    ),
                tint = iconColor,
            )
        }
        Text(
            text = label,
            style =
                if (nested) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            fontWeight =
                when {
                    nested -> FontWeight.Normal
                    else -> FontWeight.Medium
                },
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (shortcutHint != null && highlighted) {
            Text(
                text = shortcutHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * Full-screen scrim with a rounded cutout for the selected tile.
 * Hosted inside screen content so the hint bar stays undimmed.
 */
@Composable
fun WajihaContextMenuScrim(
    tileCutoutRoot: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimColor = WajihaColors.MenuScrim
    val density = LocalDensity.current
    val focusStyle = LocalFocusIndicatorStyle.current
    val ringPadPx =
        with(density) {
            when (focusStyle.borderStyle) {
                FocusBorderStyle.Glow,
                FocusBorderStyle.Neon,
                FocusBorderStyle.Aura,
                FocusBorderStyle.SoftPulse,
                FocusBorderStyle.GradientPulse,
                -> focusStyle.selectedThickness.toPx() * 3f

                FocusBorderStyle.Double -> focusStyle.selectedThickness.toPx() * 2f

                else -> focusStyle.selectedThickness.toPx()
            }
        }
    val tileCornerRadiusPx =
        with(density) {
            WajihaShapes.tileCornerRadius.toPx() * WajihaFocus.selectedScale
        }
    var overlayRootBounds by remember { mutableStateOf<Rect?>(null) }

    val localTileCutout =
        remember(tileCutoutRoot, overlayRootBounds, ringPadPx) {
            val overlay = overlayRootBounds ?: return@remember null
            val tile = tileCutoutRoot ?: return@remember null
            val scaled = inflateForTileScale(tile, WajihaFocus.selectedScale, ringPadPx)
            Rect(
                left = scaled.left - overlay.left,
                top = scaled.top - overlay.top,
                right = scaled.right - overlay.left,
                bottom = scaled.bottom - overlay.top,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayRootBounds = it.boundsInRoot() }
                .pointerInput(localTileCutout, tileCornerRadiusPx) {
                    detectTapGestures { offset ->
                        val cutout = localTileCutout
                        if (cutout != null &&
                            tileCutoutRoundRect(cutout, tileCornerRadiusPx).contains(offset)
                        ) {
                            return@detectTapGestures
                        }
                        onDismiss()
                    }
                },
    ) {
        val cutout = localTileCutout

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (h <= 0f) return@Canvas
            if (cutout == null) {
                drawRect(scrimColor, topLeft = Offset.Zero, size = Size(w, h))
                return@Canvas
            }
            val clamped =
                Rect(
                    left = cutout.left.coerceIn(0f, w),
                    top = cutout.top.coerceIn(0f, h),
                    right = cutout.right.coerceIn(0f, w),
                    bottom = cutout.bottom.coerceIn(0f, h),
                )
            val scrimPath =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, w, h))
                    addRoundRect(tileCutoutRoundRect(clamped, tileCornerRadiusPx))
                }
            drawPath(scrimPath, scrimColor)
        }
    }
}

private fun inflateForTileScale(
    rect: Rect,
    scale: Float,
    ringPadPx: Float = 0f,
): Rect {
    val cx = rect.center.x
    val cy = rect.center.y
    val hw = rect.width * scale / 2f + ringPadPx
    val hh = rect.height * scale / 2f + ringPadPx
    return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
}

private fun tileCutoutRoundRect(
    cutout: Rect,
    cornerRadiusPx: Float,
): RoundRect {
    val cornerRadius =
        CornerRadius(
            x = cornerRadiusPx.coerceAtMost(cutout.width / 2f),
            y = cornerRadiusPx.coerceAtMost(cutout.height / 2f),
        )
    return RoundRect(
        left = cutout.left,
        top = cutout.top,
        right = cutout.right,
        bottom = cutout.bottom,
        topLeftCornerRadius = cornerRadius,
        topRightCornerRadius = cornerRadius,
        bottomRightCornerRadius = cornerRadius,
        bottomLeftCornerRadius = cornerRadius,
    )
}
