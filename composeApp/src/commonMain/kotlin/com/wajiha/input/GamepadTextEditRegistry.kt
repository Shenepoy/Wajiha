package com.wajiha.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks the active [GamepadSafeTextField] edit session so B / system back
 * dismisses the keyboard before menus, navigation, or other back handlers run.
 */
object GamepadTextEditRegistry {
    private var dismissHandler: (() -> Boolean)? = null

    var isEditing by mutableStateOf(false)
        private set

    fun register(handler: () -> Boolean) {
        dismissHandler = handler
        isEditing = true
    }

    fun unregister(handler: () -> Boolean) {
        if (dismissHandler === handler) {
            dismissHandler = null
            isEditing = false
        }
    }

    /** Dismiss keyboard / exit edit mode when active. Returns true if consumed. */
    fun dismissIfEditing(): Boolean {
        if (!isEditing) return false
        val dismissed = dismissHandler?.invoke() == true
        if (dismissed) {
            dismissHandler = null
            isEditing = false
        }
        return dismissed
    }
}
