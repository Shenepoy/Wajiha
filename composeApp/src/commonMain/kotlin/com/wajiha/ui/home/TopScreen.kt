package com.wajiha.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.platform.SystemControls
import com.wajiha.state.HeroContext
import com.wajiha.ui.gamedetail.GameDetailMetadataPanel
import com.wajiha.ui.gamedetail.GameDetailViewModel
import com.wajiha.ui.gamedetail.MetadataPanelStyle
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaMotion
import org.koin.compose.koinInject

/**
 * Top screen (3DS style): hero/preview of the focused game — backdrop,
 * logo overlay, metadata, play stats. Idle state when nothing is focused.
 */
@Composable
fun TopScreen(
    focused: GameTile?,
    platformName: String?,
    heroContext: HeroContext = HeroContext.GameLibrary,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.onBackground.luminance() > 0.5f
    val frameTop = if (isDark) WajihaColors.ScreenFrame else WajihaColors.ScreenFrameLight
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(frameTop, scheme.background)
                )
            )
    ) {
        AnimatedContent(
            targetState = heroContext,
            contentKey = { it.transitionKey },
            transitionSpec = {
                fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
            },
            label = "hero-context"
        ) { context ->
            when (context) {
                is HeroContext.GameLibrary -> GameLibraryHero(focused, platformName)
                is HeroContext.Settings -> SettingsHero(context)
                is HeroContext.Apps -> AppsHero(context)
                is HeroContext.System -> SystemHero(context)
                is HeroContext.GameDetail -> GameDetailHero(context.gameId)
            }
        }
    }
}

@Composable
private fun GameLibraryHero(focused: GameTile?, platformName: String?) {
    AnimatedContent(
        targetState = focused != null,
        transitionSpec = {
            fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
        },
        label = "hero-mode"
    ) { hasFocus ->
        if (!hasFocus) {
            IdleHero()
        } else {
            GameHero(requireNotNull(focused), platformName)
        }
    }
}

