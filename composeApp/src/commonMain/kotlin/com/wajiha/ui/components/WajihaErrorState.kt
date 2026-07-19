package com.wajiha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun WajihaErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(onRetry) {
        if (onRetry != null) {
            withFrameNanos { }
            try {
                retryFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(WajihaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.md),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            GamepadButton(
                text = "Retry",
                onClick = onRetry,
                focusRequester = retryFocus,
                focusId = "error:retry",
            )
        }
    }
}
