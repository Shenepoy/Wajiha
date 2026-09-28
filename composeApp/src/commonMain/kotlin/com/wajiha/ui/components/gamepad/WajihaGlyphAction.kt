package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.wajiha.input.GamepadHintButton
import com.wajiha.platform.UiSound
import com.wajiha.ui.theme.WajihaIconSize
import com.wajiha.ui.theme.WajihaSpacing

/**
 * The one glyph-plus-label control.
 *
 * Hint rows ([com.wajiha.ui.components.gamepad.GamepadActionBar]) pass no
 * [onClick]. Corner and dialog actions pass [onClick] and render a
 * [GamepadButton] beside the glyph. Wide menus keep in-page glyphs on the
 * hint bar. Page-to-page actions use this control in the screen corners.
 */
@Composable
fun WajihaGlyphAction(
    button: GamepadHintButton,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glyphAtEnd: Boolean = false,
    enabled: Boolean = true,
    outlined: Boolean = true,
    sound: UiSound? = if (onClick == null) null else UiSound.Open,
    labelColor: Color? = null,
    focusRequester: FocusRequester? = null,
    focusId: Any? = null,
    gamepadFocusable: Boolean = true,
) {
    val resolvedLabelColor =
        labelColor
            ?: if (onClick == null || !enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        if (!glyphAtEnd) {
            GamepadHintGlyph(button = button, size = WajihaIconSize.md)
        }
        if (onClick != null) {
            GamepadButton(
                text = label,
                onClick = onClick,
                outlined = outlined,
                enabled = enabled,
                sound = sound,
                focusRequester = focusRequester,
                focusId = focusId,
                gamepadFocusable = gamepadFocusable,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = resolvedLabelColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (glyphAtEnd) {
            GamepadHintGlyph(button = button, size = WajihaIconSize.md)
        }
    }
}
