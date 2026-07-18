package com.wajiha.state

/** How the platform appears on the library (quiet) hero. */
enum class TopHeroPlatformStyle {
    /** No platform chrome on the library hero. */
    Off,

    /** Console logo in the bottom-right corner of the hero frame (default). */
    CornerLogo,

    /** Platform icon + name beside the game title. */
    TitleRow,
    ;

    companion object {
        fun fromName(value: String?): TopHeroPlatformStyle = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CornerLogo
    }
}
