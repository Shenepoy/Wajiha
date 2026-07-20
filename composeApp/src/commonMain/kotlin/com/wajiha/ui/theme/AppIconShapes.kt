package com.wajiha.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.IconAppearancePreferences

/** Draw-time clip shapes for Apps drawer icons (reimplemented; not from Lawnchair). */
object AppIconShapes {
    val circle: Shape = CircleShape
    val square: Shape = RoundedCornerShape(0.dp)
    val roundedSquare: Shape = RoundedCornerShape(12.dp)

    /** Soft superellipse-style squircle via cubic corners. */
    val squircle: Shape =
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val r = size.minDimension * 0.32f
                val path =
                    Path().apply {
                        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
                    }
                return Outline.Generic(path)
            }
        }

    fun forPreference(shapeId: String): Shape =
        when (IconAppearancePreferences.normalizeShape(shapeId)) {
            "circle" -> circle
            "squircle" -> squircle
            "rounded_square" -> roundedSquare
            "square" -> square
            else -> WajihaShapes.tile
        }
}
