package com.wajiha.ui.scraper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.input.GamepadNavHost
import com.wajiha.input.GamepadNavItem
import com.wajiha.input.GamepadNavMode
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.rememberGamepadNavController
import com.wajiha.ui.components.WajihaLoadingState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadFocusable
import com.wajiha.ui.components.gamepad.GamepadForm
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.GamepadSwitch
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Standalone scraper page (e.g. opened from per-platform settings).
 * Settings embeds the same content via [scraperSettingsItems].
 */
@Composable
fun ScraperScreen(
    viewModel: ScraperViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberGamepadNavController(
        mode = GamepadNavMode.Vertical,
        onBack = { onBack(); true }
    )
    WajihaScreen(
        layerId = "scraper",
        modifier = modifier,
        onBack = onBack,
        showActionBar = true,
        gamepadHints = listOf("A" to "Confirm", "B" to "Back")
    ) {
        GamepadNavHost(controller = navController) {
            Column(modifier = Modifier.fillMaxSize()) {
                WajihaToolbar(title = "Scraper", onBack = onBack)
                GamepadForm(modifier = Modifier.fillMaxSize()) {
                    ScraperSettingsColumn(viewModel)
                }
            }
        }
    }
}

/** Column embed for Settings master-detail scroll (non-lazy). */
@Composable
fun ScraperSettingsColumn(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        Text("Sources & credentials", style = MaterialTheme.typography.titleSmall)
        ScraperSourcesBlock(viewModel, firstFocusRequester)
        HorizontalDivider(Modifier.padding(vertical = WajihaSpacing.xs))
        Text("Batch scrape", style = MaterialTheme.typography.titleSmall)
        ScraperBatchBlock(viewModel)
        HorizontalDivider(Modifier.padding(vertical = WajihaSpacing.xs))
        Text("Manual match", style = MaterialTheme.typography.titleSmall)
        ScraperManualBlock(viewModel)
    }
}

/** Embeddable scraper hub for Settings master-detail scroll. */
fun LazyListScope.scraperSettingsItems(viewModel: ScraperViewModel) {
    item(key = "scraper_sources_hdr") {
        Text("Sources & credentials", style = MaterialTheme.typography.titleSmall)
    }
    scraperSourcesItems(viewModel)
    item(key = "scraper_batch_hdr") {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Batch scrape", style = MaterialTheme.typography.titleSmall)
    }
    scraperBatchItems(viewModel)
    item(key = "scraper_manual_hdr") {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Manual match", style = MaterialTheme.typography.titleSmall)
    }
    item(key = "scraper_manual_body") {
        ScraperManualBlock(viewModel)
    }
}

private fun LazyListScope.scraperSourcesItems(viewModel: ScraperViewModel) {
    item { ScraperSourcesBlock(viewModel) }
}

