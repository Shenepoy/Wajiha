package com.wajiha.ui.components.gamepad

import com.wajiha.input.GamepadHintButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingHintCapabilitiesTest {
    @Test
    fun focusedCapabilitiesOnlyAdvertiseSupportedActions() {
        val hints =
            settingsGamepadHints(
                capabilities =
                    SettingHintCapabilities(
                        primaryAction = "Rescan",
                        secondaryAction = "Edit",
                        canReset = false,
                    ),
            )

        assertEquals("Rescan", hints.first { it.button == GamepadHintButton.A }.action)
        assertEquals("Edit", hints.first { it.button == GamepadHintButton.X }.action)
        assertFalse(hints.any { it.button == GamepadHintButton.Y })
    }

    @Test
    fun adjustableResettableSettingReportsBothCapabilities() {
        val hints =
            settingsGamepadHints(
                capabilities =
                    SettingHintCapabilities(
                        primaryAction = "Increase",
                        canReset = true,
                        canAdjust = true,
                    ),
            )

        assertTrue(hints.any { it.button == GamepadHintButton.DpadLeftRight })
        assertTrue(hints.any { it.button == GamepadHintButton.Y })
    }
}
