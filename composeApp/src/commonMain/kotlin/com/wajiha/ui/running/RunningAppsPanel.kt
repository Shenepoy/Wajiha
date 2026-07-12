package com.wajiha.ui.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wajiha.platform.AppActions
import com.wajiha.state.DualScreenStore
import com.wajiha.ui.components.SecondaryPanelScaffold
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.runningAppsGamepadHints
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Running apps panel (secondary-screen mode): recent foreground apps with
 * bring-to-front on either display and kill actions.
 */
@Composable
fun RunningAppsPanel(
    modifier: Modifier = Modifier,
    showGamepadHints: Boolean = true
) {
    val store = koinInject<DualScreenStore>()
    val appActions = koinInject<AppActions>()
    val apps by store.runningApps.collectAsState()
    val secondaryDisplayId by store.secondaryDisplayId.collectAsState()

    SecondaryPanelScaffold(
        modifier = modifier,
        showGamepadHints = showGamepadHints,
        hints = runningAppsGamepadHints
    ) {
        Text(
            text = "Running apps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = WajihaSpacing.sm)
        )
        GamepadList(
            items = apps,
            key = { it.packageName },
            modifier = Modifier.fillMaxSize(),
            emptyContent = {
                WajihaEmptyState(
                    title = "No running apps",
                    subtitle = "Grant usage access in onboarding to enable this panel.",
                    modifier = Modifier.fillMaxSize()
                )
            }
        ) { app ->
            Surface(
                shape = WajihaShapes.focus,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = WajihaSpacing.sm + WajihaSpacing.xs,
                        vertical = WajihaSpacing.sm
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (app.isGame) {
                            Text(
                                text = "Game",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    GamepadButton(
                        text = "Top",
                        onClick = { appActions.moveAppToDisplay(app.packageName, 0) },
                        outlined = true
                    )
                    secondaryDisplayId?.let { displayId ->
                        GamepadButton(
                            text = "Bottom",
                            onClick = { appActions.moveAppToDisplay(app.packageName, displayId) },
                            outlined = true,
                            modifier = Modifier.padding(start = WajihaSpacing.xs)
                        )
                    }
                    GamepadButton(
                        text = "Kill",
                        onClick = { appActions.killApp(app.packageName) },
                        outlined = true,
                        modifier = Modifier.padding(start = WajihaSpacing.xs)
                    )
                }
            }
        }
    }
}
