package com.wajiha.ui.components.gamepad

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.FocusContinuityController
import com.wajiha.input.FocusRestorePolicy
import com.wajiha.ui.theme.FocusBorderStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FocusRingOverlayStateTest {
    @Test
    fun ringRetainsOldGeometryUntilSuccessorPublishes() {
        val controller =
            FocusContinuityController(
                rootLayerId = "screen",
                restorePolicy = FocusRestorePolicy.ResetToDefault,
            )
        val overlay = FocusRingOverlayState(controller)
        val first = controller.anchor("first")
        val second = controller.anchor("second")
        val firstToken = Any()
        val secondToken = Any()

        controller.register(first)
        controller.claim(first, FocusClaimSource.Gamepad)
        overlay.publish(entry(firstToken, first, left = 10f))
        overlay.unregister(first)
        overlay.clear(firstToken)

        assertNotNull(overlay.entry)
        assertEquals(10f, overlay.entry?.boundsInRoot?.left)

        controller.register(second)
        controller.claim(second, FocusClaimSource.Touch)
        overlay.publish(entry(secondToken, second, left = 40f))

        assertEquals(40f, overlay.entry?.boundsInRoot?.left)
    }

    @Test
    fun poppingOverlayLayerRestoresCachedUnderlayRing() {
        val controller =
            FocusContinuityController(
                rootLayerId = "screen",
                restorePolicy = FocusRestorePolicy.ResetToDefault,
            )
        val overlay = FocusRingOverlayState(controller)
        val tile = controller.anchor("tile")
        controller.register(tile)
        controller.claim(tile, FocusClaimSource.Gamepad)
        overlay.publish(entry(Any(), tile, left = 5f))

        controller.pushLayer("menu")
        val menu = controller.anchor("menu-row", layerId = "menu")
        controller.register(menu)
        controller.claim(menu, FocusClaimSource.Gamepad)
        overlay.publish(entry(Any(), menu, left = 80f))
        assertEquals(80f, overlay.entry?.boundsInRoot?.left)

        controller.popLayer("menu")
        assertEquals(5f, overlay.entry?.boundsInRoot?.left)
    }

    private fun entry(
        token: Any,
        anchor: com.wajiha.input.FocusAnchor,
        left: Float,
    ): FocusRingOverlayEntry =
        FocusRingOverlayEntry(
            token = token,
            anchor = anchor,
            boundsInRoot = Rect(left, 0f, left + 20f, 20f),
            color = Color.White,
            thickness = 2.dp,
            borderStyle = FocusBorderStyle.Solid,
            shape = RectangleShape,
        )
}
