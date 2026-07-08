package com.wajiha.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.GameEntity
import com.wajiha.data.scraper.MediaType
import com.wajiha.input.GamepadKeys
import com.wajiha.ui.components.FolderTabRow
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadSwitch
import com.wajiha.ui.components.gamepad.gameDetailGamepadHints
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

private enum class ActionPaneSection(val label: String) {
    Launch("Launch"),
    Emulator("Emulator"),
    Scraper("Scraper")
}

@Composable
fun GameDetailScreen(
    gameId: Long,
    secondaryDisplayId: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    dualDisplay: Boolean = false,
    viewModel: GameDetailViewModel = koinInject()
) {
    LaunchedEffect(gameId) { viewModel.open(gameId) }
    val state by viewModel.uiState.collectAsState()
    val game = state.game

    val sections = ActionPaneSection.entries
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    val sectionFocus = remember { FocusRequester() }

    fun selectSection(index: Int) {
        selectedSectionIndex = index.coerceIn(0, sections.lastIndex)
    }

    LaunchedEffect(selectedSectionIndex) {
        try {
            sectionFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    WajihaScreen(
        layerId = "game_detail_$gameId",
        modifier = modifier,
        onBack = onBack,
        showActionBar = true,
        gamepadHints = gameDetailGamepadHints,
        onPreviewKey = { event ->
            when {
                GamepadKeys.isL1(event.type, event.key) -> {
                    if (selectedSectionIndex > 0) {
                        selectSection(selectedSectionIndex - 1)
                        true
                    } else false
                }
                GamepadKeys.isR1(event.type, event.key) -> {
                    if (selectedSectionIndex < sections.lastIndex) {
                        selectSection(selectedSectionIndex + 1)
                        true
                    } else false
                }
                else -> false
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WajihaToolbar(
                title = game?.displayName ?: "Game",
                onBack = onBack,
                backFocusable = false
            )

            if (game == null) {
                Text(
                    text = "Game not found",
                    modifier = Modifier.padding(WajihaSpacing.md),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (dualDisplay) {
                GameDetailTabsPane(
                    game = game,
                    state = state,
                    sections = sections,
                    selectedSectionIndex = selectedSectionIndex,
                    onSelectSection = ::selectSection,
                    secondaryDisplayId = secondaryDisplayId,
                    sectionFocus = sectionFocus,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = WajihaSpacing.md)
                        .padding(bottom = WajihaSpacing.md)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = WajihaSpacing.md)
                        .padding(bottom = WajihaSpacing.md)
                ) {
                    GameDetailHero(
                        game = game,
                        platformName = state.platform?.name,
                        boxartPath = state.media.firstOrNull { it.type == "boxart" }?.localPath,
                        onSetFavorite = viewModel::setFavorite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = WajihaSpacing.xs)
                    )
                    GameDetailSectionCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.34f)
                    ) {
                        GameDetailMetadataPanel(
                            game = game,
                            platformName = state.platform?.name,
                            totalPlaytimeSec = state.totalPlaytimeSec,
                            style = MetadataPanelStyle.Compact
                        )
                    }
                    GameDetailTabsPane(
                        game = game,
                        state = state,
                        sections = sections,
                        selectedSectionIndex = selectedSectionIndex,
                        onSelectSection = ::selectSection,
                        secondaryDisplayId = secondaryDisplayId,
                        sectionFocus = sectionFocus,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = WajihaSpacing.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDetailTabsPane(
    game: GameEntity,
    state: GameDetailUiState,
    sections: List<ActionPaneSection>,
    selectedSectionIndex: Int,
    onSelectSection: (Int) -> Unit,
    secondaryDisplayId: Int?,
    sectionFocus: FocusRequester,
    viewModel: GameDetailViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FolderTabRow(
            tabs = sections.map { it.label },
            selectedIndex = selectedSectionIndex,
            onSelect = onSelectSection,
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false }
                .padding(top = WajihaSpacing.xs)
        )

        GameDetailSectionCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset(y = (-1).dp)
        ) {
            when (sections[selectedSectionIndex]) {
                ActionPaneSection.Launch -> LaunchSectionContent(
                    game = game,
                    secondaryDisplayId = secondaryDisplayId,
                    onSetLaunchDisplay = viewModel::setLaunchOnDisplay,
                    onLaunchTop = { viewModel.launchOnDisplay(0) },
                    onLaunchBottom = {
                        viewModel.launchOnDisplay(secondaryDisplayId ?: 4)
                    },
                    firstFocusRequester = sectionFocus
                )
                ActionPaneSection.Emulator -> EmulatorSectionContent(
                    emulators = state.emulators,
                    selectedId = game.emulatorOverrideId,
                    onSelect = viewModel::setEmulatorOverride,
                    firstFocusRequester = sectionFocus
                )
                ActionPaneSection.Scraper -> ScraperSectionContent(
                    state = state,
                    onRescrape = { viewModel.rescrape() },
                    onRescrapeFrom = viewModel::rescrape,
                    onScrapeMedia = viewModel::scrapeMediaOnly,
                    firstFocusRequester = sectionFocus
                )
            }

            state.scrapeMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = WajihaSpacing.sm)
                )
            }
            state.actionError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = WajihaSpacing.xs)
                )
            }
        }
    }
}

