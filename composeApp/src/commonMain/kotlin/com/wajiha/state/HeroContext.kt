package com.wajiha.state

/** What the top-screen hero should reflect while browsing outside the game library. */
sealed class HeroContext {
    abstract val transitionKey: String

    data object GameLibrary : HeroContext() {
        override val transitionKey: String = "game"
    }

    data class Settings(
        val sectionLabel: String? = null,
    ) : HeroContext() {
        override val transitionKey: String = "settings"
    }

    data class Apps(
        val appCount: Int = 0,
        val focusedLabel: String? = null,
    ) : HeroContext() {
        override val transitionKey: String = "apps"
    }

    data class System(
        val batteryPercent: Int = -1,
        val charging: Boolean = false,
        val wifiEnabled: Boolean = false,
    ) : HeroContext() {
        override val transitionKey: String = "system"
    }

    data class GameDetail(
        val gameId: Long,
    ) : HeroContext() {
        override val transitionKey: String = "game_detail_$gameId"
    }

    /**
     * Manual scrape review: top screen shows the active slot candidate grid
     * (or a select-slot hint while the bottom overview is idle).
     */
    data object ScrapeReview : HeroContext() {
        override val transitionKey: String = "scrape_review"
    }
}

/** Which launcher surface is active on each display (drives [HeroContext] resolution). */
enum class LauncherPanel {
    GameLibrary,
    Settings,
    Apps,
    System,
    GameDetail,
}

/**
 * Shared menu destination for SELECT / swap-screen handoff between primary and
 * secondary activities. Local Compose routes stay per-activity; this snapshot is
 * what the display that gains the menu adopts so Settings (etc.) survive a swap.
 */
enum class MenuDestination {
    Home,
    Settings,
    PlatformPicker,
    PlatformDetail,
    Scraper,
    Apps,
    System,
    GameDetail,
    NowRunning,
}

data class MenuRouteSnapshot(
    val destination: MenuDestination = MenuDestination.Home,
    val platformDetailId: String? = null,
    val gameDetailId: Long? = null,
)
