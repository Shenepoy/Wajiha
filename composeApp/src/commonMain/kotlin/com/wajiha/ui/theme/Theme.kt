package com.wajiha.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColors = lightColorScheme(
    primary = WajihaColors.AccentDeep,
    onPrimary = WajihaColors.SurfaceLight,
    primaryContainer = WajihaColors.AccentContainerLight,
    onPrimaryContainer = WajihaColors.OnLight,
    secondary = WajihaColors.Secondary,
    onSecondary = WajihaColors.SurfaceLight,
    secondaryContainer = WajihaColors.SecondaryContainerLight,
    onSecondaryContainer = WajihaColors.OnLight,
    tertiary = WajihaColors.Tertiary,
    tertiaryContainer = WajihaColors.TertiaryContainerLight,
    onTertiaryContainer = WajihaColors.OnLight,
    background = WajihaColors.BackgroundLight,
    onBackground = WajihaColors.OnLight,
    surface = WajihaColors.SurfaceLight,
    onSurface = WajihaColors.OnLight,
    surfaceVariant = WajihaColors.SurfaceVariantLight,
    onSurfaceVariant = WajihaColors.OnLightMuted,
    outline = WajihaColors.OnLightMuted.copy(alpha = 0.55f),
    error = WajihaColors.Error
)

private val DarkColors = darkColorScheme(
    primary = WajihaColors.Accent,
    onPrimary = WajihaColors.ScreenFrame,
    primaryContainer = WajihaColors.AccentContainerDark,
    onPrimaryContainer = WajihaColors.OnDark,
    secondary = WajihaColors.Secondary,
    onSecondary = WajihaColors.ScreenFrame,
    secondaryContainer = WajihaColors.SecondaryContainerDark,
    onSecondaryContainer = WajihaColors.OnDark,
    tertiary = WajihaColors.Tertiary,
    tertiaryContainer = WajihaColors.TertiaryContainerDark,
    onTertiaryContainer = WajihaColors.OnDark,
    background = WajihaColors.BackgroundDark,
    onBackground = WajihaColors.OnDark,
    surface = WajihaColors.SurfaceDark,
    onSurface = WajihaColors.OnDark,
    surfaceVariant = WajihaColors.SurfaceVariantDark,
    onSurfaceVariant = WajihaColors.OnDarkMuted,
    outline = WajihaColors.OnDarkMuted.copy(alpha = 0.45f),
    error = WajihaColors.ErrorDark
)

@Composable
fun WajihaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    focusIndicatorStyle: FocusIndicatorStyle = FocusIndicatorStyle(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = wajihaTypography(),
    ) {
        CompositionLocalProvider(LocalFocusIndicatorStyle provides focusIndicatorStyle) {
            content()
        }
    }
}

/** Maps the persisted theme preference ("dark" | "light" | "system"). */
@Composable
fun themeIsDark(preference: String): Boolean = when (preference) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}
