package com.wajiha.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.wajiha.data.prefs.GameGridPreferences
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.rememberedFocusTarget
import com.wajiha.input.requestContentFocus
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.platform.LaunchableApp
import com.wajiha.state.GamepadOwner
import com.wajiha.state.NowPlayingState
import com.wajiha.ui.apps.AppContextMenu
import com.wajiha.ui.apps.AppContextTarget
import com.wajiha.ui.components.DockShellAction
import com.wajiha.ui.components.DockSlot
import com.wajiha.ui.components.WajihaDock
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.buildDockSlots
import com.wajiha.ui.components.dockPinSlotCount
import com.wajiha.ui.components.dockPinStartIndex
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadHintGlyph
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.components.gamepad.hasCenterInViewport
import com.wajiha.ui.components.gamepad.smoothBringItemIntoView
import com.wajiha.ui.components.gamepad.visibleFractionOnScrollAxis
import com.wajiha.ui.secondary.SessionGridTile
import com.wajiha.ui.secondary.sessionDisplayLabel
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaMotion
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Bottom screen (3DS style): horizontally scrolling icon grid, platform
 * filter chips, app-drawer and settings shortcuts.
 *
 * Native gamepad focus: D-pad moves only between game tiles; header is touch-only.
 */
@Composable
fun BottomScreen(
    state: HomeUiState,
    gridRows: Int = GameGridPreferences.DEFAULT_ROWS,
    gameGridArt: String = GameGridPreferences.DEFAULT_ART,
    gameGridTileSize: String = GameGridPreferences.DEFAULT_TILE_SIZE,
    gameGridShowTitles: Boolean = true,
    gameGridShowTileChrome: Boolean = true,
    onSelectPlatform: (String?) -> Unit,
    onFocusGame: (Long?) -> Unit,
    onLaunchGame: (Long) -> Unit,
    onOpenGameDetail: (Long) -> Unit = {},
    onLaunchGameOnDisplay: (Long, Int) -> Unit = { id, _ -> onLaunchGame(id) },
    onRemoveFromLibrary: (Long) -> Unit = {},
    onDeleteGameFile: (Long) -> Unit = {},
    secondaryDisplayId: Int? = null,
    dualDisplay: Boolean = false,
    /** When true, filter / Apps / Settings sit above the grid (dual, or single without hero). */
    showHeaderChrome: Boolean = dualDisplay,
    /** Focused game title for single-screen name strip; null hides it. */
    focusedGameTitle: String? = null,
    /** Show the focused game's hero art behind the entire grid screen. */
    focusedGameHeroBackground: Boolean = false,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: (() -> Unit)? = null,
    onAddGames: (() -> Unit)? = null,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    sessions: List<NowPlayingState> = emptyList(),
    featuredSessionPackage: String? = null,
    topDisplayPackage: String? = null,
    onFocusSession: (String) -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onCloseSession: (String) -> Unit = {},
    showSessionGrid: Boolean = true,
    /** Persistent home dock above the gamepad action bar. */
    showHomeDock: Boolean = true,
    dockApps: List<LaunchableApp> = emptyList(),
    dockFavoritePackages: List<String> = emptyList(),
    dockIconShape: String = "system",
    onLaunchDockApp: (String) -> Unit = {},
    onLaunchDockAppOnDisplay: (String, Int) -> Unit = { pkg, _ -> onLaunchDockApp(pkg) },
    onRemoveDockFavorite: (String) -> Unit = {},
    onMoveDockFavorite: (String, Int) -> Unit = { _, _ -> },
    onOpenDockAppInfo: (String) -> Unit = {},
    onLoadDockApps: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rememberedGameId = rememberedFocusTarget("home_grid") as? Long
    var selectedGameId by remember {
        mutableStateOf(
            state.tiles
                .firstOrNull { it.game.id == rememberedGameId }
                ?.game
                ?.id
                ?: state.tiles
                    .firstOrNull()
                    ?.game
                    ?.id,
        )
    }

    /** Clamped rows from measure-time resolve — D-pad must match LazyHorizontalGrid. */
    var effectiveGridRows by remember(gridRows) { mutableStateOf(gridRows.coerceAtLeast(1)) }
    var selectedSessionPackage by remember { mutableStateOf<String?>(null) }
    var previousGameIds by remember { mutableStateOf(state.tiles.map { it.game.id }) }
    var contextMenuTarget by remember { mutableStateOf<GameContextTarget?>(null) }
    var contextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var sessionContextMenuTarget by remember { mutableStateOf<SessionContextTarget?>(null) }
    var sessionContextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var dockFocused by remember { mutableStateOf(false) }
    var selectedDockIndex by remember { mutableStateOf(0) }
    var dockContextMenuTarget by remember { mutableStateOf<AppContextTarget?>(null) }
    var dockContextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val tileBoundsById = remember { mutableStateMapOf<Long, Rect>() }
    val sessionTileBoundsByPackage = remember { mutableStateMapOf<String, Rect>() }
    val dockTileBoundsByIndex = remember { mutableStateMapOf<Int, Rect>() }
    val dockTileBoundsByPackage = remember { mutableStateMapOf<String, Rect>() }
    var restoringGridFocus by remember { mutableStateOf(false) }
    var restoreFocusGameId by remember { mutableStateOf<Long?>(null) }
    var restoringSessionFocus by remember { mutableStateOf(false) }
    var restoreFocusSessionPackage by remember { mutableStateOf<String?>(null) }
    val tileFocusRequesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    val sessionFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val dockFocusRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    val emptyActionFocus = remember { FocusRequester() }
    val menuOpen =
        contextMenuTarget != null ||
            sessionContextMenuTarget != null ||
            dockContextMenuTarget != null
    val sessionFocused = selectedSessionPackage != null
    val gridSessions = if (showSessionGrid) sessions else emptyList()
    val focusManager = LocalFocusManager.current
    val dockPins =
        remember(dockApps, dockFavoritePackages) {
            val byPkg = dockApps.associateBy { it.packageName }
            dockFavoritePackages.mapNotNull { byPkg[it] }
        }
    val dockSlots = remember(dockPins) { buildDockSlots(pins = dockPins) }
    val showDock = showHomeDock
    val dockMenuPackage = dockContextMenuTarget?.packageName
    val dockMenuPinIndex =
        remember(dockMenuPackage, dockSlots) {
            if (dockMenuPackage == null) {
                -1
            } else {
                dockSlots.indexOfFirst {
                    it is DockSlot.Pin && it.app.packageName == dockMenuPackage
                }
            }
        }
    val dockMenuPinBounds =
        dockMenuPackage?.let { dockTileBoundsByPackage[it] }
    LaunchedEffect(showDock) {
        if (showDock) onLoadDockApps()
    }
    LaunchedEffect(dockSlots.size) {
        dockSlots.indices.forEach { index ->
            if (dockFocusRequesters[index] == null) {
                dockFocusRequesters[index] = FocusRequester()
            }
        }
        if (selectedDockIndex !in dockSlots.indices && dockSlots.isNotEmpty()) {
            selectedDockIndex = 0
        }
    }
    // Keep selection + menu cutout on the same pin while favorites reorder.
    LaunchedEffect(dockMenuPackage, dockMenuPinIndex, dockMenuPinBounds) {
        if (dockMenuPackage == null || dockMenuPinIndex < 0) return@LaunchedEffect
        if (selectedDockIndex != dockMenuPinIndex) {
            selectedDockIndex = dockMenuPinIndex
        }
        if (dockMenuPinBounds != null && dockContextMenuAnchorBounds != dockMenuPinBounds) {
            dockContextMenuAnchorBounds = dockMenuPinBounds
        }
    }
    LaunchedEffect(showDock) {
        if (!showDock) {
            dockFocused = false
            dockContextMenuTarget = null
            dockContextMenuAnchorBounds = null
        }
    }
    val initiallySelectedGameIndex = state.tiles.indexOfFirst { it.game.id == selectedGameId }
    val initialLibraryIndex =
        if (initiallySelectedGameIndex >= 0) {
            val prefix = if (gridSessions.isEmpty()) 0 else gridSessions.size + 1
            prefix + initiallySelectedGameIndex
        } else {
            0
        }
    // Start directly on the remembered semantic target. Without this, a role
    // swap paints index 0 for one frame before owner restoration scrolls here.
    val libraryGridState =
        rememberLazyGridState(
            initialFirstVisibleItemIndex = initialLibraryIndex,
        )
    var pendingLibraryViewportSnap by remember { mutableStateOf(false) }
    val pendingLibraryViewportSnapLatest = rememberUpdatedState(pendingLibraryViewportSnap)
    // True while a finger is down on the library grid — used to arm post-touch D-pad snap
    // without mistaking programmatic focus-scroll for a touch drag.
    val libraryPointerDown = remember { AtomicBoolean(false) }
    // Side of the screen the selection occupied at finger-down (before any scroll).
    val librarySnapPreferTrailing = remember { AtomicBoolean(false) }
    val selectedGameIdForSnap = rememberUpdatedState(selectedGameId)
    val libraryScrollEdgePadPx =
        with(LocalDensity.current) { (WajihaSpacing.md + WajihaSpacing.sm).roundToPx() }
    // Lock library selection to the menu target so focus-stealing tiles can't
    // rewrite selectedGameId while the context menu owns gamepad focus.
    val selectionLocked = menuOpen || restoringGridFocus || restoringSessionFocus

    fun selectionOnTrailingHalfOfViewport(selectedId: Long?): Boolean {
        if (selectedId == null) return false
        val layoutInfo = libraryGridState.layoutInfo
        val vpStart = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
        val vpEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val selectedInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == selectedId }
        if (selectedInfo != null) {
            val center = selectedInfo.offset.x + selectedInfo.size.width / 2
            val mid = (vpStart + vpEnd) / 2
            return center >= mid
        }
        // Already off-screen — fall back to list-index vs visible range.
        val ids = state.tiles.map { it.game.id }
        val selIdx = ids.indexOf(selectedId)
        if (selIdx < 0) return false
        val visIdxs =
            layoutInfo.visibleItemsInfo.mapNotNull { info ->
                (info.key as? Long)?.let { ids.indexOf(it) }?.takeIf { it >= 0 }
            }
        if (visIdxs.isEmpty()) return false
        val midIdx = ((visIdxs.minOrNull() ?: 0) + (visIdxs.maxOrNull() ?: 0)) / 2
        return selIdx >= midIdx
    }

    LaunchedEffect(libraryGridState) {
        snapshotFlow {
            libraryGridState.firstVisibleItemIndex to libraryGridState.firstVisibleItemScrollOffset
        }.drop(1)
            .collect {
                if (libraryPointerDown.get()) {
                    pendingLibraryViewportSnap = true
                    WajihaLog.i(WajihaTags.DEBUG, "gridFocus: arm viewportSnap (touch scroll)")
                }
            }
    }

    fun libraryLazyIndexForGame(gameId: Long): Int? {
        val gameIndex = state.tiles.indexOfFirst { it.game.id == gameId }
        if (gameIndex < 0) return null
        val prefix = if (gridSessions.isEmpty()) 0 else gridSessions.size + 1
        return prefix + gameIndex
    }

    // Selection chrome is driven by selectedGameId (instant). Scroll catches up
    // one column at a time; kill any touch-fling first so it can't yank the grid.
    LaunchedEffect(selectedGameId, sessionFocused, menuOpen) {
        if (menuOpen || sessionFocused) return@LaunchedEffect
        val gameId = selectedGameId ?: return@LaunchedEffect
        val lazyIndex = libraryLazyIndexForGame(gameId) ?: return@LaunchedEffect
        WajihaLog.i(
            WajihaTags.DEBUG,
            "gridFocus: effect start gameId=$gameId lazyIndex=$lazyIndex " +
                libraryGridState.debugScrollSnapshot(gameId),
        )
        // Cancels in-progress touch fling / prior focus scroll (acquires scroll mutex).
        libraryGridState.scroll { }
        withFrameNanos { }
        libraryGridState.smoothBringGameIntoView(
            gameId = gameId,
            lazyIndex = lazyIndex,
            edgePaddingPx = libraryScrollEdgePadPx,
        )
        WajihaLog.i(
            WajihaTags.DEBUG,
            "gridFocus: effect afterScroll gameId=$gameId " +
                libraryGridState.debugScrollSnapshot(gameId),
        )
        try {
            tileFocusRequesters[gameId]?.requestFocus()
            WajihaLog.i(WajihaTags.DEBUG, "gridFocus: requestFocus gameId=$gameId")
        } catch (e: Exception) {
            WajihaLog.i(WajihaTags.DEBUG, "gridFocus: requestFocus FAILED gameId=$gameId err=$e")
        }
        WajihaLog.i(
            WajihaTags.DEBUG,
            "gridFocus: effect end gameId=$gameId " +
                libraryGridState.debugScrollSnapshot(gameId),
        )
    }

    LaunchedEffect(menuOpen) {
        if (menuOpen) {
            focusManager.clearFocus(force = true)
        }
    }

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

    fun openSessionContextMenu(packageName: String) {
        val session = gridSessions.find { it.packageName == packageName } ?: return
        val label = sessionDisplayLabel(session) ?: packageName
        WajihaLog.i(WajihaTags.LAUNCH, "sessionContextMenu: open pkg=$packageName label=$label")
        selectedSessionPackage = packageName
        selectedGameId = null
        onFocusSession(packageName)
        sessionContextMenuAnchorBounds = sessionTileBoundsByPackage[packageName]
        restoringSessionFocus = false
        restoreFocusSessionPackage = null
        sessionContextMenuTarget = SessionContextTarget(packageName, label)
    }

    fun dismissSessionContextMenu() {
        val menuPackage = sessionContextMenuTarget?.packageName
        sessionContextMenuTarget = null
        sessionContextMenuAnchorBounds = null
        if (menuPackage != null) {
            selectedSessionPackage = menuPackage
            onFocusSession(menuPackage)
            restoreFocusSessionPackage = menuPackage
        }
        restoringSessionFocus = true
    }

    fun cycleSessionFocus(delta: Int): Boolean {
        if (gridSessions.size < 2) return false
        val packages = gridSessions.map { it.packageName }
        val currentIndex = selectedSessionPackage?.let { packages.indexOf(it) } ?: -1
        val nextIndex =
            when {
                currentIndex < 0 -> if (delta > 0) 0 else packages.lastIndex
                else -> (currentIndex + delta).coerceIn(0, packages.lastIndex)
            }
        if (nextIndex == currentIndex) return false
        val nextPackage = packages[nextIndex]
        selectedSessionPackage = nextPackage
        selectedGameId = null
        onFocusSession(nextPackage)
        try {
            sessionFocusRequesters[nextPackage]?.requestFocus()
        } catch (_: Exception) {
        }
        return true
    }

    fun exitSessionFocusToLibrary(): Boolean {
        selectedSessionPackage = null
        dockFocused = false
        val gameId =
            selectedGameId ?: state.tiles
                .firstOrNull()
                ?.game
                ?.id
        if (gameId == null) return false
        selectedGameId = gameId
        onFocusGame(gameId)
        return true
    }

    fun enterDock(preferredIndex: Int = 0): Boolean {
        if (!showDock || dockSlots.isEmpty()) return false
        selectedSessionPackage = null
        dockFocused = true
        selectedDockIndex = preferredIndex.coerceIn(0, dockSlots.lastIndex)
        try {
            dockFocusRequesters[selectedDockIndex]?.requestFocus()
        } catch (_: Exception) {
        }
        return true
    }

    /** Enter dock from the library, landing on a pin aligned to the focused column. */
    fun enterDockFromLibrary(): Boolean {
        val ids = state.tiles.map { it.game.id }
        val index = selectedGameId?.let { ids.indexOf(it) } ?: -1
        val rows = effectiveGridRows.coerceAtLeast(1)
        val col = if (index >= 0) index / rows else 0
        val pinCount = dockPinSlotCount(dockPins)
        val pinLocal = col.coerceIn(0, pinCount - 1)
        return enterDock(dockPinStartIndex() + pinLocal)
    }

    fun exitDockToLibrary(): Boolean {
        if (!dockFocused) return false
        dockFocused = false
        dockContextMenuTarget = null
        dockContextMenuAnchorBounds = null
        val gameId =
            selectedGameId ?: state.tiles
                .firstOrNull()
                ?.game
                ?.id
        if (gameId != null) {
            selectedGameId = gameId
            onFocusGame(gameId)
            try {
                tileFocusRequesters[gameId]?.requestFocus()
            } catch (_: Exception) {
            }
            return true
        }
        if (gridSessions.isNotEmpty()) {
            val pkg = gridSessions.first().packageName
            selectedSessionPackage = pkg
            onFocusSession(pkg)
            try {
                sessionFocusRequesters[pkg]?.requestFocus()
            } catch (_: Exception) {
            }
            return true
        }
        return true
    }

    fun moveDockFocus(delta: Int): Boolean {
        if (!dockFocused || dockSlots.isEmpty()) return false
        val next = (selectedDockIndex + delta).coerceIn(0, dockSlots.lastIndex)
        if (next == selectedDockIndex) return true
        selectedDockIndex = next
        try {
            dockFocusRequesters[next]?.requestFocus()
        } catch (_: Exception) {
        }
        return true
    }

    fun activateDockIndex(index: Int): Boolean {
        when (val slot = dockSlots.getOrNull(index)) {
            is DockSlot.Pin -> {
                onLaunchDockApp(slot.app.packageName)
            }

            DockSlot.Add -> {
                onOpenApps()
            }

            is DockSlot.Shell -> {
                when (slot.action) {
                    DockShellAction.Apps -> onOpenApps()
                    DockShellAction.Settings -> onOpenSettings()
                }
            }

            null -> {
                return false
            }
        }
        return true
    }

    fun openDockPinMenu(index: Int): Boolean {
        val pin = dockSlots.getOrNull(index) as? DockSlot.Pin ?: return false
        selectedDockIndex = index
        dockContextMenuAnchorBounds =
            dockTileBoundsByPackage[pin.app.packageName] ?: dockTileBoundsByIndex[index]
        dockContextMenuTarget = AppContextTarget(pin.app.packageName, pin.app.label)
        return true
    }

    fun dismissDockContextMenu() {
        dockContextMenuTarget = null
        dockContextMenuAnchorBounds = null
        if (dockFocused) {
            try {
                dockFocusRequesters[selectedDockIndex]?.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * After touch-scroll, land on a ≥50%-visible top-row tile on the same side of
     * the screen the selection occupied when the scroll began (left→leading,
     * right→trailing). Returns false when selection is already center-visible.
     */
    fun snapLibraryFocusToVisibleViewport(): Boolean {
        val layoutInfo = libraryGridState.layoutInfo
        val gameVisible =
            layoutInfo.visibleItemsInfo.filter {
                it.key is Long && it.hasCenterInViewport(layoutInfo, horizontalScroll = true)
            }
        if (gameVisible.isEmpty()) return false
        val selectedId = selectedGameId
        // Already on a fully visible tile — don't eat this D-pad press.
        if (selectedId != null && gameVisible.any { it.key == selectedId }) {
            WajihaLog.i(
                WajihaTags.DEBUG,
                "gridFocus: snap skip (sel already visible) sel=$selectedId",
            )
            return false
        }
        val topRow =
            gameVisible
                .filter { it.row == 0 }
                .sortedBy { it.offset.x }
        // Prefer fully on-screen edge tiles; fall back to ≥50% when the viewport
        // only has peeks (e.g. mid-fling).
        val vpStart = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
        val vpEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val fullyOnScreen =
            topRow.filter {
                it.offset.x >= vpStart && it.offset.x + it.size.width <= vpEnd
            }
        val edgeRow =
            fullyOnScreen.ifEmpty {
                topRow.ifEmpty {
                    gameVisible.sortedWith(compareBy({ it.offset.x }, { it.row }))
                }
            }
        if (edgeRow.isEmpty()) return false
        val preferTrailing = librarySnapPreferTrailing.get()
        val targetInfo = if (preferTrailing) edgeRow.last() else edgeRow.first()
        val targetId = targetInfo.key as? Long ?: return false
        val edge = if (preferTrailing) "trailing" else "leading"
        val frac =
            targetInfo.visibleFractionOnScrollAxis(layoutInfo, horizontalScroll = true)
        WajihaLog.i(
            WajihaTags.DEBUG,
            "gridFocus: snap sel=$selectedId → $targetId edge=$edge visibleFrac=$frac " +
                "item=[${targetInfo.offset.x},${targetInfo.offset.x + targetInfo.size.width}] " +
                "topRow=${edgeRow.map { "${it.key}@${it.offset.x}" }}",
        )
        selectedSessionPackage = null
        selectedGameId = targetId
        onFocusGame(targetId)
        return true
    }

    LaunchedEffect(libraryGridState, state.tiles) {
        snapshotFlow { libraryGridState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && !libraryPointerDown.get() && pendingLibraryViewportSnap) {
                    pendingLibraryViewportSnap = false
                    snapLibraryFocusToVisibleViewport()
                }
            }
    }

    /**
     * D-pad for library tiles from [selectedGameId] (column-major, [gridRows]).
     * Games sit after the session divider in a clean grid, so index math matches
     * the visual layout — independent of wherever Compose focus last was (touch).
     */
    fun moveLibraryGridFocus(
        deltaRow: Int,
        deltaCol: Int,
    ): Boolean {
        val ids = state.tiles.map { it.game.id }
        val currentId = selectedGameId ?: return false
        val index = ids.indexOf(currentId)
        if (index < 0) return false
        val rows = effectiveGridRows.coerceAtLeast(1)
        val row = index % rows
        val col = index / rows
        val nextRow = row + deltaRow
        val nextCol = col + deltaCol
        if (nextRow !in 0 until rows) {
            if (deltaRow > 0 && showDock && row == rows - 1) {
                return enterDockFromLibrary()
            }
            return true
        }
        if (nextCol < 0) {
            if (gridSessions.isEmpty()) return true
            val lastSessionCol = (gridSessions.lastIndex) / rows
            val sessionIndex =
                (lastSessionCol * rows + nextRow).coerceIn(0, gridSessions.lastIndex)
            val pkg = gridSessions[sessionIndex].packageName
            selectedSessionPackage = pkg
            selectedGameId = null
            onFocusSession(pkg)
            try {
                sessionFocusRequesters[pkg]?.requestFocus()
            } catch (_: Exception) {
            }
            return true
        }
        val nextIndex = nextCol * rows + nextRow
        if (nextIndex !in ids.indices) return true
        val nextId = ids[nextIndex]
        selectedSessionPackage = null
        WajihaLog.i(
            WajihaTags.DEBUG,
            "gridFocus: move sel=$currentId idx=$index → $nextId idx=$nextIndex " +
                "dRow=$deltaRow dCol=$deltaCol " +
                libraryGridState.debugScrollSnapshot(nextId),
        )
        selectedGameId = nextId
        onFocusGame(nextId)
        return true
    }

    fun moveLibraryAfterOptionalViewportSnap(
        deltaRow: Int,
        deltaCol: Int,
    ): Boolean {
        if (pendingLibraryViewportSnap) {
            pendingLibraryViewportSnap = false
            WajihaLog.i(
                WajihaTags.DEBUG,
                "gridFocus: consume viewportSnap dRow=$deltaRow dCol=$deltaCol " +
                    "sel=$selectedGameId ${libraryGridState.debugScrollSnapshot(selectedGameId ?: -1L)}",
            )
            if (snapLibraryFocusToVisibleViewport()) {
                return true
            }
            // Selection already on-screen — treat as a normal step.
        }
        return moveLibraryGridFocus(deltaRow = deltaRow, deltaCol = deltaCol)
    }

    val gamepadHints =
        remember(
            state.platforms,
            menuOpen,
            sessionFocused,
            dockFocused,
            gridSessions.size,
            dualDisplay,
            showDock,
            dockSlots,
            selectedDockIndex,
        ) {
            buildList {
                if (menuOpen) {
                    add(GamepadHint(GamepadHintButton.B, "Back"))
                } else if (dockFocused) {
                    add(GamepadHint(GamepadHintButton.A, "Launch"))
                    if (dockSlots.getOrNull(selectedDockIndex) is DockSlot.Pin) {
                        add(GamepadHint(GamepadHintButton.X, "Edit"))
                    }
                    add(GamepadHint(GamepadHintButton.B, "Games"))
                    if (dualDisplay) {
                        add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
                        add(GamepadHint(GamepadHintButton.Select, "Swap"))
                    }
                } else if (sessionFocused) {
                    add(GamepadHint(GamepadHintButton.A, "Switch"))
                    add(GamepadHint(GamepadHintButton.Y, "Close"))
                    add(GamepadHint(GamepadHintButton.X, "Menu"))
                    if (gridSessions.size > 1) {
                        add(GamepadHint(GamepadHintButton.DpadUpDown, "Sessions"))
                    }
                    add(GamepadHint(GamepadHintButton.DpadRight, "Games"))
                    add(GamepadHint(GamepadHintButton.B, "Back"))
                    if (dualDisplay) {
                        add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
                        add(GamepadHint(GamepadHintButton.Select, "Swap"))
                    }
                } else {
                    add(GamepadHint(GamepadHintButton.A, "Launch"))
                    add(GamepadHint(GamepadHintButton.X, "Menu"))
                    add(GamepadHint(GamepadHintButton.B, "Back"))
                    if (state.platforms.isNotEmpty()) {
                        add(GamepadHint(GamepadHintButton.L1R1, "Filter"))
                    }
                    if (dualDisplay) {
                        add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
                        add(GamepadHint(GamepadHintButton.Select, "Swap"))
                    }
                }
            }
        }
    val focusedHeroPath =
        if (focusedGameHeroBackground) {
            state.tiles.firstOrNull { it.game.id == selectedGameId }?.heroPath
        } else {
            null
        }

    WajihaScreen(
        modifier = modifier.fillMaxSize(),
        layerId = "home_grid",
        // BUTTON_B is routed via the activity back dispatcher (not preview keys).
        onBack =
            if (dockFocused && showDock) {
                {
                    exitDockToLibrary()
                    Unit
                }
            } else {
                null
            },
        showActionBar = true,
        gamepadHints = gamepadHints,
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        backgroundContent =
            if (focusedHeroPath != null) {
                { GameGridHeroBackdrop(path = focusedHeroPath) }
            } else {
                null
            },
        onOwnerGainedFocus = {
            if (!menuOpen) {
                if (dockFocused && showDock) {
                    dockFocusRequesters[selectedDockIndex]?.requestContentFocus()
                } else {
                    val sessionPkg = selectedSessionPackage
                    if (sessionPkg != null) {
                        val sessionIndex = gridSessions.indexOfFirst { it.packageName == sessionPkg }
                        val sessionIsVisible =
                            libraryGridState.layoutInfo.visibleItemsInfo.any { info ->
                                info.key == "session-$sessionPkg" &&
                                    info.hasCenterInViewport(
                                        libraryGridState.layoutInfo,
                                        horizontalScroll = true,
                                    )
                            }
                        if (sessionIndex >= 0 && !sessionIsVisible) {
                            libraryGridState.scrollToItem(sessionIndex)
                            withFrameNanos { }
                        }
                        sessionFocusRequesters[sessionPkg]?.requestContentFocus()
                    } else {
                        val gameId =
                            selectedGameId ?: state.tiles
                                .firstOrNull()
                                ?.game
                                ?.id
                        if (gameId != null) {
                            val gameIsVisible =
                                libraryGridState.layoutInfo.visibleItemsInfo.any { info ->
                                    info.key == gameId &&
                                        info.hasCenterInViewport(
                                            libraryGridState.layoutInfo,
                                            horizontalScroll = true,
                                        )
                                }
                            if (!gameIsVisible) {
                                libraryLazyIndexForGame(gameId)?.let { lazyIndex ->
                                    // Fallback for mutations or an already-mounted grid
                                    // whose viewport predates the role swap.
                                    libraryGridState.scrollToItem(lazyIndex)
                                    withFrameNanos { }
                                }
                            }
                            tileFocusRequesters[gameId]?.requestContentFocus()
                        } else if (showDock) {
                            enterDock()
                        }
                    }
                }
            }
        },
        onPreviewKey = { event ->
            if (menuOpen) return@WajihaScreen false
            if (dockFocused && showDock) {
                return@WajihaScreen when {
                    GamepadKeys.isLeft(event.type, event.key) -> {
                        moveDockFocus(-1)
                    }

                    GamepadKeys.isRight(event.type, event.key) -> {
                        moveDockFocus(1)
                    }

                    GamepadKeys.isUp(event.type, event.key) -> {
                        exitDockToLibrary()
                    }

                    GamepadKeys.isDown(event.type, event.key) -> {
                        true
                    }

                    GamepadKeys.isConfirm(event.type, event.key) -> {
                        activateDockIndex(selectedDockIndex)
                    }

                    GamepadKeys.isX(event.type, event.key) -> {
                        openDockPinMenu(selectedDockIndex)
                        // Consume even on Add/shell — hints hide Edit there; don't leak X.
                        true
                    }

                    GamepadKeys.isBack(event.type, event.key) -> {
                        exitDockToLibrary()
                    }

                    GamepadKeys.isL1(event.type, event.key) -> {
                        cyclePlatformFilter(
                            state = state,
                            delta = -1,
                            onSelectPlatform = onSelectPlatform,
                        )
                    }

                    GamepadKeys.isR1(event.type, event.key) -> {
                        cyclePlatformFilter(
                            state = state,
                            delta = 1,
                            onSelectPlatform = onSelectPlatform,
                        )
                    }

                    else -> {
                        false
                    }
                }
            }
            selectedSessionPackage?.let { pkg ->
                when {
                    GamepadKeys.isLeft(event.type, event.key) -> {
                        true
                    }

                    GamepadKeys.isRight(event.type, event.key) -> {
                        exitSessionFocusToLibrary()
                    }

                    GamepadKeys.isUp(event.type, event.key) -> {
                        cycleSessionFocus(-1) || gridSessions.size == 1
                    }

                    GamepadKeys.isDown(event.type, event.key) -> {
                        if (cycleSessionFocus(1)) {
                            true
                        } else if (showDock) {
                            enterDockFromLibrary()
                        } else {
                            gridSessions.size == 1
                        }
                    }

                    GamepadKeys.isX(event.type, event.key) -> {
                        openSessionContextMenu(pkg)
                        true
                    }

                    GamepadKeys.isY(event.type, event.key) -> {
                        onCloseSession(pkg)
                        true
                    }

                    GamepadKeys.isConfirm(event.type, event.key) -> {
                        onOpenSession(pkg)
                        true
                    }

                    GamepadKeys.isBack(event.type, event.key) -> {
                        exitSessionFocusToLibrary()
                    }

                    else -> {
                        null
                    }
                }?.let { return@WajihaScreen it }
            }
            when {
                GamepadKeys.isUp(event.type, event.key) && selectedGameId != null -> {
                    moveLibraryAfterOptionalViewportSnap(deltaRow = -1, deltaCol = 0)
                }

                GamepadKeys.isDown(event.type, event.key) && selectedGameId != null -> {
                    moveLibraryAfterOptionalViewportSnap(deltaRow = 1, deltaCol = 0)
                }

                GamepadKeys.isLeft(event.type, event.key) && selectedGameId != null -> {
                    moveLibraryAfterOptionalViewportSnap(deltaRow = 0, deltaCol = -1)
                }

                GamepadKeys.isRight(event.type, event.key) && selectedGameId != null -> {
                    moveLibraryAfterOptionalViewportSnap(deltaRow = 0, deltaCol = 1)
                }

                GamepadKeys.isX(event.type, event.key) && selectedGameId != null -> {
                    openContextMenu(selectedGameId!!)
                    true
                }

                GamepadKeys.isConfirm(event.type, event.key) && selectedGameId != null -> {
                    WajihaLog.i(
                        WajihaTags.LAUNCH,
                        "confirm: gamepad A launches gameId=$selectedGameId",
                    )
                    onLaunchGame(selectedGameId!!)
                    true
                }

                GamepadKeys.isL1(event.type, event.key) -> {
                    cyclePlatformFilter(
                        state = state,
                        delta = -1,
                        onSelectPlatform = onSelectPlatform,
                    )
                }

                GamepadKeys.isR1(event.type, event.key) -> {
                    cyclePlatformFilter(
                        state = state,
                        delta = 1,
                        onSelectPlatform = onSelectPlatform,
                    )
                }

                else -> {
                    false
                }
            }
        },
    ) {
        // Menu lives inside content so GamepadActionBar hints stay undimmed / unblocked.
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dual / hero-hidden single: chrome above the grid.
                // Single + hero: chrome overlays the banner (App.kt).
                if (showHeaderChrome) {
                    HomeChromeBar(
                        state = state,
                        onSelectPlatform = onSelectPlatform,
                        onOpenApps = onOpenApps,
                        onOpenSettings = onOpenSettings,
                        onOpenSystem = onOpenSystem,
                        overlayOnHero = false,
                        // Dock owns Apps/Settings; System stays top-right when available.
                        showAppsAction = !showDock,
                        showSettingsAction = !showDock,
                        // Center only in single + no-hero when the game name is shown.
                        // Dual and name-hidden single keep filters parked on the left.
                        centerFilters = !dualDisplay && focusedGameTitle != null,
                        gameTitle = focusedGameTitle,
                    )
                }

                if (state.tiles.isEmpty() && gridSessions.isEmpty()) {
                    WajihaEmptyState(
                        title = "No games yet",
                        subtitle = "Add a platform, then point Wajiha at a ROM folder",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        action = {
                            LaunchedEffect(onAddGames) {
                                withFrameNanos { }
                                try {
                                    emptyActionFocus.requestFocus()
                                } catch (_: Exception) {
                                }
                            }
                            if (onAddGames != null) {
                                GamepadButton(
                                    text = "Add platform",
                                    onClick = onAddGames,
                                    focusRequester = emptyActionFocus,
                                    focusId = "empty:add_platform",
                                    modifier = Modifier.padding(top = WajihaSpacing.sm),
                                )
                            }
                            GamepadButton(
                                text = "Open Settings",
                                onClick = onOpenSettings,
                                focusRequester = if (onAddGames == null) emptyActionFocus else null,
                                focusId = "empty:settings",
                                outlined = true,
                                modifier = Modifier.padding(top = WajihaSpacing.xs),
                            )
                        },
                    )
                } else {
                    CompositionLocalProvider(
                        LocalGamepadFocusChromeScope provides GamepadFocusChromeScope.GameGrid,
                    ) {
                        LaunchedEffect(gridSessions.map { it.packageName }) {
                            if (selectedSessionPackage != null &&
                                selectedSessionPackage !in gridSessions.map { it.packageName }
                            ) {
                                selectedSessionPackage = null
                            }
                        }

                        LaunchedEffect(state.tiles.map { it.game.id }) {
                            val ids = state.tiles.map { it.game.id }
                            if (ids.isEmpty()) return@LaunchedEffect
                            if (selectedGameId !in ids) {
                                val previousIndex =
                                    previousGameIds
                                        .indexOf(selectedGameId)
                                        .takeIf { it >= 0 }
                                        ?: 0
                                val fallback =
                                    rememberedGameId
                                        ?.takeIf { it in ids }
                                        ?: ids[previousIndex.coerceIn(ids.indices)]
                                selectedGameId = fallback
                                onFocusGame(fallback)
                            }
                            previousGameIds = ids
                            if (!menuOpen && !restoringGridFocus && !restoringSessionFocus &&
                                !sessionFocused && !dockFocused && selectedGameId != null
                            ) {
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
                                    withFrameNanos { }
                                    try {
                                        tileFocusRequesters[gameId]?.requestFocus()
                                    } catch (_: Exception) {
                                    }
                                }
                                restoringGridFocus = false
                                restoreFocusGameId = null
                            }
                        }

                        LaunchedEffect(menuOpen, restoringSessionFocus, restoreFocusSessionPackage) {
                            if (!menuOpen && restoringSessionFocus) {
                                val pkg = restoreFocusSessionPackage ?: selectedSessionPackage
                                if (pkg != null) {
                                    withFrameNanos { }
                                    try {
                                        sessionFocusRequesters[pkg]?.requestFocus()
                                    } catch (_: Exception) {
                                    }
                                }
                                restoringSessionFocus = false
                                restoreFocusSessionPackage = null
                            }
                        }

                        val gridSlot =
                            remember(gameGridArt, gridRows, gameGridTileSize) {
                                GameGridPreferences.Slot(
                                    art = gameGridArt,
                                    rows = gridRows,
                                    tileSize = gameGridTileSize,
                                    configured = true,
                                )
                            }
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val resolvedGrid =
                                remember(gridSlot, maxHeight) {
                                    GameGridPreferences.resolve(gridSlot, maxHeight)
                                }
                            val tileAspect = resolvedGrid.aspectRatio
                            val tileScale = resolvedGrid.contentScale
                            val tileArt = resolvedGrid.art
                            val tileHeightFrac = resolvedGrid.sizeFactor.coerceIn(0.70f, 1f)
                            // Keep D-pad math in lockstep with LazyHorizontalGrid rows —
                            // LaunchedEffect lags one frame and can send Down into the dock
                            // while the visible grid still has a lower row.
                            SideEffect {
                                if (effectiveGridRows != resolvedGrid.rows) {
                                    effectiveGridRows = resolvedGrid.rows
                                }
                            }
                            LaunchedEffect(resolvedGrid.rows, tileArt, resolvedGrid.tileSize) {
                                // Density/art change: keep selection, re-snap viewport + focus.
                                val gameId = selectedGameId ?: return@LaunchedEffect
                                val index = state.tiles.indexOfFirst { it.game.id == gameId }
                                if (index >= 0) {
                                    val sessionOffset =
                                        if (gridSessions.isNotEmpty()) {
                                            gridSessions.size + 1
                                        } else {
                                            0
                                        }
                                    runCatching {
                                        libraryGridState.scrollToItem(sessionOffset + index)
                                    }
                                }
                                withFrameNanos { }
                                runCatching { tileFocusRequesters[gameId]?.requestFocus() }
                            }
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(resolvedGrid.rows),
                                state = libraryGridState,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        // Track finger-down in Initial pass so LazyGrid scroll
                                        // still reaches us; scroll-while-down arms viewport snap.
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(
                                                    requireUnconsumed = false,
                                                    pass = PointerEventPass.Initial,
                                                )
                                                libraryPointerDown.set(true)
                                                // Capture side only for a fresh snap cycle. A second
                                                // swipe after the tile has drifted left must not
                                                // overwrite trailing=true from the original right-side
                                                // selection.
                                                if (!pendingLibraryViewportSnapLatest.value) {
                                                    val preferTrailing =
                                                        selectionOnTrailingHalfOfViewport(
                                                            selectedGameIdForSnap.value,
                                                        )
                                                    librarySnapPreferTrailing.set(preferTrailing)
                                                    WajihaLog.i(
                                                        WajihaTags.DEBUG,
                                                        "gridFocus: capture snapSide trailing=$preferTrailing " +
                                                            "sel=${selectedGameIdForSnap.value}",
                                                    )
                                                } else {
                                                    WajihaLog.i(
                                                        WajihaTags.DEBUG,
                                                        "gridFocus: keep snapSide trailing=${librarySnapPreferTrailing.get()} " +
                                                            "sel=${selectedGameIdForSnap.value}",
                                                    )
                                                }
                                                try {
                                                    while (true) {
                                                        val event =
                                                            awaitPointerEvent(PointerEventPass.Initial)
                                                        if (event.changes.none { it.pressed }) break
                                                    }
                                                } finally {
                                                    libraryPointerDown.set(false)
                                                    if (!libraryGridState.isScrollInProgress &&
                                                        pendingLibraryViewportSnapLatest.value
                                                    ) {
                                                        pendingLibraryViewportSnap = false
                                                        snapLibraryFocusToVisibleViewport()
                                                    }
                                                    // Keep Compose focus on the selected tile (only it
                                                    // is focusable). clearFocus here used to drop key
                                                    // delivery so the post-scroll D-pad never snapped.
                                                }
                                            }
                                        },
                                contentPadding =
                                    PaddingValues(
                                        start = WajihaSpacing.md + WajihaSpacing.xs,
                                        top = WajihaSpacing.md + WajihaSpacing.xs,
                                        // Extra end/bottom so D-pad can rest the last column/row
                                        // fully on-screen (focus ring + selected scale).
                                        end = WajihaSpacing.xl,
                                        bottom = WajihaSpacing.xl,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.md),
                            ) {
                                if (gridSessions.isNotEmpty()) {
                                    items(gridSessions, key = { "session-${it.packageName}" }) { session ->
                                        val isSelected =
                                            session.packageName ==
                                                (sessionContextMenuTarget?.packageName ?: selectedSessionPackage)
                                        val sessionFocus =
                                            remember(session.packageName) {
                                                sessionFocusRequesters.getOrPut(session.packageName) { FocusRequester() }
                                            }
                                        val isMenuSession =
                                            menuOpen &&
                                                sessionContextMenuTarget?.packageName == session.packageName
                                        val isRestoreSession =
                                            restoringSessionFocus &&
                                                (restoreFocusSessionPackage ?: selectedSessionPackage) ==
                                                session.packageName
                                        SessionGridTile(
                                            session = session,
                                            isOnTop = topDisplayPackage == session.packageName,
                                            isFeatured = featuredSessionPackage == session.packageName,
                                            selected = isSelected && !dockFocused,
                                            onSelect = {
                                                if (selectionLocked) return@SessionGridTile
                                                if (gamepadOwner != null) {
                                                    onClaimGamepad?.invoke(gamepadOwner)
                                                }
                                                dockFocused = false
                                                selectedSessionPackage = session.packageName
                                                selectedGameId = null
                                                onFocusSession(session.packageName)
                                            },
                                            onOpen = { onOpenSession(session.packageName) },
                                            onClose = { onCloseSession(session.packageName) },
                                            onLongPress = {
                                                if (selectionLocked) return@SessionGridTile
                                                if (gamepadOwner != null) {
                                                    onClaimGamepad?.invoke(gamepadOwner)
                                                }
                                                dockFocused = false
                                                selectedSessionPackage = session.packageName
                                                selectedGameId = null
                                                onFocusSession(session.packageName)
                                                openSessionContextMenu(session.packageName)
                                            },
                                            focusRequester = sessionFocus,
                                            gamepadFocusable =
                                                when {
                                                    menuOpen || dockFocused -> false

                                                    restoringSessionFocus -> isRestoreSession

                                                    // Only the selected session accepts Compose focus —
                                                    // prevents scroll from parking focus on a peek tile.
                                                    else -> isSelected
                                                },
                                            navHighlighted = (isMenuSession || isRestoreSession) && isSelected,
                                            artStyle = tileArt,
                                            contentScale = tileScale,
                                            modifier =
                                                Modifier
                                                    .fillMaxHeight(tileHeightFrac)
                                                    .aspectRatio(tileAspect)
                                                    .then(
                                                        if (isMenuSession) Modifier.zIndex(1f) else Modifier,
                                                    ).onGloballyPositioned { coords ->
                                                        sessionTileBoundsByPackage[session.packageName] =
                                                            coords.boundsInRoot()
                                                    },
                                        )
                                    }
                                    item(
                                        key = "session-divider",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        VerticalDivider(
                                            modifier = Modifier.fillMaxSize(),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        )
                                    }
                                }
                                items(state.tiles, key = { it.game.id }) { tile ->
                                    val lockedGameId = contextMenuTarget?.gameId ?: selectedGameId
                                    val isSelected = tile.game.id == lockedGameId
                                    val tileFocus =
                                        remember(tile.game.id) {
                                            tileFocusRequesters.getOrPut(tile.game.id) { FocusRequester() }
                                        }
                                    val isMenuTile = menuOpen && contextMenuTarget?.gameId == tile.game.id
                                    val isRestoreTile =
                                        restoringGridFocus &&
                                            (restoreFocusGameId ?: selectedGameId) == tile.game.id
                                    GameTileCard(
                                        tile = tile,
                                        selected = isSelected && !dockFocused,
                                        artStyle = tileArt,
                                        contentScale = tileScale,
                                        showTitle = gameGridShowTitles,
                                        showTileChrome = gameGridShowTileChrome,
                                        onSelect = {
                                            if (selectionLocked) return@GameTileCard
                                            if (gamepadOwner != null) {
                                                onClaimGamepad?.invoke(gamepadOwner)
                                            }
                                            // Touch pick owns the origin — don't snap on next D-pad.
                                            pendingLibraryViewportSnap = false
                                            dockFocused = false
                                            selectedSessionPackage = null
                                            selectedGameId = tile.game.id
                                            onFocusGame(tile.game.id)
                                        },
                                        onLaunch = { onLaunchGame(tile.game.id) },
                                        onLongPress = {
                                            if (selectionLocked) return@GameTileCard
                                            if (gamepadOwner != null) {
                                                onClaimGamepad?.invoke(gamepadOwner)
                                            }
                                            dockFocused = false
                                            selectedGameId = tile.game.id
                                            onFocusGame(tile.game.id)
                                            openContextMenu(tile.game.id)
                                        },
                                        onFocusChanged = { focused ->
                                            WajihaLog.i(
                                                WajihaTags.DEBUG,
                                                "gridFocus: tileFocus gameId=${tile.game.id} " +
                                                    "name=${tile.game.displayName} focused=$focused " +
                                                    "selected=$isSelected " +
                                                    libraryGridState.debugScrollSnapshot(tile.game.id),
                                            )
                                        },
                                        focusRequester = tileFocus,
                                        gamepadFocusable =
                                            when {
                                                menuOpen || dockFocused -> false

                                                restoringGridFocus -> isRestoreTile

                                                // Only the selected tile is focusable. Touch-scroll
                                                // otherwise moves Compose focus onto the first/peek
                                                // tile while selectedGameId stays elsewhere — user
                                                // sees "first" then D-pad snap jumps to the real edge.
                                                else -> isSelected
                                            },
                                        navHighlighted = (isMenuTile || isRestoreTile) && isSelected,
                                        modifier =
                                            Modifier
                                                .fillMaxHeight(tileHeightFrac)
                                                .aspectRatio(tileAspect)
                                                .then(
                                                    if (isMenuTile) Modifier.zIndex(1f) else Modifier,
                                                ).onGloballyPositioned { coords ->
                                                    tileBoundsById[tile.game.id] = coords.boundsInRoot()
                                                },
                                    )
                                }
                            }
                        }
                    }
                }

                if (showDock) {
                    WajihaDock(
                        pins = dockPins,
                        selectedIndex = selectedDockIndex,
                        dockFocused = dockFocused,
                        iconShape = dockIconShape,
                        onSelectIndex = { index ->
                            dockFocused = true
                            selectedSessionPackage = null
                            selectedDockIndex = index
                        },
                        onActivateIndex = { index ->
                            dockFocused = true
                            selectedDockIndex = index
                            activateDockIndex(index)
                        },
                        onLongPressIndex = { index ->
                            dockFocused = true
                            selectedDockIndex = index
                            if (!openDockPinMenu(index)) {
                                activateDockIndex(index)
                            }
                        },
                        focusRequesters = dockFocusRequesters,
                        onSlotBoundsChanged = { index, bounds ->
                            dockTileBoundsByIndex[index] = bounds
                            when (val slot = dockSlots.getOrNull(index)) {
                                is DockSlot.Pin -> {
                                    dockTileBoundsByPackage[slot.app.packageName] = bounds
                                }

                                else -> {
                                    Unit
                                }
                            }
                        },
                        // Keep pin selection chrome while the menu owns Compose focus.
                        claimFocus = dockContextMenuTarget == null,
                    )
                }
            }

            GameContextMenu(
                target = contextMenuTarget,
                anchorBounds = contextMenuAnchorBounds,
                secondaryDisplayId = secondaryDisplayId,
                dualDisplay = dualDisplay,
                onDismiss = ::dismissContextMenu,
                onOpenOnDisplay = onLaunchGameOnDisplay,
                onOpenInfo = onOpenGameDetail,
                onRemoveFromLibrary = onRemoveFromLibrary,
                onDeleteFile = onDeleteGameFile,
                modifier = Modifier.zIndex(2f),
            )

            SessionContextMenu(
                target = sessionContextMenuTarget,
                anchorBounds = sessionContextMenuAnchorBounds,
                onDismiss = ::dismissSessionContextMenu,
                onCloseSession = onCloseSession,
                modifier = Modifier.zIndex(2f),
            )

            AppContextMenu(
                target = dockContextMenuTarget,
                anchorBounds = dockContextMenuAnchorBounds,
                onDismiss = ::dismissDockContextMenu,
                onOpenAppInfo = onOpenDockAppInfo,
                onLaunchOnDisplay = onLaunchDockAppOnDisplay,
                dualDisplay = dualDisplay,
                topDisplayId = 0,
                bottomDisplayId = secondaryDisplayId ?: 4,
                isFavorite = true,
                canMoveFavoriteUp =
                    dockContextMenuTarget?.let { target ->
                        dockPins.indexOfFirst { it.packageName == target.packageName } > 0
                    } ?: false,
                canMoveFavoriteDown =
                    dockContextMenuTarget?.let { target ->
                        val idx = dockPins.indexOfFirst { it.packageName == target.packageName }
                        idx >= 0 && idx < dockPins.lastIndex
                    } ?: false,
                onToggleFavorite = { pkg ->
                    onRemoveDockFavorite(pkg)
                    dismissDockContextMenu()
                },
                onMoveFavoriteUp = { pkg ->
                    onMoveDockFavorite(pkg, -1)
                },
                onMoveFavoriteDown = { pkg ->
                    onMoveDockFavorite(pkg, 1)
                },
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}

