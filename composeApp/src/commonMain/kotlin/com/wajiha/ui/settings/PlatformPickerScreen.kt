package com.wajiha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wajiha.data.db.PlatformEntity

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
    modifier: Modifier = Modifier
) {
    val allPlatforms by settingsViewModel.allPlatforms.collectAsState()
    val folders by settingsViewModel.folders.collectAsState()
    val inUseIds = remember(folders, allPlatforms) {
        settingsViewModel.inUsePlatformIds(allPlatforms, folders)
    }
    var query by remember { mutableStateOf("") }

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

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text(
                text = "Add platform",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search systems") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Text(
            text = "Pick a system, then set folders, emulator, and scraper ids.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered, key = { it.id }) { platform ->
                PlatformPickRow(
                    platform = platform,
                    alreadyInUse = platform.id in inUseIds,
                    onClick = { onPick(platform.id) }
                )
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
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
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
