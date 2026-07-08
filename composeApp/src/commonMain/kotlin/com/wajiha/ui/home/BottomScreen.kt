package com.wajiha.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.wajiha.input.GamepadKeys
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay

/**
 * Bottom screen (3DS style): horizontally scrolling icon grid, platform
 * filter chips, app-drawer and settings shortcuts.
 *
 * Native gamepad focus: D-pad moves only between game tiles; header is touch-only.
 */
@Composable
fun BottomScreen(
    state: HomeUiState,
    gridRows: Int = 2,
    onSelectPlatform: (String?) -> Unit,
    onFocusGame: (Long?) -> Unit,
    onLaunchGame: (Long) -> Unit,
    onOpenGameDetail: (Long) -> Unit = {},
    onLaunchGameOnDisplay: (Long, Int) -> Unit = { id, _ -> onLaunchGame(id) },
    onRemoveFromLibrary: (Long) -> Unit = {},
    onDeleteGameFile: (Long) -> Unit = {},
    secondaryDisplayId: Int? = null,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: (() -> Unit)? = null,
    onAddGames: (() -> Unit)? = null,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedGameId by remember {
        mutableStateOf(state.tiles.firstOrNull()?.game?.id)
    }
    var contextMenuTarget by remember { mutableStateOf<GameContextTarget?>(null) }
    var contextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val tileBoundsById = remember { mutableStateMapOf<Long, Rect>() }
    var restoringGridFocus by remember { mutableStateOf(false) }
    var restoreFocusGameId by remember { mutableStateOf<Long?>(null) }
    val tileFocusRequesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    val menuOpen = contextMenuTarget != null

    fun openContextMenu(gameId: Long) {
        val tile = state.tiles.find { it.game.id == gameId }
        val name = tile?.game?.displayName ?: ""
        WajihaLog.i(WajihaTags.LAUNCH, "contextMenu: open gameId=$gameId name=$name")
        selectedGameId = gameId
        onFocusGame(gameId)
        contextMenuAnchorBounds = tileBoundsById[gameId]
        restoringGridFocus = false
        restoreFocusGameId = null
        contextMenuTarget = GameContextTarget(gameId, name)
    }

    fun dismissContextMenu() {
        val menuGameId = contextMenuTarget?.gameId
        contextMenuTarget = null
        contextMenuAnchorBounds = null
        if (menuGameId != null) {
            selectedGameId = menuGameId
            onFocusGame(menuGameId)
            restoreFocusGameId = menuGameId
        }
        restoringGridFocus = true
    }

    val gamepadHints = remember(state.platforms, menuOpen) {
        buildList {
            if (menuOpen) {
                add("B" to "Back")
            } else {
                add("A" to "Launch")
                add("X" to "Menu")
                add("B" to "Back")
                if (state.platforms.isNotEmpty()) add("L1/R1" to "Filter")
                add("SELECT" to "Swap")
            }
        }
    }

    WajihaScreen(
        modifier = modifier.fillMaxSize(),
        layerId = "home_grid",
        showActionBar = true,
        gamepadHints = gamepadHints,
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onPreviewKey = { event ->
            if (menuOpen) return@WajihaScreen false
            when {
                GamepadKeys.isX(event.type, event.key) && selectedGameId != null -> {
                    openContextMenu(selectedGameId!!)
                    true
                }
                GamepadKeys.isConfirm(event.type, event.key) && selectedGameId != null -> {
                    WajihaLog.i(
                        WajihaTags.LAUNCH,
                        "confirm: gamepad A launches gameId=$selectedGameId"
                    )
                    onLaunchGame(selectedGameId!!)
                    true
                }
                GamepadKeys.isL1(event.type, event.key) -> cyclePlatformFilter(
                    state = state,
                    delta = -1,
                    onSelectPlatform = onSelectPlatform
                )
                GamepadKeys.isR1(event.type, event.key) -> cyclePlatformFilter(
                    state = state,
                    delta = 1,
                    onSelectPlatform = onSelectPlatform
                )
                else -> false
            }
        }
    ) {
        // Menu lives inside content so GamepadActionBar hints stay undimmed / unblocked.
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = WajihaSpacing.md,
                        end = WajihaSpacing.sm,
                        top = WajihaSpacing.sm,
                        bottom = WajihaSpacing.xs
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
                ) {
                    item {
                        GamepadChip(
                            label = "All",
                            selected = state.selectedPlatformId == null,
                            onClick = { onSelectPlatform(null) },
                            gamepadFocusable = false
                        )
                    }
                    items(state.platforms, key = { it.id }) { platform ->
                        GamepadChip(
                            label = platform.shortName.uppercase(),
                            selected = state.selectedPlatformId == platform.id,
                            onClick = { onSelectPlatform(platform.id) },
                            gamepadFocusable = false
                        )
                    }
                }
                TextButton(
                    onClick = onOpenApps,
                    modifier = Modifier.focusProperties { canFocus = false }
                ) { Text("Apps") }
                if (onOpenSystem != null) {
                    TextButton(
                        onClick = onOpenSystem,
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) { Text("System") }
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.focusProperties { canFocus = false }
                ) { Text("Settings") }
            }

            if (state.tiles.isEmpty()) {
                WajihaEmptyState(
                    title = "No games yet",
                    subtitle = "Add a platform, then point Wajiha at a ROM folder",
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        if (onAddGames != null) {
                            TextButton(
                                onClick = onAddGames,
                                modifier = Modifier
                                    .padding(top = WajihaSpacing.sm)
                                    .focusProperties { canFocus = false }
                            ) {
                                Text("Add platform")
                            }
                        }
                        TextButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .padding(top = WajihaSpacing.xs)
                                .focusProperties { canFocus = false }
                        ) {
                            Text("Open Settings")
                        }
                    }
                )
            } else {
                CompositionLocalProvider(
                    LocalGamepadFocusChromeScope provides GamepadFocusChromeScope.GameGrid
                ) {
                LaunchedEffect(state.tiles.map { it.game.id }) {
                    val ids = state.tiles.map { it.game.id }
                    if (ids.isEmpty()) return@LaunchedEffect
                    if (selectedGameId !in ids) {
                        selectedGameId = ids.first()
                        onFocusGame(ids.first())
                    }
                    if (!menuOpen && !restoringGridFocus && selectedGameId != null) {
                        try {
                            tileFocusRequesters[selectedGameId]?.requestFocus()
                        } catch (_: Exception) {
                        }
                    }
                }

                LaunchedEffect(menuOpen, restoringGridFocus, restoreFocusGameId) {
                    if (!menuOpen && restoringGridFocus) {
                        val gameId = restoreFocusGameId ?: selectedGameId
                        if (gameId != null) {
                            // Wait for menu layer pop + tiles to become focusable again.
                            delay(50)
                            try {
                                tileFocusRequesters[gameId]?.requestFocus()
                            } catch (_: Exception) {
                            }
                        }
                        restoringGridFocus = false
                        restoreFocusGameId = null
                    }
                }

                LazyHorizontalGrid(
                    rows = GridCells.Fixed(gridRows),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(WajihaSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs)
                ) {
                    items(state.tiles, key = { it.game.id }) { tile ->
                        val isSelected = tile.game.id == selectedGameId
                        val tileFocus = remember(tile.game.id) {
                            tileFocusRequesters.getOrPut(tile.game.id) { FocusRequester() }
                        }
                        val isMenuTile = menuOpen && contextMenuTarget?.gameId == tile.game.id
                        val isRestoreTile = restoringGridFocus &&
                            (restoreFocusGameId ?: selectedGameId) == tile.game.id
                        GameTileCard(
                            tile = tile,
                            selected = isSelected,
                            onSelect = {
                                if (gamepadOwner != null) {
                                    onClaimGamepad?.invoke(gamepadOwner)
                                }
                                selectedGameId = tile.game.id
                                onFocusGame(tile.game.id)
                            },
                            onLaunch = { onLaunchGame(tile.game.id) },
                            onLongPress = {
                                if (gamepadOwner != null) {
                                    onClaimGamepad?.invoke(gamepadOwner)
                                }
                                selectedGameId = tile.game.id
                                onFocusGame(tile.game.id)
                                openContextMenu(tile.game.id)
                            },
                            focusRequester = tileFocus,
                            gamepadFocusable = when {
                                menuOpen -> false
                                restoringGridFocus -> isRestoreTile
                                else -> true
                            },
                            navHighlighted = (isMenuTile || isRestoreTile) && isSelected,
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .then(
                                    if (isMenuTile) Modifier.zIndex(1f) else Modifier
                                )
                                .onGloballyPositioned { coords ->
                                    tileBoundsById[tile.game.id] = coords.boundsInRoot()
                                }
                        )
                    }
                }
                }
            }
        }

        GameContextMenu(
            target = contextMenuTarget,
            anchorBounds = contextMenuAnchorBounds,
            secondaryDisplayId = secondaryDisplayId,
            onDismiss = ::dismissContextMenu,
            onOpenOnDisplay = onLaunchGameOnDisplay,
            onOpenInfo = onOpenGameDetail,
            onRemoveFromLibrary = onRemoveFromLibrary,
            onDeleteFile = onDeleteGameFile,
            modifier = Modifier.zIndex(2f),
        )
        }
    }
}

