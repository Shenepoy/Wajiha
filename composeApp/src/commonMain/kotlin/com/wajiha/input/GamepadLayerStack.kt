package com.wajiha.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks active gamepad UI layers (screen, dialog, modal).
 * Top layer receives confirm/back first; [deactivateAll] on game start.
 */
class GamepadLayerStack {
    private val _layers = MutableStateFlow<List<String>>(emptyList())
    val layers: StateFlow<List<String>> = _layers.asStateFlow()

    val topLayer: String? get() = _layers.value.lastOrNull()

    fun push(layerId: String) {
        _layers.value = _layers.value.filter { it != layerId } + layerId
    }

    fun pop(layerId: String) {
        _layers.value = _layers.value.filter { it != layerId }
    }

    fun deactivateAll() {
        _layers.value = emptyList()
    }

    fun isActive(layerId: String): Boolean = layerId == topLayer
}

/** Process-wide layer stack shared by screens and modals. */
object GamepadLayers {
    val stack = GamepadLayerStack()
}
