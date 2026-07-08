package com.wajiha.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.FocusIndicatorPreferenceValues

/** Where the focus ring stroke sits relative to the focused item bounds. */
enum class FocusPlacement {
    Inside,
    Outside
}

/** Visual style for Wajiha gamepad focus chrome. */
enum class FocusBorderStyle {
    Solid,
    Dotted,
    Dashed,
    MarchingAnts,
    Pulsing,
    Double,
    Glow,
    CornerBrackets,
    GradientPulse,
    Neon
}

object FocusIndicatorDefaults {
    const val BORDER_STYLE = "Solid"
    const val COLOR = "theme"
    const val THICKNESS = 2
    const val PLACEMENT = "Inside"
}

/**
 * Customizable focus ring tokens. [color] null uses [WajihaFocus.borderColor].
 * Defaults match the theme accent and [WajihaFocus] border widths.
 */
data class FocusIndicatorStyle(
    val color: Color? = null,
    val thickness: Dp = WajihaFocus.borderWidth,
    val borderStyle: FocusBorderStyle = FocusBorderStyle.Solid,
    val selectedThickness: Dp = WajihaFocus.selectedBorderWidth,
    val placement: FocusPlacement = FocusPlacement.Inside
)

val LocalFocusIndicatorStyle = compositionLocalOf { FocusIndicatorStyle() }

fun focusBorderStyleFromPreference(value: String): FocusBorderStyle =
    runCatching { FocusBorderStyle.valueOf(FocusIndicatorPreferenceValues.normalizeBorderStyle(value)) }
        .getOrDefault(FocusBorderStyle.Solid)

fun focusPlacementFromPreference(value: String): FocusPlacement =
    when (FocusIndicatorPreferenceValues.normalizePlacement(value)) {
        "Outside" -> FocusPlacement.Outside
        else -> FocusPlacement.Inside
    }

@Composable
fun focusColorFromPreference(value: String): Color? {
    val normalized = FocusIndicatorPreferenceValues.normalizeColor(value)
    if (normalized.startsWith("#")) {
        return FocusIndicatorPreferenceValues.parseHexColor(normalized)
    }
    return when (normalized) {
        "white" -> Color.White
        "yellow" -> WajihaColors.Tertiary
        "cyan" -> WajihaColors.Accent
        "red" -> Color(0xFFE53935)
        "green" -> Color(0xFF43A047)
        "magenta" -> Color(0xFFD81B60)
        "orange" -> Color(0xFFFF9800)
        "lime" -> Color(0xFFCDDC39)
        "pink" -> Color(0xFFF48FB1)
        else -> null
    }
}

@Composable
fun focusColorPreview(value: String): Color =
    focusColorFromPreference(value) ?: WajihaFocus.borderColor()

@Composable
fun focusColorDisplayLabel(value: String): String =
    FocusIndicatorPreferenceValues.displayColorLabel(value)

@Composable
fun AppSettings.toFocusIndicatorStyle(): FocusIndicatorStyle {
    val thicknessDp = FocusIndicatorPreferenceValues.normalizeThickness(focusThickness).dp
    return FocusIndicatorStyle(
        color = focusColorFromPreference(focusColor),
        thickness = thicknessDp,
        borderStyle = focusBorderStyleFromPreference(focusBorderStyle),
        selectedThickness = (thicknessDp.value + 1f).dp,
        placement = focusPlacementFromPreference(focusPlacement)
    )
}