@Composable
private fun GameGridHeroBackdrop(path: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.background),
    ) {
        AsyncImage(
            model = path,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    scheme.background.copy(alpha = 0.72f),
                                    scheme.background.copy(alpha = 0.82f),
                                    scheme.background.copy(alpha = 0.92f),
                                ),
                        ),
                    ),
        )
    }
}

/**
 * Platform filter + Apps / System / Settings shortcuts.
 *
 * [overlayOnHero] — filter bottom-center of the hero banner; actions bottom-end
 * (single-screen with banner).
 * [centerFilters] — filter centered with optional [gameTitle] on the left
 * (single-screen, hero off, name shown). Dual / name-hidden single use a left-
 * parked filter row.
 *
 * When the home dock is shown, Apps/Settings move to the dock corners and only
 * System remains here (top-right).
 */
@Composable
fun HomeChromeBar(
    state: HomeUiState,
    onSelectPlatform: (String?) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: (() -> Unit)? = null,
    overlayOnHero: Boolean,
    showAppsAction: Boolean = true,
    showSettingsAction: Boolean = true,
    centerFilters: Boolean = false,
    gameTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val showChromeActions =
        showAppsAction ||
            showSettingsAction ||
            onOpenSystem != null
    if (overlayOnHero) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                ),
                        ),
                    ).padding(
                        start = WajihaSpacing.md,
                        end = WajihaSpacing.sm,
                        top = WajihaSpacing.md,
                        bottom = WajihaSpacing.xs,
                    ),
        ) {
            HomePlatformFilterRow(
                state = state,
                onSelectPlatform = onSelectPlatform,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(if (showChromeActions) 0.62f else 0.86f),
                contentPadding = PaddingValues(horizontal = WajihaSpacing.sm),
                fadeEdges = true,
                centerContent = true,
            )
            if (showChromeActions) {
                HomeChromeActions(
                    onOpenApps = onOpenApps,
                    onOpenSettings = onOpenSettings,
                    onOpenSystem = onOpenSystem,
                    showApps = showAppsAction,
                    showSettings = showSettingsAction,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    } else if (centerFilters && gameTitle != null) {
        // True-center filters against Settings. Name width tracks free space
        // left of the chips: grows when filters are few, shrinks to the left
        // gutter when the row is full / scrollable.
        val density = LocalDensity.current
        var actionsWidthPx by remember { mutableStateOf(0) }
        var filterLeadingGapPx by remember { mutableStateOf(0) }
        var filterOverflowing by remember { mutableStateOf(false) }
        val sideGutter =
            with(density) {
                actionsWidthPx.toDp().coerceAtLeast(1.dp) + WajihaSpacing.xs
            }
        val nameGap = WajihaSpacing.sm
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(
                        start = WajihaSpacing.md,
                        end = WajihaSpacing.sm,
                        top = WajihaSpacing.sm,
                        bottom = WajihaSpacing.xs,
                    ),
        ) {
            val nameWidth =
                with(density) {
                    val gutterPx = sideGutter.toPx()
                    val gapPx = nameGap.toPx()
                    // Keep fade + a small gap so marquee never paints on chips.
                    val safetyPx = (HomeTitleEdgeFadeWidth + 4.dp).toPx()
                    val maxNamePx =
                        if (filterOverflowing || actionsWidthPx == 0) {
                            (gutterPx - gapPx - safetyPx).coerceAtLeast(0f)
                        } else {
                            (gutterPx + filterLeadingGapPx - gapPx - safetyPx).coerceAtLeast(0f)
                        }
                    maxNamePx
                        .toDp()
                        .coerceIn(48.dp, maxWidth * 0.5f)
                }
            HomePlatformFilterRow(
                state = state,
                onSelectPlatform = onSelectPlatform,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = sideGutter),
                contentPadding = PaddingValues(horizontal = WajihaSpacing.xs),
                fadeEdges = true,
                centerContent = true,
                onLeadingGapChanged = { gapPx, overflowing ->
                    filterLeadingGapPx = gapPx
                    filterOverflowing = overflowing
                },
            )
            HomeGameTitleMarquee(
                text = gameTitle,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(nameWidth)
                        .zIndex(1f),
            )
            if (showChromeActions) {
                HomeChromeActions(
                    onOpenApps = onOpenApps,
                    onOpenSettings = onOpenSettings,
                    onOpenSystem = onOpenSystem,
                    showApps = showAppsAction,
                    showSettings = showSettingsAction,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .zIndex(2f)
                            .onSizeChanged { actionsWidthPx = it.width },
                )
            }
        }
    } else {
        // Dual, or single with hero off and no game name: filters parked left.
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(
                        start = WajihaSpacing.md,
                        end = WajihaSpacing.sm,
                        top = WajihaSpacing.sm,
                        bottom = WajihaSpacing.xs,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomePlatformFilterRow(
                state = state,
                onSelectPlatform = onSelectPlatform,
                modifier = Modifier.weight(1f),
                fadeEdges = true,
                centerContent = false,
            )
            if (showChromeActions) {
                HomeChromeActions(
                    onOpenApps = onOpenApps,
                    onOpenSettings = onOpenSettings,
                    onOpenSystem = onOpenSystem,
                    showApps = showAppsAction,
                    showSettings = showSettingsAction,
                )
            }
        }
    }
}

