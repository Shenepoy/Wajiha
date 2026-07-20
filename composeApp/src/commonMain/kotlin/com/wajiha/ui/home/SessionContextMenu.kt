package com.wajiha.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.GamepadTextEditRegistry
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.theme.FocusBorderStyle
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalFocusIndicatorStyle
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ContextMenuWidth = 176.dp
private val ContextMenuTitleGap = 4.dp
private val ContextMenuRowHeight = 40.dp
private val ContextMenuIconSlot = 18.dp
private val ContextMenuMarqueeIdleMs = 1500
private val ContextMenuMarqueePxPerSec = 40f

private enum class SessionMenuSide { Right, Left, Above, Below }

data class SessionContextTarget(
    val packageName: String,
    val sessionLabel: String,
)

private fun chooseMenuSide(
    anchor: Rect,
    menuW: Float,
    menuH: Float,
    containerW: Float,
    containerH: Float,
    gapPx: Float,
    padPx: Float,
): SessionMenuSide {
    val rightSpace = containerW - padPx - anchor.right
    val leftSpace = anchor.left - padPx
    val belowSpace = containerH - padPx - anchor.bottom
    val aboveSpace = anchor.top - padPx

    return when {
        rightSpace >= menuW + gapPx -> SessionMenuSide.Right
        leftSpace >= menuW + gapPx -> SessionMenuSide.Left
        belowSpace >= menuH + gapPx -> SessionMenuSide.Below
        aboveSpace >= menuH + gapPx -> SessionMenuSide.Above
        rightSpace >= leftSpace && rightSpace >= belowSpace && rightSpace >= aboveSpace -> SessionMenuSide.Right
        leftSpace >= belowSpace && leftSpace >= aboveSpace -> SessionMenuSide.Left
        belowSpace >= aboveSpace -> SessionMenuSide.Below
        else -> SessionMenuSide.Above
    }
}

