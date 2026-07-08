package com.wajiha.ui.secondary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import com.wajiha.state.SecondaryMode

/**
 * Compact pill overlay centered along the bottom edge of the secondary display.
 * Shown when a gaming app is active but the user is browsing the launcher.
 */
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
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.key == Key.ButtonA)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(end = 6.dp)
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