@Composable
private fun HomeGameTitleMarquee(
    text: String,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.titleMedium
    val textColor = MaterialTheme.colorScheme.onBackground
    val edgeColor = MaterialTheme.colorScheme.background
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fadeWidthPx = with(density) { HomeTitleEdgeFadeWidth.toPx() }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val textLayoutResult =
            remember(text, textStyle, containerWidthPx) {
                textMeasurer.measure(
                    text = text,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        val textWidthPx = textLayoutResult.size.width.toFloat()
        // Scroll until the trailing end clears the fade into the readable zone.
        val readableWidthPx = (containerWidthPx - fadeWidthPx).coerceAtLeast(0f)
        val scrollDistance = (textWidthPx - readableWidthPx).coerceAtLeast(0f)
        val overflow = scrollDistance > 1f
        val offsetX = remember(text) { Animatable(0f) }

        LaunchedEffect(text, overflow, scrollDistance) {
            if (!overflow) {
                offsetX.snapTo(0f)
                return@LaunchedEffect
            }
            val durationMs =
                ((scrollDistance / HomeTitleMarqueePxPerSec) * 1000f)
                    .roundToInt()
                    .coerceIn(1500, 12_000)
            while (true) {
                offsetX.snapTo(0f)
                delay(HomeTitleMarqueeIdleMs)
                offsetX.animateTo(-scrollDistance, tween(durationMs))
                delay(HomeTitleMarqueeIdleMs)
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(edgeColor)
                    .drawWithContent {
                        drawContent()
                        if (overflow) {
                            val fadeStart = (size.width - fadeWidthPx).coerceAtLeast(0f)
                            drawRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, edgeColor),
                                        startX = fadeStart,
                                        endX = size.width,
                                    ),
                                topLeft = Offset(fadeStart, 0f),
                                size = Size(size.width - fadeStart, size.height),
                            )
                        }
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier =
                    Modifier
                        // Full intrinsic width so offset text isn't clipped by the Text itself;
                        // the parent Box clips to the title band.
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            )
        }
    }
}

