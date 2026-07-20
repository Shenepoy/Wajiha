package com.wajiha.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecondaryNavigationStateTest {
    @Test
    fun rendererHandoffPreservesDetailRouteAndArguments() {
        val beforeHandoff =
            SecondaryNavigationState(
                route = SecondaryRoute.PlatformDetail,
                platformDetailId = "nintendo-3ds",
                platformDetailFromPicker = true,
            )

        val afterHandoff = beforeHandoff.copy()

        assertEquals(SecondaryRoute.PlatformDetail, afterHandoff.route)
        assertEquals("nintendo-3ds", afterHandoff.platformDetailId)
        assertTrue(afterHandoff.platformDetailFromPicker)
    }

    @Test
    fun gameDetailCanReturnToModesWithoutLeavingStaleId() {
        val detail =
            SecondaryNavigationState(
                route = SecondaryRoute.GameDetail,
                gameDetailId = 42L,
            )

        val modes = detail.copy(route = SecondaryRoute.Modes, gameDetailId = null)

        assertEquals(SecondaryRoute.Modes, modes.route)
        assertEquals(null, modes.gameDetailId)
    }
}
