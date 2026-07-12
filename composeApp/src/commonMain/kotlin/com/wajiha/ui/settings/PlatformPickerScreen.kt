package com.wajiha.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wajiha.data.db.PlatformEntity
import com.wajiha.input.GamepadNavHost
import com.wajiha.input.GamepadNavItem
import com.wajiha.input.GamepadNavMode
import com.wajiha.input.rememberGamepadNavController
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.GamepadSearchField
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Searchable catalog of all known platforms (Daijishō / iiSU index style).
 * Selecting one opens that platform's settings page — Settings itself only
 * lists platforms already in use.
 */
@Composable
fun PlatformPickerScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null
) {
    val allPlatforms by settingsViewModel.allPlatforms.collectAsState()
    val folders by settingsViewModel.folders.collectAsState()
    val inUseIds = remember(folders, allPlatforms) {
        settingsViewModel.inUsePlatformIds(allPlatforms, folders)
    }
    var query by remember { mutableStateOf("") }
    val navController = rememberGamepadNavController(
        mode = GamepadNavMode.Vertical,
        onBack = { onBack(); true }
    )

    val filtered = remember(allPlatforms, query) {
        val q = query.trim().lowercase()
        val base = if (q.isEmpty()) {
            allPlatforms
        } else {
            allPlatforms.filter {
                it.name.lowercase().contains(q) ||
                    it.shortName.lowercase().contains(q) ||
                    it.id.lowercase().contains(q)
            }
        }
        base.sortedWith(
            compareByDescending<PlatformEntity> { it.id in inUseIds }
                .thenBy { it.name.lowercase() }
        )
    }

    WajihaScreen(
        layerId = "platform_picker",
        modifier = modifier,
        onBack = onBack,
        showActionBar = true,
        gamepadHints = listOf(
            "A" to "Add/Open",
            "B" to "Back",
            "L2" to "Focus screen",
            "Search" to "A to edit"
        ),
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = {
            navController.focusState.focusedIndex = 0
        }
    ) {
        GamepadNavHost(controller = navController) {
            Column(modifier = Modifier.fillMaxSize()) {
                WajihaToolbar(title = "Add platform", onBack = onBack)

                GamepadSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search systems",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WajihaSpacing.md, vertical = WajihaSpacing.xs),
                    navItemId = "search"
                )

                Text(
                    text = "Pick a system, then set folders, emulator, scraper ids, and per-system scraper overrides.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = WajihaSpacing.md, vertical = WajihaSpacing.xs)
                )

                GamepadList(
                    items = filtered,
                    key = { it.id },
                    modifier = Modifier.weight(1f)
                ) { platform ->
                    GamepadNavItem(
                        onActivate = { onPick(platform.id) },
                        itemId = platform.id
                    ) {
                        PlatformPickRow(
                            platform = platform,
                            alreadyInUse = platform.id in inUseIds,
                            onClick = { onPick(platform.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformPickRow(
    platform: PlatformEntity,
    alreadyInUse: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WajihaSpacing.sm + WajihaSpacing.xs, horizontal = WajihaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(platform.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = platform.shortName.uppercase() +
                    if (alreadyInUse) " · already added" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (alreadyInUse) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Text(
            text = if (alreadyInUse) "Open" else "Add",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
