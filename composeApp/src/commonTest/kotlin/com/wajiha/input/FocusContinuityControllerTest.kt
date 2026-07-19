package com.wajiha.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusContinuityControllerTest {
    @Test
    fun touchClaimBecomesSingleLogicalAnchor() {
        val controller =
            FocusContinuityController(
                rootLayerId = "screen",
                restorePolicy = FocusRestorePolicy.ResetToDefault,
            )
        val first = controller.anchor("first")
        val second = controller.anchor("second")

        controller.register(first)
        controller.register(second)
        controller.claim(second, FocusClaimSource.Touch)

        assertEquals(second, controller.activeAnchor)
        assertFalse(controller.isActive(first))
        assertTrue(controller.isActive(second))
    }

    @Test
    fun removedTargetHandsOffWithoutClearingLogicalFocus() {
        val controller =
            FocusContinuityController(
                rootLayerId = "screen",
                restorePolicy = FocusRestorePolicy.ResetToDefault,
            )
        val removed = controller.anchor("removed")
        val neighbor = controller.anchor("neighbor")

        controller.register(removed)
        controller.claim(removed, FocusClaimSource.Gamepad)
        controller.unregister(removed)

        assertEquals(removed, controller.activeAnchor)
        assertTrue(controller.offer(neighbor))
        assertEquals(neighbor, controller.activeAnchor)
    }

    @Test
    fun coveredLayerRestoresItsFrozenAnchor() {
        val controller =
            FocusContinuityController(
                rootLayerId = "screen",
                restorePolicy = FocusRestorePolicy.ResetToDefault,
            )
        val underlay = controller.anchor("tile")
        controller.register(underlay)
        controller.claim(underlay, FocusClaimSource.Gamepad)

        controller.pushLayer("menu")
        val menu = controller.anchor("close", layerId = "menu")
        controller.register(menu)
        controller.claim(menu, FocusClaimSource.Gamepad)
        controller.popLayer("menu")

        assertEquals("screen", controller.activeLayerId)
        assertEquals(underlay, controller.activeAnchor)
    }

    @Test
    fun staleLayerCannotStealFocus() {
        val controller =
            FocusContinuityController(
                rootLayerId = "screen",
                restorePolicy = FocusRestorePolicy.ResetToDefault,
            )
        val screen = controller.anchor("tile")
        controller.register(screen)
        controller.pushLayer("menu")

        assertFalse(controller.claim(screen, FocusClaimSource.Touch))
        assertEquals("menu", controller.activeLayerId)
    }

    @Test
    fun zoneRoundTripPreservesEachIndex() {
        val coordinator = GamepadNavCoordinator()
        val first = GamepadNavController(GamepadFocusState(GamepadNavMode.Vertical))
        val second = GamepadNavController(GamepadFocusState(GamepadNavMode.Vertical))
        repeat(4) { index ->
            first.register("first-$index", onActivate = {})
            second.register("second-$index", onActivate = {})
        }
        first.focusState.focusedIndex = 2
        second.focusState.focusedIndex = 3
        coordinator.registerZone(0, first)
        coordinator.registerZone(1, second)

        coordinator.moveToNextZone()
        coordinator.moveToPrevZone()

        assertEquals(2, first.focusState.focusedIndex)
        assertEquals(3, second.focusState.focusedIndex)
    }
}
