package com.wajiha.ui.home.hero

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.HeroDisplaySlot
import com.wajiha.data.prefs.HeroElementId
import com.wajiha.data.prefs.HeroElementLayout
import com.wajiha.data.prefs.HeroLayout
import com.wajiha.data.prefs.HeroLayoutFitter
import com.wajiha.data.prefs.HeroLayoutPresets
import com.wajiha.ui.home.GameTile
import com.wajiha.ui.home.VideoPreview
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Free-form hero canvas driven by [HeroLayout].
 *
 * @param playCoverVideo library hero may play video; detail/editor should pass false.
 * @param detailContext when true, Favorite / SectionHint may render.
 * @param dimFreeform when true (settings-hero interactive), free-form content is dimmed for overlays.
 */
@Composable
fun HeroCanvas(
    tile: GameTile,
    platformName: String?,
    layout: HeroLayout,
    displaySlot: HeroDisplaySlot,
    playCoverVideo: Boolean,
    detailContext: Boolean,
    selectedElementId: HeroElementId? = null,
    dimFreeform: Boolean = false,
    sectionHint: String? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Callers (GameHero / GameDetailHero) already run resolveForPaint when needed.
        // Only mirror for RTL here to avoid a second auto-fit pass.
        val painted =
            remember(layout, rtl) {
                if (rtl) HeroLayoutFitter.mirrorStartEdge(layout) else layout
            }
        val canvasW = maxWidth
        val canvasH = maxHeight

        painted.element(HeroElementId.Backdrop)?.takeIf { it.visible }?.let {
            val backdrop = tile.heroPath ?: tile.boxartPath
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            scheme.background.copy(alpha = WajihaAlphas.surfaceMuted),
                                            scheme.background.copy(alpha = 0.92f),
                                        ),
                                ),
                            ),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (dimFreeform) {
                            Modifier.background(scheme.background.copy(alpha = 0.45f))
                        } else {
                            Modifier
                        },
                    ),
        ) {
            for (el in painted.elements) {
                if (!el.visible || el.id == HeroElementId.Backdrop) continue
                if (el.id.detailOnly && !detailContext) continue
                val boxMod =
                    elementModifier(
                        el = el,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        selected = selectedElementId == el.id,
                    )
                when (el.id) {
                    HeroElementId.Cover -> {
                        CoverElement(
                            tile = tile,
                            playVideo = playCoverVideo,
                            border = painted.coverBorder,
                            modifier = boxMod,
                        )
                    }

                    HeroElementId.Logo -> {
                        LogoElement(tile = tile, modifier = boxMod)
                    }

                    HeroElementId.PlatformIcon -> {
                        tile.iconPath?.let { path ->
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = boxMod.clip(WajihaShapes.tile),
                            )
                        }
                    }

                    HeroElementId.Platform -> {
                        platformName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = boxMod,
                            )
                        }
                    }

                    HeroElementId.Title -> {
                        Text(
                            text = tile.game.displayName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = scheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = boxMod,
                        )
                    }

                    HeroElementId.Metadata -> {
                        val meta =
                            listOfNotNull(
                                tile.game.developer,
                                tile.game.releaseDate?.take(4),
                                tile.game.genre,
                                tile.game.region?.uppercase(),
                                tile.game.ageRating,
                            ).joinToString("  ·  ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = boxMod,
                            )
                        }
                    }

                    HeroElementId.Description -> {
                        tile.game.description?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = boxMod,
                            )
                        }
                    }

                    HeroElementId.PlayStats -> {
                        if (tile.game.playCount > 0) {
                            Text(
                                text = "Played ${tile.game.playCount}×",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = boxMod,
                            )
                        }
                    }

                    HeroElementId.Favorite -> {
                        if (tile.game.favorite) {
                            Text(
                                text = "★ Favorite",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = boxMod,
                            )
                        }
                    }

                    HeroElementId.SectionHint -> {
                        sectionHint?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = boxMod,
                            )
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverElement(
    tile: GameTile,
    playVideo: Boolean,
    border: Boolean,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = WajihaShapes.heroInner
    Box(
        modifier =
            modifier
                .clip(shape)
                .then(
                    if (border) {
                        Modifier.border(
                            width = WajihaSpacing.folderEdge,
                            color = scheme.outline.copy(alpha = WajihaAlphas.outlineSubtle),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        when {
            playVideo && tile.videoPath != null -> {
                VideoPreview(path = tile.videoPath, modifier = Modifier.fillMaxSize())
            }

            tile.boxartPath != null -> {
                AsyncImage(
                    model = tile.boxartPath,
                    contentDescription = tile.game.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(scheme.surfaceVariant),
                ) {
                    Text(
                        text =
                            tile.game.displayName
                                .take(2)
                                .uppercase(),
                        style = MaterialTheme.typography.displayLarge,
                        color = scheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoElement(
    tile: GameTile,
    modifier: Modifier,
) {
    val path = tile.logoPath ?: return
    Box(
        modifier =
            modifier.background(
                Color.Black.copy(alpha = 0.25f),
                shape = WajihaShapes.tile,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = path,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(WajihaSpacing.sm),
        )
    }
}

@Composable
private fun elementModifier(
    el: HeroElementLayout,
    canvasW: androidx.compose.ui.unit.Dp,
    canvasH: androidx.compose.ui.unit.Dp,
    selected: Boolean,
): Modifier {
    val wFrac = if (el.w > 0f) el.w else defaultWidth(el.id)
    val hFrac = if (el.h > 0f) el.h else defaultHeight(el.id)
    val x = el.x.coerceIn(0f, 1f)
    val y = el.y.coerceIn(0f, 1f)
    return Modifier
        .offset(x = canvasW * x, y = canvasH * y)
        .size(width = canvasW * wFrac, height = canvasH * hFrac)
        .then(
            if (selected) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = WajihaShapes.tile,
                )
            } else {
                Modifier
            },
        )
}

private fun defaultWidth(id: HeroElementId): Float =
    when (id) {
        HeroElementId.Cover -> 0.28f
        HeroElementId.Logo -> 0.30f
        HeroElementId.PlatformIcon -> 0.06f
        HeroElementId.Title -> 0.50f
        else -> 0.40f
    }

private fun defaultHeight(id: HeroElementId): Float =
    when (id) {
        HeroElementId.Cover -> 0.70f
        HeroElementId.Logo -> 0.16f
        HeroElementId.PlatformIcon -> 0.08f
        HeroElementId.Title -> 0.12f
        HeroElementId.Description -> 0.18f
        else -> 0.08f
    }

/** Build a paint layout from settings for [slot], applying auto-fit when unconfigured. */
fun resolveHeroLayout(
    settings: AppSettings,
    slot: HeroDisplaySlot,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    rtl: Boolean,
): HeroLayout =
    HeroLayoutFitter.resolveForPaint(
        bundle = settings.heroLayoutBundle,
        slot = slot,
        canvasWidthPx = canvasWidthPx,
        canvasHeightPx = canvasHeightPx,
        rtl = rtl,
    )

fun heroLayoutOrClassic(
    settings: AppSettings,
    slot: HeroDisplaySlot,
): HeroLayout =
    settings.heroLayoutBundle.slot(slot).takeIf { it.elements.isNotEmpty() }
        ?: HeroLayoutPresets.classic()
