package com.wajiha.ui.ra

import com.wajiha.data.ra.RaAchievement

enum class AchievementSortMode {
    Default,
    EarnedFirst,
    Points,
    Title,
}

fun filterAchievements(
    items: List<RaAchievement>,
    query: String,
): List<RaAchievement> {
    val q = query.trim()
    if (q.isEmpty()) return items
    return items.filter { item ->
        item.title.contains(q, ignoreCase = true) ||
            item.description.contains(q, ignoreCase = true)
    }
}

fun sortAchievements(
    items: List<RaAchievement>,
    mode: AchievementSortMode,
): List<RaAchievement> =
    when (mode) {
        AchievementSortMode.Default -> {
            items.sortedBy { it.displayOrder }
        }

        AchievementSortMode.EarnedFirst -> {
            items.sortedWith(
                compareByDescending<RaAchievement> { it.earned }
                    .thenBy { it.displayOrder },
            )
        }

        AchievementSortMode.Points -> {
            items.sortedWith(
                compareByDescending<RaAchievement> { it.points }
                    .thenBy { it.displayOrder },
            )
        }

        AchievementSortMode.Title -> {
            items.sortedWith(
                compareBy<RaAchievement, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                    .thenBy { it.displayOrder },
            )
        }
    }
