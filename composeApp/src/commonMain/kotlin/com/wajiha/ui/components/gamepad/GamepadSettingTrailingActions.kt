package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadHintButton
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

private const val DisabledContentAlpha = 0.38f

/**
 * Trailing Edit | Rescan (or similar) pair for a focused [GamepadSettingRow] dual-action row.
 * Buttons are touch-only; A/X stay on the parent row.
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
        GamepadHintCornerButton(
            text = secondaryLabel,
            onClick = onSecondaryClick,
            hint = secondaryHint,
            showHint = showHints,
            gamepadFocusable = false,
        )
        GamepadHintCornerButton(
            text = primaryLabel,
            onClick = onPrimaryClick,
            hint = primaryHint,
            showHint = showHints,
            gamepadFocusable = false,
        )
    }
}

/**
 * Fixed-width trailing settings action (Add / Rescan / Open / Edit).
 *
 * Drawn as a sized outlined box — Material [GamepadButton] / OutlinedButton still
 * content-measures its label, so fillMaxSize wrappers never equalized short vs long
 * labels (Add vs Rescan).
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SettingsTrailingActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = true,
    gamepadFocusable: Boolean = false,
    focusId: Any? = null,
    width: Dp = SettingsTrailingActionWidth,
) {
    val shape = WajihaShapes.button
    val interactionSource = rememberPressInteractionSource()
    val feedback = LocalUiFeedback.current

    Box(
        modifier =
            modifier
                .width(width)
                .height(LocalSettingRowMinHeight.current)
                .clip(shape)
                .then(
                    if (outlined) {
                        Modifier.border(
                            border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    },
                ).alpha(if (enabled) 1f else DisabledContentAlpha)
                .wajihaPressedFeedback(interactionSource, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    feedback.confirm()
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (outlined) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Fixed-width [SettingsTrailingActionButton] with an optional [GamepadHintGlyph] corner. */
@Composable
fun GamepadHintCornerButton(
    text: String,
    onClick: () -> Unit,
    hint: GamepadHintButton,
    modifier: Modifier = Modifier,
    showHint: Boolean = true,
    outlined: Boolean = true,
    gamepadFocusable: Boolean = true,
    focusId: Any? = null,
) {
    Box(modifier = modifier) {
        SettingsTrailingActionButton(
            text = text,
            onClick = onClick,
            outlined = outlined,
            gamepadFocusable = gamepadFocusable,
            focusId = focusId,
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
