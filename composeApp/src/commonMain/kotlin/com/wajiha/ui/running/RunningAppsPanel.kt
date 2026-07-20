package com.wajiha.ui.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.wajiha.platform.AppActions
import com.wajiha.state.DualScreenStore
import com.wajiha.state.RunningApp
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.GamepadSettingTrailingActions
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.WajihaSettingBlurb
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Running apps section content for folder chrome: bring-to-front / kill.
 * Parent owns [com.wajiha.ui.components.gamepad.WajihaSettingPanel].
 */
@Composable
fun RunningAppsPanel(initialFocusRequester: FocusRequester? = null) {
    val store = koinInject<DualScreenStore>()
    val appActions = koinInject<AppActions>()
    val apps by store.runningApps.collectAsState()
    val secondaryDisplayId by store.secondaryDisplayId.collectAsState()
    val dual = store.isDualLayout()
    val bottomId = secondaryDisplayId?.takeIf { dual }

    WajihaSettingBlurb(
        if (dual) {
            "Bring an app to the top or bottom display, or force-stop it."
        } else {
            "Bring an app to the front, or force-stop it."
        },
    )
    GamepadList(
        items = apps,
        key = { it.packageName },
        modifier = Modifier.fillMaxSize(),
        emptyContent = {
            WajihaEmptyState(
                title = "No running apps",
                subtitle = "Grant usage access in onboarding to enable this panel.",
                modifier = Modifier.fillMaxSize(),
            )
        },
    ) { app ->
        RunningAppRow(
            app = app,
            isFirst = app.packageName == apps.firstOrNull()?.packageName,
            bottomId = bottomId,
            appActions = appActions,
            initialFocusRequester = initialFocusRequester,
        )
    }
}

@Composable
private fun RunningAppRow(
    app: RunningApp,
    isFirst: Boolean,
    bottomId: Int?,
    appActions: AppActions,
    initialFocusRequester: FocusRequester?,
) {
    val bringTop = { appActions.moveAppToDisplay(app.packageName, 0) }
    val kill = { appActions.killApp(app.packageName) }
    GamepadSettingRow(
        label = app.label,
        labelMeta = if (app.isGame) "Game" else null,
        type = SettingType.Action,
        focusId = "running_${app.packageName}",
        focusRequester = initialFocusRequester.takeIf { isFirst },
        onActivate = bringTop,
        onSecondaryActivate = kill,
        content = {
            if (bottomId != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                ) {
                    GamepadButton(
                        text = "Kill",
                        onClick = kill,
                        outlined = true,
                        gamepadFocusable = false,
                    )
                    GamepadButton(
                        text = "Bottom",
                        onClick = {
                            appActions.moveAppToDisplay(app.packageName, bottomId)
                        },
                        outlined = true,
                        gamepadFocusable = false,
                    )
                    GamepadButton(
                        text = "Top",
                        onClick = bringTop,
                        outlined = true,
                        gamepadFocusable = false,
                    )
                }
            } else {
                GamepadSettingTrailingActions(
                    secondaryLabel = "Kill",
                    onSecondaryClick = kill,
                    primaryLabel = "Top",
                    onPrimaryClick = bringTop,
                    showHints = true,
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
    )
}
