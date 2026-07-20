package com.wajiha.ui.components.gamepad

import com.wajiha.input.GamepadFocusState
import com.wajiha.input.GamepadNavController
import com.wajiha.input.GamepadNavMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamepadTextFieldStateTest {
    @Test
    fun selectionAndEditingAreSeparateTransitions() {
        var state = GamepadTextFieldState.Idle

        state = state.transition(GamepadTextFieldEvent.Select)
        assertEquals(GamepadTextFieldState.Selected, state)

        state = state.transition(GamepadTextFieldEvent.BeginEditing)
        assertEquals(GamepadTextFieldState.Editing, state)

        state = state.transition(GamepadTextFieldEvent.EndEditing)
        assertEquals(GamepadTextFieldState.Selected, state)

        state = state.transition(GamepadTextFieldEvent.Deselect)
        assertEquals(GamepadTextFieldState.Idle, state)
    }

    @Test
    fun touchCanEnterEditingDirectlyWithoutImplicitFocusEvent() {
        val state =
            GamepadTextFieldState.Idle.transition(GamepadTextFieldEvent.BeginEditing)

        assertEquals(GamepadTextFieldState.Editing, state)
    }

    @Test
    fun editExitIsConsumedBeforeScreenBack() {
        var state = GamepadTextFieldState.Selected
        var backCount = 0
        val controller =
            GamepadNavController(
                focusState = GamepadFocusState(GamepadNavMode.Vertical),
                onBack = {
                    backCount += 1
                    true
                },
            )
        controller.register(
            id = "field",
            onActivate = {},
            onEnterEdit = {
                state = state.transition(GamepadTextFieldEvent.BeginEditing)
                true
            },
            onExitEdit = {
                state = state.transition(GamepadTextFieldEvent.EndEditing)
                true
            },
        )

        assertTrue(controller.activate())
        assertEquals(GamepadTextFieldState.Editing, state)

        assertTrue(controller.back())
        assertEquals(GamepadTextFieldState.Selected, state)
        assertEquals(0, backCount)

        assertTrue(controller.back())
        assertEquals(1, backCount)
    }

    @Test
    fun onlyErrorSeverityMarksFieldInvalid() {
        assertTrue(
            WajihaFieldMessageState(
                text = "Required",
                severity = WajihaFieldMessageSeverity.Error,
            ).isError,
        )
        assertFalse(
            WajihaFieldMessageState(
                text = "Use the account email",
                severity = WajihaFieldMessageSeverity.Supporting,
            ).isError,
        )
    }
}
