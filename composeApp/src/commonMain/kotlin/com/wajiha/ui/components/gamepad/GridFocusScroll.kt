package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/** True when the item fully fits in the padded viewport on the scroll axis. */
fun LazyGridItemInfo.isFullyInViewport(
    layoutInfo: LazyGridLayoutInfo,
    horizontalScroll: Boolean,
    edgePadPx: Int = 0,
): Boolean {
    val itemStart = if (horizontalScroll) offset.x else offset.y
    val itemExtent = if (horizontalScroll) size.width else size.height
    val itemEnd = itemStart + itemExtent
    val vpStart =
        layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding + edgePadPx
    val vpEnd =
        layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding - edgePadPx
    if (vpEnd <= vpStart) return false
    return itemStart >= vpStart && itemEnd <= vpEnd
}

/**
 * Scroll so [key] is fully inside the padded viewport on the scroll axis.
 *
 * Home grid passes one D-pad step at a time (one stride is enough). App drawer
 * and long jumps may need several strides — loop until fully visible.
 */
suspend fun LazyGridState.smoothBringItemIntoView(
    key: Any,
    lazyIndex: Int,
    horizontalScroll: Boolean,
    edgePaddingPx: Int,
    maxSteps: Int = 12,
) {
    withFrameNanos { }
    repeat(maxSteps) {
        val info = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        if (info != null && info.isFullyInViewport(layoutInfo, horizontalScroll, edgePaddingPx)) {
            return
        }
        val stride = gridScrollStridePx(horizontalScroll).coerceAtLeast(1)
        val delta =
            if (info != null) {
                edgePadDeltaPx(info, horizontalScroll, edgePaddingPx)
                    .coerceIn(-stride.toFloat(), stride.toFloat())
            } else {
                val maxVisible =
                    layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: firstVisibleItemIndex
                val minVisible =
                    layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: firstVisibleItemIndex
                when {
                    lazyIndex > maxVisible -> stride.toFloat()
                    lazyIndex < minVisible -> -stride.toFloat()
                    else -> stride.toFloat()
                }
            }
        if (abs(delta) <= 0.5f) return
        scrollBy(delta)
        withFrameNanos { }
    }
}

private fun LazyGridState.gridScrollStridePx(horizontalScroll: Boolean): Int {
    val viewport =
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return viewport / 3
    return if (horizontalScroll) {
        val row0 = visible.filter { it.row == 0 }.sortedBy { it.offset.x }
        if (row0.size >= 2) {
            (row0[1].offset.x - row0[0].offset.x).coerceAtLeast(1)
        } else {
            row0
                .firstOrNull()
                ?.size
                ?.width
                ?.coerceAtLeast(1) ?: (viewport / 3)
        }
    } else {
        val col0 = visible.filter { it.column == 0 }.sortedBy { it.offset.y }
        if (col0.size >= 2) {
            (col0[1].offset.y - col0[0].offset.y).coerceAtLeast(1)
        } else {
            col0
                .firstOrNull()
                ?.size
                ?.height
                ?.coerceAtLeast(1) ?: (viewport / 3)
        }
    }
}

private fun LazyGridState.edgePadDeltaPx(
    info: LazyGridItemInfo,
    horizontalScroll: Boolean,
    edgePaddingPx: Int,
): Float {
    val viewportStart =
        layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding + edgePaddingPx
    val viewportEnd =
        layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding - edgePaddingPx
    if (viewportEnd <= viewportStart) return 0f
    val itemStart = if (horizontalScroll) info.offset.x else info.offset.y
    val itemExtent = if (horizontalScroll) info.size.width else info.size.height
    val itemEnd = itemStart + itemExtent
    return when {
        itemStart < viewportStart -> (itemStart - viewportStart).toFloat()
        itemEnd > viewportEnd -> (itemEnd - viewportEnd).toFloat()
        else -> 0f
    }
}
