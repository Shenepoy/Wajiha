package com.wajiha.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object WajihaFocus {
    val borderWidth: Dp = 2.dp
    val selectedBorderWidth: Dp = 3.dp
    val selectedScale: Float = 1.02f

    @Composable
    fun borderColor(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun selectedBorderColor(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun selectedBackground(): Color = MaterialTheme.colorScheme.primaryContainer

    @Composable
    fun selectedContentColor(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    fun pressedOverlay(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
}
