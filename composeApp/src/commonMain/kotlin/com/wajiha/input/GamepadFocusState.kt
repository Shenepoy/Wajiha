package com.wajiha.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How D-pad directions map to index changes. */
enum class GamepadNavMode {
    /** List / form: up-down only. */
    Vertical,

    /** Folder tabs: left-right only. */
    Horizontal,

    /** Home grid: 2-D index with [gridRows] rows per column. */
    Grid,
}

/**
 * Mutable focus state for a single navigation zone.
 * Index is explicit — not tied to Compose focus.
 */
class GamepadFocusState(
    val mode: GamepadNavMode,
    val gridRows: Int = 2,
) {
    var focusedIndex by mutableIntStateOf(0)
    var itemCount by mutableIntStateOf(0)

    /** When set, D-pad is delegated to the focused text field. */
    var editing by mutableStateOf(false)

    fun clampIndex() {
        if (itemCount <= 0) {
            focusedIndex = 0
        } else {
            focusedIndex = focusedIndex.coerceIn(0, itemCount - 1)
        }
    }

    fun moveUp(): Boolean =
        when (mode) {
            GamepadNavMode.Vertical -> {
                moveBy(-1)
            }

            GamepadNavMode.Horizontal -> {
                false
            }

            GamepadNavMode.Grid -> {
                val row = focusedIndex % gridRows
                if (row > 0) moveBy(-1) else false
            }
        }

    fun moveDown(): Boolean =
        when (mode) {
            GamepadNavMode.Vertical -> {
                moveBy(1)
            }

            GamepadNavMode.Horizontal -> {
                false
            }

            GamepadNavMode.Grid -> {
                val row = focusedIndex % gridRows
                if (row < gridRows - 1 && focusedIndex + 1 < itemCount) moveBy(1) else false
            }
        }

    fun moveLeft(): Boolean =
        when (mode) {
            GamepadNavMode.Vertical -> {
                false
            }

            GamepadNavMode.Horizontal -> {
                moveBy(-1)
            }

            GamepadNavMode.Grid -> {
                val next = focusedIndex - gridRows
                if (next >= 0) {
                    focusedIndex = next
                    true
                } else {
                    false
                }
            }
        }

    fun moveRight(): Boolean =
        when (mode) {
            GamepadNavMode.Vertical -> {
                false
            }

            GamepadNavMode.Horizontal -> {
                moveBy(1)
            }

            GamepadNavMode.Grid -> {
                val next = focusedIndex + gridRows
                if (next < itemCount) {
                    focusedIndex = next
                    true
                } else {
                    false
                }
            }
        }

    private fun moveBy(delta: Int): Boolean {
        if (itemCount <= 0) return false
        val next = (focusedIndex + delta).coerceIn(0, itemCount - 1)
        if (next == focusedIndex) return false
        focusedIndex = next
        return true
    }

    fun isFocused(slot: Int): Boolean = slot == focusedIndex && itemCount > 0
}
