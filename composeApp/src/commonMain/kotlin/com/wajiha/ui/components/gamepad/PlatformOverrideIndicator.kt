package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaElevation
import com.wajiha.ui.theme.WajihaIconSize
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val HintAutoDismissMs = 2_500L

/**
 * Subtle yellow rectangle shown when a per-platform setting differs from the global default.
 * Tap or focus reveals a short hint; auto-dismisses after a few seconds, or on tap/outside/focus loss.
 */
@Composable
fun PlatformOverrideIndicator(
    hint: String = "Changed from global default",
    modifier: Modifier = Modifier,
) {
    var showHint by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(showHint) {
        if (showHint) {
            delay(HintAutoDismissMs)
            showHint = false
        }
    }

    Box(
        modifier =
            modifier
                .size(width = WajihaSpacing.md - WajihaSpacing.xs, height = WajihaSpacing.xs + WajihaSpacing.folderEdge)
                .clip(WajihaShapes.focus)
                .background(WajihaColors.OverrideAmber.copy(alpha = 0.82f))
                .focusable()
                .wajihaGamepadFocus()
                .onFocusChanged { state ->
                    showHint = state.isFocused
                }.pointerInput(hint) {
                    detectTapGestures(
                        onTap = { showHint = !showHint },
                        onLongPress = { showHint = true },
                    )
                },
    )

    if (showHint) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(0, with(density) { WajihaIconSize.sm.roundToPx() }),
            onDismissRequest = { showHint = false },
            properties = PopupProperties(focusable = false),
        ) {
            Surface(
                shape = WajihaShapes.chip,
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = WajihaElevation.overlay,
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier =
                        Modifier.padding(
                            horizontal = WajihaSpacing.sm,
                            vertical = WajihaSpacing.xs,
                        ),
                )
            }
        }
    }
}
