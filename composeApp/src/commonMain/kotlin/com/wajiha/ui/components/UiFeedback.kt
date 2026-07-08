package com.wajiha.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wajiha.platform.AppActions
import com.wajiha.platform.UiSound

/** Central feedback hooks for navigation, confirm, and errors. */
class UiFeedback(private val appActions: AppActions) {
    fun navigate() = appActions.playSound(UiSound.Navigate)
    fun confirm() = appActions.playSound(UiSound.Open)
    fun back() = appActions.playSound(UiSound.Back)
    fun launch() = appActions.playSound(UiSound.Launch)
    fun error() = appActions.playSound(UiSound.Back)

    fun play(sound: UiSound) = appActions.playSound(sound)

    /** Plays navigate when the tab/section index actually changes. */
    fun tabSelect(currentIndex: Int, newIndex: Int) {
        if (newIndex != currentIndex) navigate()
    }
}

val LocalUiFeedback = staticCompositionLocalOf<UiFeedback> {
    error("LocalUiFeedback not provided")
}

@Composable
fun rememberUiFeedback(): UiFeedback = org.koin.compose.koinInject()
