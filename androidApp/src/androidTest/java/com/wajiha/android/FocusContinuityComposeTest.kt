package com.wajiha.android

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.wajiha.input.FocusContinuityController
import com.wajiha.input.GamepadScreen
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.ui.components.gamepad.GamepadTile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FocusContinuityComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun touchClaimsTheSameSemanticTargetThatDrawsChrome() {
        var controller: FocusContinuityController? = null

        composeRule.setContent {
            GamepadScreen(layerId = "test") {
                controller = LocalFocusContinuityController.current
                GamepadTile(
                    selected = true,
                    onSelect = {},
                    onLaunch = {},
                    focusId = "tile-42",
                    modifier =
                        Modifier
                            .size(80.dp)
                            .testTag("tile"),
                ) {}
            }
        }

        composeRule
            .onNodeWithTag("tile")
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals("tile-42", controller?.activeAnchor?.targetId)
        }
    }
}
