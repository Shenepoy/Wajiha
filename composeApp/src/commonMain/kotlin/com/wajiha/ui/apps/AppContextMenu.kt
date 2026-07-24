package com.wajiha.ui.apps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.wajiha.ui.components.WajihaContextMenuMetrics
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.icons.WajihaIcons
import com.wajiha.ui.theme.FocusBorderStyle
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalFocusIndicatorStyle
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ContextMenuMarqueeIdleMs = 1500
private val ContextMenuMarqueePxPerSec = 40f

private enum class MenuSide { Right, Left, Above, Below }

private enum class AppContextMenuFocusRow {
    PrimaryOpen,
    MoveUp,
    MoveDown,
}

data class AppContextTarget(
    val packageName: String,
    val appLabel: String,
)

private fun chooseMenuSide(
    anchor: Rect,
    menuW: Float,
    menuH: Float,
    containerW: Float,
    containerH: Float,
    gapPx: Float,
    padPx: Float,
): MenuSide {
    val rightSpace = containerW - padPx - anchor.right
    val leftSpace = anchor.left - padPx
    val belowSpace = containerH - padPx - anchor.bottom
    val aboveSpace = anchor.top - padPx
    val neededW = menuW + gapPx
    val neededH = menuH + gapPx

    return when {
        // Bottom-edge anchors (dock pins): sit above instead of floating beside.
        belowSpace < neededH && aboveSpace >= neededH -> MenuSide.Above

        rightSpace >= neededW -> MenuSide.Right

        leftSpace >= neededW -> MenuSide.Left

        belowSpace >= neededH -> MenuSide.Below

        aboveSpace >= neededH -> MenuSide.Above

        rightSpace >= leftSpace && rightSpace >= belowSpace && rightSpace >= aboveSpace -> MenuSide.Right

        leftSpace >= belowSpace && leftSpace >= aboveSpace -> MenuSide.Left

        belowSpace >= aboveSpace -> MenuSide.Below

        else -> MenuSide.Above
    }
}

private fun rootRectToLocal(
    root: Rect,
    overlayRoot: Rect?,
): Rect {
    if (overlayRoot == null) return root
    return Rect(
        left = root.left - overlayRoot.left,
        top = root.top - overlayRoot.top,
        right = root.right - overlayRoot.left,
        bottom = root.bottom - overlayRoot.top,
    )
}

