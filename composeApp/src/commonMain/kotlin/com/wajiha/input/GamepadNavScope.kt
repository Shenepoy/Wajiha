package com.wajiha.input

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

val LocalGamepadNavController = compositionLocalOf<GamepadNavController?> { null }

/** Host-level coordinator for multi-zone screens (tabs + content). */
class GamepadNavCoordinator {
    private val zones = mutableMapOf<Int, GamepadNavController>()
    var activeZoneIndex by mutableIntStateOf(0)

    val maxZoneIndex: Int get() = zones.keys.maxOrNull() ?: 0

    fun registerZone(
        zoneIndex: Int,
        controller: GamepadNavController,
    ) {
        zones[zoneIndex] = controller
        updateActive()
    }

    fun unregisterZone(zoneIndex: Int) {
        zones.remove(zoneIndex)
        activeZoneIndex = activeZoneIndex.coerceIn(0, maxZoneIndex)
        updateActive()
    }

    fun setActiveZone(index: Int) {
        if (zones.isEmpty()) return
        activeZoneIndex = index.coerceIn(0, maxZoneIndex)
        updateActive()
    }

    fun moveToNextZone(): Boolean {
        if (activeZoneIndex < maxZoneIndex) {
            activeZoneIndex++
            updateActive()
            return true
        }
        return false
    }

    fun moveToPrevZone(): Boolean {
        if (activeZoneIndex > 0) {
            activeZoneIndex--
            updateActive()
            return true
        }
        return false
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val zone = zones[activeZoneIndex] ?: return false
        val consumed = zone.handleKeyEvent(event)
        if (!consumed && event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionDown -> return moveToNextZone()
                Key.DirectionUp -> return moveToPrevZone()
            }
        }
        return consumed
    }

    private fun updateActive() {
        zones.values.forEach { it.active = false }
        zones[activeZoneIndex]?.active = true
    }
}

val LocalGamepadNavCoordinator = compositionLocalOf<GamepadNavCoordinator?> { null }
