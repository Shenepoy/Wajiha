package com.wajiha.data.prefs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * Apps drawer grid density: columns, target visible rows, and icon size tier.
 *
 * Absolute prefs ranges are wide; [resolve] clamps to what fits on the **current**
 * display (top vs bottom / different dp sizes) using measured grid constraints.
 *
 * The grid always fills the available width. Icon size is a fraction of the column
 * slot ([ICON_SMALL] / [ICON_MEDIUM] / [ICON_LARGE]), then capped so roughly
 * [preferredRows] rows fit.
 */
object AppDrawerGridPreferences {
    const val ICON_SMALL = "small"
    const val ICON_MEDIUM = "medium"
    const val ICON_LARGE = "large"

    const val ORIENTATION_VERTICAL = "vertical"
    const val ORIENTATION_HORIZONTAL = "horizontal"

    const val SCROLL_CONTINUOUS = "continuous"
    const val SCROLL_PAGES = "pages"

    const val DEFAULT_COLUMNS = 4
    const val DEFAULT_ROWS = 3
    const val DEFAULT_ICON_SIZE = ICON_MEDIUM
    const val DEFAULT_ORIENTATION = ORIENTATION_VERTICAL
    const val DEFAULT_SCROLL_MODE = SCROLL_CONTINUOUS
    const val DEFAULT_SHOW_LABELS = true

    const val ABS_MIN_COLUMNS = 2
    const val ABS_MAX_COLUMNS = 8
    const val ABS_MIN_ROWS = 2
    const val ABS_MAX_ROWS = 6

    val iconSizes = listOf(ICON_SMALL, ICON_MEDIUM, ICON_LARGE)
    val orientations = listOf(ORIENTATION_VERTICAL, ORIENTATION_HORIZONTAL)
    val scrollModes = listOf(SCROLL_CONTINUOUS, SCROLL_PAGES)

    val MinIconSize = 40.dp
    val LabelLine = 16.dp
    val LabelGap = 6.dp

    /** Horizontal padding inside each tile (both sides combined). */
    val TilePadHorizontal = 8.dp

    /** Vertical padding inside each tile (both sides combined) — air for the focus ring. */
    val TilePadVertical = 20.dp

    /** Keep edge inset minimal so the grid reads as full-bleed on wide panes. */
    val ContentPadHorizontal = 4.dp
    val ContentPadVertical = 6.dp
    val ItemSpacing = 4.dp
    val OuterTileGutter = 2.dp

    /** Minimum cell size used when computing max columns/rows. */
    val MinCellWidth: Dp = MinIconSize + TilePadHorizontal + OuterTileGutter * 2

    fun minCellHeight(showLabels: Boolean): Dp =
        MinIconSize +
            (if (showLabels) LabelLine + LabelGap else 0.dp) +
            TilePadVertical +
            OuterTileGutter * 2

    val MinCellHeight: Dp = minCellHeight(showLabels = true)

    fun normalizeColumns(value: Int?): Int = (value ?: DEFAULT_COLUMNS).coerceIn(ABS_MIN_COLUMNS, ABS_MAX_COLUMNS)

    fun normalizeRows(value: Int?): Int = (value ?: DEFAULT_ROWS).coerceIn(ABS_MIN_ROWS, ABS_MAX_ROWS)

