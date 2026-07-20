package com.wajiha.ui.scraper.review

import androidx.compose.ui.graphics.Color

/**
 * Brand colors for scrape-review source badges — intentional separate palette,
 * not Material / [com.wajiha.ui.theme.WajihaColors] chrome.
 */
object ScrapeReviewSourceColors {
    val steamGridDb = Color(0xFF395C6B)
    val screenScraper = Color(0xFFC45C26)
    val libretro = Color(0xFF3D5A80)
    val retroAchievements = Color(0xFFB8860B)
    val romm = Color(0xFF2E7D4F)
    val local = Color(0xFF5C5C5C)
    val unknown = Color(0xFF4A4A4A)

    fun forSource(sourceId: String): Color =
        when (sourceId) {
            "steamgriddb" -> steamGridDb
            "screenscraper" -> screenScraper
            "libretro" -> libretro
            "ra" -> retroAchievements
            "romm" -> romm
            "local" -> local
            else -> unknown
        }
}
