package com.wajiha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wajiha.ui.components.gamepad.wajihaPressedFeedback
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/** Same border as [com.wajiha.ui.components.gamepad.GamepadButton] outlined / Material3 OutlinedButton. */
@Composable
internal fun folderChromeBorder(): BorderStroke = ButtonDefaults.outlinedButtonBorder(enabled = true)

@Composable
internal fun folderChromeOutlineColor(): Color =
    when (val brush = folderChromeBorder().brush) {
        is SolidColor -> brush.value
        else -> MaterialTheme.colorScheme.outline
    }

/**
 * Horizontal folder-style tabs where the selected tab visually connects to a
 * content panel below (manila-folder metaphor). Right-aligned within the row.
 *
 * Pair with [com.wajiha.ui.components.gamepad.WajihaSettingPanel] (`folderPanel = true`).
 *
 * Chrome matches [com.wajiha.ui.components.gamepad.GamepadButton] outlined style:
 * `colorScheme.outline` at 1.dp with 4.dp corners.
 *
 * Tabs are touch-only and switched via L1/R1 at screen level — not gamepad focusable.
 */
@Composable
fun FolderTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = WajihaSpacing.touchMin,
    showTopEdge: Boolean = true,
    /**
     * Selected tab [LayoutCoordinates] for a parent that draws the shared folder
     * seam (e.g. Settings chrome with Back).
     */
    onSelectedTabCoordinates: ((LayoutCoordinates) -> Unit)? = null,
) {
    val chromeBorder = folderChromeBorder()
    val outlineColor = folderChromeOutlineColor()
    // Cache every tab's bounds so the seam gap can jump immediately on switch
    // instead of briefly using the previous tab (visible flash).
    val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
    val tabCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    val selectedBounds = tabBounds[selectedIndex]
    val stroke = chromeBorder.width

    // Push cached coordinates to parents that draw the seam (Settings + Back row).
    val selectedCoords = tabCoords[selectedIndex]
    SideEffect {
        if (selectedCoords != null && selectedCoords.isAttached) {
            onSelectedTabCoordinates?.invoke(selectedCoords)
        }
    }

    Row(
        modifier =
            modifier.then(
                if (showTopEdge) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawFolderTopEdge(
                            color = outlineColor,
                            seamY = size.height - stroke.toPx() / 2f,
                            width = size.width,
                            selectedTab = selectedBounds,
                            strokeWidth = stroke.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs, Alignment.End),
        verticalAlignment = Alignment.Bottom,
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FolderTab(
                label = label,
                selected = selected,
                onClick = { onSelect(index) },
                minHeight = minHeight,
                outlineColor = outlineColor,
                modifier =
                    Modifier.onGloballyPositioned { coords ->
                        tabCoords[index] = coords
                        val parent = coords.positionInParent()
                        val w = coords.size.width.toFloat()
                        val h = coords.size.height.toFloat()
                        tabBounds[index] =
                            Rect(
                                left = parent.x,
                                top = parent.y,
                                right = parent.x + w,
                                bottom = parent.y + h,
                            )
                        if (selected) {
                            onSelectedTabCoordinates?.invoke(coords)
                        }
                    },
            )
        }
    }
}

/**
 * Thin folder seam with a gap under [selectedTab] so that tab can join the panel.
 */
internal fun DrawScope.drawFolderTopEdge(
    color: Color,
    seamY: Float,
    width: Float,
    selectedTab: Rect?,
    strokeWidth: Float = 1.dp.toPx(),
) {
    if (selectedTab == null) {
        drawLine(color, Offset(0f, seamY), Offset(width, seamY), strokeWidth)
        return
    }
    val left = selectedTab.left
    val right = selectedTab.right
    if (left > 0f) {
        drawLine(color, Offset(0f, seamY), Offset(left, seamY), strokeWidth)
    }
    if (right < width) {
        drawLine(color, Offset(right, seamY), Offset(width, seamY), strokeWidth)
    }
}

@Composable
private fun FolderTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    minHeight: Dp,
    outlineColor: Color,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = WajihaShapes.folderTab
    val chromeBorder = folderChromeBorder()
    val corner = WajihaShapes.focusCornerRadius
    val outlinedColors = ButtonDefaults.outlinedButtonColors()
    // Instant swap — animated mid-states flash against the panel join.
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            outlinedColors.containerColor
        }
    val labelColor =
        if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            outlinedColors.contentColor
        }
    val topPadding = if (selected) 0.dp else WajihaSpacing.xs

    Box(
        modifier = modifier.padding(top = topPadding),
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            modifier =
                Modifier
                    .clip(shape)
                    .wajihaPressedFeedback(pressed, shape)
                    .then(
                        if (selected) {
                            Modifier.folderTabOpenBottomBorder(
                                outlineColor,
                                chromeBorder.width,
                                corner,
                            )
                        } else {
                            Modifier.border(border = chromeBorder, shape = shape)
                        },
                    ).pointerInput(onClick) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                val released = tryAwaitRelease()
                                pressed = false
                                if (released) onClick()
                            },
                        )
                    },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = labelColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = minHeight)
                        .padding(
                            horizontal = WajihaSpacing.sm,
                            vertical = WajihaSpacing.sm,
                        ).then(
                            if (pressed) {
                                Modifier.background(WajihaFocus.pressedOverlay())
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

/** Top + sides stroke with rounded top corners; bottom open to meet the panel. */
private fun Modifier.folderTabOpenBottomBorder(
    color: Color,
    width: Dp,
    cornerRadius: Dp,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val inset = stroke / 2f
        val radius = cornerRadius.toPx()
        val path =
            Path().apply {
                moveTo(inset, size.height)
                lineTo(inset, radius + inset)
                arcTo(
                    rect =
                        Rect(
                            left = inset,
                            top = inset,
                            right = inset + radius * 2f,
                            bottom = inset + radius * 2f,
                        ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(size.width - radius - inset, inset)
                arcTo(
                    rect =
                        Rect(
                            left = size.width - inset - radius * 2f,
                            top = inset,
                            right = size.width - inset,
                            bottom = inset + radius * 2f,
                        ),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                lineTo(size.width - inset, size.height)
            }
        drawPath(path, color = color, style = Stroke(width = stroke))
    }

/**
 * Left / right / bottom stroke with rounded bottom corners; top open for folder tabs.
 * Matches OutlinedButton corner treatment ([WajihaShapes.button]).
 */
internal fun Modifier.folderPanelOpenTopBorder(
    color: Color,
    width: Dp,
    cornerRadius: Dp = WajihaShapes.focusCornerRadius,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val inset = stroke / 2f
        val radius = cornerRadius.toPx()
        val path =
            Path().apply {
                moveTo(inset, 0f)
                lineTo(inset, size.height - radius - inset)
                arcTo(
                    rect =
                        Rect(
                            left = inset,
                            top = size.height - inset - radius * 2f,
                            right = inset + radius * 2f,
                            bottom = size.height - inset,
                        ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false,
                )
                lineTo(size.width - radius - inset, size.height - inset)
                arcTo(
                    rect =
                        Rect(
                            left = size.width - inset - radius * 2f,
                            top = size.height - inset - radius * 2f,
                            right = size.width - inset,
                            bottom = size.height - inset,
                        ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false,
                )
                lineTo(size.width - inset, 0f)
            }
        drawPath(path, color = color, style = Stroke(width = stroke))
    }