private fun offsetForLockedSide(
    side: SessionMenuSide,
    anchor: Rect,
    menuW: Float,
    menuH: Float,
    containerW: Float,
    containerH: Float,
    gapPx: Float,
    padPx: Float,
): IntOffset {
    val minX = padPx
    val minY = padPx
    val maxX = (containerW - padPx - menuW).coerceAtLeast(minX)
    val maxY = (containerH - padPx - menuH).coerceAtLeast(minY)

    fun clampX(x: Float) = x.coerceIn(minX, maxX)

    fun clampY(y: Float) = y.coerceIn(minY, maxY)

    return when (side) {
        SessionMenuSide.Right -> {
            IntOffset(
                clampX(anchor.right + gapPx).roundToInt(),
                clampY(anchor.top).roundToInt(),
            )
        }

        SessionMenuSide.Left -> {
            IntOffset(
                clampX(anchor.left - menuW - gapPx).roundToInt(),
                clampY(anchor.top).roundToInt(),
            )
        }

        SessionMenuSide.Below -> {
            IntOffset(
                clampX(anchor.center.x - menuW / 2f).roundToInt(),
                clampY(anchor.bottom + gapPx).roundToInt(),
            )
        }

        SessionMenuSide.Above -> {
            IntOffset(
                clampX(anchor.center.x - menuW / 2f).roundToInt(),
                clampY(anchor.top - menuH - gapPx).roundToInt(),
            )
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

@Composable
private fun ContextMenuTitleText(
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
                ((scrollDistance / ContextMenuMarqueePxPerSec) * 1000f)
                    .roundToInt()
                    .coerceIn(1500, 10_000)
            while (true) {
                offsetX.snapTo(0f)
                delay(ContextMenuMarqueeIdleMs.toLong())
                offsetX.animateTo(-scrollDistance, tween(durationMs))
                delay(ContextMenuMarqueeIdleMs.toLong())
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
private fun SessionContextMenuRow(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ContextMenuRowHeight)
                .clip(WajihaShapes.focus)
                .wajihaFocusIndicator(highlighted = focused, shape = WajihaShapes.focus)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .wajihaGamepadFocus()
                .onPreviewKeyEvent { event ->
                    if (GamepadKeys.isConfirm(event.type, event.key)) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }.pointerInput(onClick) { detectTapGestures { onClick() } }
                .padding(
                    start = WajihaSpacing.sm,
                    end = WajihaSpacing.sm,
                    top = 2.dp,
                    bottom = 2.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(WajihaSpacing.xs),
    ) {
        Icon(
            imageVector = Icons.Filled.Clear,
            contentDescription = null,
            modifier = Modifier.size(ContextMenuIconSlot),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ContextMenuDimScrim(
    tileCutoutRoot: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.48f,
) {
    val scrimColor = Color.Black.copy(alpha = scrimAlpha)
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
                        if (cutout != null && tileCutoutRoundRect(cutout, tileCornerRadiusPx).contains(offset)) {
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
            val scrimPath =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, w, h))
                    addRoundRect(tileCutoutRoundRect(cutout, tileCornerRadiusPx))
                }
            drawPath(scrimPath, scrimColor)
        }
    }
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

/**
 * Context menu for an active session tile. X or long-press opens; B dismisses; A confirms Close.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SessionContextMenu(
    target: SessionContextTarget?,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onCloseSession: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (target == null) return

    val closeRowFocus = remember(target.packageName) { FocusRequester() }
    val layerId = "session_context_${target.packageName}"
    val focusContinuity = LocalFocusContinuityController.current

    DisposableEffect(layerId, focusContinuity) {
        GamepadLayers.stack.push(layerId)
        focusContinuity?.pushLayer(layerId)
        onDispose {
            GamepadLayers.stack.pop(layerId)
            focusContinuity?.popLayer(layerId)
        }
    }

    LaunchedEffect(target.packageName) {
        withFrameNanos { }
        try {
            closeRowFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun handleBack(): Boolean {
        if (GamepadTextEditRegistry.dismissIfEditing()) return true
        onDismiss()
        return true
    }

    val density = LocalDensity.current
    val gap = WajihaSpacing.sm
    var menuSize by remember(target.packageName) { mutableStateOf(IntSize.Zero) }
    var lockedSide by remember(target.packageName) { mutableStateOf<SessionMenuSide?>(null) }

    BackHandler { handleBack() }

    CompositionLocalProvider(LocalFocusLayerId provides layerId) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (GamepadKeys.isBack(event.type, event.key)) handleBack() else false
                    },
        ) {
            ContextMenuDimScrim(
                tileCutoutRoot = anchorBounds,
                onDismiss = onDismiss,
            )

            val menuOffset =
                remember(
                    anchorBounds,
                    constraints.maxWidth,
                    constraints.maxHeight,
                    menuSize,
                    lockedSide,
                ) {
                    with(density) {
                        val menuW =
                            if (menuSize.width > 0) {
                                menuSize.width.toFloat()
                            } else {
                                ContextMenuWidth.toPx()
                            }
                        val menuH =
                            if (menuSize.height > 0) {
                                menuSize.height.toFloat()
                            } else {
                                96.dp.toPx()
                            }
                        val gapPx = gap.toPx()
                        val padPx = WajihaSpacing.sm.toPx()
                        val cw = constraints.maxWidth.toFloat()
                        val ch = constraints.maxHeight.toFloat()

                        if (anchorBounds != null) {
                            val anchor = inflateForTileScale(anchorBounds, WajihaFocus.selectedScale)
                            val side =
                                lockedSide ?: chooseMenuSide(
                                    anchor = anchor,
                                    menuW = menuW,
                                    menuH = menuH,
                                    containerW = cw,
                                    containerH = ch,
                                    gapPx = gapPx,
                                    padPx = padPx,
                                )
                            offsetForLockedSide(
                                side = side,
                                anchor = anchor,
                                menuW = menuW,
                                menuH = menuH,
                                containerW = cw,
                                containerH = ch,
                                gapPx = gapPx,
                                padPx = padPx,
                            )
                        } else {
                            IntOffset(
                                (cw - menuW - padPx).roundToInt().coerceAtLeast(padPx.roundToInt()),
                                padPx.roundToInt(),
                            )
                        }
                    }
                }

            LaunchedEffect(menuSize, anchorBounds, constraints.maxWidth, constraints.maxHeight) {
                if (lockedSide == null && menuSize != IntSize.Zero && anchorBounds != null) {
                    with(density) {
                        val anchor = inflateForTileScale(anchorBounds, WajihaFocus.selectedScale)
                        lockedSide =
                            chooseMenuSide(
                                anchor = anchor,
                                menuW = menuSize.width.toFloat(),
                                menuH = menuSize.height.toFloat(),
                                containerW = constraints.maxWidth.toFloat(),
                                containerH = constraints.maxHeight.toFloat(),
                                gapPx = gap.toPx(),
                                padPx = WajihaSpacing.sm.toPx(),
                            )
                    }
                }
            }

            val menuPlaced = menuSize != IntSize.Zero

            Box(
                modifier =
                    Modifier
                        .zIndex(1f)
                        .alpha(if (menuPlaced) 1f else 0f)
                        .offset { menuOffset },
            ) {
                Column(
                    modifier =
                        Modifier
                            .onSizeChanged { size ->
                                if (size != IntSize.Zero) menuSize = size
                            }.width(ContextMenuWidth),
                ) {
                    Surface(
                        shape = WajihaShapes.chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        ContextMenuTitleText(
                            text = target.sessionLabel,
                            modifier =
                                Modifier.padding(
                                    horizontal = WajihaSpacing.sm,
                                    vertical = WajihaSpacing.xs,
                                ),
                        )
                    }

                    Spacer(modifier = Modifier.height(ContextMenuTitleGap))

                    Surface(
                        shape = WajihaShapes.chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        CompositionLocalProvider(
                            LocalGamepadFocusChromeScope provides GamepadFocusChromeScope.Menu,
                        ) {
                            Column(modifier = Modifier.padding(vertical = WajihaSpacing.xs)) {
                                SessionContextMenuRow(
                                    label = "Close",
                                    focusRequester = closeRowFocus,
                                    onClick = {
                                        onCloseSession(target.packageName)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
