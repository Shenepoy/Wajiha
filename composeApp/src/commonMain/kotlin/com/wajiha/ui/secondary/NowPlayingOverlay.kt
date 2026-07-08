package com.wajiha.ui.secondary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import com.wajiha.state.SecondaryMode
import com.wajiha.ui.components.gamepad.GamepadFocusable
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun NowPlayingOverlay(
    store: DualScreenStore,
    modifier: Modifier = Modifier
) {
    val nowPlaying by store.nowPlaying.collectAsState()
    val mode by store.secondaryMode.collectAsState()

    val show = nowPlaying != null &&
        mode != SecondaryMode.NowPlaying &&
        mode != SecondaryMode.Off

    if (!show) return

    key(nowPlaying!!.packageName) {
        NowPlayingOverlayButton(
            state = nowPlaying!!,
            onClick = { store.setSecondaryMode(SecondaryMode.NowPlaying) },
            modifier = modifier
        )
    }
}

@Composable
private fun NowPlayingOverlayButton(
    state: NowPlayingState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = state.gameName ?: state.appLabel ?: state.packageName

    GamepadFocusable(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = WajihaSpacing.sm + WajihaSpacing.xs, vertical = WajihaSpacing.sm)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(end = WajihaSpacing.xs)
            )
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(end = WajihaSpacing.xs)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
