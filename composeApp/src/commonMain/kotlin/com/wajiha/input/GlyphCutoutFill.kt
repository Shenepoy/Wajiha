package com.wajiha.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap

/**
 * Kenney filled mono glyphs use transparent letter cutouts. On a light UI those cutouts
 * show the background and wash out. This fills enclosed transparent regions (letter
 * interiors) with [fill], leaving exterior transparency alone.
 */
fun ImageBitmap.withEnclosedTransparencyFilled(fill: Color = Color.Black): ImageBitmap {
    val w = width
    val h = height
    if (w <= 0 || h <= 0) return this
    val pixels = toPixelMap()
    val outside = BooleanArray(w * h)
    val queue = ArrayDeque<Int>(w + h)

    fun index(
        x: Int,
        y: Int,
    ) = y * w + x

    fun isClear(
        x: Int,
        y: Int,
    ): Boolean = pixels[x, y].alpha < 0.06f

    fun offer(
        x: Int,
        y: Int,
    ) {
        if (x !in 0 until w || y !in 0 until h) return
        val i = index(x, y)
        if (outside[i] || !isClear(x, y)) return
        outside[i] = true
        queue.addLast(i)
    }

    for (x in 0 until w) {
        offer(x, 0)
        offer(x, h - 1)
    }
    for (y in 0 until h) {
        offer(0, y)
        offer(w - 1, y)
    }
    while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        val x = i % w
        val y = i / w
        offer(x - 1, y)
        offer(x + 1, y)
        offer(x, y - 1)
        offer(x, y + 1)
    }

    val holes = ArrayList<Int>()
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = index(x, y)
            if (isClear(x, y) && !outside[i]) holes.add(i)
        }
    }
    if (holes.isEmpty()) return this

    val out = ImageBitmap(w, h)
    val canvas = Canvas(out)
    val paint = Paint()
    canvas.drawImage(this, Offset.Zero, paint)
    paint.color = fill
    for (i in holes) {
        val x = i % w
        val y = i / w
        canvas.drawRect(
            left = x.toFloat(),
            top = y.toFloat(),
            right = x + 1f,
            bottom = y + 1f,
            paint = paint,
        )
    }
    return out
}

fun GamepadHintButton.isFaceGlyphButton(): Boolean =
    this == GamepadHintButton.A ||
        this == GamepadHintButton.B ||
        this == GamepadHintButton.X ||
        this == GamepadHintButton.Y
