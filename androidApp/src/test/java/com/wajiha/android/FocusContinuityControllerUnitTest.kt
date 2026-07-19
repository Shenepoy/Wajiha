package com.wajiha.android

import com.wajiha.input.FocusClaimSource
import com.wajiha.input.FocusContinuityController
import com.wajiha.input.FocusRestorePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FocusContinuityControllerUnitTest {
    @Test
    fun modalRoundTripRestoresUnderlayAndRejectsStaleClaims() {
        val controller =
            FocusContinuityController(
                rootLayerId = "screen",
                restorePolicy = FocusRestorePolicy.ResetToDefault,
            )
        val tile = controller.anchor("tile")
        controller.register(tile)
        controller.claim(tile, FocusClaimSource.Gamepad)

        controller.pushLayer("menu")
        assertFalse(controller.claim(tile, FocusClaimSource.Touch))
        val menuRow = controller.anchor("row", layerId = "menu")
        controller.register(menuRow)
        controller.claim(menuRow, FocusClaimSource.Gamepad)
        controller.popLayer("menu")

        assertEquals(tile, controller.activeAnchor)
    }

    @Test
    fun removedAnchorHandsOffDirectlyToNeighbor() {
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

        controller.offer(neighbor)

        assertEquals(neighbor, controller.activeAnchor)
    }
}
