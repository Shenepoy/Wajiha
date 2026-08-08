package com.wajiha.input

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlyphCutoutFillTest {
    @Test
    fun fillsEnclosedHolesButNotExterior() {
        // 5x5 white ring with a transparent center hole and transparent exterior.
        val ring = ImageBitmap(5, 5)
        val ringCanvas = Canvas(ring)
        val ringPaint = Paint().apply { color = Color.White }
        for (y in 1..3) {
            for (x in 1..3) {
                if (x == 2 && y == 2) continue
                ringCanvas.drawRect(
                    left = x.toFloat(),
                    top = y.toFloat(),
                    right = x + 1f,
                    bottom = y + 1f,
                    paint = ringPaint,
                )
            }
        }

        val out = ring.withEnclosedTransparencyFilled(Color.Black)
        val pm = out.toPixelMap()
        assertTrue(pm[0, 0].alpha < 0.06f)
        assertTrue(pm[2, 2].alpha > 0.9f)
        assertTrue(pm[2, 2].red < 0.05f)
        assertTrue(pm[1, 1].red > 0.9f)
    }
}
