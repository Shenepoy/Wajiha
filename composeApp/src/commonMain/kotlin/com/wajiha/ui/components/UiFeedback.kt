package com.wajiha.ui.components

import com.wajiha.platform.AppActions
import com.wajiha.platform.UiSound

/** Central feedback hooks for navigation, confirm, and errors. */
class UiFeedback(private val appActions: AppActions) {
    fun navigate() = appActions.playSound(UiSound.Navigate)
    fun confirm() = appActions.playSound(UiSound.Open)
    fun back() = appActions.playSound(UiSound.Back)
    fun launch() = appActions.playSound(UiSound.Launch)
    fun error() = appActions.playSound(UiSound.Back)
}
