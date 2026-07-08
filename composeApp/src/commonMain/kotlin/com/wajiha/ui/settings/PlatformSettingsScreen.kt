package com.wajiha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.ui.components.GamepadSafeTextField
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

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text(
                text = platform?.name ?: "Platform",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
        }

        if (platform == null) {
            Text(
                text = "Platform not found",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("General", style = MaterialTheme.typography.titleSmall)
                }
                item {
                    NameFields(
                        platform = platform,
                        onNameCommit = viewModel::setDisplayName,
                        onShortNameCommit = viewModel::setShortName
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Enabled in library", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${state.gameCount} game(s) · ${state.folders.size} folder(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = platform.enabled,
                            onCheckedChange = viewModel::setEnabled
                        )
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    Text("Default emulator", style = MaterialTheme.typography.titleSmall)
                }
                item {
                    Text(
                        text = "Player used when launching games for this system " +
                            "(same idea as Daijishō / Cocoon players).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.emulators.isEmpty()) {
                    item {
                        Text(
                            text = "No emulators configured for this platform.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.emulators, key = { it.id }) { emulator ->
                        EmulatorChoiceRow(
                            emulator = emulator,
                            selected = platform.defaultEmulatorId == emulator.id ||
                                (platform.defaultEmulatorId == null && emulator.isDefault),
                            onSelect = { viewModel.setDefaultEmulator(emulator.id) }
                        )
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ROM folders", style = MaterialTheme.typography.titleSmall)
                        Row {
                            TextButton(onClick = viewModel::rescanPlatform) { Text("Rescan") }
                            Button(onClick = viewModel::pickRomFolder) { Text("Add folder") }
                        }
                    }
                }
                if (state.folders.isEmpty()) {
                    item {
                        Text(
                            text = "No folders yet — add a ROM folder to bring this " +
                                "system into your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.folders, key = { it.id }) { folder ->
                        FolderRow(
                            folder = folder,
                            onRemove = { viewModel.removeFolder(folder.id) }
                        )
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scraper ids", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = onOpenScraper) { Text("Scraper hub") }
                    }
                }
                item {
                    Text(
                        text = "ScreenScraper / RetroAchievements / Libretro names come from " +
                            "platform packs; tweak here if a match is wrong.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
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
    GamepadSafeTextField(
        value = name,
        onValueChange = { name = it },
        label = "Display name",
        modifier = Modifier.fillMaxWidth()
    )
    // Persist when the edited value differs and field settles
    LaunchedEffect(name) {
        if (name != platform.name && name.isNotBlank()) {
            kotlinx.coroutines.delay(450)
            if (name != platform.name) onNameCommit(name)
        }
    }
    GamepadSafeTextField(
        value = shortName,
        onValueChange = { shortName = it },
        label = "Short name",
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
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
        TextButton(onClick = onSelect) {
            Text(
                text = if (selected) "Default" else "Use",
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
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
        TextButton(onClick = onRemove) { Text("Remove") }
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

    GamepadSafeTextField(
        value = ss,
        onValueChange = { ss = it },
        label = "ScreenScraper system id",
        modifier = Modifier.fillMaxWidth()
    )
    LaunchedEffect(ss) {
        val current = platform.screenScraperId?.toString().orEmpty()
        if (ss != current) {
            kotlinx.coroutines.delay(450)
            if (ss != current) onScreenScraperId(ss)
        }
    }
    GamepadSafeTextField(
        value = ra,
        onValueChange = { ra = it },
        label = "RetroAchievements console id",
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    LaunchedEffect(ra) {
        val current = platform.raConsoleId?.toString().orEmpty()
        if (ra != current) {
            kotlinx.coroutines.delay(450)
            if (ra != current) onRaConsoleId(ra)
        }
    }
    GamepadSafeTextField(
        value = libretro,
        onValueChange = { libretro = it },
        label = "Libretro thumbnails name",
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    LaunchedEffect(libretro) {
        val current = platform.libretroName.orEmpty()
        if (libretro != current) {
            kotlinx.coroutines.delay(450)
            if (libretro != current) onLibretroName(libretro)
        }
    }
}
