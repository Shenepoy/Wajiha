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

/** Controls whether focus chrome ignores touch input mode. */
enum class GamepadFocusChromeScope {
    /** Hide focus chrome while touch mode is active; restore on gamepad key. */
    Default,

    /** Game grid tiles — always show nav/selection chrome. */
    GameGrid,

    /** Context menus — always show item focus while navigating. */
    Menu,
}

val LocalGamepadFocusChromeScope = compositionLocalOf { GamepadFocusChromeScope.Default }

/**
 * True when gamepad focus rings and nav highlights should render for the current scope.
 * [GamepadActionBar] is separate: Auto + no connected pad hides the hint bar.
 */
@Composable
fun showGamepadChrome(highlighted: Boolean = true): Boolean {
    if (!highlighted) return false
    return when (LocalGamepadFocusChromeScope.current) {
        GamepadFocusChromeScope.GameGrid,
        GamepadFocusChromeScope.Menu,
        -> true

        GamepadFocusChromeScope.Default -> LocalInputMode.current == InputMode.Gamepad
    }
}
