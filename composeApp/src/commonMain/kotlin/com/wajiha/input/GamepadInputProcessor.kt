package com.wajiha.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.time.Clock

/**
 * Throttles rapid key repeats and applies a short grace period after layer push.
 */
class GamepadInputProcessor(
    private val repeatIntervalMs: Long = 120L,
    /** D-pad hold-to-scroll cadence (higher = slower focus moves). */
    private val dpadRepeatIntervalMs: Long = 130L, // 120L
    private val gracePeriodMs: Long = 250L,
) {
    private var lastKey: Key? = null
    private var lastEventTimeMs: Long = 0L
    private var layerPushedAtMs: Long = 0L

    fun onLayerPushed() {
        layerPushedAtMs = Clock.System.now().toEpochMilliseconds()
    }

    fun shouldConsume(
        type: KeyEventType,
        key: Key,
    ): Boolean {
        if (key == Key.ButtonX || isShoulderTabKey(key)) return false
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - layerPushedAtMs < gracePeriodMs) return true
        if (type != KeyEventType.KeyDown) return false
        val interval = if (key in dpadKeys) dpadRepeatIntervalMs else repeatIntervalMs
        if (key == lastKey && now - lastEventTimeMs < interval) {
            return true
        }
        lastKey = key
        lastEventTimeMs = now
        return false
    }

    fun reset() {
        lastKey = null
        lastEventTimeMs = 0L
    }
}

private val dpadKeys =
    setOf(
        Key.DirectionUp,
        Key.DirectionDown,
        Key.DirectionLeft,
        Key.DirectionRight,
    )

private fun isShoulderTabKey(key: Key): Boolean =
    key == Key.ButtonL1 ||
        key == Key.ButtonR1 ||
        key == Key.PageUp ||
        key == Key.PageDown ||
        key == Key.MoveHome ||
        key == Key.MoveEnd
