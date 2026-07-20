package com.wajiha.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wajiha.data.db.PlatformEntity
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.requestContentFocus
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaFolderSettingChrome
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.WajihaToolbar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.GamepadSearchField
import com.wajiha.ui.components.gamepad.ProvideSettingsDensity
import com.wajiha.ui.components.gamepad.WajihaActionSetting
import com.wajiha.ui.components.gamepad.WajihaSettingBlurb
import com.wajiha.ui.components.gamepad.withoutDualScreenChrome
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

private enum class PlatformCatalogFilter(
    val label: String,
) {
    All("All"),
    New("New"),
    InLibrary("In library"),
}

/**
 * Searchable catalog of known platforms using the same folder-panel and
 * settings-row controls as the rest of Wajiha settings.
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
    val inUseIds = remember(folders) { folders.map { it.platformId }.toSet() }
    val feedback = LocalUiFeedback.current
    val filterTabs = PlatformCatalogFilter.entries
    val sectionFocus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val selectedFilter = filterTabs[selectedFilterIndex]

    fun selectFilter(index: Int) {
        val next = index.coerceIn(0, filterTabs.lastIndex)
        feedback.tabSelect(selectedFilterIndex, next)
        selectedFilterIndex = next
    }

    val filtered =
        remember(allPlatforms, query, selectedFilter, inUseIds) {
            val normalizedQuery = query.trim().lowercase()
            allPlatforms
                .asSequence()
                .filter { platform ->
                    normalizedQuery.isEmpty() ||
                        platform.name.lowercase().contains(normalizedQuery) ||
                        platform.shortName.lowercase().contains(normalizedQuery) ||
                        platform.id.lowercase().contains(normalizedQuery)
                }.filter { platform ->
                    when (selectedFilter) {
                        PlatformCatalogFilter.All -> true
                        PlatformCatalogFilter.New -> platform.id !in inUseIds
                        PlatformCatalogFilter.InLibrary -> platform.id in inUseIds
                    }
                }.sortedWith(
                    compareByDescending<PlatformEntity> { it.id in inUseIds }
                        .thenBy { it.name.lowercase() },
                ).toList()
        }
    val newCount = allPlatforms.count { it.id !in inUseIds }
    val filterLabels =
        listOf(
            "All ${allPlatforms.size}",
            "New $newCount",
            "In library ${allPlatforms.size - newCount}",
        )

    DisposableEffect(Unit) {
        onDispose { clearSettingsHero(dualStore) }
    }

    LaunchedEffect(selectedFilterIndex) {
        withFrameNanos { }
        sectionFocus.requestContentFocus()
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
                    GamepadHint(GamepadHintButton.L1R1, "Filter"),
                    GamepadHint(GamepadHintButton.L2, "Focus screen"),
                ).withoutDualScreenChrome(isDual),
            gamepadOwner = gamepadOwner,
            onClaimGamepad = onClaimGamepad,
            onOwnerGainedFocus = { sectionFocus.requestContentFocus() },
            onPreviewKey = { event ->
                if (GamepadLayers.stack.topLayer != "platform_picker") {
                    return@WajihaScreen false
                }
                when {
                    GamepadKeys.isL1(event.type, event.key) && selectedFilterIndex > 0 -> {
                        selectFilter(selectedFilterIndex - 1)
                        true
                    }

                    GamepadKeys.isR1(event.type, event.key) &&
                        selectedFilterIndex < filterTabs.lastIndex -> {
                        selectFilter(selectedFilterIndex + 1)
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isDual) {
                    WajihaToolbar(
                        title = "Choose platform",
                        onBack = onBack,
                        backFocusable = false,
                    )
                }

                WajihaFolderSettingChrome(
                    tabs = filterLabels,
                    selectedIndex = selectedFilterIndex,
                    onSelect = ::selectFilter,
                    onBack = onBack.takeIf { isDual },
                    scrollable = false,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    WajihaSettingBlurb(
                        "Choose a system, then select the folder that contains its games.",
                    )
                    GamepadSearchField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Search systems",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (filtered.size == 1) "1 system" else "${filtered.size} systems",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = WajihaSpacing.xs),
                    )
                    GamepadList(
                        items = filtered,
                        key = { it.id },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp),
                        emptyContent = {
                            PlatformCatalogEmptyState(
                                catalogEmpty = allPlatforms.isEmpty(),
                                query = query,
                                focusRequester = sectionFocus,
                                onClear = {
                                    query = ""
                                    selectFilter(PlatformCatalogFilter.All.ordinal)
                                },
                            )
                        },
                    ) { platform ->
                        val alreadyInUse = platform.id in inUseIds
                        WajihaActionSetting(
                            label = platform.name,
                            labelMeta =
                                buildString {
                                    append(platform.shortName.uppercase())
                                    if (alreadyInUse) append(" · in library")
                                },
                            actionLabel = if (alreadyInUse) "Open" else "Set up",
                            onClick = { onPick(platform.id) },
                            focusRequester =
                                if (platform == filtered.firstOrNull()) sectionFocus else null,
                            onFocusedChanged = { focused ->
                                if (focused) {
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
                                } else {
                                    clearSettingsHero(dualStore)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformCatalogEmptyState(
    catalogEmpty: Boolean,
    query: String,
    focusRequester: FocusRequester,
    onClear: () -> Unit,
) {
    WajihaEmptyState(
        title = if (catalogEmpty) "No platforms available" else "No matching systems",
        subtitle =
            when {
                catalogEmpty -> "The platform catalog could not be loaded."
                query.isNotBlank() -> "Try a different name or show all systems."
                else -> "No systems match this filter."
            },
        action =
            if (catalogEmpty) {
                null
            } else {
                {
                    GamepadButton(
                        text = "Show all systems",
                        onClick = onClear,
                        focusRequester = focusRequester,
                    )
                }
            },
    )
}
