package com.wajiha.state

enum class SecondaryRoute {
    Modes,
    Settings,
    PlatformPicker,
    PlatformDetail,
    Scraper,
    GameDetail,
}

data class SecondaryNavigationState(
    val route: SecondaryRoute = SecondaryRoute.Modes,
    val platformDetailId: String? = null,
    val platformDetailFromPicker: Boolean = false,
    val gameDetailId: Long? = null,
)
