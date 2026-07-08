package com.wajiha.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun VideoPreview(path: String, modifier: Modifier) {
    // Video previews not supported on this platform yet
    Box(modifier = modifier.background(Color.Black))
}
