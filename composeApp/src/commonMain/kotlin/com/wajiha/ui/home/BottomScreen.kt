package com.wajiha.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.rememberedFocusTarget
import com.wajiha.input.requestContentFocus
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.GamepadOwner
import com.wajiha.state.NowPlayingState
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadHintGlyph
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.components.gamepad.hasCenterInViewport
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
import kotlin.math.abs
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
    gridRows: Int = 2,
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
    var selectedSessionPackage by remember { mutableStateOf<String?>(null) }
    var previousGameIds by remember { mutableStateOf(state.tiles.map { it.game.id }) }
    var contextMenuTarget by remember { mutableStateOf<GameContextTarget?>(null) }
    var contextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var sessionContextMenuTarget by remember { mutableStateOf<SessionContextTarget?>(null) }
    var sessionContextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val tileBoundsById = remember { mutableStateMapOf<Long, Rect>() }
    val sessionTileBoundsByPackage = remember { mutableStateMapOf<String, Rect>() }
    var restoringGridFocus by remember { mutableStateOf(false) }
    var restoreFocusGameId by remember { mutableStateOf<Long?>(null) }
    var restoringSessionFocus by remember { mutableStateOf(false) }
    var restoreFocusSessionPackage by remember { mutableStateOf<String?>(null) }
    val tileFocusRequesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    val sessionFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val emptyActionFocus = remember { FocusRequester() }
    val menuOpen = contextMenuTarget != null || sessionContextMenuTarget != null
    val sessionFocused = selectedSessionPackage != null
    val gridSessions = if (showSessionGrid) sessions else emptyList()
    val focusManager = LocalFocusManager.current
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
        val rows = gridRows.coerceAtLeast(1)
        val row = index % rows
        val col = index / rows
        val nextRow = row + deltaRow
        val nextCol = col + deltaCol
        if (nextRow !in 0 until rows) return true
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
        remember(state.platforms, menuOpen, sessionFocused, gridSessions.size, dualDisplay) {
            buildList {
                if (menuOpen) {
                    add(GamepadHint(GamepadHintButton.B, "Back"))
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
                    }
                }
            }
        },
        onPreviewKey = { event ->
            if (menuOpen) return@WajihaScreen false
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
                        cycleSessionFocus(1) || gridSessions.size == 1
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
                        modifier = Modifier.fillMaxSize(),
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
                                !sessionFocused && selectedGameId != null
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

                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(gridRows),
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
                            contentPadding = PaddingValues(WajihaSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs),
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
                                        selected = isSelected,
                                        onSelect = {
                                            if (selectionLocked) return@SessionGridTile
                                            if (gamepadOwner != null) {
                                                onClaimGamepad?.invoke(gamepadOwner)
                                            }
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
                                            selectedSessionPackage = session.packageName
                                            selectedGameId = null
                                            onFocusSession(session.packageName)
                                            openSessionContextMenu(session.packageName)
                                        },
                                        focusRequester = sessionFocus,
                                        gamepadFocusable =
                                            when {
                                                menuOpen -> false

                                                restoringSessionFocus -> isRestoreSession

                                                // Only the selected session accepts Compose focus —
                                                // prevents scroll from parking focus on a peek tile.
                                                else -> isSelected
                                            },
                                        navHighlighted = (isMenuSession || isRestoreSession) && isSelected,
                                        modifier =
                                            Modifier
                                                .aspectRatio(3f / 4f)
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
                                    selected = isSelected,
                                    onSelect = {
                                        if (selectionLocked) return@GameTileCard
                                        if (gamepadOwner != null) {
                                            onClaimGamepad?.invoke(gamepadOwner)
                                        }
                                        // Touch pick owns the origin — don't snap on next D-pad.
                                        pendingLibraryViewportSnap = false
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
                                            menuOpen -> false

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
                                            .aspectRatio(3f / 4f)
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
 */
@Composable
fun HomeChromeBar(
    state: HomeUiState,
    onSelectPlatform: (String?) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: (() -> Unit)? = null,
    overlayOnHero: Boolean,
    centerFilters: Boolean = false,
    gameTitle: String? = null,
    modifier: Modifier = Modifier,
) {
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
                        .fillMaxWidth(0.62f),
                contentPadding = PaddingValues(horizontal = WajihaSpacing.sm),
                fadeEdges = true,
                centerContent = true,
            )
            HomeChromeActions(
                onOpenApps = onOpenApps,
                onOpenSettings = onOpenSettings,
                onOpenSystem = onOpenSystem,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
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
            HomeChromeActions(
                onOpenApps = onOpenApps,
                onOpenSettings = onOpenSettings,
                onOpenSystem = onOpenSystem,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(2f)
                        .onSizeChanged { actionsWidthPx = it.width },
            )
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
            HomeChromeActions(
                onOpenApps = onOpenApps,
                onOpenSettings = onOpenSettings,
                onOpenSystem = onOpenSystem,
            )
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

    Box(modifier = modifier) {
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
        if (showStartFade) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(FilterEdgeFadeWidth)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(edgeColor, Color.Transparent),
                            ),
                        ),
            )
        }
        if (showEndFade) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(FilterEdgeFadeWidth)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, edgeColor),
                            ),
                        ),
            )
        }
    }
}

