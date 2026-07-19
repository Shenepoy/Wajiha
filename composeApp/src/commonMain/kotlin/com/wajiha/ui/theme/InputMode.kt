package com.wajiha.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class InputMode {
    Gamepad,
    Touch,
}

@Stable
class InputModeController(
    initial: InputMode = InputMode.Gamepad,
) {
    var mode by mutableStateOf(initial)
        private set

    fun onTouch() {
        mode = InputMode.Touch
    }

    fun onGamepadKey() {
        mode = InputMode.Gamepad
    }
}

@Composable
fun rememberInputModeController(initial: InputMode = InputMode.Gamepad): InputModeController = remember { InputModeController(initial) }

val LocalInputMode = compositionLocalOf { InputMode.Touch }

val LocalInputModeController = staticCompositionLocalOf<InputModeController?> { null }

/** Classifies focus chrome surfaces; visibility is no longer suppressed by touch. */
enum class GamepadFocusChromeScope {
    /** Standard focus chrome. */
    Default,

    /** Game grid tiles — always show nav/selection chrome. */
    GameGrid,

    /** Context menus — always show item focus while navigating. */
    Menu,
}

val LocalGamepadFocusChromeScope = compositionLocalOf { GamepadFocusChromeScope.Default }

/**
 * True when focus rings and navigation highlights should render.
 *
 * Touch and gamepad share one logical selection, so touch must never make the
 * active control appear uncontrolled. Input mode remains useful for scrolling,
 * keyboard behavior, and hints, but no longer controls focus-ring visibility.
 * [GamepadActionBar] is separate: Auto + no connected pad hides the hint bar.
 */
@Composable
fun showGamepadChrome(highlighted: Boolean = true): Boolean = highlighted
