package com.wajiha.data.prefs

import kotlin.math.max
import kotlin.math.min

/**
 * Runtime auto-fit for an unconfigured hero display slot.
 * Keeps visibility; scales down and clamps so elements stay on-canvas.
 */
object HeroLayoutFitter {
    private const val PAD = 0.02f
    private const val MIN_COVER_W = 0.12f
    private const val MIN_COVER_H = 0.16f
    private const val MIN_LOGO_W = 0.14f
    private const val MIN_TEXT_W = 0.18f
    private const val MAX_TEXT_W = 0.90f

    fun resolveForPaint(
        bundle: HeroLayoutBundle,
        slot: HeroDisplaySlot,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        rtl: Boolean,
    ): HeroLayout {
        if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) {
            return bundle.slot(slot)
        }
        val current = bundle.slot(slot)
        val painted =
            if (current.configured) {
                current
            } else {
                val other =
                    when (slot) {
                        HeroDisplaySlot.Primary -> bundle.secondary
                        HeroDisplaySlot.Secondary -> bundle.primary
                    }
                val source =
                    if (other.configured) {
                        other
                    } else {
                        HeroLayoutPresets.template(current.presetId).copy(
                            coverBorder = current.coverBorder,
                            // Preserve migrated visibility from the unconfigured slot.
                            elements =
                                mergeVisibility(
                                    template = HeroLayoutPresets.template(current.presetId).elements,
                                    visibilitySource = current.elements,
                                ),
                        )
                    }
                autoFit(source, canvasWidthPx, canvasHeightPx).copy(
                    presetId = current.presetId,
                    configured = false,
                    coverBorder = current.coverBorder,
                )
            }
        return if (rtl) mirrorStartEdge(painted) else painted
    }

    fun autoFit(
        source: HeroLayout,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
    ): HeroLayout {
        if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) return source
        val aspect = canvasWidthPx / canvasHeightPx
        val movable =
            source.elements.filter {
                it.visible && it.id != HeroElementId.Backdrop
            }
        if (movable.isEmpty()) return source

        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for (el in movable) {
            val (w, h) = sized(el, aspect)
            minX = min(minX, el.x)
            minY = min(minY, el.y)
            maxX = max(maxX, el.x + w)
            maxY = max(maxY, el.y + h)
        }
        val contentW = (maxX - minX).coerceAtLeast(0.01f)
        val contentH = (maxY - minY).coerceAtLeast(0.01f)
        val availW = 1f - PAD * 2
        val availH = 1f - PAD * 2
        val scale = min(1f, min(availW / contentW, availH / contentH))

        val fitted =
            source.elements.map { el ->
                if (el.id == HeroElementId.Backdrop) {
                    el.copy(x = 0f, y = 0f, w = 1f, h = 1f)
                } else if (!el.visible) {
                    el
                } else {
                    var (w, h) = sized(el, aspect)
                    w *= scale
                    h *= scale
                    var x = PAD + (el.x - minX) * scale
                    var y = PAD + (el.y - minY) * scale
                    val mins = minSize(el.id, aspect)
                    w = max(w, mins.first)
                    h = max(h, mins.second)
                    if (el.w == 0f && el.id.isTextLike()) {
                        w = w.coerceAtMost(MAX_TEXT_W)
                    }
                    if (x + w > 1f - PAD) x = (1f - PAD - w).coerceAtLeast(PAD)
                    if (y + h > 1f - PAD) y = (1f - PAD - h).coerceAtLeast(PAD)
                    x = x.coerceIn(PAD, 1f - PAD)
                    y = y.coerceIn(PAD, 1f - PAD)
                    el.copy(x = x, y = y, w = if (el.w == 0f) 0f else w, h = if (el.h == 0f) 0f else h)
                }
            }
        return source.copy(elements = fitted)
    }

    fun mirrorStartEdge(layout: HeroLayout): HeroLayout =
        layout.copy(
            elements =
                layout.elements.map { el ->
                    if (el.id == HeroElementId.Backdrop) {
                        el
                    } else {
                        val w = if (el.w > 0f) el.w else 0.2f
                        el.copy(x = (1f - el.x - w).coerceIn(0f, 1f))
                    }
                },
        )

    private fun sized(
        el: HeroElementLayout,
        canvasAspect: Float,
    ): Pair<Float, Float> {
        if (el.w > 0f && el.h > 0f) return el.w to el.h
        return when (el.id) {
            HeroElementId.Cover -> {
                // Prefer 3:4 in normalized canvas space.
                val h = if (el.h > 0f) el.h else 0.70f
                val w = h / canvasAspect * (3f / 4f)
                (if (el.w > 0f) el.w else w) to h
            }

            HeroElementId.Logo -> {
                val w = if (el.w > 0f) el.w else 0.40f
                val h = w * canvasAspect * (9f / 16f)
                w to (if (el.h > 0f) el.h else h)
            }

            HeroElementId.PlatformIcon -> {
                0.06f to 0.08f
            }

            HeroElementId.Title -> {
                (el.w.takeIf { it > 0f } ?: 0.50f) to (el.h.takeIf { it > 0f } ?: 0.10f)
            }

            else -> {
                (el.w.takeIf { it > 0f } ?: 0.40f) to (el.h.takeIf { it > 0f } ?: 0.08f)
            }
        }
    }

    private fun minSize(
        id: HeroElementId,
        canvasAspect: Float,
    ): Pair<Float, Float> =
        when (id) {
            HeroElementId.Cover -> {
                val h = MIN_COVER_H
                val w = max(MIN_COVER_W, h / canvasAspect * (3f / 4f))
                w to h
            }

            HeroElementId.Logo -> {
                MIN_LOGO_W to 0.08f
            }

            HeroElementId.Title -> {
                MIN_TEXT_W to 0.06f
            }

            else -> {
                0.08f to 0.05f
            }
        }

    private fun HeroElementId.isTextLike(): Boolean =
        this == HeroElementId.Title ||
            this == HeroElementId.Metadata ||
            this == HeroElementId.Description ||
            this == HeroElementId.PlayStats ||
            this == HeroElementId.Platform ||
            this == HeroElementId.Favorite ||
            this == HeroElementId.SectionHint

    private fun mergeVisibility(
        template: List<HeroElementLayout>,
        visibilitySource: List<HeroElementLayout>,
    ): List<HeroElementLayout> {
        val vis = visibilitySource.associate { it.id to it.visible }
        return template.map { el -> el.copy(visible = vis[el.id] ?: el.visible) }
    }
}
