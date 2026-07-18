package com.wajiha.ui.ra

import com.wajiha.data.ra.RaAchievement
import kotlin.test.Test
import kotlin.test.assertEquals

class AchievementsFilterTest {
    @Test
    fun filterAchievements_matchesTitleOrDescription() {
        val items =
            listOf(
                RaAchievement(id = 1, title = "First Boss", description = "Defeat the giant"),
                RaAchievement(id = 2, title = "Collector", description = "Find all coins"),
                RaAchievement(id = 3, title = "Speedrun", description = "Finish under 10 minutes"),
            )
        assertEquals(3, filterAchievements(items, "").size)
        assertEquals(listOf(1L), filterAchievements(items, "boss").map { it.id })
        assertEquals(listOf(2L), filterAchievements(items, "COINS").map { it.id })
        assertEquals(emptyList(), filterAchievements(items, "xyzzy"))
    }

    @Test
    fun sortAchievements_earnedFirstThenDisplayOrder() {
        val items =
            listOf(
                RaAchievement(id = 1, title = "Locked early", displayOrder = 1),
                RaAchievement(
                    id = 2,
                    title = "Earned late",
                    displayOrder = 99,
                    dateEarned = "2024-01-01",
                ),
                RaAchievement(
                    id = 3,
                    title = "Earned early",
                    displayOrder = 5,
                    dateEarned = "2024-01-02",
                ),
                RaAchievement(id = 4, title = "Locked late", displayOrder = 50),
            )
        assertEquals(
            listOf(3L, 2L, 1L, 4L),
            sortAchievements(items, AchievementSortMode.EarnedFirst).map { it.id },
        )
        assertEquals(
            listOf(1L, 3L, 4L, 2L),
            sortAchievements(items, AchievementSortMode.Default).map { it.id },
        )
        assertEquals(
            listOf(2L, 3L, 1L, 4L),
            sortAchievements(
                items.map {
                    when (it.id) {
                        2L -> it.copy(points = 50)
                        3L -> it.copy(points = 10)
                        else -> it.copy(points = 1)
                    }
                },
                AchievementSortMode.Points,
            ).map { it.id },
        )
        assertEquals(
            listOf(2L, 3L, 1L, 4L),
            sortAchievements(items, AchievementSortMode.Title).map { it.id },
        )
    }
}