    fun normalizeIconSize(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_ICON_SIZE }
        return iconSizes.firstOrNull { it.equals(raw, ignoreCase = true) } ?: DEFAULT_ICON_SIZE
    }

    fun iconSizeLabel(value: String): String =
        when (normalizeIconSize(value)) {
            ICON_SMALL -> "S"
            ICON_LARGE -> "L"
            else -> "M"
        }

    fun iconSizeTitle(value: String): String =
        when (normalizeIconSize(value)) {
            ICON_SMALL -> "Small"
            ICON_LARGE -> "Large"
            else -> "Medium"
        }

    fun normalizeOrientation(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_ORIENTATION }
        return orientations.firstOrNull { it.equals(raw, ignoreCase = true) } ?: DEFAULT_ORIENTATION
    }

    fun isHorizontal(value: String?): Boolean = normalizeOrientation(value) == ORIENTATION_HORIZONTAL

    fun orientationLabel(value: String): String =
        when (normalizeOrientation(value)) {
            ORIENTATION_HORIZONTAL -> "Horiz"
            else -> "Vert"
        }

    fun normalizeScrollMode(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_SCROLL_MODE }
        return scrollModes.firstOrNull { it.equals(raw, ignoreCase = true) } ?: DEFAULT_SCROLL_MODE
    }

    fun isContinuous(value: String?): Boolean = normalizeScrollMode(value) == SCROLL_CONTINUOUS

    fun isPages(value: String?): Boolean = normalizeScrollMode(value) == SCROLL_PAGES

    fun scrollModeLabel(value: String): String =
        when (normalizeScrollMode(value)) {
            SCROLL_PAGES -> "Pages"
            else -> "Scroll"
        }

    /** Fraction of the fitted cell slot used by the glyph (S/M/L must look distinct). */
    fun iconScale(value: String): Float =
        when (normalizeIconSize(value)) {
            ICON_SMALL -> 0.55f
            ICON_LARGE -> 1f
            else -> 0.78f
        }

    fun maxColumns(availableWidth: Dp): Int {
        val avail = (availableWidth - ContentPadHorizontal * 2).coerceAtLeast(MinCellWidth)
        val spacing = ItemSpacing
        val n = floor(((avail + spacing) / (MinCellWidth + spacing)).toDouble()).toInt()
        return n.coerceIn(ABS_MIN_COLUMNS, ABS_MAX_COLUMNS)
    }

    fun maxRows(
        availableHeight: Dp,
        showLabels: Boolean = true,
    ): Int {
        val minH = minCellHeight(showLabels)
        val avail = (availableHeight - ContentPadVertical * 2).coerceAtLeast(minH)
        val spacing = ItemSpacing
        val n = floor(((avail + spacing) / (minH + spacing)).toDouble()).toInt()
        return n.coerceIn(ABS_MIN_ROWS, ABS_MAX_ROWS)
    }

    data class Layout(
        val columns: Int,
        val rows: Int,
        val iconSize: Dp,
        /** Equal tile slot width (viewport ÷ columns). Needed so horizontal grids don't grow with label text. */
        val cellWidth: Dp,
        val hSpacing: Dp,
        val vSpacing: Dp,
        val contentPadHorizontal: Dp,
        val contentPadVertical: Dp,
        val tilePadHorizontal: Dp,
        val tilePadVertical: Dp,
        val maxColumns: Int,
        val maxRows: Int,
        val showLabels: Boolean,
    )

    /**
     * Clamp preferred density to the measured grid area.
     *
     * Grid fills [availableWidth]. Icon tier scales within the column slot; rows
     * cap height so roughly that many rows fit on screen.
     */
    fun resolve(
        availableWidth: Dp,
        availableHeight: Dp,
        preferredColumns: Int,
        preferredRows: Int,
        preferredIconSize: String = DEFAULT_ICON_SIZE,
        showLabels: Boolean = DEFAULT_SHOW_LABELS,
    ): Layout {
        val minH = minCellHeight(showLabels)
        val maxCols = maxColumns(availableWidth)
        val maxRowsCap = maxRows(availableHeight, showLabels)
        val columns = normalizeColumns(preferredColumns).coerceAtMost(maxCols)
        val rows = normalizeRows(preferredRows).coerceAtMost(maxRowsCap)
        val scale = iconScale(preferredIconSize)

        val hSpacing = ItemSpacing
        val vSpacing = ItemSpacing
        val padH = ContentPadHorizontal
        val padV = ContentPadVertical

        val innerW = (availableWidth - padH * 2).coerceAtLeast(MinCellWidth)
        val innerH = (availableHeight - padV * 2).coerceAtLeast(minH)

        val cellW = (innerW - hSpacing * (columns - 1)) / columns.toFloat()
        val cellH = (innerH - vSpacing * (rows - 1)) / rows.toFloat()

        val chromeW = TilePadHorizontal + OuterTileGutter * 2
        val labelChrome = if (showLabels) LabelLine + LabelGap else 0.dp
        val chromeH = TilePadVertical + labelChrome + OuterTileGutter * 2
        // Fit the cell first, then apply S/M/L scale — otherwise a height cap
        // flattens every tier to the same size.
        val maxFit =
            minOf(cellW - chromeW, cellH - chromeH)
                .coerceAtLeast(MinIconSize)
        val iconSize = (maxFit * scale).coerceAtLeast(28.dp)

        return Layout(
            columns = columns,
            rows = rows,
            iconSize = iconSize,
            cellWidth = cellW,
            hSpacing = hSpacing,
            vSpacing = vSpacing,
            contentPadHorizontal = padH,
            contentPadVertical = padV,
            tilePadHorizontal = TilePadHorizontal / 2,
            tilePadVertical = TilePadVertical / 2,
            maxColumns = maxCols,
            maxRows = maxRowsCap,
            showLabels = showLabels,
        )
    }

    /** Ordered favorite package names (CSV). Empty string → empty list. */
    fun parseFavoritePackages(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun serializeFavoritePackages(packages: List<String>): String =
        packages
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    /**
     * Favorites in saved order (skipping packages not currently installed),
     * then remaining apps in their existing order (already A–Z from the loader).
     */
    fun <T> orderWithFavorites(
        apps: List<T>,
        favoritePackages: List<String>,
        packageName: (T) -> String,
    ): List<T> {
        if (apps.isEmpty() || favoritePackages.isEmpty()) return apps
        val byPackage = apps.associateBy(packageName)
        val favoriteSet = LinkedHashSet<String>()
        val favorites =
            favoritePackages.mapNotNull { pkg ->
                byPackage[pkg]?.also { favoriteSet.add(pkg) }
            }
        if (favorites.isEmpty()) return apps
        val rest = apps.filter { packageName(it) !in favoriteSet }
        return favorites + rest
    }

    /** Visible favorite packages: saved order ∩ [installedPackages]. */
    fun visibleFavoritePackages(
        favoritePackages: List<String>,
        installedPackages: Collection<String>,
    ): List<String> {
        if (favoritePackages.isEmpty() || installedPackages.isEmpty()) return emptyList()
        val installed = installedPackages.toHashSet()
        return favoritePackages.filter { it in installed }.distinct()
    }

    fun canMoveFavoriteUp(
        packageName: String,
        favoritePackages: List<String>,
        installedPackages: Collection<String>,
    ): Boolean {
        val visible = visibleFavoritePackages(favoritePackages, installedPackages)
        val index = visible.indexOf(packageName)
        return index > 0
    }

    fun canMoveFavoriteDown(
        packageName: String,
        favoritePackages: List<String>,
        installedPackages: Collection<String>,
    ): Boolean {
        val visible = visibleFavoritePackages(favoritePackages, installedPackages)
        val index = visible.indexOf(packageName)
        return index >= 0 && index < visible.lastIndex
    }

    /**
     * Move [packageName] among currently installed favorites by [delta] (−1 / +1).
     * Rewrites the stored list to the new visible order (prunes uninstalled entries).
     */
    fun moveFavoriteAmongVisible(
        favoritePackages: List<String>,
        packageName: String,
        delta: Int,
        installedPackages: Collection<String>,
    ): List<String>? {
        if (delta == 0) return null
        val visible = visibleFavoritePackages(favoritePackages, installedPackages).toMutableList()
        val index = visible.indexOf(packageName)
        if (index < 0) return null
        val target = (index + delta).coerceIn(0, visible.lastIndex)
        if (target == index) return null
        val item = visible.removeAt(index)
        visible.add(target, item)
        return visible
    }
}