@Composable
private fun GameDetailHero(
    game: GameEntity,
    platformName: String?,
    boxartPath: String?,
    onSetFavorite: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = WajihaShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WajihaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(3f / 4f)
                    .clip(WajihaShapes.tile)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (boxartPath != null) {
                    AsyncImage(
                        model = boxartPath,
                        contentDescription = game.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = game.displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = game.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            PlatformBadge(label = platformName ?: game.platformId)
            GamepadSwitch(
                label = "Favorite",
                checked = game.favorite,
                onCheckedChange = onSetFavorite,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlatformBadge(label: String) {
    Surface(
        shape = WajihaShapes.chip,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = WajihaSpacing.sm,
                vertical = WajihaSpacing.xs / 2
            )
        )
    }
}

@Composable
private fun GameDetailSectionCard(
    modifier: Modifier = Modifier,
    sectionContent: @Composable () -> Unit
) {
    val shape = WajihaShapes.folderPanel
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = outlineColor, shape = shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WajihaSpacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
        ) {
            sectionContent()
        }
    }
}

@Composable
private fun SectionBlurb(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = WajihaSpacing.xs)
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        modifier = Modifier.padding(vertical = WajihaSpacing.xs)
    )
}

enum class MetadataPanelStyle {
    /** Top screen: full description, standard typography. */
    Full,
    /** Single-display card below hero. */
    Compact
}

