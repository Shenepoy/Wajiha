package com.wajiha.input

import androidx.compose.ui.input.key.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks active gamepad UI layers (screen, dialog, modal).
 * Top layer receives confirm/back first; [deactivateAll] on game start.
 *
 * [setPreviewHandler] registers screen-level key handlers (L1/R1 tab cycle, etc.)
 * that [dispatchTopPreviewKey] can invoke without Compose focus — see Android bridge.
 */
class GamepadLayerStack {
    private val _layers = MutableStateFlow<List<String>>(emptyList())
    val layers: StateFlow<List<String>> = _layers.asStateFlow()

    private val previewHandlers = mutableMapOf<String, (KeyEvent) -> Boolean>()

    val topLayer: String? get() = _layers.value.lastOrNull()

    fun push(layerId: String) {
        _layers.value = _layers.value.filter { it != layerId } + layerId
    }

    fun pop(layerId: String) {
        _layers.value = _layers.value.filter { it != layerId }
        previewHandlers.remove(layerId)
    }

    fun setPreviewHandler(
        layerId: String,
        handler: ((KeyEvent) -> Boolean)?,
    ) {
        if (handler == null) {
            previewHandlers.remove(layerId)
        } else {
            previewHandlers[layerId] = handler
        }
    }

    /** Invokes the top layer's preview handler when registered. */
    fun dispatchTopPreviewKey(event: KeyEvent): Boolean {
        val layer = topLayer ?: return false
        return previewHandlers[layer]?.invoke(event) == true
    }

    fun deactivateAll() {
        // Dismiss modal/menu overlays only — keep screen layers so confirm handlers
        // on home_grid and settings survive a game start.
        val modalLayers =
            _layers.value.filter { layerId ->
                layerId.startsWith("game_context_") ||
                    layerId.startsWith("dialog_") ||
                    layerId.startsWith("modal_")
            }
        if (modalLayers.isEmpty()) return
        _layers.value = _layers.value.filterNot { it in modalLayers }
        modalLayers.forEach { previewHandlers.remove(it) }
    }

    fun isActive(layerId: String): Boolean = layerId == topLayer
}

/** Process-wide layer stack shared by screens and modals. */
object GamepadLayers {
    val stack = GamepadLayerStack()
}
