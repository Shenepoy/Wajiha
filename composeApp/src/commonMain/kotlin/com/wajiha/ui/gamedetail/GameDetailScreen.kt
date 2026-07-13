package com.wajiha.ui.gamedetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.data.scraper.missingGapTypes
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.requestContentFocus
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.FolderTabRow
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.MultiChoiceOption
import com.wajiha.ui.components.gamepad.SettingSectionScrollColumn
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.gameDetailGamepadHints
import com.wajiha.ui.scraper.ScrapeModeSelector
import com.wajiha.ui.scraper.ScrapeUiMode
import com.wajiha.ui.scraper.review.ScrapeReviewPicker
import com.wajiha.ui.scraper.review.ScrapeReviewViewModel
import com.wajiha.ui.scraper.singleGameHelperText
import com.wajiha.ui.scraper.toPolicy
import com.wajiha.ui.settings.toEmulatorChoiceOptions
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

private enum class ActionPaneSection(
    val label: String,
) {
    Launch("Launch"),
    Emulator("Emulator"),
    Scraper("Scraper"),
}

@Composable
fun GameDetailScreen(
    gameId: Long,
    secondaryDisplayId: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    dualDisplay: Boolean = false,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    viewModel: GameDetailViewModel = koinInject(),
) {
    LaunchedEffect(gameId) { viewModel.open(gameId) }
    val state by viewModel.uiState.collectAsState()
    val game = state.game

    val sections = ActionPaneSection.entries
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    val sectionFocus = remember { FocusRequester() }
    val feedback = LocalUiFeedback.current

    fun selectSection(index: Int) {
        val newIndex = index.coerceIn(0, sections.lastIndex)
        feedback.tabSelect(selectedSectionIndex, newIndex)
        selectedSectionIndex = newIndex
    }

    LaunchedEffect(selectedSectionIndex) {
        try {
            sectionFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    WajihaScreen(
        layerId = "game_detail_$gameId",
        modifier = Modifier,
        onBack = onBack,
        showActionBar = true,
        gamepadHints = gameDetailGamepadHints(isDual = dualDisplay),
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = { sectionFocus.requestContentFocus() },
        onPreviewKey = { event ->
            val screenLayer = "game_detail_$gameId"
            if (GamepadLayers.stack.topLayer != screenLayer) return@WajihaScreen false
            when {
                GamepadKeys.isL1(event.type, event.key) -> {
                    if (selectedSectionIndex > 0) {
                        selectSection(selectedSectionIndex - 1)
                        true
                    } else {
                        false
                    }
                }

                GamepadKeys.isR1(event.type, event.key) -> {
                    if (selectedSectionIndex < sections.lastIndex) {
                        selectSection(selectedSectionIndex + 1)
                        true
                    } else {
                        false
                    }
                }

                else -> {
                    false
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WajihaToolbar(
                title = game?.displayName ?: "Game",
                onBack = onBack,
                backFocusable = false,
            )

            if (game == null) {
                Text(
                    text = "Game not found",
                    modifier = Modifier.padding(WajihaSpacing.md),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    showFavoriteRow = true,
                    dualDisplay = true,
                    gamepadOwner = gamepadOwner,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = WajihaSpacing.md)
                            .padding(bottom = WajihaSpacing.md),
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = WajihaSpacing.md),
                ) {
                    GameDetailCompactHero(
                        game = game,
                        platformName = state.platform?.name,
                        boxartPath = state.media.firstOrNull { it.type == "boxart" }?.localPath,
                        onSetFavorite = viewModel::setFavorite,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = WajihaSpacing.xs),
                    )
                    GameDetailSectionCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = WajihaSpacing.sm)
                                .weight(0.28f),
                    ) {
                        GameDetailMetadataPanel(
                            game = game,
                            platformName = state.platform?.name,
                            totalPlaytimeSec = state.totalPlaytimeSec,
                            style = MetadataPanelStyle.Compact,
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
                        showFavoriteRow = false,
                        dualDisplay = false,
                        gamepadOwner = gamepadOwner,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = WajihaSpacing.sm, bottom = WajihaSpacing.md),
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
    showFavoriteRow: Boolean,
    dualDisplay: Boolean = false,
    gamepadOwner: GamepadOwner? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FolderTabRow(
            tabs = sections.map { it.label },
            selectedIndex = selectedSectionIndex,
            onSelect = onSelectSection,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = false }
                    .padding(top = WajihaSpacing.sm),
        )

        GameDetailSectionCard(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .offset(y = (-1).dp),
        ) {
            when (sections[selectedSectionIndex]) {
                ActionPaneSection.Launch -> {
                    LaunchSectionContent(
                        game = game,
                        secondaryDisplayId = secondaryDisplayId,
                        dualDisplay = dualDisplay,
                        onSetLaunchDisplay = viewModel::setLaunchOnDisplay,
                        onSetFavorite = viewModel::setFavorite,
                        onLaunchTop = { viewModel.launchOnDisplay(0) },
                        onLaunchBottom = {
                            viewModel.launchOnDisplay(secondaryDisplayId ?: 4)
                        },
                        showFavoriteRow = showFavoriteRow,
                        firstFocusRequester = sectionFocus,
                    )
                }

                ActionPaneSection.Emulator -> {
                    EmulatorSectionContent(
                        emulators = state.emulators,
                        selectedId = game.emulatorOverrideId,
                        onSelect = viewModel::setEmulatorOverride,
                        firstFocusRequester = sectionFocus,
                    )
                }

                ActionPaneSection.Scraper -> {
                    ScraperSectionContent(
                        state = state,
                        onScrape = { policy -> viewModel.rescrape(policy = policy) },
                        onDeleteMedia = viewModel::deleteMedia,
                        dualDisplay = dualDisplay,
                        gamepadOwner = gamepadOwner,
                        firstFocusRequester = sectionFocus,
                    )
                }
            }

            state.scrapeMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        when (state.scrapeMessageSuccess) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(top = WajihaSpacing.sm),
                )
            }
            state.actionError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = WajihaSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun GameDetailCompactHero(
    game: GameEntity,
    platformName: String?,
    boxartPath: String?,
    onSetFavorite: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = Modifier,
        shape = WajihaShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(WajihaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .height(88.dp)
                        .aspectRatio(3f / 4f)
                        .clip(WajihaShapes.tile)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (boxartPath != null) {
                    AsyncImage(
                        model = boxartPath,
                        contentDescription = game.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = game.displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                PlatformBadge(label = platformName ?: game.platformId)
                DetailToggleRow(
                    label = "Favorite",
                    checked = game.favorite,
                    onCheckedChange = onSetFavorite,
                    defaultChecked = false,
                    onReset = { onSetFavorite(false) },
                )
            }
        }
    }
}

@Composable
private fun PlatformBadge(label: String) {
    Surface(
        shape = WajihaShapes.chip,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier =
                Modifier.padding(
                    horizontal = WajihaSpacing.sm,
                    vertical = WajihaSpacing.xs / 2,
                ),
        )
    }
}

@Composable
private fun GameDetailSectionCard(
    modifier: Modifier = Modifier,
    sectionContent: @Composable () -> Unit,
) {
    val shape = WajihaShapes.folderPanel
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = outlineColor, shape = shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(WajihaSpacing.md),
        ) {
            SettingSectionScrollColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            ) {
                sectionContent()
            }
        }
    }
}

