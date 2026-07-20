package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Matches home platform-filter edge softener. */
val ScrollEdgeFadeWidth = 14.dp

/**
 * Softens the start/end of a scrollable surface when content overflows
 * (same look as the home library filter row).
 */
@Composable
fun ScrollEdgeFadeBox(
    showStart: Boolean,
    showEnd: Boolean,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    fadeWidth: Dp = ScrollEdgeFadeWidth,
    edgeColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.drawWithContent {
                drawContent()
                if (!showStart && !showEnd) return@drawWithContent
                if (horizontal) {
                    val width = fadeWidth.toPx().coerceAtMost(size.width)
                    if (showStart && width > 0f) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    colors = listOf(edgeColor, Color.Transparent),
                                    startX = 0f,
                                    endX = width,
                                ),
                            size = Size(width, size.height),
                        )
                    }
                    if (showEnd && width > 0f) {
                        val fadeStart = size.width - width
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, edgeColor),
                                    startX = fadeStart,
                                    endX = size.width,
                                ),
                            topLeft = Offset(fadeStart, 0f),
                            size = Size(width, size.height),
                        )
                    }
                } else {
                    val height = fadeWidth.toPx().coerceAtMost(size.height)
                    if (showStart && height > 0f) {
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors = listOf(edgeColor, Color.Transparent),
                                    startY = 0f,
                                    endY = height,
                                ),
                            size = Size(size.width, height),
                        )
                    }
                    if (showEnd && height > 0f) {
                        val fadeStart = size.height - height
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, edgeColor),
                                    startY = fadeStart,
                                    endY = size.height,
                                ),
                            topLeft = Offset(0f, fadeStart),
                            size = Size(size.width, height),
                        )
                    }
                }
            },
        content = content,
    )
}