private fun offsetForLockedSide(
    side: MenuSide,
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

    /** Center when possible; otherwise keep the menu flush with the nearer anchor edge. */
    fun alignHorizontal(): Float {
        val centered = anchor.center.x - menuW / 2f
        return when {
            centered < minX -> clampX(anchor.left)
            centered > maxX -> clampX(anchor.right - menuW)
            else -> centered
        }
    }

    return when (side) {
        MenuSide.Right -> {
            IntOffset(
                clampX(anchor.right + gapPx).roundToInt(),
                clampY(anchor.top).roundToInt(),
            )
        }

        MenuSide.Left -> {
            IntOffset(
                clampX(anchor.left - menuW - gapPx).roundToInt(),
                clampY(anchor.top).roundToInt(),
            )
        }

        MenuSide.Below -> {
            IntOffset(
                alignHorizontal().roundToInt(),
                clampY(anchor.bottom + gapPx).roundToInt(),
            )
        }

        MenuSide.Above -> {
            IntOffset(
                alignHorizontal().roundToInt(),
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
private fun AppContextMenuRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = Icons.Filled.Info,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WajihaContextMenuMetrics.rowHeight)
                .clip(WajihaShapes.focus)
                .wajihaFocusIndicator(highlighted = focused, shape = WajihaShapes.focus)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .wajihaGamepadFocus()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(WajihaContextMenuMetrics.iconSlot),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
 * Context menu for an app tile. X or long-press opens; B dismisses.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppContextMenu(
    target: AppContextTarget?,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onOpenAppInfo: (packageName: String) -> Unit,
    onLaunchOnDisplay: (packageName: String, displayId: Int) -> Unit = { _, _ -> },
    dualDisplay: Boolean = false,
    topDisplayId: Int = 0,
    bottomDisplayId: Int = 4,
    isFavorite: Boolean = false,
    canMoveFavoriteUp: Boolean = false,
    canMoveFavoriteDown: Boolean = false,
    onToggleFavorite: (packageName: String) -> Unit = {},
    onMoveFavoriteUp: (packageName: String) -> Unit = {},
    onMoveFavoriteDown: (packageName: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (target == null) return

    val firstRowFocus = remember(target.packageName) { FocusRequester() }
    val moveUpFocus = remember(target.packageName) { FocusRequester() }
    val moveDownFocus = remember(target.packageName) { FocusRequester() }
    var focusRow by remember(target.packageName) {
        mutableStateOf(AppContextMenuFocusRow.PrimaryOpen)
    }
    var focusEpoch by remember(target.packageName) { mutableStateOf(0) }
    val layerId = "app_context_${target.packageName}"
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
        focusRow = AppContextMenuFocusRow.PrimaryOpen
        focusEpoch++
    }

    LaunchedEffect(focusEpoch, focusRow, canMoveFavoriteUp, canMoveFavoriteDown) {
        // Wait for favorite reorder + row insert/remove to settle before reclaiming focus.
        withFrameNanos { }
        withFrameNanos { }
        val requester =
            when (focusRow) {
                AppContextMenuFocusRow.PrimaryOpen -> {
                    firstRowFocus
                }

                AppContextMenuFocusRow.MoveUp -> {
                    when {
                        canMoveFavoriteUp -> moveUpFocus
                        canMoveFavoriteDown -> moveDownFocus
                        else -> firstRowFocus
                    }
                }

                AppContextMenuFocusRow.MoveDown -> {
                    when {
                        canMoveFavoriteDown -> moveDownFocus
                        canMoveFavoriteUp -> moveUpFocus
                        else -> firstRowFocus
                    }
                }
            }
        try {
            requester.requestFocus()
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
    var lockedSide by remember(target.packageName) { mutableStateOf<MenuSide?>(null) }
    var overlayRootBounds by remember(target.packageName) { mutableStateOf<Rect?>(null) }
    // Re-pick side when the tile moves under an open menu (favorite reorder).
    LaunchedEffect(anchorBounds?.center) {
        lockedSide = null
    }

    BackHandler { handleBack() }

    CompositionLocalProvider(LocalFocusLayerId provides layerId) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .onGloballyPositioned { overlayRootBounds = it.boundsInRoot() }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (GamepadKeys.isBack(event.type, event.key)) handleBack() else false
                    },
        ) {
            ContextMenuDimScrim(
                tileCutoutRoot = anchorBounds,
                onDismiss = onDismiss,
            )

            val localAnchor =
                remember(anchorBounds, overlayRootBounds) {
                    anchorBounds?.let { root ->
                        inflateForTileScale(
                            rootRectToLocal(root, overlayRootBounds),
                            WajihaFocus.selectedScale,
                        )
                    }
                }

            val menuOffset =
                remember(
                    localAnchor,
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
                                WajihaContextMenuMetrics.width.toPx()
                            }
                        val menuH =
                            if (menuSize.height > 0) {
                                menuSize.height.toFloat()
                            } else {
                                220.dp.toPx()
                            }
                        val gapPx = gap.toPx()
                        val padPx = WajihaSpacing.sm.toPx()
                        val cw = constraints.maxWidth.toFloat()
                        val ch = constraints.maxHeight.toFloat()

                        if (localAnchor != null) {
                            val side =
                                lockedSide ?: chooseMenuSide(
                                    anchor = localAnchor,
                                    menuW = menuW,
                                    menuH = menuH,
                                    containerW = cw,
                                    containerH = ch,
                                    gapPx = gapPx,
                                    padPx = padPx,
                                )
                            offsetForLockedSide(
                                side = side,
                                anchor = localAnchor,
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

            LaunchedEffect(
                menuSize,
                localAnchor,
                constraints.maxWidth,
                constraints.maxHeight,
            ) {
                if (lockedSide == null && menuSize != IntSize.Zero && localAnchor != null) {
                    with(density) {
                        lockedSide =
                            chooseMenuSide(
                                anchor = localAnchor,
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
                            }.width(WajihaContextMenuMetrics.width),
                ) {
                    Surface(
                        shape = WajihaShapes.chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        ContextMenuTitleText(
                            text = target.appLabel,
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
                            Column(modifier = Modifier.padding(vertical = WajihaSpacing.xs)) {
                                if (dualDisplay) {
                                    AppContextMenuRow(
                                        label = "Open top",
                                        icon = WajihaIcons.OpenInNew,
                                        focusRequester = firstRowFocus,
                                        onClick = {
                                            onLaunchOnDisplay(target.packageName, topDisplayId)
                                            onDismiss()
                                        },
                                    )
                                    AppContextMenuRow(
                                        label = "Open bottom",
                                        icon = WajihaIcons.OpenInNewDown,
                                        onClick = {
                                            onLaunchOnDisplay(
                                                target.packageName,
                                                bottomDisplayId,
                                            )
                                            onDismiss()
                                        },
                                    )
                                } else {
                                    AppContextMenuRow(
                                        label = "Open",
                                        icon = Icons.Filled.PlayArrow,
                                        focusRequester = firstRowFocus,
                                        onClick = {
                                            onLaunchOnDisplay(target.packageName, topDisplayId)
                                            onDismiss()
                                        },
                                    )
                                }
                                AppContextMenuRow(
                                    label = if (isFavorite) "Remove favorite" else "Favorite",
                                    icon = Icons.Filled.Star,
                                    onClick = {
                                        onToggleFavorite(target.packageName)
                                        onDismiss()
                                    },
                                )
                                if (isFavorite && canMoveFavoriteUp) {
                                    AppContextMenuRow(
                                        label = "Move up",
                                        icon = Icons.Filled.KeyboardArrowUp,
                                        focusRequester = moveUpFocus,
                                        onClick = {
                                            focusRow = AppContextMenuFocusRow.MoveUp
                                            focusEpoch++
                                            onMoveFavoriteUp(target.packageName)
                                        },
                                    )
                                }
                                if (isFavorite && canMoveFavoriteDown) {
                                    AppContextMenuRow(
                                        label = "Move down",
                                        icon = Icons.Filled.KeyboardArrowDown,
                                        focusRequester = moveDownFocus,
                                        onClick = {
                                            focusRow = AppContextMenuFocusRow.MoveDown
                                            focusEpoch++
                                            onMoveFavoriteDown(target.packageName)
                                        },
                                    )
                                }
                                AppContextMenuRow(
                                    label = "App info",
                                    icon = Icons.Filled.Info,
                                    onClick = {
                                        onOpenAppInfo(target.packageName)
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
