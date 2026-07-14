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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.wajiha.data.db.PlatformEntity
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadNavHost
import com.wajiha.input.GamepadNavItem
import com.wajiha.input.GamepadNavMode
import com.wajiha.input.rememberGamepadNavController
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.GamepadSearchField
import com.wajiha.ui.components.gamepad.ProvideSettingsDensity
import com.wajiha.ui.components.gamepad.withoutDualScreenChrome
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

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
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
) {
    val dualStore = koinInject<DualScreenStore>()
    val screenState by dualStore.state.collectAsState()
    val isDual = screenState != DualScreenState.SingleDisplay
    val settings by settingsViewModel.settings.collectAsState()
    val allPlatforms by settingsViewModel.allPlatforms.collectAsState()
    val folders by settingsViewModel.folders.collectAsState()
    val inUseIds =
        remember(folders) {
            folders.map { it.platformId }.toSet()
        }
    var query by remember { mutableStateOf("") }
    val navController =
        rememberGamepadNavController(
            mode = GamepadNavMode.Vertical,
            onBack = {
                onBack()
                true
            },
        )

    val filtered =
        remember(allPlatforms, query) {
            val q = query.trim().lowercase()
            val base =
                if (q.isEmpty()) {
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
                    .thenBy { it.name.lowercase() },
            )
        }

    DisposableEffect(Unit) {
        onDispose { clearSettingsHero(dualStore) }
    }

    ProvideSettingsDensity {
        WajihaScreen(
            layerId = "platform_picker",
            modifier = modifier,
            onBack = onBack,
            showActionBar = true,
            gamepadHints =
                listOf(
                    GamepadHint(GamepadHintButton.A, "Select"),
                    GamepadHint(GamepadHintButton.B, "Back"),
                    GamepadHint(GamepadHintButton.L2, "Focus screen"),
                ).withoutDualScreenChrome(isDual),
            gamepadOwner = gamepadOwner,
            onClaimGamepad = onClaimGamepad,
            onOwnerGainedFocus = {
                navController.focusState.focusedIndex = 0
            },
        ) {
            GamepadNavHost(controller = navController) {
                Column(modifier = Modifier.fillMaxSize()) {
                    WajihaToolbar(title = "Add platform", onBack = onBack)

                    GamepadSearchField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Search systems",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = WajihaSpacing.md, vertical = WajihaSpacing.xs),
                        navItemId = "search",
                    )

                    Text(
                        text =
                            "Pick a system to open its settings. Add a ROM folder on the " +
                                "Folders tab to list it under Settings → Library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                horizontal = WajihaSpacing.md,
                                vertical = WajihaSpacing.xs,
                            ),
                    )

                    GamepadList(
                        items = filtered,
                        key = { it.id },
                        modifier = Modifier.weight(1f),
                    ) { platform ->
                        val alreadyInUse = platform.id in inUseIds
                        GamepadNavItem(
                            onActivate = { onPick(platform.id) },
                            itemId = platform.id,
                        ) { highlighted ->
                            LaunchedEffect(highlighted, platform.id, settings.settingsHeroActions, alreadyInUse) {
                                if (highlighted) {
                                    publishSettingsHero(
                                        store = dualStore,
                                        detail =
                                            pickerRowHeroDetail(
                                                name = platform.name,
                                                alreadyInUse = alreadyInUse,
                                                actionsEnabled = settings.settingsHeroActions,
                                            ),
                                        onPrimaryAction = { onPick(platform.id) },
                                    )
                                }
                            }
                            PlatformPickRow(
                                platform = platform,
                                alreadyInUse = alreadyInUse,
                            )
                        }
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
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = WajihaSpacing.sm, horizontal = WajihaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platform.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text =
                    platform.shortName.uppercase() +
                        if (alreadyInUse) " · already in library" else "",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (alreadyInUse) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        Text(
            text = if (alreadyInUse) "Open" else "Configure",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