@Composable
fun GameDetailMetadataPanel(
    game: GameEntity,
    platformName: String?,
    totalPlaytimeSec: Long,
    modifier: Modifier = Modifier,
    style: MetadataPanelStyle = MetadataPanelStyle.Full
) {
    val bodyStyle = when (style) {
        MetadataPanelStyle.Full -> MaterialTheme.typography.bodyMedium
        MetadataPanelStyle.Compact -> MaterialTheme.typography.bodySmall
    }
    val labelStyle = when (style) {
        MetadataPanelStyle.Full -> MaterialTheme.typography.labelMedium
        MetadataPanelStyle.Compact -> MaterialTheme.typography.labelSmall
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            if (style == MetadataPanelStyle.Full) WajihaSpacing.xs else WajihaSpacing.xs / 2
        )
    ) {
        MetaInfoRow("Platform", platformName ?: game.platformId, labelStyle, bodyStyle)
        game.region?.let { MetaInfoRow("Region", it.uppercase(), labelStyle, bodyStyle) }
        game.ageRating?.let { MetaInfoRow("Age rating", it, labelStyle, bodyStyle) }
        game.releaseDate?.let { MetaInfoRow("Release", it, labelStyle, bodyStyle) }
        game.developer?.let { MetaInfoRow("Developer", it, labelStyle, bodyStyle) }
        game.publisher?.let { MetaInfoRow("Publisher", it, labelStyle, bodyStyle) }
        game.genre?.let { MetaInfoRow("Genre", it, labelStyle, bodyStyle) }
        game.players?.let { MetaInfoRow("Players", it, labelStyle, bodyStyle) }
        game.rating?.let { MetaInfoRow("Rating", "%.1f".format(it), labelStyle, bodyStyle) }

        GroupDivider()

        MetaInfoRow("Plays", "${game.playCount}", labelStyle, bodyStyle)
        val hours = totalPlaytimeSec / 3600
        val mins = (totalPlaytimeSec % 3600) / 60
        val playTime = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        MetaInfoRow("Play time", playTime, labelStyle, bodyStyle)

        GroupDivider()

        MetaInfoRow("File", game.fileName, labelStyle, bodyStyle)
        if (game.fileSize > 0) {
            MetaInfoRow("Size", formatFileSize(game.fileSize), labelStyle, bodyStyle)
        }
        MetaInfoRow(
            label = "ROM path",
            value = game.uri,
            labelStyle = labelStyle,
            bodyStyle = bodyStyle,
            maxValueLines = if (style == MetadataPanelStyle.Full) Int.MAX_VALUE else 2
        )
        MetaInfoRow(
            label = "Scrape status",
            value = formatScrapeStatus(game.scrapedAt),
            labelStyle = labelStyle,
            bodyStyle = bodyStyle
        )

        game.description?.let { desc ->
            GroupDivider()
            Text(
                text = "Description",
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = desc,
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = WajihaSpacing.xs / 2)
            )
        }
    }
}

