package com.wajiha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun WajihaSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.padding(
                    start = WajihaSpacing.md,
                    top = WajihaSpacing.md,
                    bottom = WajihaSpacing.sm,
                ),
        )
        content()
    }
}

@Composable
fun WajihaSectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = WajihaSpacing.sm),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}

@Composable
fun WajihaToolbar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backFocusable: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        com.wajiha.ui.components.gamepad.GamepadButton(
            text = "Back",
            onClick = onBack,
            outlined = true,
            gamepadFocusable = backFocusable,
            sound = null,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun WajihaPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(WajihaSpacing.md),
    ) {
        content()
    }
}