private fun cyclePlatformFilter(
    state: HomeUiState,
    delta: Int,
    onSelectPlatform: (String?) -> Unit
): Boolean {
    if (state.platforms.isEmpty()) return false
    val filters = buildList<String?> {
        add(null)
        addAll(state.platforms.map { it.id })
    }
    val currentIndex = filters.indexOf(state.selectedPlatformId).coerceAtLeast(0)
    val nextIndex = (currentIndex + delta).coerceIn(0, filters.lastIndex)
    if (nextIndex == currentIndex) return false
    onSelectPlatform(filters[nextIndex])
    return true
}

@Composable
private fun GameTileCard(
    tile: GameTile,
    selected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    gamepadFocusable: Boolean = true,
    navHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    GamepadTile(
        selected = selected,
        onSelect = onSelect,
        onLaunch = onLaunch,
        onLongPress = onLongPress,
        focusRequester = focusRequester,
        gamepadFocusable = gamepadFocusable,
        navHighlighted = navHighlighted,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = WajihaShapes.tile
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
                            modifier = Modifier.padding(
                                horizontal = WajihaSpacing.sm,
                                vertical = WajihaSpacing.sm
                            )
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(WajihaSpacing.sm),
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
                            .padding(WajihaSpacing.sm)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
    }
}
