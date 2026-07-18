package com.wajiha.state

/**
 * Which Dan Patrick clear-logo color variant to show for platform chrome.
 * [Auto] follows the app theme: dark → Light Color, light → Dark Color.
 */
enum class PlatformLogoColorStyle {
    Auto,
    LightColor,
    DarkColor,
    LightBw,
    DarkBw,
    LightJustWhite,
    DarkJustBlack,
    ;

    /** Resource folder under `files/platform_logos/`. Null when [Auto]. */
    fun resourceKey(): String? =
        when (this) {
            Auto -> null
            LightColor -> "light_color"
            DarkColor -> "dark_color"
            LightBw -> "light_bw"
            DarkBw -> "dark_bw"
            LightJustWhite -> "light_just_white"
            DarkJustBlack -> "dark_just_black"
        }

    companion object {
        fun fromName(value: String?): PlatformLogoColorStyle = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Auto

        /**
         * Resolves the on-disk variant folder for [style] given whether the
         * app theme is currently dark.
         */
        fun resolveResourceKey(
            style: PlatformLogoColorStyle,
            darkTheme: Boolean,
        ): String =
            style.resourceKey()
                ?: if (darkTheme) {
                    "light_color"
                } else {
                    "dark_color"
                }
    }
}
