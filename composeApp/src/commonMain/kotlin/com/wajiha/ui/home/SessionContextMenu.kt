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
import com.wajiha.ui.components.WajihaContextMenuMetrics
import com.wajiha.ui.components.WajihaContextMenuPanel
import com.wajiha.ui.components.WajihaContextMenuRow
import com.wajiha.ui.components.WajihaContextMenuScrim
import com.wajiha.ui.components.WajihaDialog
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
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
    var pendingClose by remember(target.packageName) { mutableStateOf(false) }
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

    BackHandler(enabled = !pendingClose) { handleBack() }

    CompositionLocalProvider(LocalFocusLayerId provides layerId) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (pendingClose) return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (GamepadKeys.isBack(event.type, event.key)) handleBack() else false
                    },
        ) {
            WajihaContextMenuScrim(
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
                                WajihaContextMenuMetrics.width.toPx()
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
                WajihaContextMenuPanel(
                    title = target.sessionLabel,
                    modifier =
                        Modifier.onSizeChanged { size ->
                            if (size != IntSize.Zero) menuSize = size
                        },
                ) {
                    WajihaContextMenuRow(
                        label = "Close",
                        icon = Icons.Filled.Clear,
                        focusRequester = closeRowFocus,
                        onClick = { pendingClose = true },
                    )
                }
            }
        }
    }

    WajihaDialog(
        visible = pendingClose,
        title = "Close ${target.sessionLabel}?",
        message = "This stops the app. Unsaved progress is lost.",
        onDismiss = { pendingClose = false },
        onConfirm = {
            pendingClose = false
            onCloseSession(target.packageName)
            onDismiss()
        },
        confirmText = "Close",
        dismissText = "Cancel",
    )
}
