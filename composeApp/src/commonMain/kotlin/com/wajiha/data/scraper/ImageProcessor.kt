package com.wajiha.data.scraper

/** Result of processing: re-encoded bytes plus the new file extension. */
class ProcessedImage(
    val bytes: ByteArray,
    val extension: String,
)

/**
 * Downscales and recompresses downloaded images with platform bitmap APIs,
 * enforcing [ScraperSettings.maxImageResolution]. Implementations return
 * null when the original bytes should be kept (already small enough,
 * unsupported format, decode failure).
 */
interface ImageProcessor {
    /**
     * @param maxEdge longest allowed edge in pixels (> 0)
     * @param preferAlpha re-encode as a format with transparency (logos/icons)
     */
    fun process(
        bytes: ByteArray,
        maxEdge: Int,
        preferAlpha: Boolean,
    ): ProcessedImage?
}

/** Used when the host provides no implementation (tests). */
object NoopImageProcessor : ImageProcessor {
    override fun process(
        bytes: ByteArray,
        maxEdge: Int,
        preferAlpha: Boolean,
    ): ProcessedImage? = null
}
