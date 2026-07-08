package com.wajiha.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Inline video preview for the top screen. Android actual uses media3
 * ExoPlayer; plays the scraped preview video (muted, looping) when present.
 */
@Composable
expect fun VideoPreview(path: String, modifier: Modifier)
