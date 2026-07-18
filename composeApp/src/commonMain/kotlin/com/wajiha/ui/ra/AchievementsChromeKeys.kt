package com.wajiha.ui.ra

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.wajiha.input.GamepadKeys

/**
 * Host-level Y/X for Achievements search/sort chrome. Kept outside the list
 * [GamepadNavHost] so toggles still work while the search field is editing.
 */
fun handleAchievementsChromePreviewKey(
    event: KeyEvent,
    searchOpen: Boolean,
    sortOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    onSortOpenChange: (Boolean) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        GamepadKeys.isY(event.type, event.key) && !sortOpen -> {
            if (searchOpen) {
                onSearchOpenChange(false)
            } else {
                onSortOpenChange(false)
                onSearchOpenChange(true)
            }
            true
        }

        GamepadKeys.isX(event.type, event.key) && !searchOpen -> {
            if (sortOpen) {
                onSortOpenChange(false)
            } else {
                onSearchOpenChange(false)
                onSortOpenChange(true)
            }
            true
        }

        else -> {
            false
        }
    }
}