private val HomeTitleMarqueeIdleMs = 2_000L
private val HomeTitleMarqueePxPerSec = 36f
private val HomeTitleEdgeFadeWidth = 32.dp

@Composable
private fun HomePlatformFilterRow(
    state: HomeUiState,
    onSelectPlatform: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    fadeEdges: Boolean = false,
    centerContent: Boolean = false,
    onLeadingGapChanged: ((leadingGapPx: Int, overflowing: Boolean) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val selectedIndex =
        remember(state.selectedPlatformId, state.platforms) {
            platformFilterIndex(state)
        }
    val showStartFade by remember {
        derivedStateOf { fadeEdges && listState.canScrollBackward }
    }
    val showEndFade by remember {
        derivedStateOf { fadeEdges && listState.canScrollForward }
    }
    val edgeColor = MaterialTheme.colorScheme.background
    val chipArrangement =
        if (centerContent) {
            Arrangement.spacedBy(WajihaSpacing.sm, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(WajihaSpacing.sm)
        }

    LaunchedEffect(selectedIndex, state.platforms.size) {
        // Wait a frame so layoutInfo is current after selection changes (L1/R1).
        delay(16)
        listState.ensurePlatformFilterVisible(selectedIndex)
    }

    LaunchedEffect(listState, state.platforms.size, onLeadingGapChanged) {
        if (onLeadingGapChanged == null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val overflowing =
                listState.canScrollForward || listState.canScrollBackward
            val leadingGap =
                when {
                    overflowing -> 0
                    visible.isEmpty() -> 0
                    visible.size < info.totalItemsCount -> 0
                    else -> (visible.first().offset - info.viewportStartOffset).coerceAtLeast(0)
                }
            leadingGap to overflowing
        }.collect { (gap, overflowing) ->
            onLeadingGapChanged(gap, overflowing)
        }
    }

    Box(
        modifier =
            modifier.drawWithContent {
                drawContent()
                val fadeWidth = FilterEdgeFadeWidth.toPx().coerceAtMost(size.width)
                if (showStartFade) {
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(edgeColor, Color.Transparent),
                                startX = 0f,
                                endX = fadeWidth,
                            ),
                        size = Size(fadeWidth, size.height),
                    )
                }
                if (showEndFade) {
                    val fadeStart = size.width - fadeWidth
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, edgeColor),
                                startX = fadeStart,
                                endX = size.width,
                            ),
                        topLeft = Offset(fadeStart, 0f),
                        size = Size(fadeWidth, size.height),
                    )
                }
            },
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = chipArrangement,
        ) {
            item(key = "all") {
                GamepadChip(
                    label = "All",
                    selected = state.selectedPlatformId == null,
                    onClick = { onSelectPlatform(null) },
                    gamepadFocusable = false,
                )
            }
            items(state.platforms, key = { it.id }) { platform ->
                GamepadChip(
                    label = platform.shortName.uppercase(),
                    selected = state.selectedPlatformId == platform.id,
                    onClick = { onSelectPlatform(platform.id) },
                    gamepadFocusable = false,
                )
            }
        }
    }
}

