package com.wajiha.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
actual fun VideoPreview(
    path: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player =
        remember(path) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(path))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                playWhenReady = true
                prepare()
            }
        }
    DisposableEffect(path) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
