package com.wajiha.ui.secondary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import com.wajiha.state.SecondaryMode
import com.wajiha.ui.components.gamepad.GamepadFocusable
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/** Sits just above the gamepad action bar (~36dp) with a tight gap. */
internal val NowPlayingOverlayBottomPadding = 36.dp

/** Bottom-right anchor above gamepad action bar hints. */
fun BoxScope.nowPlayingOverlayPlacement(modifier: Modifier = Modifier): Modifier =
    modifier
        .align(Alignment.BottomEnd)
        .padding(end = WajihaSpacing.md, bottom = NowPlayingOverlayBottomPadding)

/** Chip is only useful on the games grid — not on Now Running or sibling mode tabs. */
internal fun shouldShowNowPlayingOverlay(mode: SecondaryMode): Boolean =
    mode == SecondaryMode.GameGrid

@Composable
fun NowPlayingOverlay(
    store: DualScreenStore,
    modifier: Modifier = Modifier,
    hiddenByGameplayDim: Boolean = false
) {
    val nowPlaying by store.nowPlayingUiState.collectAsState()
    val mode by store.secondaryMode.collectAsState()

    val show = nowPlaying != null &&
        shouldShowNowPlayingOverlay(mode) &&
        !hiddenByGameplayDim
    if (!show) return

    key(nowPlaying!!.packageName) {
        NowPlayingOverlayChip(
            state = nowPlaying!!,
            onClick = { store.setSecondaryMode(SecondaryMode.NowPlaying) },
            modifier = modifier
        )
    }
}

@Composable
private fun NowPlayingOverlayChip(
    state: NowPlayingState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = state.gameName ?: state.appLabel ?: state.packageName
    val scheme = MaterialTheme.colorScheme
    val chipShape = WajihaShapes.chip

    GamepadFocusable(
        onClick = onClick,
        shape = chipShape,
        modifier = modifier
            .defaultMinSize(minHeight = WajihaSpacing.touchMin)
            .widthIn(max = 300.dp)
            .clip(chipShape)
            .background(scheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = scheme.outline.copy(alpha = 0.3f),
                shape = chipShape
            )
            .padding(horizontal = WajihaSpacing.md, vertical = WajihaSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = WajihaSpacing.sm)
        ) {
            if (state.boxartPath != null) {
                AsyncImage(
                    model = state.boxartPath,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(WajihaShapes.tile),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 26.dp)
                        .clip(WajihaShapes.chip)
                        .background(scheme.primary.copy(alpha = 0.85f))
                )
            }
            Spacer(modifier = Modifier.size(WajihaSpacing.md))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