@Composable
private fun SectionBlurb(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = WajihaSpacing.xs),
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        modifier = Modifier.padding(vertical = WajihaSpacing.xs),
    )
}

@Composable
private fun DetailToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    defaultChecked: Boolean,
    onReset: () -> Unit,
    description: String? = null,
    focusRequester: FocusRequester? = null,
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.Toggle,
        checked = checked,
        onCheckedChange = onCheckedChange,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = checked == defaultChecked,
    )
}

@Composable
private fun DetailChoiceRow(
    label: String,
    description: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.BinaryChoice,
        options = options,
        selected = selected,
        onSelect = onSelect,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = selected == defaultValue,
    )
}

@Composable
private fun DetailMultiChoiceRow(
    label: String,
    description: String,
    choiceOptions: List<MultiChoiceOption>,
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.MultiChoice,
        multiChoiceOptions = choiceOptions,
        selected = selected,
        onSelect = onSelect,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = selected == defaultValue,
    )
}

enum class MetadataPanelStyle {
    /** Top screen: full description, standard typography. */
    Full,

    /** Single-display card below hero. */
    Compact,
}

/** Per-section visibility for [GameDetailMetadataPanel] on the top-screen Info hero. */
data class MetadataPanelVisibility(
    val metadata: Boolean = true,
    val description: Boolean = true,
    val playStats: Boolean = true,
)