@Composable
private fun IdleHero() {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Wajiha",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary
            )
            Text(
                text = "Pick a game on the touch screen",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ContextHeroFrame(
    title: String,
    subtitle: String? = null,
    hint: String? = null,
    accent: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val accentColor = accent ?: scheme.primary
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            scheme.background.copy(alpha = 0.95f)
                        ),
                        radius = 900f
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onBackground,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsHero(context: HeroContext.Settings) {
    ContextHeroFrame(
        title = "Wajiha Settings",
        subtitle = context.sectionLabel,
        hint = "Settings open on the bottom screen  ·  L1 / R1 switch sections"
    )
}

@Composable
private fun GameDetailHero(gameId: Long) {
    val viewModel = koinInject<GameDetailViewModel>()
    LaunchedEffect(gameId) { viewModel.open(gameId) }
    val state by viewModel.uiState.collectAsState()
    val game = state.game
    val scheme = MaterialTheme.colorScheme
    val boxartPath = state.media.firstOrNull { it.type == "boxart" }?.localPath
    val heroPath = state.media.firstOrNull { it.type == "hero" }?.localPath

    if (game == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        heroPath?.let { backdrop ->
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                scheme.background.copy(alpha = 0.55f),
                                scheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
                color = scheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (boxartPath != null) {
                        AsyncImage(
                            model = boxartPath,
                            contentDescription = game.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = game.displayName.take(2).uppercase(),
                            style = MaterialTheme.typography.displayLarge,
                            color = scheme.primary
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = scheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                state.platform?.name?.let { platform ->
                    Text(
                        text = platform,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (game.favorite) {
                    Text(
                        text = "★ Favorite",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                GameDetailMetadataPanel(
                    game = game,
                    platformName = state.platform?.name,
                    totalPlaytimeSec = state.totalPlaytimeSec,
                    style = MetadataPanelStyle.Full,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AppsHero(context: HeroContext.Apps) {
    val countLine = when (context.appCount) {
        0 -> "No apps found"
        1 -> "1 app installed"
        else -> "${context.appCount} apps installed"
    }
    ContextHeroFrame(
        title = "Apps",
        subtitle = context.focusedLabel ?: countLine,
        hint = context.focusedLabel?.let { countLine }
            ?: "A to launch  ·  B back to games"
    )
}

@Composable
private fun SystemHero(context: HeroContext.System) {
    val controls = koinInject<SystemControls>()
    val liveStatus by controls.status.collectAsState()
    val battery = if (liveStatus.batteryPercent >= 0) {
        liveStatus.batteryPercent
    } else {
        context.batteryPercent
    }
    val charging = liveStatus.charging || context.charging
    val wifiOn = liveStatus.wifiEnabled || context.wifiEnabled
    val statusLine = buildList {
        if (battery >= 0) {
            add(
                buildString {
                    append("Battery $battery%")
                    if (charging) append(" ⚡")
                }
            )
        }
        add(if (wifiOn) "Wi‑Fi on" else "Wi‑Fi off")
    }.joinToString("  ·  ")
    ContextHeroFrame(
        title = "System",
        subtitle = statusLine.ifBlank { "Quick settings" },
        hint = "Brightness, volume, and device controls below",
        accent = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun HeroArtworkTransition(
    tile: GameTile,
    modifier: Modifier = Modifier,
    content: @Composable (GameTile) -> Unit
) {
    AnimatedContent(
        targetState = tile,
        modifier = modifier,
        contentKey = { it.game.id },
        transitionSpec = {
            (fadeIn(WajihaMotion.fadeInSpec()) +
                slideInHorizontally(WajihaMotion.fadeInSpec()) { it / 24 })
                .togetherWith(
                    fadeOut(WajihaMotion.fadeOutSpec()) +
                        slideOutHorizontally(WajihaMotion.fadeOutSpec()) { -it / 24 }
                )
        },
        label = "hero-artwork"
    ) { current ->
        content(current)
    }
}

@Composable
private fun HeroMetadataTransition(
    tile: GameTile,
    modifier: Modifier = Modifier,
    content: @Composable (GameTile) -> Unit
) {
    AnimatedContent(
        targetState = tile,
        modifier = modifier,
        contentKey = { it.game.id },
        transitionSpec = {
            fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
        },
        label = "hero-metadata"
    ) { current ->
        content(current)
    }
}

@Composable
private fun GameHero(tile: GameTile, platformName: String?) {
    val scheme = MaterialTheme.colorScheme
    val backdrop = tile.heroPath ?: tile.boxartPath
    Box(modifier = Modifier.fillMaxSize()) {
        HeroArtworkTransition(
            tile = tile,
            modifier = Modifier.fillMaxSize()
        ) { current ->
            val currentBackdrop = current.heroPath ?: current.boxartPath
            if (currentBackdrop != null) {
                AsyncImage(
                    model = currentBackdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                scheme.background.copy(alpha = 0.55f),
                                scheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
                color = scheme.surfaceVariant
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HeroArtworkTransition(
                        tile = tile,
                        modifier = Modifier.fillMaxSize()
                    ) { current ->
                        when {
                            current.videoPath != null -> {
                                VideoPreview(
                                    path = current.videoPath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            current.boxartPath != null -> {
                                AsyncImage(
                                    model = current.boxartPath,
                                    contentDescription = current.game.displayName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = current.game.displayName.take(2).uppercase(),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = scheme.primary
                                    )
                                }
                            }
                        }
                    }
                    HeroArtworkTransition(
                        tile = tile,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.85f)
                            .padding(12.dp)
                    ) { current ->
                        current.logoPath?.let { logo ->
                            AsyncImage(
                                model = logo,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            HeroMetadataTransition(
                tile = tile,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) { current ->
                GameHeroMetadata(current, platformName)
            }
        }
    }
}

@Composable
private fun GameHeroMetadata(tile: GameTile, platformName: String?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            tile.iconPath?.let { icon ->
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            platformName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
            }
        }
        Text(
            text = tile.game.displayName,
            style = MaterialTheme.typography.headlineLarge,
            color = scheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        val meta = listOfNotNull(
            tile.game.developer,
            tile.game.releaseDate?.take(4),
            tile.game.genre,
            tile.game.region?.uppercase(),
            tile.game.ageRating
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        tile.game.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (tile.game.playCount > 0) {
            Text(
                text = "Played ${tile.game.playCount}×",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.tertiary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
