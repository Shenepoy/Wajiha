package com.wajiha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.state.DualScreenStore
import com.wajiha.state.SettingsHeroActionBridge
import com.wajiha.state.SettingsHeroDetail
import com.wajiha.state.SettingsHeroKind
import com.wajiha.state.SettingsHeroPlatformExtras
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import com.wajiha.ui.theme.focusColorPreview
import org.koin.compose.koinInject

private val HeroCardShape = RoundedCornerShape(18.dp)
private val HeroInnerShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsFocusHero(
    sectionLabel: String?,
    contentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val store = koinInject<DualScreenStore>()
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val detail by store.settingsHeroDetail.collectAsState()
    val scheme = MaterialTheme.colorScheme

    if (!settings.settingsHeroHelp) {
        SettingsHeroStage(modifier = modifier) {
            SettingsHeroCard(
                contentFocusRequester = contentFocusRequester,
                interactive = false,
                fillHeight = true,
            ) {
                Text(
                    text = sectionLabel ?: "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        return
    }

    val showActions = settings.settingsHeroActions
    val interactive =
        showActions &&
            detail != null &&
            (detail!!.options.isNotEmpty() || detail!!.actions.isNotEmpty())

    LaunchedEffect(interactive, detail?.title) {
        store.setSettingsHeroPicking(interactive)
    }
    DisposableEffect(Unit) {
        onDispose { store.setSettingsHeroPicking(false) }
    }

    val firstFocus = remember { FocusRequester() }
    val resolvedFocus = contentFocusRequester ?: firstFocus

    SettingsHeroStage(modifier = modifier) {
        SettingsHeroCard(
            contentFocusRequester = if (!interactive) contentFocusRequester else null,
            interactive = interactive,
            fillHeight = true,
        ) {
            if (detail == null) {
                SettingsHeroChrome(
                    sectionLabel = sectionLabel,
                    swapRoles = settings.swapScreenRoles,
                )
            } else {
                SettingsHeroDetailBody(
                    detail = detail!!,
                    showActions = showActions,
                    swapRoles = settings.swapScreenRoles,
                    firstFocusRequester = if (interactive) resolvedFocus else null,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeroStage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                scheme.primary.copy(alpha = 0.08f),
                                scheme.background,
                            ),
                    ),
                )
                // Tight inset so the card uses most of the secondary display.
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsHeroCard(
    contentFocusRequester: FocusRequester?,
    interactive: Boolean,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .then(
                    if (!interactive && contentFocusRequester != null) {
                        Modifier
                            .focusRequester(contentFocusRequester)
                            .wajihaGamepadFocus()
                    } else {
                        Modifier
                    },
                ),
        shape = HeroCardShape,
        color = scheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(scheme.primary),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                content = content,
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsHeroChrome(
    sectionLabel: String?,
    swapRoles: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = sectionLabel ?: "Settings",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = sectionOverviewCopy(sectionLabel),
        style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurfaceVariant,
    )
    Box(modifier = Modifier.weight(1f))
    if (sectionLabel == "Screens") {
        RoleDiagram(swapRoles = swapRoles)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.SettingsHeroDetailBody(
    detail: SettingsHeroDetail,
    showActions: Boolean,
    swapRoles: Boolean = false,
    firstFocusRequester: FocusRequester? = null,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val titleStyle =
        if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
    val hasPlatformArts = detail.platform?.boxartPaths?.isNotEmpty() == true
    val showOverview =
        detail.sectionOverview
            ?.takeIf {
                detail.kind == SettingsHeroKind.LibraryChrome ||
                    detail.kind == SettingsHeroKind.SectionOverview
            }?.takeIf { it != detail.subtitle }

    if (hasPlatformArts && !compact) {
        HeroHeader(detail = detail, titleStyle = titleStyle, compact = compact)
        PreviewChip(kind = detail.previewKind, payload = detail.previewPayload)
        PlatformMetaBlock(platform = detail.platform!!)
        HeroActions(
            detail = detail,
            showActions = showActions,
            firstFocusRequester = firstFocusRequester,
        )
        Box(modifier = Modifier.weight(1f))
        PlatformBoxartRow(
            paths = detail.platform!!.boxartPaths,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    HeroHeader(detail = detail, titleStyle = titleStyle, compact = compact)
    if (detail.kind != SettingsHeroKind.PlatformRow) {
        HeroValueBlock(detail = detail)
    }

    PreviewChip(kind = detail.previewKind, payload = detail.previewPayload)

    detail.platform?.let { PlatformMetaBlock(platform = it) }

    detail.libraryTotals?.let { (platforms, games) ->
        Surface(
            shape = HeroInnerShape,
            color = scheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "$platforms platform(s) · $games game(s) in library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }

    showOverview?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
    }

    if (detail.showRoleDiagram || detail.kind == SettingsHeroKind.ScreensChrome) {
        RoleDiagram(swapRoles = swapRoles)
    }

    if (!compact) {
        Box(modifier = Modifier.weight(1f))
    }

    HeroActions(
        detail = detail,
        showActions = showActions,
        firstFocusRequester = firstFocusRequester,
    )
}

@Composable
private fun HeroHeader(
    detail: SettingsHeroDetail,
    titleStyle: TextStyle,
    compact: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val platform = detail.platform
    if (platform != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusDot(active = platform.enabled)
            Text(
                text = detail.title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "${platform.folderCount} folder(s) · ${platform.gameCount} game(s)",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Text(
        text = detail.title,
        style = titleStyle,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    detail.subtitle?.let {
        Text(
            text = it,
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    val color =
        if (active) {
            Color(0xFF2E7D32)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(18.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f)),
        )
        Box(
            modifier =
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color),
        )
    }
}

@Composable
private fun HeroValueBlock(detail: SettingsHeroDetail) {
    val scheme = MaterialTheme.colorScheme
    val bigValue =
        when {
            detail.numberValue != null -> {
                buildString {
                    append(detail.numberValue)
                    detail.numberUnit?.let { u -> append(' ').append(u) }
                }
            }

            !detail.valueText.isNullOrBlank() -> detail.valueText
            else -> null
        } ?: return

    Surface(
        shape = HeroInnerShape,
        color = scheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = bigValue,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroActions(
    detail: SettingsHeroDetail,
    showActions: Boolean,
    firstFocusRequester: FocusRequester?,
) {
    val scheme = MaterialTheme.colorScheme
    if (showActions && detail.options.isNotEmpty()) {
        HorizontalDivider(
            color = scheme.outline.copy(alpha = 0.16f),
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = "Options",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
        ) {
            detail.options.forEachIndexed { index, opt ->
                GamepadChip(
                    label = opt.label,
                    selected = opt.selected,
                    onClick = { SettingsHeroActionBridge.onSelectOption?.invoke(opt.value) },
                    focusRequester = if (index == 0) firstFocusRequester else null,
                )
            }
        }
    }

    if (showActions && detail.actions.isNotEmpty()) {
        HorizontalDivider(
            color = scheme.outline.copy(alpha = 0.16f),
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
            detail.actions.forEachIndexed { index, action ->
                val isPrimary =
                    action.id == "rescan" ||
                        action.id == "configure" ||
                        action.id == "open" ||
                        action.id == "select"
                GamepadButton(
                    text = action.label,
                    onClick = {
                        when (action.id) {
                            "edit", "open", "configure" -> {
                                if (action.id == "edit") {
                                    SettingsHeroActionBridge.onSecondaryAction?.invoke()
                                } else {
                                    SettingsHeroActionBridge.onPrimaryAction?.invoke()
                                        ?: SettingsHeroActionBridge.onSecondaryAction?.invoke()
                                }
                            }

                            else -> SettingsHeroActionBridge.onPrimaryAction?.invoke()
                        }
                    },
                    outlined = !isPrimary || action.id == "edit",
                    focusRequester =
                        if (index == 0 && detail.options.isEmpty()) {
                            firstFocusRequester
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun PreviewChip(
    kind: String?,
    payload: String?,
) {
    if (kind == null || payload.isNullOrBlank()) return
    val scheme = MaterialTheme.colorScheme
    when (kind) {
        "focusColor" -> {
            val color = focusColorPreview(payload)
            Surface(
                shape = HeroInnerShape,
                color = scheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, scheme.outline.copy(alpha = 0.35f), CircleShape),
                    )
                    Text(
                        text = "Focus color · $payload",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        "theme", "glyphs" -> {
            Surface(
                shape = HeroInnerShape,
                color = scheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = "${kind.replaceFirstChar { it.uppercase() }} · $payload",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PlatformMetaBlock(platform: SettingsHeroPlatformExtras) {
    val scheme = MaterialTheme.colorScheme
    val extensionList =
        platform.extensions
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    Surface(
        shape = HeroInnerShape,
        color = scheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetaLine(label = "ID", value = platform.platformId)
            platform.shortName
                ?.takeIf { it.isNotBlank() && !it.equals(platform.platformId, ignoreCase = true) }
                ?.let { MetaLine(label = "Short", value = it) }
            if (extensionList.isNotEmpty()) {
                MetaLine(
                    label = "Files",
                    value = extensionList.take(8).joinToString(", ") { ".$it" } +
                        if (extensionList.size > 8) " +" else "",
                )
            }
            platform.emulatorLabel?.let { MetaLine(label = "Emulator", value = it) }
            platform.screenScraperId?.let { MetaLine(label = "Scraper", value = it.toString()) }
            platform.raConsoleId?.let { MetaLine(label = "Achievements", value = it.toString()) }
            if (platform.folderPaths.isNotEmpty()) {
                HorizontalDivider(
                    color = scheme.outline.copy(alpha = 0.14f),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                Text(
                    text = if (platform.folderPaths.size == 1) "Folder" else "Folders",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                platform.folderPaths.take(4).forEach { path ->
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaLine(
    label: String,
    value: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlatformBoxartRow(
    paths: List<String>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = HeroInnerShape,
        color = scheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                paths.take(6).forEach { path ->
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(3f / 4f)
                                .clip(WajihaShapes.card),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleDiagram(swapRoles: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val topRole = if (swapRoles) "Menu" else "Hero"
    val bottomRole = if (swapRoles) "Hero" else "Menu"
    Column(verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)) {
        Text(
            text = "Display roles",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)) {
            RoleCard(label = "Top", role = topRole, modifier = Modifier.weight(1f))
            RoleCard(label = "Bottom", role = bottomRole, modifier = Modifier.weight(1f))
        }
        Text(
            text = if (swapRoles) "Roles swapped" else "Default: hero on top, menu on bottom",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleCard(
    label: String,
    role: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = HeroInnerShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(WajihaSpacing.sm),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = role,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun sectionOverviewCopy(sectionLabel: String?): String =
    when (sectionLabel) {
        "Library" -> "Systems with ROM folders. Add a platform, edit folders, or rescan."
        "Scraper" -> "Global scraper sources and batch options for metadata artwork."
        "Screens" -> "Single vs dual layout, display roles, and gameplay secondary modes."
        "Appearance" -> "Grid look, theme, focus ring, and controller glyphs."
        "System" -> "Permissions, launcher defaults, and app info."
        else -> "Browse settings sections with L1 / R1."
    }
