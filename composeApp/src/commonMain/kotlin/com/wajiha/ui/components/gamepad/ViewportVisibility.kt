package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Fraction of the item that overlaps the padded (on-screen) viewport on the
 * scroll axis. Content padding is excluded so edge peeks in the gutter don't count.
 */
fun LazyGridItemInfo.visibleFractionOnScrollAxis(
    layoutInfo: LazyGridLayoutInfo,
    horizontalScroll: Boolean,
): Float {
    val itemStart = if (horizontalScroll) offset.x.toFloat() else offset.y.toFloat()
    val itemExtent = if (horizontalScroll) size.width.toFloat() else size.height.toFloat()
    if (itemExtent <= 0f) return 0f
    val vpStart =
        (layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding).toFloat()
    val vpEnd =
        (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding).toFloat()
    if (vpEnd <= vpStart) return 0f
    val visible = min(itemStart + itemExtent, vpEnd) - max(itemStart, vpStart)
    return (visible / itemExtent).coerceIn(0f, 1f)
}

/** True when at least half of the item is on-screen on the scroll axis. */
fun LazyGridItemInfo.hasCenterInViewport(
    layoutInfo: LazyGridLayoutInfo,
    horizontalScroll: Boolean,
): Boolean = visibleFractionOnScrollAxis(layoutInfo, horizontalScroll) >= 0.5f

/** True when the vertical center of [bounds] lies inside [viewport]. */
fun hasCenterInViewportVertically(
    bounds: Rect,
    viewport: Rect,
): Boolean {
    if (bounds.height <= 0f) return false
    val centerY = bounds.top + bounds.height / 2f
    return centerY >= viewport.top && centerY < viewport.bottom
}
