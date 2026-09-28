package com.wajiha.ui.secondary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.state.NowPlayingState
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun NowPlayingPanel(
    state: NowPlayingState?,
    modifier: Modifier = Modifier,
    heroBackground: Boolean = true,
    useLogo: Boolean = true,
    backgroundInParent: Boolean = false,
) {
    val label = sessionDisplayLabel(state)
    val backdrop = state?.heroPath ?: state?.boxartPath
    val showLogo = useLogo && state?.logoPath != null
    val content: @Composable () -> Unit = {
        NowPlayingPanelContent(
            state = state,
            label = label,
            showLogo = showLogo,
            showBoxartThumb = !heroBackground || backdrop == null,
            onHero = heroBackground && backdrop != null,
        )
    }

    if (heroBackground && backdrop != null && backgroundInParent) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    } else if (heroBackground && backdrop != null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            NowPlayingBackdrop(state = state)
            content()
        }
    } else {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
fun NowPlayingBackdrop(
    state: NowPlayingState?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val backdrop = state?.heroPath ?: state?.boxartPath
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (backdrop == null) {
                        Modifier.background(scheme.background)
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Light vignette — keep hero art readable without burying it under
            // an opaque Material fill. Darker only at the bottom for chrome/text.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.18f),
                                        Color.Black.copy(alpha = 0.32f),
                                        Color.Black.copy(alpha = 0.55f),
                                    ),
                            ),
                        ),
            )
        }
    }
}

@Composable
private fun NowPlayingPanelContent(
    state: NowPlayingState?,
    label: String?,
    showLogo: Boolean,
    showBoxartThumb: Boolean,
    onHero: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val titleColor = if (onHero) Color.White else scheme.onBackground
    val eyebrowColor = if (onHero) Color.White.copy(alpha = 0.92f) else scheme.primary
    val metaColor = if (onHero) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant
    Column(
        modifier = Modifier.padding(WajihaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showBoxartThumb && state?.boxartPath != null) {
            AsyncImage(
                model = state.boxartPath,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(120.dp)
                        .clip(WajihaShapes.card),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(WajihaSpacing.md))
        }
        Text(
            text = "Now Running",
            style = MaterialTheme.typography.labelLarge,
            color = eyebrowColor,
        )
        Spacer(modifier = Modifier.height(WajihaSpacing.sm))
        if (showLogo) {
            AsyncImage(
                model = state?.logoPath,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth(0.7f)
                        .heightIn(max = 96.dp),
            )
        } else {
            Text(
                text = label ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state?.platformId != null) {
            Spacer(modifier = Modifier.height(WajihaSpacing.xs))
            Text(
                text = state.platformId.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = metaColor,
            )
        }
    }
}
