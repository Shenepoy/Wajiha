package com.wajiha.state

/** How active game sessions appear on the bottom screen during gameplay. */
enum class NowPlayingDisplayMode {
    None,
    GridTiles,
    FloatingChip,
    Both,
    ;

    val showsGridTiles: Boolean
        get() = this == GridTiles || this == Both

    val showsFloatingChip: Boolean
        get() = this == FloatingChip || this == Both

    companion object {
        fun fromName(value: String?): NowPlayingDisplayMode = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Both
    }
}
