package com.wajiha.ui.scraper.review

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.input.RememberGamepadOwnerFocus
import com.wajiha.input.dismissKeyboardOnOutsideTap
import com.wajiha.input.dismissTextEdit
import com.wajiha.input.requestContentFocus
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.CriticalChangeActions
import com.wajiha.ui.components.WajihaLoadingState
import com.wajiha.ui.components.gamepad.GamepadActionBar
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject

val scrapeReviewGamepadHints: List<Pair<String, String>> =
    listOf(
        "A" to "Select",
        "B" to "Back",
        "X" to "Clear",
        "Y" to "Confirm",
        "SELECT" to "Search",
    )

/**
 * Dual-display Manual review:
 * - Bottom: overview mosaic (Back | Game | Search | 1/8 · Options | Confirm)
 * - Top (when [dualDisplay]): slot candidate grid (title · grid · n / total)
 * Single-display falls back to sequential overview ↔ picker on one screen.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScrapeReviewPicker(
    viewModel: ScrapeReviewViewModel,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showSkip: Boolean = true,
    dualDisplay: Boolean = false,
    /** Display hosting this dialog (menu screen). Used for L2 content focus. */
    hostGamepadOwner: GamepadOwner? = null,
    firstFocusRequester: FocusRequester? = null,
) {
    val state by viewModel.state.collectAsState()
    val game = state.game
    val layerId = "scrape_review_${game?.id ?: "queue"}"
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dualStore = koinInject<DualScreenStore>()
    var searchOpen by remember(game?.id, state.queueIndex) { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var focusedSlot by remember(game?.id, state.queueIndex) {
        mutableStateOf(state.visibleSlots.firstOrNull() ?: ReviewSlot.Boxart)
    }
    val inPicker = state.activeSlot != null
    // On dual Thor, overview stays on bottom; slot grid lives on the top hero.
    val showPickerHere = inPicker && !dualDisplay
    val overviewFocus = remember { FocusRequester() }
    val menuOwner =
        hostGamepadOwner
            ?: if (dualDisplay) dualStore.menuGamepadOwner() else null
    RememberGamepadOwnerFocus(owner = menuOwner) {
        if (!showPickerHere) overviewFocus.requestContentFocus()
    }

    DisposableEffect(dualDisplay) {
        if (dualDisplay) dualStore.setScrapeReviewActive(true)
        onDispose {
            dualStore.setScrapeReviewActive(false)
            dualStore.setScrapeReviewPicking(false)
        }
    }
    LaunchedEffect(dualDisplay, inPicker) {
        dualStore.setScrapeReviewPicking(dualDisplay && inPicker)
    }

    fun releaseSearchFocus() {
        dismissTextEdit(focusManager, keyboard)
    }

    fun dismiss() {
        releaseSearchFocus()
        dualStore.setScrapeReviewActive(false)
        viewModel.reset()
        onCancel()
    }

    fun confirm() {
        if (state.applying || game == null || !state.hasChanges) return
        releaseSearchFocus()
        val single = state.queueTotal <= 1
        viewModel.apply {
            if (single) dismiss()
        }
    }

    fun skipGame() {
        if (!showSkip || state.applying) return
        releaseSearchFocus()
        val single = state.queueTotal <= 1
        viewModel.skip {
            if (single) dismiss()
        }
    }

    fun runSearch() {
        releaseSearchFocus()
        viewModel.search()
        searchOpen = false
    }

    fun clearFocused() {
        val type = focusedSlot.mediaType()
        if (type != null) {
            if (state.hasExistingMedia(type) || type in state.mediaPicks) {
                viewModel.clearSlot(type)
            }
        } else if (state.metadataFrom != null) {
            viewModel.clearMetadata()
        }
    }

    BackHandler {
        when {
            inPicker -> {
                viewModel.closeSlot()
            }

            searchOpen -> {
                releaseSearchFocus()
                searchOpen = false
            }

            else -> {
                dismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = ::dismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        GamepadOverlayLayer(
            layerId = layerId,
            onDismiss = {
                if (inPicker) viewModel.closeSlot() else dismiss()
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // When dual + picking, gamepad is on the top display — skip here.
                        if (dualDisplay && inPicker) return@onPreviewKeyEvent false
                        when {
                            GamepadKeys.isY(event.type, event.key) -> {
                                confirm()
                                true
                            }

                            GamepadKeys.isX(event.type, event.key) -> {
                                if (!showPickerHere) {
                                    clearFocused()
                                    true
                                } else {
                                    false
                                }
                            }

                            GamepadKeys.isSelect(event.type, event.key) -> {
                                if (searchOpen) runSearch() else searchOpen = true
                                true
                            }

                            GamepadKeys.isConfirm(event.type, event.key) && !showPickerHere -> {
                                viewModel.openSlot(focusedSlot)
                                true
                            }

                            GamepadKeys.isBack(event.type, event.key) -> {
                                when {
                                    searchOpen -> {
                                        releaseSearchFocus()
                                        searchOpen = false
                                        true
                                    }

                                    inPicker -> {
                                        viewModel.closeSlot()
                                        true
                                    }

                                    else -> {
                                        dismissTextEdit(focusManager, keyboard)
                                    }
                                }
                            }

                            else -> {
                                false
                            }
                        }
                    },
        ) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Bottom chrome: always game overview header (even while top shows picker)
                    ReviewHeader(
                        title = game?.displayName ?: "Manual",
                        queueLabel =
                            if (state.queueTotal > 1) {
                                "${state.queueIndex + 1}/${state.queueTotal}"
                            } else {
                                null
                            },
                        searchOpen = searchOpen,
                        showBack = true,
                        onBack = {
                            if (inPicker && !dualDisplay) {
                                viewModel.closeSlot()
                            } else if (inPicker && dualDisplay) {
                                viewModel.closeSlot()
                            } else {
                                dismiss()
                            }
                        },
                        onToggleSearch = {
                            if (searchOpen) {
                                releaseSearchFocus()
                                searchOpen = false
                            } else {
                                searchOpen = true
                            }
                        },
                        searchEnabled = !state.applying && !state.loading,
                    )

                    if (searchOpen && !state.loading && !state.applying) {
                        ReviewSearchBar(
                            draft = state.draftSearchName,
                            applied = state.searchName,
                            searching = state.slotLoading,
                            onDraftChange = viewModel::setDraftSearchName,
                            onSearch = ::runSearch,
                            enabled = !state.slotLoading,
                        )
                    }

                    when {
                        state.loading -> {
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                WajihaLoadingState(message = "Loading…")
                            }
                        }

                        state.applying -> {
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                WajihaLoadingState(message = "Applying…")
                            }
                        }

                        showPickerHere -> {
                            SlotPicker(
                                slot = state.activeSlot!!,
                                state = state,
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                firstFocusRequester = overviewFocus,
                            )
                        }

                        else -> {
                            ReviewOverview(
                                state = state,
                                focusedSlot = focusedSlot,
                                onFocusSlot = { focusedSlot = it },
                                onOpenSlot = { viewModel.openSlot(it) },
                                onClearSlot = { slot ->
                                    val type = slot.mediaType()
                                    if (type != null) {
                                        if (state.hasExistingMedia(type) || type in state.mediaPicks) {
                                            viewModel.clearSlot(type)
                                        }
                                    } else {
                                        viewModel.clearMetadata()
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                firstFocusRequester = overviewFocus,
                            )
                        }
                    }

                    // Footer: Options | count (single-display picker) | Confirm
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!showPickerHere) {
                            Box {
                                GamepadButton(
                                    text = "Options",
                                    onClick = { optionsOpen = true },
                                    outlined = true,
                                )
                                DropdownMenu(
                                    expanded = optionsOpen,
                                    onDismissRequest = { optionsOpen = false },
                                ) {
                                    if (state.lockedSlots == null) {
                                        DropdownMenuItem(
                                            text = { Text("Auto-fill") },
                                            onClick = {
                                                optionsOpen = false
                                                viewModel.autoFillFromPriorities()
                                            },
                                        )
                                    }
                                    if (showSkip && state.queueTotal > 1) {
                                        DropdownMenuItem(
                                            text = { Text("Skip game") },
                                            onClick = {
                                                optionsOpen = false
                                                skipGame()
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Close") },
                                        onClick = {
                                            optionsOpen = false
                                            dismiss()
                                        },
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = WajihaShapes.chip,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = pickerCountLabel(state),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (!showPickerHere) {
                            CriticalChangeActions(
                                hasChanges = state.hasChanges,
                                onRevert = viewModel::revertStaged,
                                onConfirm = ::confirm,
                                confirmEnabled = !state.applying && game != null,
                            )
                        }
                    }

                    state.error?.let { err ->
                        Text(
                            err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    GamepadActionBar(
                        hints =
                            when {
                                showPickerHere -> {
                                    listOf(
                                        "A" to "Select",
                                        "B" to "Back",
                                        "L2" to "Focus screen",
                                        "SELECT" to if (searchOpen) "Go" else "Search",
                                    )
                                }

                                dualDisplay && inPicker -> {
                                    listOf(
                                        "A" to "Pick (top)",
                                        "B" to "Close slot",
                                        "Y" to "Confirm",
                                        "L2" to "Focus screen",
                                        "SELECT" to if (searchOpen) "Go" else "Search",
                                    )
                                }

                                else -> {
                                    listOf(
                                        "A" to "Open",
                                        "B" to "Back",
                                        "X" to "Clear",
                                        "Y" to "Confirm",
                                        "L2" to "Focus screen",
                                        "SELECT" to if (searchOpen) "Go" else "Search",
                                    )
                                }
                            },
                    )
                }
            }
        }
    }
}

/**
 * Top-screen hero for Manual scrape: slot title, candidate grid, count `n / total`.
 * Status bar stays from [TopScreen]; this fills the hero body.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScrapeReviewSlotHero(
    viewModel: ScrapeReviewViewModel = koinInject(),
    contentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val slot = state.activeSlot
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searchOpen by remember(slot) { mutableStateOf(false) }

    fun runSearch() {
        dismissTextEdit(focusManager, keyboard)
        viewModel.search()
        searchOpen = false
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        GamepadKeys.isY(event.type, event.key) -> {
                            if (!state.applying && state.game != null && state.hasChanges) {
                                viewModel.apply(null)
                            }
                            true
                        }

                        GamepadKeys.isBack(event.type, event.key) -> {
                            when {
                                searchOpen -> {
                                    dismissTextEdit(focusManager, keyboard)
                                    searchOpen = false
                                    true
                                }

                                slot != null -> {
                                    viewModel.closeSlot()
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }

                        GamepadKeys.isSelect(event.type, event.key) -> {
                            if (searchOpen) runSearch() else searchOpen = true
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Title row — slot name (status bar is drawn by TopScreen)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = WajihaShapes.chip,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = slot?.label() ?: "Box art",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (slot != null) {
                GamepadButton(
                    text = if (searchOpen) "✕" else "⌕",
                    onClick = {
                        if (searchOpen) {
                            dismissTextEdit(focusManager, keyboard)
                            searchOpen = false
                        } else {
                            searchOpen = true
                        }
                    },
                    outlined = true,
                    enabled = !state.applying && !state.loading,
                )
            }
        }

        if (searchOpen && slot != null) {
            ReviewSearchBar(
                draft = state.draftSearchName,
                applied = state.searchName,
                searching = state.slotLoading,
                onDraftChange = viewModel::setDraftSearchName,
                onSearch = ::runSearch,
                enabled = !state.slotLoading,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            slot == null -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Select a slot on the bottom screen",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.slotLoading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    WajihaLoadingState(message = "Loading…")
                }
            }

            else -> {
                SlotPicker(
                    slot = slot,
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    firstFocusRequester = contentFocusRequester,
                )
            }
        }

        // Count: 12 / 80
        Surface(
            shape = WajihaShapes.chip,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = if (slot != null) pickerCountLabel(state) else "—",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ReviewHeader(
    title: String,
    queueLabel: String?,
    searchOpen: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    searchEnabled: Boolean,
    showBack: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Left cluster: Back | Game Name 🔍
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showBack) {
                GamepadButton(
                    text = "Back",
                    onClick = onBack,
                    outlined = true,
                    sound = null,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            GamepadButton(
                text = if (searchOpen) "✕" else "⌕",
                onClick = onToggleSearch,
                outlined = true,
                enabled = searchEnabled,
            )
        }
        if (queueLabel != null) {
            Surface(
                shape = WajihaShapes.chip,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = queueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun pickerCountLabel(state: ScrapeReviewState): String {
    val slot = state.activeSlot ?: return "0 / 0"
    return if (slot == ReviewSlot.Metadata) {
        val n = state.metadataOptions().size
        "$n / $n"
    } else {
        val type = slot.requireMediaType()
        val n = state.mediaOptions(type).size
        // Wireframe "12 / 80": loaded / known ( + when more pages available)
        if (state.mediaHasMore[type] == true) "$n / $n+" else "$n / $n"
    }
}

@Composable
private fun ReviewSearchBar(
    draft: String,
    applied: String,
    searching: Boolean,
    onDraftChange: (String) -> Unit,
    onSearch: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .dismissKeyboardOnOutsideTap(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GamepadSafeTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = "Search",
                modifier = Modifier.weight(1f),
            )
            CompactFilledButton(
                text = if (searching) "…" else "Go",
                onClick = {
                    dismissTextEdit()
                    onSearch()
                },
                enabled = enabled && !searching,
            )
        }
        Text(
            text =
                if (draft != applied) {
                    "Draft — Go/SELECT to refresh this tab"
                } else {
                    "Query: $applied"
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReviewOverview(
    state: ScrapeReviewState,
    focusedSlot: ReviewSlot,
    onFocusSlot: (ReviewSlot) -> Unit,
    onOpenSlot: (ReviewSlot) -> Unit,
    onClearSlot: (ReviewSlot) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    val slots = state.visibleSlots

    fun tileRequester(slot: ReviewSlot): FocusRequester? = if (slot == focusedSlot) firstFocusRequester else null

    if (slots.size == 1) {
        val slot = slots.first()
        Box(modifier = modifier.padding(10.dp)) {
            OverviewTile(
                slot = slot,
                state = state,
                focused = true,
                onFocus = { onFocusSlot(slot) },
                onOpen = { onOpenSlot(slot) },
                onClear = { onClearSlot(slot) },
                modifier = Modifier.fillMaxSize(),
                focusRequester = tileRequester(slot),
            )
        }
        return
    }

    // Wireframe mosaic: left Icon/Boxart | center Metadata/Logo+Fanart/Banner | right Hero/Screenshots
    Row(
        modifier = modifier.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(0.85f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OverviewTile(
                slot = ReviewSlot.Icon,
                state = state,
                focused = focusedSlot == ReviewSlot.Icon,
                onFocus = { onFocusSlot(ReviewSlot.Icon) },
                onOpen = { onOpenSlot(ReviewSlot.Icon) },
                onClear = { onClearSlot(ReviewSlot.Icon) },
                modifier = Modifier.weight(0.32f).fillMaxWidth(),
                focusRequester = tileRequester(ReviewSlot.Icon),
            )
            OverviewTile(
                slot = ReviewSlot.Boxart,
                state = state,
                focused = focusedSlot == ReviewSlot.Boxart,
                onFocus = { onFocusSlot(ReviewSlot.Boxart) },
                onOpen = { onOpenSlot(ReviewSlot.Boxart) },
                onClear = { onClearSlot(ReviewSlot.Boxart) },
                modifier = Modifier.weight(0.68f).fillMaxWidth(),
                focusRequester = tileRequester(ReviewSlot.Boxart),
            )
        }
        Column(
            modifier = Modifier.weight(1.25f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OverviewTile(
                slot = ReviewSlot.Metadata,
                state = state,
                focused = focusedSlot == ReviewSlot.Metadata,
                onFocus = { onFocusSlot(ReviewSlot.Metadata) },
                onOpen = { onOpenSlot(ReviewSlot.Metadata) },
                onClear = { onClearSlot(ReviewSlot.Metadata) },
                modifier = Modifier.weight(0.26f).fillMaxWidth(),
                focusRequester = tileRequester(ReviewSlot.Metadata),
            )
            Row(
                modifier = Modifier.weight(0.42f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverviewTile(
                    slot = ReviewSlot.Logo,
                    state = state,
                    focused = focusedSlot == ReviewSlot.Logo,
                    onFocus = { onFocusSlot(ReviewSlot.Logo) },
                    onOpen = { onOpenSlot(ReviewSlot.Logo) },
                    onClear = { onClearSlot(ReviewSlot.Logo) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    focusRequester = tileRequester(ReviewSlot.Logo),
                )
                OverviewTile(
                    slot = ReviewSlot.Fanart,
                    state = state,
                    focused = focusedSlot == ReviewSlot.Fanart,
                    onFocus = { onFocusSlot(ReviewSlot.Fanart) },
                    onOpen = { onOpenSlot(ReviewSlot.Fanart) },
                    onClear = { onClearSlot(ReviewSlot.Fanart) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    focusRequester = tileRequester(ReviewSlot.Fanart),
                )
            }
            OverviewTile(
                slot = ReviewSlot.Banner,
                state = state,
                focused = focusedSlot == ReviewSlot.Banner,
                onFocus = { onFocusSlot(ReviewSlot.Banner) },
                onOpen = { onOpenSlot(ReviewSlot.Banner) },
                onClear = { onClearSlot(ReviewSlot.Banner) },
                modifier = Modifier.weight(0.32f).fillMaxWidth(),
                focusRequester = tileRequester(ReviewSlot.Banner),
            )
        }
        Column(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OverviewTile(
                slot = ReviewSlot.Hero,
                state = state,
                focused = focusedSlot == ReviewSlot.Hero,
                onFocus = { onFocusSlot(ReviewSlot.Hero) },
                onOpen = { onOpenSlot(ReviewSlot.Hero) },
                onClear = { onClearSlot(ReviewSlot.Hero) },
                modifier = Modifier.weight(0.58f).fillMaxWidth(),
                focusRequester = tileRequester(ReviewSlot.Hero),
            )
            OverviewTile(
                slot = ReviewSlot.Screenshot,
                state = state,
                focused = focusedSlot == ReviewSlot.Screenshot,
                onFocus = { onFocusSlot(ReviewSlot.Screenshot) },
                onOpen = { onOpenSlot(ReviewSlot.Screenshot) },
                onClear = { onClearSlot(ReviewSlot.Screenshot) },
                modifier = Modifier.weight(0.42f).fillMaxWidth(),
                focusRequester = tileRequester(ReviewSlot.Screenshot),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverviewTile(
    slot: ReviewSlot,
    state: ScrapeReviewState,
    focused: Boolean,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val type = slot.mediaType()
    val staged = type != null && type in state.mediaPicks
    val stagedPick = type?.let { state.mediaPicks[it] }
    val url =
        when (slot) {
            ReviewSlot.Metadata -> {
                state.metadataFrom?.thumbnailUrl
                    ?: state.metadataFrom
                        ?.media
                        ?.firstOrNull()
                        ?.url
            }

            else -> {
                type?.let { state.stagedOrExistingUrl(it) }
            }
        }
    val willClear = staged && stagedPick?.candidate == null
    val border =
        when {
            focused -> MaterialTheme.colorScheme.primary
            staged -> WajihaColors.Tertiary
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        }

    Box(
        modifier =
            modifier
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                ).onFocusChanged { if (it.isFocused) onFocus() }
                .wajihaGamepadFocus()
                .wajihaFocusIndicator(
                    highlighted = focused,
                    shape = RoundedCornerShape(4.dp),
                    selected = focused,
                ).border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onPreviewKeyEvent { event ->
                    when {
                        GamepadKeys.isConfirm(event.type, event.key) -> {
                            onOpen()
                            true
                        }

                        GamepadKeys.isX(event.type, event.key) -> {
                            onClear()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.combinedClickable(
                    onClick = {
                        onFocus()
                        onOpen()
                    },
                    onLongClick = {
                        onFocus()
                        onClear()
                    },
                ),
    ) {
        when {
            !url.isNullOrBlank() && !willClear -> {
                AsyncImage(
                    model = url,
                    contentDescription = slot.label(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            slot == ReviewSlot.Metadata && state.metadataFrom != null -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.metadataFrom!!.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                state.metadataFrom?.sourceId,
                                state.metadataFrom?.metadata?.developer,
                                state.metadataFrom?.metadata?.region,
                            ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (willClear) "Clear" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Bottom label strip (wireframe box caption)
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ).padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text =
                    buildString {
                        append(slot.label())
                        when {
                            willClear -> append(" · clear")
                            staged -> append(" · picked")
                        }
                    },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SlotPicker(
    slot: ReviewSlot,
    state: ScrapeReviewState,
    viewModel: ScrapeReviewViewModel,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.slotError?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        when (slot) {
            ReviewSlot.Metadata -> {
                MetadataPickerList(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    firstFocusRequester = firstFocusRequester,
                )
            }

            else -> {
                val type = slot.requireMediaType()
                MediaPickerGrid(
                    type = type,
                    state = state,
                    onSelect = { sourceId, media ->
                        viewModel.selectMedia(type, sourceId, media)
                    },
                    onClear = { viewModel.clearSlot(type) },
                    onLeave = { viewModel.leaveSlot(type) },
                    onLoadMore = { viewModel.loadMoreMedia(type) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    firstFocusRequester = firstFocusRequester,
                )
            }
        }
    }
}

@Composable
private fun MetadataPickerList(
    state: ScrapeReviewState,
    viewModel: ScrapeReviewViewModel,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    val listState = rememberLazyListState()
    val options = state.metadataOptions()
    if (options.isEmpty() && !state.slotLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No matches — search to refresh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp, top = 4.dp),
    ) {
        items(options, key = { it.key() }) { candidate ->
            CompactCandidateRow(
                candidate = candidate,
                selected =
                    candidate.key() == state.selectedCandidateKey ||
                        candidate.key() == state.metadataFrom?.key(),
                onSelect = {
                    dismissTextEdit()
                    viewModel.selectCandidate(candidate, fillEmptyMediaOnly = true)
                },
                focusRequester =
                    if (candidate == options.firstOrNull()) {
                        firstFocusRequester
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun MediaPickerGrid(
    type: MediaType,
    state: ScrapeReviewState,
    onSelect: (String, MediaCandidate) -> Unit,
    onClear: () -> Unit,
    onLeave: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    val options =
        remember(state.slotCache, state.extraMedia, type, state.searchName) {
            state.mediaOptions(type)
        }
    val staged = type in state.mediaPicks
    val selected = state.mediaPicks[type]
    val hasMore = state.mediaHasMore[type] == true
    val loadingMore = state.mediaLoadingMore
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, hasMore, loadingMore, type) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            last to total
        }.distinctUntilChanged()
            .collect { (last, total) ->
                if (hasMore && !loadingMore && total > 0 && last >= total - 6) {
                    onLoadMore()
                }
            }
    }

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(
                "Leave",
                selected = !staged,
                onClick = onLeave,
                focusRequester = if (options.isEmpty()) firstFocusRequester else null,
            )
            CompactChip(
                "Clear",
                selected = staged && selected?.candidate == null,
                onClick = onClear,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (loadingMore || state.slotLoading) {
                Text(
                    "Loading…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (options.isEmpty() && !state.slotLoading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No ${type.dbName} yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Wireframe: dense portrait grid of candidates
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                state = gridState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp, top = 4.dp),
            ) {
                items(options, key = { it.second.url }) { (sourceId, media) ->
                    val isSelected = staged && selected?.candidate?.url == media.url
                    MediaThumb(
                        url = media.url,
                        label = sourceId,
                        selected = isSelected,
                        onClick = { onSelect(sourceId, media) },
                        focusRequester =
                            if (media.url == options.firstOrNull()?.second?.url) {
                                firstFocusRequester
                            } else {
                                null
                            },
                    )
                }
                if (hasMore) {
                    item(key = "load-more") {
                        CompactFilledButton(
                            text = if (loadingMore) "…" else "More",
                            onClick = onLoadMore,
                            enabled = !loadingMore,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactCandidateRow(
    candidate: ScrapeCandidate,
    selected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val border =
        if (selected || focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                ).onFocusChanged { focused = it.isFocused }
                .wajihaGamepadFocus()
                .wajihaFocusIndicator(
                    highlighted = focused || selected,
                    shape = RoundedCornerShape(4.dp),
                    selected = selected,
                ).border(1.dp, border, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .onPreviewKeyEvent { event ->
                    if (GamepadKeys.isConfirm(event.type, event.key)) {
                        onSelect()
                        true
                    } else {
                        false
                    }
                }.combinedClickable(onClick = onSelect)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = candidate.thumbnailUrl ?: candidate.media.firstOrNull()?.url,
            contentDescription = candidate.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                candidate.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${candidate.sourceId} · ${candidate.media.size} media",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (selected) {
            Text(
                "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumb(
    url: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val border =
        if (selected || focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                ).onFocusChanged { focused = it.isFocused }
                .wajihaGamepadFocus()
                .wajihaFocusIndicator(
                    highlighted = focused || selected,
                    shape = RoundedCornerShape(3.dp),
                    selected = selected,
                ).border(if (selected || focused) 2.dp else 1.dp, border, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
                .onPreviewKeyEvent { event ->
                    if (GamepadKeys.isConfirm(event.type, event.key)) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }.combinedClickable(onClick = onClick),
    ) {
        AsyncImage(
            model = url,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier =
            Modifier
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                ).wajihaGamepadFocus()
                .clip(WajihaShapes.chip)
                .background(bg)
                .onPreviewKeyEvent { event ->
                    if (GamepadKeys.isConfirm(event.type, event.key)) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }.combinedClickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun CompactFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
