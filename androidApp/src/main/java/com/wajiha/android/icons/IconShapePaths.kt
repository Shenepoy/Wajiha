package com.wajiha.android.icons

import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import com.wajiha.data.prefs.IconAppearancePreferences

/** Android [Path] masks matching [com.wajiha.ui.theme.AppIconShapes] (Apache-safe reimplementation). */
object IconShapePaths {
    fun forPreference(
        shapeId: String,
        size: Int,
    ): Path {
        val s = size.toFloat()
        return when (IconAppearancePreferences.normalizeShape(shapeId)) {
            "circle" -> circle(s)
            "squircle" -> squircle(s)
            "rounded_square" -> roundedSquare(s, cornerFraction = 0.22f)
            "square" -> square(s)
            else -> systemMask(size) ?: roundedSquare(s, cornerFraction = 0.15f)
        }
    }

    private fun circle(size: Float): Path =
        Path().apply {
            addOval(RectF(0f, 0f, size, size), Path.Direction.CW)
        }

    private fun square(size: Float): Path =
        Path().apply {
            addRect(0f, 0f, size, size, Path.Direction.CW)
        }

    private fun roundedSquare(
        size: Float,
        cornerFraction: Float,
    ): Path {
        val r = size * cornerFraction
        return Path().apply {
            addRoundRect(RectF(0f, 0f, size, size), r, r, Path.Direction.CW)
        }
    }

    /** Soft squircle via rounded rect with large relative corners. */
    private fun squircle(size: Float): Path = roundedSquare(size, cornerFraction = 0.32f)

    /** OEM adaptive mask scaled to [size], if available. */
    private fun systemMask(size: Int): Path? =
        try {
            val aid =
                AdaptiveIconDrawable(
                    ColorDrawable(android.graphics.Color.BLACK),
                    ColorDrawable(android.graphics.Color.WHITE),
                )
            aid.setBounds(0, 0, size, size)
            Path(aid.iconMask)
        } catch (_: Exception) {
            null
        }
}
