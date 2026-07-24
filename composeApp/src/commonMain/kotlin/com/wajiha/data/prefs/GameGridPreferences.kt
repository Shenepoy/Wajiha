package com.wajiha.data.prefs

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * Game library grid art style and density.
 *
 * Prefs are stored per physical display (primary / secondary), mirroring
 * [AppDrawerGridPreferences]. [resolve] clamps rows against measured viewport height.
 */
object GameGridPreferences {
    const val ART_COVER = "cover"
    const val ART_ICON = "icon"
    const val ART_LOGO = "logo"

    const val TILE_SMALL = "small"
    const val TILE_MEDIUM = "medium"
    const val TILE_LARGE = "large"

    const val DEFAULT_ART = ART_COVER
    const val DEFAULT_ROWS = 2
    const val DEFAULT_TILE_SIZE = TILE_MEDIUM

    const val ABS_MIN_ROWS = 2
    const val ABS_MAX_ROWS = 5

    val artStyles = listOf(ART_COVER, ART_ICON, ART_LOGO)
    val tileSizes = listOf(TILE_SMALL, TILE_MEDIUM, TILE_LARGE)

    /** Matches BottomScreen LazyHorizontalGrid verticalArrangement spacedBy(md). */
    val ItemSpacing = 16.dp

    /** Matches BottomScreen contentPadding top(md+xs) + bottom(xl) ≈ 52.dp. */
    val ContentPadVertical = 52.dp
    val MinTileHeight = 72.dp

    data class Slot(
        val art: String = DEFAULT_ART,
        val rows: Int = DEFAULT_ROWS,
        val tileSize: String = DEFAULT_TILE_SIZE,
        val configured: Boolean = false,
    )

    data class Resolved(
        val art: String,
        val rows: Int,
        val tileSize: String,
        val aspectRatio: Float,
        val contentScale: ContentScale,
        val sizeFactor: Float,
    )

    fun normalizeArt(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_ART }
        return artStyles.firstOrNull { it.equals(raw, ignoreCase = true) } ?: DEFAULT_ART
    }

    fun normalizeRows(value: Int?): Int = (value ?: DEFAULT_ROWS).coerceIn(ABS_MIN_ROWS, ABS_MAX_ROWS)

    fun normalizeTileSize(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_TILE_SIZE }
        return tileSizes.firstOrNull { it.equals(raw, ignoreCase = true) } ?: DEFAULT_TILE_SIZE
    }

    fun artLabel(value: String): String =
        when (normalizeArt(value)) {
            ART_ICON -> "Icon"
            ART_LOGO -> "Logo"
            else -> "Cover"
        }

    fun tileSizeLabel(value: String): String =
        when (normalizeTileSize(value)) {
            TILE_SMALL -> "S"
            TILE_LARGE -> "L"
            else -> "M"
        }

    fun tileSizeTitle(value: String): String =
        when (normalizeTileSize(value)) {
            TILE_SMALL -> "Small"
            TILE_LARGE -> "Large"
            else -> "Medium"
        }

    fun aspectRatio(art: String): Float =
        when (normalizeArt(art)) {
            ART_ICON -> 1f
            ART_LOGO -> 16f / 9f
            else -> 3f / 4f
        }

    fun contentScale(art: String): ContentScale =
        when (normalizeArt(art)) {
            ART_COVER -> ContentScale.Crop
            else -> ContentScale.Fit
        }

    /**
     * Fraction of the LazyHorizontalGrid cell height the tile fills.
     * Must stay ≤ 1 — Compose [fillMaxHeight] clamps above 1, so a "large"
     * factor of 1.15 was a no-op and collapsed Large into Medium.
     */
    fun sizeFactor(tileSize: String): Float =
        when (normalizeTileSize(tileSize)) {
            TILE_SMALL -> 0.78f
            TILE_LARGE -> 1f
            else -> 0.90f
        }

    fun slotFor(
        settings: AppSettings,
        secondaryActivity: Boolean,
    ): Slot =
        if (secondaryActivity) {
            Slot(
                art = settings.gameGridSecondaryArt,
                rows = settings.gameGridSecondaryRows,
                tileSize = settings.gameGridSecondaryTileSize,
                configured = settings.gameGridSecondaryConfigured,
            )
        } else {
            Slot(
                art = settings.gameGridArt,
                rows = settings.gridRows,
                tileSize = settings.gameGridTileSize,
                configured = settings.gameGridConfigured,
            )
        }

    /**
     * Clamp preferred rows so tiles stay above [MinTileHeight] after size factor.
     * Returns a [Resolved] ready for [BottomScreen].
     */
    fun resolve(
        slot: Slot,
        availableHeight: Dp,
    ): Resolved {
        val art = normalizeArt(slot.art)
        val tileSize = normalizeTileSize(slot.tileSize)
        val factor = sizeFactor(tileSize)
        val preferredRows = normalizeRows(slot.rows)
        val heightPx = availableHeight.value
        val spacing = ContentPadVertical.value + ItemSpacing.value * (preferredRows - 1).coerceAtLeast(0)
        val usable = (heightPx - spacing).coerceAtLeast(0f)
        val minCell = MinTileHeight.value * factor
        val maxRowsByHeight =
            if (usable <= 0f || minCell <= 0f) {
                ABS_MIN_ROWS
            } else {
                floor(usable / minCell).toInt().coerceIn(ABS_MIN_ROWS, ABS_MAX_ROWS)
            }
        val rows = preferredRows.coerceAtMost(maxRowsByHeight).coerceIn(ABS_MIN_ROWS, ABS_MAX_ROWS)
        return Resolved(
            art = art,
            rows = rows,
            tileSize = tileSize,
            aspectRatio = aspectRatio(art),
            contentScale = contentScale(art),
            sizeFactor = factor,
        )
    }

    /** Art path for a library tile given preferred style. */
    fun resolveArtPath(
        art: String,
        boxartPath: String?,
        iconPath: String?,
        logoPath: String?,
    ): String? =
        when (normalizeArt(art)) {
            ART_ICON -> iconPath ?: boxartPath ?: logoPath
            ART_LOGO -> logoPath ?: boxartPath ?: iconPath
            else -> boxartPath ?: iconPath ?: logoPath
        }

    fun resolveSessionArtPath(
        art: String,
        boxartPath: String?,
        iconPath: String?,
        logoPath: String?,
    ): String? = resolveArtPath(art, boxartPath, iconPath, logoPath)
}
