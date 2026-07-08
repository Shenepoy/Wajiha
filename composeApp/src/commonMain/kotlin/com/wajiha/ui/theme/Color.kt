package com.wajiha.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Wajiha palette — 3DS-inspired chrome with a modern console feel:
 * deep navy dark mode, soft blue-grey light mode, vivid cyan accent.
 */
object WajihaColors {
    // Accent — soft 3DS aqua (readable, not neon)
    val Accent = Color(0xFF5EB8C9)
    val AccentDeep = Color(0xFF2A7A88)
    val AccentContainerLight = Color(0xFFC8E8EE)
    val AccentContainerDark = Color(0xFF163640)

    val Secondary = Color(0xFF93A6BD)
    val SecondaryContainerLight = Color(0xFFDCE5F0)
    val SecondaryContainerDark = Color(0xFF27354A)

    // Warm amber for favorites / play stats
    val Tertiary = Color(0xFFF2B24E)
    val TertiaryContainerLight = Color(0xFFFFE3B3)
    val TertiaryContainerDark = Color(0xFF4C3812)

    // Light chrome (3DS home-menu greys, slightly blue)
    val BackgroundLight = Color(0xFFE8EDF4)
    val SurfaceLight = Color(0xFFF4F7FB)
    val SurfaceVariantLight = Color(0xFFDAE2EC)
    val OnLight = Color(0xFF16202C)
    /** Secondary labels on light surfaces — readable without alpha hacks. */
    val OnLightMuted = Color(0xFF4A5A6E)

    // Dark chrome (deep navy, not pure black — keeps depth between layers)
    val BackgroundDark = Color(0xFF0B101B)
    val SurfaceDark = Color(0xFF121927)
    val SurfaceVariantDark = Color(0xFF1D2738)
    /** Primary text on dark — near-white for strong contrast. */
    val OnDark = Color(0xFFF2F5FA)
    /**
     * Body / caption text on dark. Solid muted blue-grey (~AA on navy) —
     * prefer this over OnDark.copy(alpha = …) which washes out.
     */
    val OnDarkMuted = Color(0xFFB9C5D8)

    val Error = Color(0xFFBA1A1A)
    val ErrorDark = Color(0xFFFFB4AB)

    // Top-screen hero backdrop / frame chrome (dark hero only)
    val ScreenFrame = Color(0xFF080B12)
    val ScreenFrameLight = Color(0xFFD4DCE8)
    val HeroScrim = Color(0x99000000)
    // Bottom-of-tile label scrim (always over artwork → keep dark)
    val TileScrim = Color(0xCC0B101B)
}
