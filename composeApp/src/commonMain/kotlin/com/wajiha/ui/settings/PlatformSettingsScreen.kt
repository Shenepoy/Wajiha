package com.wajiha.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaSection
import com.wajiha.ui.components.WajihaSectionDivider
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadForm
import com.wajiha.ui.components.gamepad.GamepadFormField
import com.wajiha.ui.components.gamepad.GamepadSwitch
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Per-platform edit page: display names, default emulator (player),
 * scraper linkage ids from the Daijishō schema, ROM folders, and enable.
 */
@Composable
fun PlatformSettingsScreen(
    platformId: String,
    onBack: () -> Unit,
    onOpenScraper: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlatformSettingsViewModel = koinInject()
) {
    LaunchedEffect(platformId) { viewModel.open(platformId) }
    val state by viewModel.uiState.collectAsState()
    val platform = state.platform

    WajihaScreen(
        layerId = "platform_settings",
        modifier = modifier,
        onBack = onBack,
        showActionBar = true,
        gamepadHints = listOf("A" to "Confirm", "B" to "Back")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WajihaToolbar(
                title = platform?.name ?: "Platform",
                onBack = onBack
            )

            if (platform == null) {
                Text(
                    text = "Platform not found",
                    modifier = Modifier.padding(WajihaSpacing.md),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GamepadForm(modifier = Modifier.fillMaxSize()) {
                    WajihaSection(title = "General") {
                        NameFields(
                            platform = platform,
                            onNameCommit = viewModel::setDisplayName,
                            onShortNameCommit = viewModel::setShortName
                        )
                        GamepadSwitch(
                            label = "Enabled in library",
                            checked = platform.enabled,
                            onCheckedChange = viewModel::setEnabled
                        )
                        Text(
                            text = "${state.gameCount} game(s) · ${state.folders.size} folder(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    WajihaSectionDivider()
                    WajihaSection(title = "Default emulator") {
                        Text(
                            text = "Player used when launching games for this system " +
                                "(same idea as Daijishō / Cocoon players).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.emulators.isEmpty()) {
                            Text(
                                text = "No emulators configured for this platform.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.emulators.forEach { emulator ->
                                EmulatorChoiceRow(
                                    emulator = emulator,
                                    selected = platform.defaultEmulatorId == emulator.id ||
                                        (platform.defaultEmulatorId == null && emulator.isDefault),
                                    onSelect = { viewModel.setDefaultEmulator(emulator.id) }
                                )
                            }
                        }
                    }

                    WajihaSectionDivider()
                    WajihaSection(title = "ROM folders") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GamepadButton(
                                text = "Rescan",
                                onClick = viewModel::rescanPlatform,
                                outlined = true
                            )
                            GamepadButton(
                                text = "Add folder",
                                onClick = viewModel::pickRomFolder,
                                modifier = Modifier.padding(start = WajihaSpacing.sm)
                            )
                        }
                        if (state.folders.isEmpty()) {
                            Text(
                                text = "No folders yet — add a ROM folder to bring this " +
                                    "system into your library.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.folders.forEach { folder ->
                                FolderRow(
                                    folder = folder,
                                    onRemove = { viewModel.removeFolder(folder.id) }
                                )
                            }
                        }
                    }

                    WajihaSectionDivider()
                    WajihaSection(title = "Scraper ids") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            GamepadButton(
                                text = "Scraper hub",
                                onClick = onOpenScraper,
                                outlined = true
                            )
                        }
                        Text(
                            text = "ScreenScraper / RetroAchievements / Libretro names come from " +
                                "platform packs; tweak here if a match is wrong.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ScraperIdFields(
                            platform = platform,
                            onScreenScraperId = viewModel::setScreenScraperId,
                            onRaConsoleId = viewModel::setRaConsoleId,
                            onLibretroName = viewModel::setLibretroName
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NameFields(
    platform: PlatformEntity,
    onNameCommit: (String) -> Unit,
    onShortNameCommit: (String) -> Unit
) {
    var name by remember(platform.id, platform.name) { mutableStateOf(platform.name) }
    var shortName by remember(platform.id, platform.shortName) {
        mutableStateOf(platform.shortName)
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = name,
            onValueChange = { name = it },
            label = "Display name",
            modifier = Modifier.fillMaxWidth()
        )
    }
    LaunchedEffect(name) {
        if (name != platform.name && name.isNotBlank()) {
            kotlinx.coroutines.delay(450)
            if (name != platform.name) onNameCommit(name)
        }
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = shortName,
            onValueChange = { shortName = it },
            label = "Short name",
            modifier = Modifier.fillMaxWidth()
        )
    }
    LaunchedEffect(shortName) {
        if (shortName != platform.shortName && shortName.isNotBlank()) {
            kotlinx.coroutines.delay(450)
            if (shortName != platform.shortName) onShortNameCommit(shortName)
        }
    }
}

@Composable
private fun EmulatorChoiceRow(
    emulator: EmulatorEntity,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(emulator.name, style = MaterialTheme.typography.bodyLarge)
            val detail = buildString {
                append(emulator.packageNames.split(',').firstOrNull()?.trim().orEmpty())
                emulator.libretroCore?.let { append(" · $it") }
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        GamepadButton(
            text = if (selected) "Default" else "Use",
            onClick = onSelect,
            outlined = !selected
        )
    }
}

@Composable
private fun FolderRow(folder: RomFolderEntity, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = folder.treeUri.substringAfterLast("%3A").substringAfterLast(':'),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        GamepadButton(text = "Remove", onClick = onRemove, outlined = true)
    }
}

@Composable
private fun ScraperIdFields(
    platform: PlatformEntity,
    onScreenScraperId: (String) -> Unit,
    onRaConsoleId: (String) -> Unit,
    onLibretroName: (String) -> Unit
) {
    var ss by remember(platform.id, platform.screenScraperId) {
        mutableStateOf(platform.screenScraperId?.toString().orEmpty())
    }
    var ra by remember(platform.id, platform.raConsoleId) {
        mutableStateOf(platform.raConsoleId?.toString().orEmpty())
    }
    var libretro by remember(platform.id, platform.libretroName) {
        mutableStateOf(platform.libretroName.orEmpty())
    }

    GamepadFormField {
        GamepadSafeTextField(
            value = ss,
            onValueChange = { ss = it },
            label = "ScreenScraper system id",
            modifier = Modifier.fillMaxWidth()
        )
    }
    LaunchedEffect(ss) {
        val current = platform.screenScraperId?.toString().orEmpty()
        if (ss != current) {
            kotlinx.coroutines.delay(450)
            if (ss != current) onScreenScraperId(ss)
        }
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = ra,
            onValueChange = { ra = it },
            label = "RetroAchievements console id",
            modifier = Modifier.fillMaxWidth()
        )
    }
    LaunchedEffect(ra) {
        val current = platform.raConsoleId?.toString().orEmpty()
        if (ra != current) {
            kotlinx.coroutines.delay(450)
            if (ra != current) onRaConsoleId(ra)
        }
    }
    GamepadFormField {
        GamepadSafeTextField(
            value = libretro,
            onValueChange = { libretro = it },
            label = "Libretro thumbnails name",
            modifier = Modifier.fillMaxWidth()
        )
    }
    LaunchedEffect(libretro) {
        val current = platform.libretroName.orEmpty()
        if (libretro != current) {
            kotlinx.coroutines.delay(450)
            if (libretro != current) onLibretroName(libretro)
        }
    }
}