@Composable
private fun ScraperSourcesBlock(
    viewModel: ScraperViewModel,
    firstFocusRequester: FocusRequester? = null
) {
    val settings by viewModel.settings.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ScreenScraper", style = MaterialTheme.typography.labelLarge)
        SourceToggle(
            label = "Enabled",
            checked = "screenscraper" in settings.enabledSources,
            focusRequester = firstFocusRequester
        ) { viewModel.toggleSource("screenscraper", it) }
        CredentialField("Username", settings.screenScraperUser) { v ->
            viewModel.update { it.copy(screenScraperUser = v) }
        }
        CredentialField("Password", settings.screenScraperPassword, secret = true) { v ->
            viewModel.update { it.copy(screenScraperPassword = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("SteamGridDB", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Enabled", "steamgriddb" in settings.enabledSources) {
            viewModel.toggleSource("steamgriddb", it)
        }
        CredentialField("API key", settings.steamGridDbApiKey, secret = true) { v ->
            viewModel.update { it.copy(steamGridDbApiKey = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("Libretro thumbnails", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Enabled (no account needed)", "libretro" in settings.enabledSources) {
            viewModel.toggleSource("libretro", it)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("RetroAchievements", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Enabled", "ra" in settings.enabledSources) {
            viewModel.toggleSource("ra", it)
        }
        CredentialField("Username", settings.raUsername) { v ->
            viewModel.update { it.copy(raUsername = v) }
        }
        CredentialField("Web API key", settings.raApiKey, secret = true) { v ->
            viewModel.update { it.copy(raApiKey = v) }
        }
        RaLoginRow()
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("RomM server", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Enabled", "romm" in settings.enabledSources) {
            viewModel.toggleSource("romm", it)
        }
        CredentialField("Server URL", settings.rommUrl) { v ->
            viewModel.update { it.copy(rommUrl = v) }
        }
        CredentialField("Username", settings.rommUsername) { v ->
            viewModel.update { it.copy(rommUsername = v) }
        }
        CredentialField("Password", settings.rommPassword, secret = true) { v ->
            viewModel.update { it.copy(rommPassword = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("Local media", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Enabled", "local" in settings.enabledSources) {
            viewModel.toggleSource("local", it)
        }
        CredentialField("Media folder path (ES-DE layout)", settings.localMediaPath) { v ->
            viewModel.update { it.copy(localMediaPath = v) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("Options", style = MaterialTheme.typography.labelLarge)
        SourceToggle("Wi-Fi only", settings.wifiOnly) { v ->
            viewModel.update { it.copy(wifiOnly = v) }
        }
        SourceToggle("Skip already-scraped games", settings.skipAlreadyScraped) { v ->
            viewModel.update { it.copy(skipAlreadyScraped = v) }
        }
        SourceToggle(
            "Overwrite existing media",
            settings.existingMediaPolicy == "overwrite"
        ) { v ->
            viewModel.update { it.copy(existingMediaPolicy = if (v) "overwrite" else "skip") }
        }
        CredentialField(
            "Region priority (comma separated)",
            settings.regionPriority.joinToString(",")
        ) { v ->
            viewModel.update {
                it.copy(regionPriority = v.split(',').map(String::trim).filter(String::isNotEmpty))
            }
        }
        CredentialField(
            "Language priority (comma separated)",
            settings.languagePriority.joinToString(",")
        ) { v ->
            viewModel.update {
                it.copy(languagePriority = v.split(',').map(String::trim).filter(String::isNotEmpty))
            }
        }
        CredentialField(
            "Max image resolution (longest edge px, 0 = keep original)",
            settings.maxImageResolution.toString()
        ) { v ->
            v.trim().toIntOrNull()?.let { px ->
                viewModel.update { it.copy(maxImageResolution = px.coerceIn(0, 8192)) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text(
            "Media priority (tap a source to promote it)",
            style = MaterialTheme.typography.labelLarge
        )
        settings.mediaPriority.entries.forEach { (mediaType, chain) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mediaType,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(96.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
                    items(chain) { sourceId ->
                        GamepadChip(
                            label = sourceId,
                            selected = false,
                            onClick = { viewModel.promoteMediaSource(mediaType, sourceId) }
                        )
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("Per-platform overrides", style = MaterialTheme.typography.labelLarge)
        PlatformOverridesEditor(viewModel)
    }
}

private val allSourceIds =
    listOf("screenscraper", "steamgriddb", "libretro", "ra", "romm", "local")

@Composable
private fun PlatformOverridesEditor(viewModel: ScraperViewModel) {
    val settings by viewModel.settings.collectAsState()
    val inUsePlatforms by viewModel.inUsePlatforms.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }

    if (inUsePlatforms.isEmpty()) {
        Text(
            text = "Add a platform to configure per-system scraper overrides.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
            items(inUsePlatforms, key = { it.id }) { platform ->
                val hasOverride = platform.id in settings.platformOverrides
                GamepadChip(
                    label = platform.shortName.uppercase() + if (hasOverride) " *" else "",
                    selected = selectedId == platform.id,
                    onClick = {
                        selectedId = if (selectedId == platform.id) null else platform.id
                    }
                )
            }
        }

        val platformId = selectedId ?: return@Column
        val override = settings.platformOverrides[platformId]
        val effectiveSources = override?.enabledSources ?: settings.enabledSources

        Text(
            text = "Sources for this platform" +
                if (override?.enabledSources == null) " (inheriting global)" else "",
            style = MaterialTheme.typography.labelMedium
        )
        allSourceIds.forEach { sourceId ->
            SourceToggle(sourceId, sourceId in effectiveSources) { enabled ->
                viewModel.togglePlatformSource(platformId, sourceId, enabled)
            }
        }
        CredentialField(
            "Region priority for this platform (empty = global)",
            override?.regionPriority?.joinToString(",") ?: ""
        ) { v ->
            viewModel.setPlatformRegionPriority(
                platformId,
                v.split(',').map(String::trim).filter(String::isNotEmpty)
            )
        }
        if (override != null) {
            TextButton(onClick = { viewModel.clearPlatformOverride(platformId) }) {
                Text("Clear override (use global settings)")
            }
        }
    }
}

@Composable
private fun RaLoginRow() {
    val raViewModel = org.koin.compose.koinInject<com.wajiha.ui.ra.RaViewModel>()
    val raState by raViewModel.state.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = raViewModel::login, enabled = !raState.loading) {
            Text("Test login")
        }
        raState.profile?.let {
            Text(
                text = "Logged in as ${it.user} (${it.totalPoints} pts)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        raState.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SourceToggle(
    label: String,
    checked: Boolean,
    focusRequester: FocusRequester? = null,
    onChecked: (Boolean) -> Unit
) {
    val navId = remember(label) { "toggle_$label" }
    if (LocalGamepadNavController.current != null) {
        GamepadNavItem(
            onActivate = { onChecked(!checked) },
            itemId = navId
        ) {
            GamepadSwitch(
                label = label,
                checked = checked,
                onCheckedChange = onChecked,
                focusRequester = focusRequester
            )
        }
    } else {
        GamepadSwitch(
            label = label,
            checked = checked,
            onCheckedChange = onChecked,
            focusRequester = focusRequester
        )
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    GamepadSafeTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = label,
        secret = secret,
        navItemId = "field_$label",
        modifier = Modifier.fillMaxWidth()
    )
}

private fun LazyListScope.scraperBatchItems(viewModel: ScraperViewModel) {
    item { ScraperBatchBlock(viewModel) }
}

@Composable
private fun ScraperBatchBlock(viewModel: ScraperViewModel) {
    val progress by viewModel.progress.collectAsState()
    val inUsePlatforms by viewModel.inUsePlatforms.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (progress.running) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = progress.currentGameName?.let { "Scraping: $it" } ?: "Scraping…",
                    style = MaterialTheme.typography.bodyLarge
                )
                LinearProgressIndicator(
                    progress = {
                        if (progress.total == 0) 0f else progress.done.toFloat() / progress.total
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${progress.done} / ${progress.total} — " +
                        "${progress.matched} matched, ${progress.failed} failed",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (progress.paused) {
                        Button(onClick = viewModel::resumeBatch) { Text("Resume") }
                    } else {
                        Button(onClick = viewModel::pauseBatch) { Text("Pause") }
                    }
                    TextButton(onClick = viewModel::cancelBatch) { Text("Cancel") }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.startBatch(null) }) { Text("Scrape all platforms") }
                if (progress.done > 0) {
                    Text(
                        text = "Last run: ${progress.matched} matched, ${progress.failed} failed",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        if (inUsePlatforms.isNotEmpty()) {
            HorizontalDivider()
            Text("Per platform", style = MaterialTheme.typography.labelMedium)
            inUsePlatforms.forEach { platform ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(platform.name, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = { viewModel.startBatch(platform.id) },
                        enabled = !progress.running
                    ) { Text("Scrape") }
                }
            }
        } else if (!progress.running) {
            Text(
                text = "Add a platform to scrape per-system batches.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScraperManualBlock(viewModel: ScraperViewModel) {
    val manual by viewModel.manual.collectAsState()
    val results by viewModel.gameResults.collectAsState()
    val game = manual.game

    if (game == null) {
        var query by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GamepadSafeTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.searchLibrary(it)
                },
                label = "Search your library",
                modifier = Modifier.fillMaxWidth()
            )
            GamepadList(
                items = results,
                key = { it.id },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) { g ->
                ScraperGamePickRow(
                    title = g.displayName,
                    subtitle = "${g.platformId} — ${g.fileName}" +
                        if (g.scrapedAt != null) " — scraped" else "",
                    onActivate = { viewModel.selectGame(g) }
                )
            }
        }
        return
    }

    var searchName by remember(game.id) { mutableStateOf(game.displayName) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(game.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = game.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = viewModel::clearSelection) { Text("Change game") }
        }
        Button(onClick = viewModel::rescrapeSelected, enabled = !manual.applying) {
            Text("Auto scrape")
        }
        manual.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (manual.applying || manual.searching) {
            WajihaLoadingState(message = if (manual.searching) "Searching…" else "Applying…")
        }

        if (manual.media.isNotEmpty()) {
            Text("Current media", style = MaterialTheme.typography.labelLarge)
            manual.media.forEach { media ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AsyncImage(
                        model = media.localPath ?: media.remoteUrl,
                        contentDescription = media.type,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(media.type, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = media.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { viewModel.deleteMedia(media) }) { Text("Delete") }
                }
            }
            HorizontalDivider()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GamepadSafeTextField(
                value = searchName,
                onValueChange = { searchName = it },
                label = "Search sources by name",
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.searchSources(searchName) },
                enabled = !manual.searching
            ) { Text("Search") }
        }
        manual.candidates.forEach { candidate ->
            CandidateRow(candidate = candidate, onApply = { viewModel.applyCandidate(candidate) })
        }
    }
}

@Composable
private fun ScraperGamePickRow(
    title: String,
    subtitle: String,
    onActivate: () -> Unit
) {
    GamepadFocusable(
        onClick = onActivate,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WajihaSpacing.sm, horizontal = WajihaSpacing.xs)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CandidateRow(candidate: ScrapeCandidate, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = candidate.thumbnailUrl ?: candidate.media.firstOrNull()?.url,
            contentDescription = candidate.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${candidate.sourceId} — ${candidate.media.size} media",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onApply) { Text("Apply") }
    }
}