private val FilterEdgeFadeWidth = 14.dp

/**
 * Keep [gameId] fully inside the padded viewport.
 *
 * Instant [scrollBy] only — at most one column per call (no page teleport).
 * Requires the whole tile (not just center) so trailing columns aren't clipped.
 */
private suspend fun LazyGridState.smoothBringGameIntoView(
    gameId: Long,
    lazyIndex: Int,
    edgePaddingPx: Int,
) {
    smoothBringItemIntoView(
        key = gameId,
        lazyIndex = lazyIndex,
        horizontalScroll = true,
        edgePaddingPx = edgePaddingPx,
    )
}

/** Compact scroll + item geometry for focus/scroll diagnostics. */
private fun LazyGridState.debugScrollSnapshot(gameId: Long): String {
    val layout = layoutInfo
    val info = layout.visibleItemsInfo.firstOrNull { it.key == gameId }
    val visible =
        layout.visibleItemsInfo
            .filter { it.key is Long }
            .joinToString(",") { "${it.key}@${it.offset.x}" }
    return "first=$firstVisibleItemIndex off=$firstVisibleItemScrollOffset " +
        "vp=[${layout.viewportStartOffset},${layout.viewportEndOffset}] " +
        "item=${info?.let { "[${it.offset.x},${it.offset.x + it.size.width}] col=${it.column}" } ?: "null"} " +
        "visKeys=$visible"
}

