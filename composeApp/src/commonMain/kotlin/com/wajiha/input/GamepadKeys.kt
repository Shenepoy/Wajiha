package com.wajiha.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

object GamepadKeys {
    fun isConfirm(type: KeyEventType, key: Key): Boolean {
        if (type != KeyEventType.KeyDown) return false
        return key == Key.DirectionCenter ||
            key == Key.Enter ||
            key == Key.NumPadEnter ||
            key == Key.ButtonA
    }

    fun isBack(type: KeyEventType, key: Key): Boolean {
        if (type != KeyEventType.KeyDown) return false
        return key == Key.Escape ||
            key == Key.Back ||
            key == Key.ButtonB
    }

    fun isL1(type: KeyEventType, key: Key): Boolean =
        type == KeyEventType.KeyDown && key in l1Keys

    fun isR1(type: KeyEventType, key: Key): Boolean =
        type == KeyEventType.KeyDown && key in r1Keys

    private val l1Keys = setOf(
        Key.ButtonL1,
        Key.PageUp,
        Key.MoveHome
    )

    private val r1Keys = setOf(
        Key.ButtonR1,
        Key.PageDown,
        Key.MoveEnd
    )

    fun isSelect(type: KeyEventType, key: Key): Boolean =
        type == KeyEventType.KeyDown &&
            (key == Key.ButtonSelect || key == Key.ButtonMode)

    fun isX(type: KeyEventType, key: Key): Boolean =
        type == KeyEventType.KeyDown && key == Key.ButtonX

    /** Any gamepad key that should restore gamepad chrome / input mode. */
    fun switchesToGamepadMode(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionUp,
            Key.DirectionDown,
            Key.DirectionLeft,
            Key.DirectionRight,
            Key.DirectionCenter,
            Key.Enter,
            Key.NumPadEnter,
            Key.Escape,
            Key.Back,
            Key.ButtonA,
            Key.ButtonB,
            Key.ButtonX,
            Key.ButtonY,
            Key.ButtonL1,
            Key.ButtonR1,
            Key.ButtonSelect,
            Key.ButtonMode,
            Key.PageUp,
            Key.PageDown,
            Key.MoveHome,
            Key.MoveEnd -> true
            else -> false
        }
    }
}

enum class GamepadConfirmPolicy {
    /** Grid: A launches the selected item. */
    LaunchOnSelect,
    /** Button/chip: A activates the focused control. */
    ActivateFocused,
    /** List row: A enters sub-focus or selects. */
    EnterSubFocus,
    /** Dialog: A confirms primary action. */
    DialogConfirm
}
