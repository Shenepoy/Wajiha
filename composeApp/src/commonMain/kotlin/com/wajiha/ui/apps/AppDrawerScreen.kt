package com.wajiha.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.wajiha.input.GamepadKeys
import com.wajiha.platform.LaunchableApp
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/** App drawer: same NeoStation select/confirm model as game tiles. */
@Composable
fun AppDrawerScreen(
    apps: List<LaunchableApp>,
    onLoad: () -> Unit,
    onLaunch: (String) -> Unit,
    onBack: () -> Unit,
    onFocusChange: (LaunchableApp?) -> Unit = {},
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onLoad() }

    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var aTileHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(apps.size, selectedPackage) {
        val focused = apps.firstOrNull { it.packageName == selectedPackage }
        onFocusChange(focused)
    }

    WajihaScreen(
        layerId = "app_drawer",
        modifier = modifier,
        showActionBar = true,
        gamepadHints = listOf("A" to "Open app", "B" to "Back"),
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onPreviewKey = { event ->
            val pkg = selectedPackage
            if (
                aTileHasFocus &&
                pkg != null &&
                GamepadKeys.isConfirm(event.type, event.key)
            ) {
                onLaunch(pkg)
                true
            } else {
                false
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WajihaSpacing.sm + WajihaSpacing.xs, vertical = WajihaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("< Back") }
                Text(
                    text = "Apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = WajihaSpacing.sm)
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(WajihaSpacing.touchMin + WajihaSpacing.xl + WajihaSpacing.sm),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(WajihaSpacing.sm + WajihaSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        selected = app.packageName == selectedPackage,
                        onSelect = {
                            selectedPackage = app.packageName
                            onFocusChange(app)
                        },
                        onLaunch = { onLaunch(app.packageName) },
                        onTileFocusChanged = { focused ->
                            if (focused) {
                                aTileHasFocus = true
                                selectedPackage = app.packageName
                                onFocusChange(app)
                            } else {
                                aTileHasFocus = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    selected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onTileFocusChanged: (Boolean) -> Unit
) {
    GamepadTile(
        selected = selected,
        onSelect = onSelect,
        onLaunch = onLaunch,
        onFocusChanged = onTileFocusChanged,
        modifier = Modifier.padding(WajihaSpacing.sm)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(WajihaSpacing.touchMin + WajihaSpacing.sm)
                    .clip(WajihaShapes.tile)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val icon = app.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = app.label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = WajihaSpacing.xs)
            )
        }
    }
}
