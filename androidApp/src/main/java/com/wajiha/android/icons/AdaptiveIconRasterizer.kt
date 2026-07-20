package com.wajiha.android.icons

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import com.wajiha.data.prefs.IconAppearancePreferences
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rasterizes app icons into a bitmap masked by the selected icon shape.
 *
 * Adaptive icons: compose FG/BG with the AID viewport inset (unmasked), scale
 * opaque content to fill the canvas (so pre-shaped OEM layers still take the
 * user mask), then apply the shape. Layers use [Drawable.setBounds]/[Drawable.draw]
 * onto [Bitmap.DENSITY_NONE] bitmaps so density mismatch cannot zoom content
 * to the top-left.
 */
object AdaptiveIconRasterizer {
    private const val ALPHA_OPAQUE_THRESHOLD = 16

    fun rasterize(
        drawable: Drawable,
        sizePx: Int,
        shapeId: String,
    ): Bitmap {
        val normalized = IconAppearancePreferences.normalizeShape(shapeId)
        val composed =
            if (drawable is AdaptiveIconDrawable) {
                composeAdaptiveUnmasked(drawable, sizePx)
            } else {
                drawDrawable(drawable, sizePx)
            }
        val result =
            if (normalized == IconAppearancePreferences.DEFAULT_SHAPE && drawable !is AdaptiveIconDrawable) {
                composed
            } else {
                // Fill opaque bounds before masking — OEM/legacy assets are often
                // already squircles with transparent margins; without this the
                // user circle/squircle mask never changes the silhouette.
                applyMask(
                    scaleOpaqueToFill(composed),
                    IconShapePaths.forPreference(normalized, sizePx),
                )
            }
        result.density = Resources.getSystem().displayMetrics.densityDpi
        return result
    }

    /**
     * Draw FG/BG into a layer canvas (viewport + 25% inset per side), crop center.
     * Avoids the OEM [AdaptiveIconDrawable] mask so our shape mask changes the silhouette.
     */
    private fun composeAdaptiveUnmasked(
        icon: AdaptiveIconDrawable,
        sizePx: Int,
    ): Bitmap {
        val insetPx =
            (sizePx * AdaptiveIconDrawable.getExtraInsetFraction())
                .roundToInt()
                .coerceAtLeast(1)
        val layerSize = sizePx + 2 * insetPx
        // Parent bounds help some OEM AdaptiveIconDrawable layer implementations.
        icon.setBounds(0, 0, sizePx, sizePx)
        val layers = blankBitmap(layerSize)
        val layerCanvas = Canvas(layers)
        var drew = false
        for (src in listOf(icon.background, icon.foreground)) {
            val layer = src?.freshCopy() ?: continue
            layer.setBounds(0, 0, layerSize, layerSize)
            layer.draw(layerCanvas)
            drew = true
        }
        if (!drew) {
            layers.recycle()
            return drawDrawable(icon, sizePx)
        }
        val viewport = Bitmap.createBitmap(layers, insetPx, insetPx, sizePx, sizePx).withoutDensity()
        layers.recycle()
        return viewport
    }

    /**
     * Scale the opaque bounding box to fill the bitmap. OEM adaptive layers are
     * often already squircles with transparent margins; without this, a circle
     * mask leaves the squircle silhouette unchanged.
     */
    private fun scaleOpaqueToFill(source: Bitmap): Bitmap {
        val bounds = opaqueBounds(source) ?: return source
        val bw = bounds.width()
        val bh = bounds.height()
        if (bw <= 0 || bh <= 0) return source
        // Already near full-bleed — skip (avoids softening edges).
        if (bw >= source.width * 0.92f && bh >= source.height * 0.92f) {
            return source
        }
        val out = blankBitmap(source.width)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, bounds, Rect(0, 0, source.width, source.height), paint)
        if (source !== out) {
            source.recycle()
        }
        return out
    }

    private fun opaqueBounds(bitmap: Bitmap): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        val row = IntArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val a = row[x] ushr 24
                if (a > ALPHA_OPAQUE_THRESHOLD) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }
        if (maxX < 0) return null
        return Rect(minX, minY, maxX + 1, maxY + 1)
    }

    private fun drawDrawable(
        drawable: Drawable,
        sizePx: Int,
    ): Bitmap {
        val out = blankBitmap(sizePx)
        val canvas = Canvas(out)
        val layer = drawable.freshCopy()
        layer.setBounds(0, 0, sizePx, sizePx)
        layer.draw(canvas)
        return out
    }

    private fun Drawable.freshCopy(): Drawable = constantState?.newDrawable()?.mutate() ?: mutate()

    private fun applyMask(
        source: Bitmap,
        mask: android.graphics.Path,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val out = blankBitmap(w)
        val canvas = Canvas(out)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawPath(mask, maskPaint)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        // Explicit dst Rect — never drawBitmap(bmp, x, y) (density-scaled from origin).
        canvas.drawBitmap(source, null, Rect(0, 0, w, h), maskPaint)
        maskPaint.xfermode = null
        if (source !== out) {
            source.recycle()
        }
        return out
    }

    private fun blankBitmap(size: Int): Bitmap = createBitmap(size, size).withoutDensity()

    private fun Bitmap.withoutDensity(): Bitmap = apply { density = Bitmap.DENSITY_NONE }
}
