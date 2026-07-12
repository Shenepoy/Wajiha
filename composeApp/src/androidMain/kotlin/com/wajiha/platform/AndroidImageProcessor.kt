package com.wajiha.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wajiha.data.scraper.ImageProcessor
import com.wajiha.data.scraper.ProcessedImage
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * BitmapFactory-based downscaler. Images whose longest edge exceeds maxEdge
 * are sample-decoded (memory-bounded), scaled to fit and re-encoded — PNG for
 * alpha media (logos/icons), JPEG for everything else.
 */
class AndroidImageProcessor : ImageProcessor {
    override fun process(
        bytes: ByteArray,
        maxEdge: Int,
        preferAlpha: Boolean,
    ): ProcessedImage? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null
            // Animated GIFs would lose animation if re-encoded — keep original
            if (bounds.outMimeType == "image/gif") return null
            val longest = max(width, height)
            if (longest <= maxEdge) return null

            var sampleSize = 1
            while (longest / (sampleSize * 2) >= maxEdge) sampleSize *= 2
            val decoded =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                ) ?: return null

            val decodedLongest = max(decoded.width, decoded.height)
            val bitmap =
                if (decodedLongest > maxEdge) {
                    val scale = maxEdge.toFloat() / decodedLongest
                    Bitmap
                        .createScaledBitmap(
                            decoded,
                            (decoded.width * scale).roundToInt().coerceAtLeast(1),
                            (decoded.height * scale).roundToInt().coerceAtLeast(1),
                            true,
                        ).also { if (it !== decoded) decoded.recycle() }
                } else {
                    decoded
                }

            val out = ByteArrayOutputStream()
            val extension: String
            if (preferAlpha || bitmap.hasAlpha()) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                extension = "png"
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                extension = "jpg"
            }
            bitmap.recycle()
            return ProcessedImage(out.toByteArray(), extension)
        } catch (_: Exception) {
            return null
        }
    }

    private companion object {
        const val JPEG_QUALITY = 88
    }
}
