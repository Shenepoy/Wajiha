package com.wajiha.ui.components.gamepad

/**
 * Logical text-field state shared by custom navigation and Compose focus.
 *
 * Selection never implies editing. Only an explicit touch or confirm action may enter [Editing].
 */
enum class GamepadTextFieldState {
    Idle,
    Selected,
    Editing,
}

internal enum class GamepadTextFieldEvent {
    Select,
    Deselect,
    BeginEditing,
    EndEditing,
}

internal fun GamepadTextFieldState.transition(event: GamepadTextFieldEvent): GamepadTextFieldState =
    when (event) {
        GamepadTextFieldEvent.Select -> {
            if (this == GamepadTextFieldState.Editing) this else GamepadTextFieldState.Selected
        }

        GamepadTextFieldEvent.Deselect -> {
            GamepadTextFieldState.Idle
        }

        GamepadTextFieldEvent.BeginEditing -> {
            GamepadTextFieldState.Editing
        }

        GamepadTextFieldEvent.EndEditing -> {
            if (this == GamepadTextFieldState.Editing) {
                GamepadTextFieldState.Selected
            } else {
                this
            }
        }
    }