@Composable
private fun MetaInfoRow(
    label: String,
    value: String,
    labelStyle: TextStyle,
    bodyStyle: TextStyle,
    maxValueLines: Int = 3
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WajihaSpacing.touchMin / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 108.dp)
        )
        Text(
            text = value,
            style = bodyStyle,
            modifier = Modifier
                .weight(1f)
                .padding(start = WajihaSpacing.sm),
            maxLines = maxValueLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f ${units[unit]}".format(size)
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun formatScrapeStatus(scrapedAt: Long?): String {
    if (scrapedAt == null) return "Never scraped"
    val dateTime = kotlin.time.Instant.fromEpochMilliseconds(scrapedAt)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val y = dateTime.year
    val m = dateTime.monthNumber.toString().padStart(2, '0')
    val d = dateTime.dayOfMonth.toString().padStart(2, '0')
    return "Scraped $y-$m-$d"
}

@Composable
private fun LaunchSectionContent(
    game: GameEntity,
    secondaryDisplayId: Int?,
    onSetLaunchDisplay: (Int?) -> Unit,
    onLaunchTop: () -> Unit,
    onLaunchBottom: () -> Unit,
    firstFocusRequester: FocusRequester? = null
) {
    SectionBlurb("Default display and quick launch options.")

    Text(
        text = "Default display",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "Which screen to use when launching from the grid.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = WajihaSpacing.xs)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        GamepadChip(
            label = "Top (0)",
            selected = game.launchOnDisplay == 0 || game.launchOnDisplay == null,
            onClick = { onSetLaunchDisplay(0) },
            focusRequester = firstFocusRequester
        )
        GamepadChip(
            label = "Bottom (${secondaryDisplayId ?: 4})",
            selected = game.launchOnDisplay == (secondaryDisplayId ?: 4),
            onClick = { onSetLaunchDisplay(secondaryDisplayId ?: 4) }
        )
    }

    GroupDivider()

    Text(
        text = "Launch now",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Row(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        GamepadButton(text = "Launch top", onClick = onLaunchTop)
        GamepadButton(text = "Launch bottom", onClick = onLaunchBottom, outlined = true)
    }
}

@Composable
private fun EmulatorSectionContent(
    emulators: List<EmulatorEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    firstFocusRequester: FocusRequester? = null
) {
    SectionBlurb("Override which emulator launches this game. Platform default is used when none is selected.")

    if (emulators.isEmpty()) {
        Text(
            text = "No emulators configured for this platform.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        GamepadChip(
            label = "Platform default",
            selected = selectedId == null,
            onClick = { onSelect(null) },
            focusRequester = firstFocusRequester
        )
        emulators.forEach { emulator ->
            EmulatorPickerRow(
                emulator = emulator,
                selected = selectedId == emulator.id,
                onSelect = { onSelect(emulator.id) }
            )
        }
    }
}

@Composable
private fun EmulatorPickerRow(
    emulator: EmulatorEntity,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WajihaSpacing.touchMin)
            .padding(vertical = WajihaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(emulator.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = emulator.packageNames.split(',').firstOrNull()?.trim().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GamepadChip(
            label = if (selected) "Selected" else "Use",
            selected = selected,
            onClick = onSelect
        )
    }
}

@Composable
private fun ScraperSectionContent(
    state: GameDetailUiState,
    onRescrape: () -> Unit,
    onRescrapeFrom: (String) -> Unit,
    onScrapeMedia: (MediaType, String) -> Unit,
    firstFocusRequester: FocusRequester? = null
) {
    var selectedSource by remember(state.scraperSources) {
        mutableStateOf(state.scraperSources.firstOrNull())
    }
    var mediaTab by remember { mutableStateOf(MediaType.Boxart) }

    SectionBlurb("Fetch metadata and artwork from configured scraper sources.")

    if (state.scraping) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
        ) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
            Text("Scraping…", style = MaterialTheme.typography.bodyMedium)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
        GamepadButton(
            text = "Scrape",
            onClick = onRescrape,
            focusRequester = firstFocusRequester
        )
        selectedSource?.let { source ->
            GamepadButton(
                text = "Redo ($source)",
                onClick = { onRescrapeFrom(source) },
                outlined = true
            )
        }
    }

    if (state.scraperSources.isEmpty()) {
        Text(
            text = "No scraper sources configured — open Scraper hub in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        GroupDivider()
        Text(
            text = "Source",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Pick which scraper to use for redo or element-specific fetches.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = WajihaSpacing.xs)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
            items(state.scraperSources) { source ->
                GamepadChip(
                    label = source,
                    selected = source == selectedSource,
                    onClick = { selectedSource = source }
                )
            }
        }

        GroupDivider()
        Text(
            text = "Scrape element only",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
            items(listOf(MediaType.Boxart, MediaType.Hero, MediaType.Logo)) { type ->
                GamepadChip(
                    label = type.dbName,
                    selected = type == mediaTab,
                    onClick = { mediaTab = type }
                )
            }
        }
        selectedSource?.let { source ->
            GamepadButton(
                text = "Fetch ${mediaTab.dbName}",
                onClick = { onScrapeMedia(mediaTab, source) },
                outlined = true
            )
        }
    }

    MediaPreviewRow(state.media)
}

@Composable
private fun MediaPreviewRow(media: List<com.wajiha.data.db.GameMediaEntity>) {
    val previewTypes = listOf("boxart", "hero", "logo")
    val items = previewTypes.mapNotNull { type ->
        media.firstOrNull { it.type == type }?.let { type to it.localPath }
    }
    if (items.isEmpty()) return

    GroupDivider()
    Text(
        text = "Media preview",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        modifier = Modifier.padding(top = WajihaSpacing.xs)
    ) {
        items(items) { (type, path) ->
            if (path != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(WajihaSpacing.xs)
                ) {
                    AsyncImage(
                        model = path,
                        contentDescription = type,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(80.dp)
                            .widthIn(min = 60.dp, max = 120.dp)
                    )
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = WajihaSpacing.xs / 2)
                    )
                }
            }
        }
    }
}