private val FilterEdgeFadeWidth = 28.dp

/**
 * Keep [gameId] in the padded viewport.
 *
 * Instant [scrollBy] only — at most one column per call (no page teleport).
 * If the tile is already center-in-viewport (e.g. just snapped after touch-scroll),
 * do nothing — an edge-pad nudge here was the post-snap jump.
 */
private suspend fun LazyGridState.smoothBringGameIntoView(
    gameId: Long,
    lazyIndex: Int,
    edgePaddingPx: Int,
) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.key == gameId }
    if (info != null && info.hasCenterInViewport(layoutInfo, horizontalScroll = true)) {
        return
    }
    val stride = libraryColumnStridePx()
    val delta =
        if (info != null) {
            libraryEdgePadDeltaPx(info, edgePaddingPx).coerceIn(-stride.toFloat(), stride.toFloat())
        } else {
            val maxVisible = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: firstVisibleItemIndex
            val minVisible = layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: firstVisibleItemIndex
            when {
                lazyIndex > maxVisible -> stride.toFloat()
                lazyIndex < minVisible -> -stride.toFloat()
                else -> stride.toFloat()
            }
        }
    if (abs(delta) > 0.5f) {
        scrollBy(delta)
    }
}

private fun LazyGridState.libraryColumnStridePx(): Int {
    val viewport =
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val row0 =
        layoutInfo.visibleItemsInfo
            .filter { it.row == 0 }
            .sortedBy { it.offset.x }
    val avgWidth =
        row0
            .map { it.size.width }
            .average()
            .let { if (it.isNaN()) viewport / 3.0 else it }
            .toInt()
            .coerceAtLeast(1)
    return if (row0.size >= 2) {
        (row0[1].offset.x - row0[0].offset.x).coerceAtLeast(1)
    } else {
        avgWidth
    }
}

private fun LazyGridState.libraryEdgePadDeltaPx(
    info: androidx.compose.foundation.lazy.grid.LazyGridItemInfo,
    edgePaddingPx: Int,
): Float {
    val viewportStart = layoutInfo.viewportStartOffset + edgePaddingPx
    val viewportEnd = layoutInfo.viewportEndOffset - edgePaddingPx
    if (viewportEnd <= viewportStart) return 0f
    val itemStart = info.offset.x
    val itemEnd = itemStart + info.size.width
    return when {
        itemStart < viewportStart -> (itemStart - viewportStart).toFloat()
        itemEnd > viewportEnd -> (itemEnd - viewportEnd).toFloat()
        else -> 0f
    }
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
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onOpenApps,
            modifier = Modifier.focusProperties { canFocus = false },
        ) { Text("Apps") }
        if (onOpenSystem != null) {
            TextButton(
                onClick = onOpenSystem,
                modifier = Modifier.focusProperties { canFocus = false },
            ) { Text("System") }
        }
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
    modifier: Modifier = Modifier,
) {
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
                if (tile.boxartPath != null) {
                    AsyncImage(
                        model = tile.boxartPath,
                        contentDescription = tile.game.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, WajihaColors.TileScrim),
                                    ),
                                ),
                    ) {
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
