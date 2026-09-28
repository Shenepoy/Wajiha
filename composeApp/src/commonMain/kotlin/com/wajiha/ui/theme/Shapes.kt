package com.wajiha.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

object WajihaShapes {
    /** Cover art / grid tiles — slight radius for artwork. */
    val tileCornerRadius = 8.dp
    val tile = RoundedCornerShape(tileCornerRadius)

    /** Content panels and popover surfaces. */
    val card = RoundedCornerShape(8.dp)

    /** Dual-display / settings focus hero card. */
    val heroCornerRadius = 18.dp
    val hero = RoundedCornerShape(heroCornerRadius)

    /**
     * Settings hero card with a top-end notch so the status / notification pill
     * sits in open stage chrome instead of overlapping the surface.
     */
    @Composable
    fun rememberHeroTopEndCarve(
        carveWidth: Dp,
        carveHeight: Dp,
        outerRadius: Dp = heroCornerRadius,
        innerRadius: Dp = WajihaSpacing.sm,
    ): Shape {
        val density = LocalDensity.current
        return remember(carveWidth, carveHeight, outerRadius, innerRadius, density) {
            val carveWPx = with(density) { carveWidth.toPx() }
            val carveHPx = with(density) { carveHeight.toPx() }
            val outerPx = with(density) { outerRadius.toPx() }
            val innerPx = with(density) { innerRadius.toPx() }
            GenericShape { size, direction ->
                addHeroTopEndCarve(
                    size = size,
                    carveWidth = carveWPx,
                    carveHeight = carveHPx,
                    outerRadius = outerPx,
                    innerRadius = innerPx,
                    rtl = direction == LayoutDirection.Rtl,
                )
            }
        }
    }

    /** Inner hero / cover panels (slightly tighter than [hero]). */
    val heroInnerCornerRadius = 12.dp
    val heroInner = RoundedCornerShape(heroInnerCornerRadius)

    /** Chips, segmented pills, badges — boxy interactive controls. */
    val chip = RoundedCornerShape(4.dp)

    /** 3DS suspended-software strip / compact overlay pills. */
    val pill = RoundedCornerShape(percent = 50)

    val dialog = RoundedCornerShape(8.dp)

    /** Buttons and focusable row chrome — matches focus ring corners. */
    val button = RoundedCornerShape(4.dp)

    val focusCornerRadius = 4.dp
    val focus = RoundedCornerShape(focusCornerRadius)

    /** Folder tab — rounded top, flat bottom to meet content panel. */
    val folderTab =
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 4.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )

    /** Folder content panel — flat top, rounded bottom and sides. */
    val folderPanel =
        RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 4.dp,
            bottomEnd = 4.dp,
        )
}

/** Path for [WajihaShapes.rememberHeroTopEndCarve] — notch at the top trailing edge. */
private fun Path.addHeroTopEndCarve(
    size: Size,
    carveWidth: Float,
    carveHeight: Float,
    outerRadius: Float,
    innerRadius: Float,
    rtl: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val oR = outerRadius.coerceAtMost(min(w, h) / 2f)
    val cW = carveWidth.coerceIn(0f, w)
    val cH = carveHeight.coerceIn(0f, h)
    if (cW <= 0f || cH <= 0f) {
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(oR)))
        return
    }
    val iR = innerRadius.coerceAtMost(min(cW, cH) / 2f)

    if (!rtl) {
        moveTo(oR, 0f)
        lineTo((w - cW - iR).coerceAtLeast(oR), 0f)
        quadraticTo(w - cW, 0f, w - cW, iR)
        lineTo(w - cW, (cH - iR).coerceAtLeast(iR))
        quadraticTo(w - cW, cH, (w - cW + iR).coerceAtMost(w - oR), cH)
        lineTo((w - oR).coerceAtLeast(w - cW + iR), cH)
        quadraticTo(w, cH, w, (cH + oR).coerceAtMost(h - oR))
        lineTo(w, h - oR)
        quadraticTo(w, h, w - oR, h)
        lineTo(oR, h)
        quadraticTo(0f, h, 0f, h - oR)
        lineTo(0f, oR)
        quadraticTo(0f, 0f, oR, 0f)
        close()
    } else {
        // Top-start carve under RTL.
        moveTo(w - oR, 0f)
        lineTo((cW + iR).coerceAtMost(w - oR), 0f)
        quadraticTo(cW, 0f, cW, iR)
        lineTo(cW, (cH - iR).coerceAtLeast(iR))
        quadraticTo(cW, cH, (cW - iR).coerceAtLeast(oR), cH)
        lineTo(oR.coerceAtMost(cW - iR), cH)
        quadraticTo(0f, cH, 0f, (cH + oR).coerceAtMost(h - oR))
        lineTo(0f, h - oR)
        quadraticTo(0f, h, oR, h)
        lineTo(w - oR, h)
        quadraticTo(w, h, w, h - oR)
        lineTo(w, oR)
        quadraticTo(w, 0f, w - oR, 0f)
        close()
    }
}