@Composable
fun GameDetailMetadataPanel(
    game: GameEntity,
    platformName: String?,
    totalPlaytimeSec: Long,
    modifier: Modifier = Modifier,
    style: MetadataPanelStyle = MetadataPanelStyle.Full,
    visibility: MetadataPanelVisibility = MetadataPanelVisibility(),
) {
    val bodyStyle =
        when (style) {
            MetadataPanelStyle.Full -> MaterialTheme.typography.bodyMedium
            MetadataPanelStyle.Compact -> MaterialTheme.typography.bodySmall
        }
    val labelStyle =
        when (style) {
            MetadataPanelStyle.Full -> MaterialTheme.typography.labelMedium
            MetadataPanelStyle.Compact -> MaterialTheme.typography.labelSmall
        }

    val showMetadata = visibility.metadata
    val showPlayStats = visibility.playStats
    val showDescription = visibility.description && !game.description.isNullOrBlank()
    if (!showMetadata && !showPlayStats && !showDescription) return

    Column(
        modifier = Modifier,
        verticalArrangement =
            Arrangement.spacedBy(
                if (style == MetadataPanelStyle.Full) WajihaSpacing.xs else WajihaSpacing.xs / 2,
            ),
    ) {
        if (showMetadata) {
            MetaInfoRow("Platform", platformName ?: game.platformId, labelStyle, bodyStyle)
            game.region?.let { MetaInfoRow("Region", it.uppercase(), labelStyle, bodyStyle) }
            game.ageRating?.let { MetaInfoRow("Age rating", it, labelStyle, bodyStyle) }
            game.releaseDate?.let { MetaInfoRow("Release", it, labelStyle, bodyStyle) }
            game.developer?.let { MetaInfoRow("Developer", it, labelStyle, bodyStyle) }
            game.publisher?.let { MetaInfoRow("Publisher", it, labelStyle, bodyStyle) }
            game.genre?.let { MetaInfoRow("Genre", it, labelStyle, bodyStyle) }
            game.players?.let { MetaInfoRow("Players", it, labelStyle, bodyStyle) }
            game.rating?.let { MetaInfoRow("Rating", "%.1f".format(it), labelStyle, bodyStyle) }
            MetaInfoRow("File", game.fileName, labelStyle, bodyStyle)
            if (game.fileSize > 0) {
                MetaInfoRow("Size", formatFileSize(game.fileSize), labelStyle, bodyStyle)
            }
            MetaInfoRow(
                label = "ROM path",
                value = game.uri,
                labelStyle = labelStyle,
                bodyStyle = bodyStyle,
                maxValueLines = if (style == MetadataPanelStyle.Full) Int.MAX_VALUE else 2,
            )
            MetaInfoRow(
                label = "Scrape status",
                value = formatScrapeStatus(game.scrapedAt),
                labelStyle = labelStyle,
                bodyStyle = bodyStyle,
            )
        }

        if (showPlayStats) {
            if (showMetadata) GroupDivider()
            MetaInfoRow("Plays", "${game.playCount}", labelStyle, bodyStyle)
            val hours = totalPlaytimeSec / 3600
            val mins = (totalPlaytimeSec % 3600) / 60
            val playTime = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            MetaInfoRow("Play time", playTime, labelStyle, bodyStyle)
        }

        if (showDescription) {
            if (showMetadata || showPlayStats) GroupDivider()
            Text(
                text = "Description",
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = game.description.orEmpty(),
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = WajihaSpacing.xs / 2),
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
    maxValueLines: Int = 3,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WajihaSpacing.touchMin / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 108.dp),
        )
        Text(
            text = value,
            style = bodyStyle,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = WajihaSpacing.sm),
            maxLines = maxValueLines,
            overflow = TextOverflow.Ellipsis,
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
    val dateTime =
        kotlin.time.Instant
            .fromEpochMilliseconds(scrapedAt)
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
    dualDisplay: Boolean,
    onSetLaunchDisplay: (Int?) -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onLaunchTop: () -> Unit,
    onLaunchBottom: () -> Unit,
    showFavoriteRow: Boolean,
    firstFocusRequester: FocusRequester? = null,
) {
    SectionBlurb(
        if (dualDisplay) {
            "Default display and quick launch options."
        } else {
            "Quick launch options."
        },
    )

    if (showFavoriteRow) {
        DetailToggleRow(
            label = "Favorite",
            description = "Pin this game in your library.",
            checked = game.favorite,
            onCheckedChange = onSetFavorite,
            defaultChecked = false,
            onReset = { onSetFavorite(false) },
            focusRequester = firstFocusRequester,
        )
        GroupDivider()
    }

    if (dualDisplay) {
        val topId = "0"
        val bottomId = (secondaryDisplayId ?: 4).toString()
        val selectedDisplay =
            when (game.launchOnDisplay) {
                0, null -> topId
                else -> bottomId
            }
        GamepadSettingRow(
            label = "Default display",
            description = "Which screen to use when launching from the grid.",
            type = SettingType.BinaryChoice,
            options =
                listOf(
                    topId to "Top ($topId)",
                    bottomId to "Bottom ($bottomId)",
                ),
            selected = selectedDisplay,
            onSelect = { value ->
                onSetLaunchDisplay(if (value == topId) 0 else (secondaryDisplayId ?: 4))
            },
            focusRequester = if (showFavoriteRow) null else firstFocusRequester,
            onReset = { onSetLaunchDisplay(null) },
            isAtDefault = game.launchOnDisplay == null,
        )

        GroupDivider()
    }

    Text(
        text = "Launch now",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        GamepadButton(
            text = if (dualDisplay) "Launch top" else "Launch",
            onClick = onLaunchTop,
            modifier = Modifier.fillMaxWidth(),
            focusRequester = if (!dualDisplay && !showFavoriteRow) firstFocusRequester else null,
        )
        if (dualDisplay) {
            GamepadButton(
                text = "Launch bottom",
                onClick = onLaunchBottom,
                outlined = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmulatorSectionContent(
    emulators: List<EmulatorEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    val systemControls = koinInject<SystemControls>()
    SectionBlurb("Override which emulator launches this game. Platform default is used when none is selected.")

    if (emulators.isEmpty()) {
        Text(
            text = "No emulators configured for this platform.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val options =
            emulators.toEmulatorChoiceOptions(
                isPackageInstalled = systemControls::isPackageInstalled,
                includePlatformDefault = true,
            )
        DetailMultiChoiceRow(
            label = "Emulator",
            description = "A opens the list. Installed emulators are selectable; missing apps stay visible but greyed out.",
            choiceOptions = options,
            selected = selectedId.orEmpty(),
            onSelect = { value -> onSelect(value.ifEmpty { null }) },
            defaultValue = "",
            onReset = { onSelect(null) },
            focusRequester = firstFocusRequester,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScraperSectionContent(
    state: GameDetailUiState,
    onScrape: (ScrapeRunPolicy) -> Unit,
    onDeleteMedia: (MediaType) -> Unit,
    dualDisplay: Boolean = false,
    gamepadOwner: GamepadOwner? = null,
    firstFocusRequester: FocusRequester? = null,
    reviewViewModel: ScrapeReviewViewModel = koinInject(),
    dualStore: DualScreenStore = koinInject(),
) {
    var mode by remember { mutableStateOf(ScrapeUiMode.FillGaps) }
    var reviewing by remember { mutableStateOf(false) }
    var focusedPreview by remember { mutableStateOf(MediaType.Boxart) }
    val game = state.game
    val gapTypes =
        remember(state.media, game?.scrapedAt) {
            state.media.missingGapTypes()
        }
    val sourcesReady = state.scraperSources.isNotEmpty()
    val previewTypes =
        remember {
            listOf(
                MediaType.Boxart,
                MediaType.Logo,
                MediaType.Hero,
                MediaType.Screenshot,
                MediaType.Fanart,
                MediaType.Banner,
                MediaType.Icon,
            )
        }

    if (reviewing && game != null) {
        ScrapeReviewPicker(
            viewModel = reviewViewModel,
            onCancel = { reviewing = false },
            showSkip = false,
            dualDisplay = dualDisplay,
            hostGamepadOwner =
                gamepadOwner
                    ?: if (dualDisplay) dualStore.menuGamepadOwner() else null,
        )
    }

    SectionBlurb("Fetch metadata and artwork from configured scraper sources.")

    if (state.scraping) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
            Text("Scraping…", style = MaterialTheme.typography.bodyMedium)
        }
    }

    ScrapeModeSelector(
        selected = mode,
        onSelect = { mode = it },
        actionLabel =
            when (mode) {
                ScrapeUiMode.Review -> "Open Manual"
                ScrapeUiMode.Force -> "Force scrape"
                ScrapeUiMode.FillGaps -> "Scrape"
            },
        onAction = {
            when (mode) {
                ScrapeUiMode.Review -> {
                    if (game == null) return@ScrapeModeSelector
                    reviewing = true
                    reviewViewModel.openGame(game)
                }

                else -> {
                    onScrape(mode.toPolicy())
                }
            }
        },
        firstFocusRequester = firstFocusRequester,
        enabled = !state.scraping,
        actionEnabled = !state.scraping && sourcesReady,
        helperText = mode.singleGameHelperText(),
        singleGameLabels = true,
    )

    Text(
        text =
            when {
                !sourcesReady -> {
                    "No scraper sources configured — enable sources in Settings → Scraper."
                }

                mode == ScrapeUiMode.FillGaps && gapTypes.isNotEmpty() -> {
                    "Missing: ${gapTypes.joinToString { it.dbName }}"
                }

                mode == ScrapeUiMode.FillGaps -> {
                    "Preferred artwork present — use Force or Manual to replace."
                }

                else -> {
                    "${state.scraperSources.size} source(s) configured"
                }
            },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = WajihaSpacing.xs),
    )

    GroupDivider()
    Text(
        text = "Artwork",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "A/tap Manual · X/long-press remove",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        modifier = Modifier.padding(top = WajihaSpacing.xs),
    ) {
        items(previewTypes, key = { it.dbName }) { type ->
            val path = state.media.firstOrNull { it.type == type.dbName }?.localPath
            val focused = focusedPreview == type
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (focused) 2.dp else 1.dp,
                            color =
                                if (focused) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            shape = RoundedCornerShape(8.dp),
                        ).background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = {
                                focusedPreview = type
                                if (game == null || !sourcesReady) return@combinedClickable
                                reviewing = true
                                reviewViewModel.openGame(
                                    game = game,
                                    focusMediaType = type,
                                    openPickerImmediately = true,
                                )
                            },
                            onLongClick = {
                                focusedPreview = type
                                if (!path.isNullOrBlank()) onDeleteMedia(type)
                            },
                        ).padding(WajihaSpacing.xs),
            ) {
                Box(
                    modifier =
                        Modifier
                            .height(80.dp)
                            .widthIn(min = 60.dp, max = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!path.isNullOrBlank()) {
                        AsyncImage(
                            model = path,
                            contentDescription = type.dbName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = type.dbName,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = WajihaSpacing.xs / 2),
                )
            }
        }
    }

    GroupDivider()
    Text(
        text =
            if (sourcesReady) {
                "Providers: ${state.scraperSources.joinToString(" · ")}"
            } else {
                "No providers — Settings → Scraper"
            },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