private fun platformFilterIndex(state: HomeUiState): Int {
    val id = state.selectedPlatformId ?: return 0
    val platformIndex = state.platforms.indexOfFirst { it.id == id }
    return if (platformIndex >= 0) platformIndex + 1 else 0
}

/** Keep the selected filter chip fully inside the LazyRow viewport. */
private suspend fun LazyListState.ensurePlatformFilterVisible(index: Int) {
    if (index < 0) return
    val layout = layoutInfo
    val visible = layout.visibleItemsInfo
    if (visible.isEmpty()) {
        animateScrollToItem(index)
        return
    }
    val item = visible.firstOrNull { it.index == index }
    if (item == null) {
        animateScrollToItem(index)
        return
    }
    val start = layout.viewportStartOffset
    val end = layout.viewportEndOffset
    val pad = 8
    when {
        item.offset < start + pad -> {
            animateScrollBy((item.offset - start - pad).toFloat())
        }

        item.offset + item.size > end - pad -> {
            animateScrollBy((item.offset + item.size - end + pad).toFloat())
        }
    }
}

@Composable
private fun HomeChromeActions(
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: (() -> Unit)?,
    showApps: Boolean = true,
    showSettings: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showApps) {
            Box(modifier = Modifier.focusProperties { canFocus = false }) {
                TextButton(onClick = onOpenApps) {
                    Text("Apps")
                }
                // Overlay on the Apps button only; does not expand the row.
                Box(modifier = Modifier.matchParentSize()) {
                    GamepadHintGlyph(
                        button = GamepadHintButton.L3,
                        size = 12.dp,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp),
                    )
                }
            }
        }
        if (onOpenSystem != null) {
            TextButton(
                onClick = onOpenSystem,
                modifier = Modifier.focusProperties { canFocus = false },
            ) { Text("System") }
        }
        if (showSettings) {
            Box(modifier = Modifier.focusProperties { canFocus = false }) {
                TextButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
                // Overlay on the Settings button only; does not expand the row.
                Box(modifier = Modifier.matchParentSize()) {
                    GamepadHintGlyph(
                        button = GamepadHintButton.Start,
                        size = 12.dp,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp),
                    )
                }
            }
        }
    }
}

