package com.wajiha.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

private data class NavSlot(
    val id: Any,
    val onActivate: () -> Unit,
    val enabled: Boolean,
    val onEnterEdit: (() -> Boolean)?,
    val onExitEdit: (() -> Boolean)?,
)

/**
 * Registers navigable slots and routes D-pad / A / B.
 * Only the top zone in a [GamepadNavHost] receives keys when active.
 */
class GamepadNavController(
    val focusState: GamepadFocusState,
    private val onBack: (() -> Boolean)? = null,
) {
    private val slots = mutableListOf<NavSlot>()
    var zoneId: String = ""
    var active: Boolean = true

    fun register(
        id: Any,
        onActivate: () -> Unit,
        enabled: Boolean = true,
        onEnterEdit: (() -> Boolean)? = null,
        onExitEdit: (() -> Boolean)? = null,
    ): Int {
        val index = slots.indexOfFirst { it.id == id }
        val slot = NavSlot(id, onActivate, enabled, onEnterEdit, onExitEdit)
        if (index >= 0) {
            slots[index] = slot
            focusState.itemCount = slots.count { it.enabled }
            return index
        }
        slots.add(slot)
        focusState.itemCount = slots.count { it.enabled }
        focusState.clampIndex()
        return slots.lastIndex
    }

    fun unregister(id: Any) {
        slots.removeAll { it.id == id }
        focusState.itemCount = slots.count { it.enabled }
        focusState.clampIndex()
    }

    fun slotIndex(id: Any): Int {
        var idx = 0
        for (slot in slots) {
            if (!slot.enabled) continue
            if (slot.id == id) return idx
            idx++
        }
        return -1
    }

    fun isSlotFocused(id: Any): Boolean {
        val idx = slotIndex(id)
        return idx >= 0 && focusState.isFocused(idx)
    }

    fun activate(): Boolean {
        if (focusState.editing) return false
        val slot = enabledSlotAt(focusState.focusedIndex) ?: return false
        return enterEditForSlot(slot)
    }

    /** Focus a slot by id and enter edit mode (touch) or run its activate handler. */
    fun enterEditFor(id: Any): Boolean {
        if (focusState.editing) {
            val current = enabledSlotAt(focusState.focusedIndex)
            if (current?.id == id) return true
            back()
        }
        val idx = slotIndex(id)
        if (idx < 0) return false
        focusState.focusedIndex = idx
        val slot = enabledSlotAt(idx) ?: return false
        return enterEditForSlot(slot)
    }

    fun back(): Boolean {
        if (focusState.editing) {
            exitEdit()
            return true
        }
        return onBack?.invoke() == true
    }

    fun exitEdit(): Boolean {
        if (!focusState.editing) return false
        val slot = enabledSlotAt(focusState.focusedIndex)
        if (slot?.onExitEdit?.invoke() == true) {
            focusState.editing = false
            return true
        }
        focusState.editing = false
        return true
    }

    private fun enterEditForSlot(slot: NavSlot): Boolean {
        if (slot.onEnterEdit != null) {
            if (slot.onEnterEdit()) {
                focusState.editing = true
                return true
            }
        }
        slot.onActivate()
        return true
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!active) return false
        if (event.type != KeyEventType.KeyDown) return false
        if (focusState.editing) {
            return if (GamepadKeys.isBack(event.type, event.key)) {
                exitEdit()
            } else {
                false
            }
        }

        return when {
            GamepadKeys.isConfirm(event.type, event.key) -> activate()
            GamepadKeys.isBack(event.type, event.key) -> back()
            event.key == Key.DirectionUp -> focusState.moveUp()
            event.key == Key.DirectionDown -> focusState.moveDown()
            event.key == Key.DirectionLeft -> focusState.moveLeft()
            event.key == Key.DirectionRight -> focusState.moveRight()
            else -> false
        }
    }

    private fun enabledSlotAt(navIndex: Int): NavSlot? {
        var idx = 0
        for (slot in slots) {
            if (!slot.enabled) continue
            if (idx == navIndex) return slot
            idx++
        }
        return null
    }
}

@Composable
fun rememberGamepadNavController(
    mode: GamepadNavMode,
    gridRows: Int = 2,
    onBack: (() -> Boolean)? = null,
): GamepadNavController {
    val focusState = remember(mode, gridRows) { GamepadFocusState(mode, gridRows) }
    return remember(focusState, onBack) { GamepadNavController(focusState, onBack) }
}
