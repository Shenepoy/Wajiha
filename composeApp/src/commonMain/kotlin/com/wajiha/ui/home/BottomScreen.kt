package com.wajiha.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.ui.theme.WajihaColors

/**
 * Bottom screen (3DS style): horizontally scrolling icon grid, platform
 * filter chips, app-drawer and settings shortcuts.
 *
 * Input model mirrored from NeoStation (`my_games_grid.dart` +
 * `GamepadNavigation.onSelectItem`):
 * - D-pad / focus moves **selection** (indexes a tile; updates hero).
 * - Gamepad A / confirm key **immediately launches** the selected tile
 *   (does not use Modifier.clickable, which steals the first DPAD_CENTER
 *   as a focus gain).
 * - Touch: first tap selects/focuses only; second tap on the same selected
 *   tile launches (NeoStation grid only selects on tap; Wajiha adds second
 *   tap for launch as required).
 */
@Composable
fun BottomScreen(
    state: HomeUiState,
    gridRows: Int = 2,
    onSelectPlatform: (String?) -> Unit,
    onFocusGame: (Long?) -> Unit,
    onLaunchGame: (Long) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: (() -> Unit)? = null,
    onAddGames: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header: platform chips + shortcuts
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    PlatformChip(
                        label = "All",
                        selected = state.selectedPlatformId == null,
                        onClick = { onSelectPlatform(null) }
                    )
                }
                items(state.platforms, key = { it.id }) { platform ->
                    PlatformChip(
                        label = platform.shortName.uppercase(),
                        selected = state.selectedPlatformId == platform.id,
                        onClick = { onSelectPlatform(platform.id) }
                    )
                }
            }
            TextButton(
                onClick = onOpenApps,
                modifier = Modifier.focusable()
            ) { Text("Apps") }
            if (onOpenSystem != null) {
                TextButton(
                    onClick = onOpenSystem,
                    modifier = Modifier.focusable()
                ) { Text("System") }
            }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.focusable()
            ) { Text("Settings") }
        }

        // Game grid — horizontal, 3DS home-menu feel
        if (state.tiles.isEmpty()) {
            EmptyLibraryHint(
                onOpenSettings = onOpenSettings,
                onAddGames = onAddGames
            )
        } else {
            val firstTileFocus = remember { FocusRequester() }
            // NeoStation-style selected index (separate from Modifiers.clickable)
            var selectedGameId by remember {
                mutableStateOf(state.tiles.firstOrNull()?.game?.id)
            }
            var aTileHasFocus by remember { mutableStateOf(false) }

            // Keep selection in sync if the filtered library rebuilds.
            LaunchedEffect(state.tiles.map { it.game.id }) {
                val ids = state.tiles.map { it.game.id }
                if (ids.isEmpty()) return@LaunchedEffect
                if (selectedGameId !in ids) {
                    selectedGameId = ids.first()
                    onFocusGame(ids.first())
                }
            }

            // Gamepad: land focus on the first tile once the grid appears
            LaunchedEffect(state.tiles.isNotEmpty()) {
                if (state.tiles.isNotEmpty()) {
                    selectedGameId = state.tiles.first().game.id
                    onFocusGame(selectedGameId)
                    try {
                        firstTileFocus.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }

            // Screen-level confirm: A launches selected tile even if a nested
            // focusable ate the first DPAD_CENTER (NeoStation onSelectItem path).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (
                            aTileHasFocus &&
                            isConfirmKeyDown(event.type, event.key) &&
                            selectedGameId != null
                        ) {
                            WajihaLog.i(
                                WajihaTags.LAUNCH,
                                "confirm: gamepad A launches gameId=$selectedGameId"
                            )
                            onLaunchGame(selectedGameId!!)
                            true
                        } else {
                            false
                        }
                    }
            ) {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(gridRows),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.tiles, key = { it.game.id }) { tile ->
                        val isFirst = tile.game.id == state.tiles.first().game.id
                        GameTileCard(
                            tile = tile,
                            selected = tile.game.id == selectedGameId,
                            onSelect = {
                                selectedGameId = tile.game.id
                                onFocusGame(tile.game.id)
                            },
                            onLaunch = { onLaunchGame(tile.game.id) },
                            onTileFocusChanged = { focused ->
                                if (focused) {
                                    aTileHasFocus = true
                                    selectedGameId = tile.game.id
                                    onFocusGame(tile.game.id)
                                } else {
                                    // Drop confirm ownership when focus leaves the grid
                                    // (e.g. Apps/Settings) so A doesn't launch a game.
                                    aTileHasFocus = false
                                }
                            },
                            initialFocusRequester = if (isFirst) firstTileFocus else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        shape = RoundedCornerShape(50),
        modifier = Modifier.focusable(),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

/**
 * Confirm / "A" keys. Activity remaps BUTTON_A → DPAD_CENTER; handle KeyDown
 * (NeoStation fires select on button press) and consume so KeyUp doesn't fire twice.
 */
private fun isConfirmKeyDown(type: KeyEventType, key: Key): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return key == Key.DirectionCenter ||
        key == Key.Enter ||
        key == Key.NumPadEnter ||
        key == Key.ButtonA
}

@Composable
private fun GameTileCard(
    tile: GameTile,
    selected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onTileFocusChanged: (Boolean) -> Unit,
    initialFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val localFocusRequester = remember { FocusRequester() }
    val focusRequester = initialFocusRequester ?: localFocusRequester
    val highlight = focused || selected
    val scale by animateFloatAsState(
        targetValue = if (highlight) 1.05f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "tileScale"
    )
    // NeoStation: GestureDetector.onTap → select only; A → onPlay.
    // Never use Modifier.clickable here — it creates a second FocusTarget and
    // the first DPAD_CENTER only focuses that child instead of activating.
    Surface(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (highlight) 3.dp else 1.dp,
                color = if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onTileFocusChanged(it.isFocused)
            }
            .focusable()
            // Confirm (A) is handled at the grid Box via onPreviewKeyEvent —
            // NeoStation screen-level onSelectItem — so we do not also handle
            // keys here (avoids double launch).
            .pointerInput(selected, focused) {
                detectTapGestures {
                    // Touch: first tap selects/focuses; second tap while
                    // already focused launches.
                    if (focused) {
                        WajihaLog.i(
                            WajihaTags.LAUNCH,
                            "touch: second tap launches gameId=${tile.game.id} " +
                                "name=${tile.game.displayName}"
                        )
                        onLaunch()
                    } else {
                        WajihaLog.d(
                            WajihaTags.LAUNCH,
                            "touch: first tap selects gameId=${tile.game.id} " +
                                "name=${tile.game.displayName}"
                        )
                        onSelect()
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                    }
                }
            },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box {
            if (tile.boxartPath != null) {
                AsyncImage(
                    model = tile.boxartPath,
                    contentDescription = tile.game.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, WajihaColors.TileScrim)
                            )
                        )
                ) {
                    Text(
                        text = tile.game.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = WajihaColors.OnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tile.game.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (tile.game.favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryHint(
    onOpenSettings: () -> Unit,
    onAddGames: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No games yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Add a platform, then point Wajiha at a ROM folder",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (onAddGames != null) {
                TextButton(onClick = onAddGames, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Add platform")
                }
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 4.dp)) {
                Text("Open Settings")
            }
        }
    }
}