private fun cyclePlatformFilter(
    state: HomeUiState,
    delta: Int,
    onSelectPlatform: (String?) -> Unit,
): Boolean {
    if (state.platforms.isEmpty()) return false
    val filters =
        buildList<String?> {
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
    onFocusChanged: (Boolean) -> Unit = {},
    focusRequester: FocusRequester? = null,
    gamepadFocusable: Boolean = true,
    navHighlighted: Boolean = false,
    artStyle: String = GameGridPreferences.DEFAULT_ART,
    contentScale: ContentScale = ContentScale.Crop,
    showTitle: Boolean = true,
    showTileChrome: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val artPath =
        GameGridPreferences.resolveArtPath(
            art = artStyle,
            boxartPath = tile.boxartPath,
            iconPath = tile.iconPath,
            logoPath = tile.logoPath,
            squarePath = tile.squarePath,
        )
    val insetArt =
        showTileChrome && contentScale == ContentScale.Fit
    GamepadTile(
        selected = selected,
        onSelect = onSelect,
        onLaunch = onLaunch,
        onLongPress = onLongPress,
        onFocusChanged = onFocusChanged,
        focusRequester = focusRequester,
        focusId = tile.game.id,
        gamepadFocusable = gamepadFocusable,
        navHighlighted = navHighlighted,
        // D-pad/touch own selection; focus-during-scroll must not rewind selectedGameId.
        selectOnFocus = false,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = WajihaShapes.tile,
        ) {
            Box {
                if (artPath != null) {
                    AsyncImage(
                        model = artPath,
                        contentDescription = tile.game.displayName,
                        contentScale = contentScale,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(if (insetArt) WajihaSpacing.sm else 0.dp),
                    )
                    if (showTileChrome || showTitle) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .then(
                                        if (showTileChrome) {
                                            Modifier.background(
                                                Brush.verticalGradient(
                                                    colors =
                                                        listOf(
                                                            Color.Transparent,
                                                            WajihaColors.TileScrim,
                                                        ),
                                                ),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                        ) {
                            if (showTitle) {
                                Text(
                                    text = tile.game.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WajihaColors.OnDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = WajihaSpacing.sm,
                                            vertical = WajihaSpacing.sm,
                                        ),
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(WajihaSpacing.sm),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = tile.game.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tile.game.favorite) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(WajihaSpacing.sm)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }
        }
    }
}
