package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadHintButton
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Trailing Edit | Rescan (or similar) pair for a focused [GamepadSettingRow] dual-action row.
 * Buttons are touch-only ([GamepadButton.gamepadFocusable] = false); A/X stay on the parent row.
 * When [showHints] is true, face-button glyphs sit in each button's bottom-right corner.
 */
@Composable
fun GamepadSettingTrailingActions(
    secondaryLabel: String,
    onSecondaryClick: () -> Unit,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHints: Boolean = false,
    secondaryHint: GamepadHintButton = GamepadHintButton.X,
    primaryHint: GamepadHintButton = GamepadHintButton.A,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HintCornerButton(
            text = secondaryLabel,
            onClick = onSecondaryClick,
            hint = secondaryHint,
            showHint = showHints,
        )
        HintCornerButton(
            text = primaryLabel,
            onClick = onPrimaryClick,
            hint = primaryHint,
            showHint = showHints,
        )
    }
}

@Composable
private fun HintCornerButton(
    text: String,
    onClick: () -> Unit,
    hint: GamepadHintButton,
    showHint: Boolean,
) {
    Box {
        GamepadButton(
            text = text,
            onClick = onClick,
            outlined = true,
            gamepadFocusable = false,
        )
        if (showHint) {
            GamepadHintGlyph(
                button = hint,
                size = 14.dp,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 3.dp, bottom = 2.dp),
            )
        }
    }
}
