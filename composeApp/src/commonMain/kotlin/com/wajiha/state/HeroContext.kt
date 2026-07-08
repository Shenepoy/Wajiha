package com.wajiha.state

/** What the top-screen hero should reflect while browsing outside the game library. */
sealed class HeroContext {
    abstract val transitionKey: String

    data object GameLibrary : HeroContext() {
        override val transitionKey: String = "game"
    }

    data class Settings(
        val sectionLabel: String? = null
    ) : HeroContext() {
        override val transitionKey: String = "settings"
    }

    data class Apps(
        val appCount: Int = 0,
        val focusedLabel: String? = null
    ) : HeroContext() {
        override val transitionKey: String = "apps"
    }

    data class System(
        val batteryPercent: Int = -1,
        val charging: Boolean = false,
        val wifiEnabled: Boolean = false
    ) : HeroContext() {
        override val transitionKey: String = "system"
    }

    data class GameDetail(
        val gameId: Long
    ) : HeroContext() {
        override val transitionKey: String = "game_detail_$gameId"
    }
}

/** Which launcher surface is active on each display (drives [HeroContext] resolution). */
enum class LauncherPanel {
    GameLibrary,
    Settings,
    Apps,
    System,
    GameDetail
}
