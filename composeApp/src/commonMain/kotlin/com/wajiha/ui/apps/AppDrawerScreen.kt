package com.wajiha.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wajiha.data.prefs.AppDrawerGridPreferences
import com.wajiha.data.prefs.IconAppearancePreferences
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.input.rememberedFocusTarget
import com.wajiha.input.requestContentFocus
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.platform.LaunchableApp
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaFolderChromeMetrics
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.GamepadHintCornerButton
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.components.gamepad.ScrollEdgeFadeBox
import com.wajiha.ui.components.gamepad.hasCenterInViewport
import com.wajiha.ui.components.gamepad.smoothBringItemIntoView
import com.wajiha.ui.theme.AppIconShapes
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.LocalInputModeController
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.flow.drop
import org.koin.compose.koinInject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

/** App drawer: same NeoStation select/confirm model as game tiles. */
@Composable
fun AppDrawerScreen(
    apps: List<LaunchableApp>,
    onLoad: () -> Unit,
    onLaunch: (String) -> Unit,
    onLaunchOnDisplay: (String, Int) -> Unit = { pkg, _ -> onLaunch(pkg) },
    onOpenAppInfo: (String) -> Unit = {},
    onBack: () -> Unit,
    onFocusChange: (LaunchableApp?) -> Unit = {},
    secondaryDisplayId: Int? = null,
    dualDisplay: Boolean = false,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    iconShape: String = IconAppearancePreferences.DEFAULT_SHAPE,
    gridColumns: Int = AppDrawerGridPreferences.DEFAULT_COLUMNS,
    gridRows: Int = AppDrawerGridPreferences.DEFAULT_ROWS,
    iconSizePreference: String = AppDrawerGridPreferences.DEFAULT_ICON_SIZE,
    gridOrientation: String = AppDrawerGridPreferences.DEFAULT_ORIENTATION,
    gridScrollMode: String = AppDrawerGridPreferences.DEFAULT_SCROLL_MODE,
    showAppLabels: Boolean = AppDrawerGridPreferences.DEFAULT_SHOW_LABELS,
    onGridColumnsChange: (Int) -> Unit = {},
    onGridRowsChange: (Int) -> Unit = {},
    onIconSizeChange: (String) -> Unit = {},
    onGridOrientationChange: (String) -> Unit = {},
    onGridScrollModeChange: (String) -> Unit = {},
    onShowAppLabelsChange: (Boolean) -> Unit = {},
    favoritePackages: List<String> = emptyList(),
    onAddFavorite: (String) -> Unit = {},
    onRemoveFavorite: (String) -> Unit = {},
    onMoveFavorite: (packageName: String, delta: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = AppDrawerGridPreferences.isHorizontal(gridOrientation)
    val continuousScroll = AppDrawerGridPreferences.isContinuous(gridScrollMode)
    val pagesScroll = AppDrawerGridPreferences.isPages(gridScrollMode)
    LaunchedEffect(Unit) { onLoad() }

    val installedPackages =
        remember(apps) { apps.map { it.packageName } }
    // Favorites first (saved order), then the rest A–Z from the loader.
    val apps =
        remember(apps, favoritePackages) {
            AppDrawerGridPreferences.orderWithFavorites(
                apps = apps,
                favoritePackages = favoritePackages,
                packageName = { it.packageName },
            )
        }
    val favoritePackageSet = remember(favoritePackages) { favoritePackages.toHashSet() }

    val dualStore = koinInject<DualScreenStore>()
    val rememberedPackage = rememberedFocusTarget("app_drawer") as? String
    var selectedPackage by remember {
        mutableStateOf(rememberedPackage?.takeIf { pkg -> apps.any { it.packageName == pkg } })
    }
    var optionsOpen by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<AppContextTarget?>(null) }
    var contextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    // Latest layout maxes for the compact options steppers.
    var layoutMaxColumns by remember {
        mutableStateOf(AppDrawerGridPreferences.ABS_MAX_COLUMNS)
    }
    var layoutMaxRows by remember { mutableStateOf(AppDrawerGridPreferences.ABS_MAX_ROWS) }

    val tileBoundsByPackage = remember { mutableStateMapOf<String, Rect>() }
    val tileFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var restoringGridFocus by remember { mutableStateOf(false) }
    var restoreFocusPackage by remember { mutableStateOf<String?>(null) }

    /** App tile focused when options opened; restored when the panel closes. */
    var optionsAnchorPackage by remember { mutableStateOf<String?>(null) }
    val menuOpen = contextMenuTarget != null
    val menuPackage = contextMenuTarget?.packageName
    val menuTileBounds = menuPackage?.let { tileBoundsByPackage[it] }
    // Favorites reorder moves the tile — keep the menu cutout on that package.
    LaunchedEffect(menuPackage, menuTileBounds) {
        if (menuPackage != null && menuTileBounds != null) {
            contextMenuAnchorBounds = menuTileBounds
        }
    }

    fun closeOptions() {
        if (!optionsOpen) return
        val anchor = optionsAnchorPackage ?: selectedPackage
        optionsOpen = false
        optionsAnchorPackage = null
        if (anchor != null) {
            selectedPackage = anchor
            restoreFocusPackage = anchor
        }
        restoringGridFocus = true
    }

    fun openOptions() {
        optionsAnchorPackage = selectedPackage
        optionsOpen = true
    }

    LaunchedEffect(Unit) {
        // Start is handled in GamepadKeyRouter (before Compose), so toggle here.
        dualStore.openAppsOptionsRequests.collect {
            if (optionsOpen) closeOptions() else openOptions()
        }
    }
    val bottomDisplayId = secondaryDisplayId ?: 4
    val initialSelectedAppIndex =
        apps.indexOfFirst { it.packageName == selectedPackage }.coerceAtLeast(0)
    // Paint the remembered app in the first frame after a role swap instead of
    // briefly showing the start of the grid and correcting on owner gain.
    val gridState =
        rememberLazyGridState(
            initialFirstVisibleItemIndex = initialSelectedAppIndex,
        )
    // Effective density for D-pad / paging; updated when the grid measures.
    var navColumns by remember {
        mutableStateOf(AppDrawerGridPreferences.normalizeColumns(gridColumns))
    }
    var navRows by remember {
        mutableStateOf(AppDrawerGridPreferences.normalizeRows(gridRows))
    }
    val pageSize = (navColumns.coerceAtLeast(1) * navRows.coerceAtLeast(1)).coerceAtLeast(1)
    val pageCount =
        remember(apps.size, pageSize) {
            if (apps.isEmpty()) 1 else ((apps.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        }
    val pagerState =
        rememberPagerState(
            initialPage = (initialSelectedAppIndex / pageSize).coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            pageCount = { pageCount },
        )
    val inputModeController = LocalInputModeController.current
    var pendingViewportSnap by remember { mutableStateOf(false) }
    // Snap landed on an item already fully on screen — don't edge-pad scroll it.
    var viewportSnapKeepsScroll by remember { mutableStateOf(false) }
    // Finger-down on the grid. D-pad switches input mode before the key handler
    // runs, so mode cannot be used to tell a touch scroll from a focus scroll.
    val touchPointerDown = remember { AtomicBoolean(false) }
    // Ignore scroll-offset churn from D-pad bring-into-view so we don't re-arm
    // touch viewport-snap and yank selection back to the top row.
    var selectionScrollActive by remember { mutableStateOf(false) }

    LaunchedEffect(apps, rememberedPackage) {
        if (selectedPackage == null && rememberedPackage != null && apps.any { it.packageName == rememberedPackage }) {
            selectedPackage = rememberedPackage
        }
    }

    LaunchedEffect(apps) {
        val current = selectedPackage ?: return@LaunchedEffect
        if (apps.none { it.packageName == current }) {
            selectedPackage = null
        }
    }

    LaunchedEffect(apps.size, selectedPackage) {
        val focused = apps.firstOrNull { it.packageName == selectedPackage }
        onFocusChange(focused)
    }

    val appScrollEdgePadPx =
        with(LocalDensity.current) { (WajihaSpacing.md + WajihaSpacing.sm).roundToPx() }

    // Selection owns continuous-grid scroll. Keyed on the selected package only
    // so a live install/removal reload does not yank the viewport.
    LaunchedEffect(selectedPackage, menuOpen, optionsOpen, horizontalScroll, continuousScroll) {
        if (!continuousScroll || menuOpen || optionsOpen) return@LaunchedEffect
        val pkg = selectedPackage ?: return@LaunchedEffect
        if (viewportSnapKeepsScroll) {
            viewportSnapKeepsScroll = false
            withFrameNanos { }
            try {
                tileFocusRequesters[pkg]?.requestFocus()
            } catch (_: Exception) {
            }
            return@LaunchedEffect
        }
        val lazyIndex = apps.indexOfFirst { it.packageName == pkg }
        if (lazyIndex < 0) return@LaunchedEffect
        selectionScrollActive = true
        try {
            gridState.scroll { }
            withFrameNanos { }
            gridState.smoothBringItemIntoView(
                key = pkg,
                lazyIndex = lazyIndex,
                horizontalScroll = horizontalScroll,
                edgePaddingPx = appScrollEdgePadPx,
            )
            withFrameNanos { }
            try {
                tileFocusRequesters[pkg]?.requestFocus()
            } catch (_: Exception) {
            }
        } finally {
            selectionScrollActive = false
        }
    }

    // Selection owns pager page. A finger swipe does not change selection, so
    // this does not pull the pager back until the user taps an app or uses D-pad.
    LaunchedEffect(selectedPackage, menuOpen, optionsOpen, pagesScroll, pageSize) {
        if (!pagesScroll || menuOpen || optionsOpen) return@LaunchedEffect
        val pkg = selectedPackage ?: return@LaunchedEffect
        val lazyIndex = apps.indexOfFirst { it.packageName == pkg }
        if (lazyIndex < 0) return@LaunchedEffect
        val page = lazyIndex / pageSize
        selectionScrollActive = true
        try {
            if (pagerState.currentPage != page) {
                pagerState.scrollToPage(page)
            }
            withFrameNanos { }
            try {
                tileFocusRequesters[pkg]?.requestFocus()
            } catch (_: Exception) {
            }
        } finally {
            selectionScrollActive = false
        }
    }

    fun selectPackage(pkg: String) {
        // A tap owns the origin — the next D-pad must not snap to another tile.
        pendingViewportSnap = false
        val app = apps.firstOrNull { it.packageName == pkg } ?: return
        selectedPackage = pkg
        onFocusChange(app)
    }

    fun moveAppGridFocus(
        deltaRow: Int,
        deltaCol: Int,
    ): Boolean {
        if (apps.isEmpty()) return false
        pendingViewportSnap = false
        inputModeController?.onGamepadKey()
        val currentIndex =
            selectedPackage
                ?.let { pkg -> apps.indexOfFirst { it.packageName == pkg } }
                ?.takeIf { it >= 0 }
                ?: 0
        val nextIndex =
            when {
                pagesScroll -> {
                    val columns = navColumns.coerceAtLeast(1)
                    val rows = navRows.coerceAtLeast(1)
                    val size = pageSize.coerceAtLeast(1)
                    val page = currentIndex / size
                    val local = currentIndex % size
                    val localRow = local / columns
                    val localCol = local % columns
                    var nextPage = page
                    var nextLocalRow = localRow + deltaRow
                    var nextLocalCol = localCol + deltaCol
                    if (horizontalScroll) {
                        // Horiz pages: Left/Right flip pages at the matrix edge.
                        when {
                            nextLocalCol >= columns -> {
                                nextPage += 1
                                nextLocalCol = 0
                            }

                            nextLocalCol < 0 -> {
                                nextPage -= 1
                                nextLocalCol = columns - 1
                            }
                        }
                        if (nextLocalRow !in 0 until rows) return true
                    } else {
                        // Vert pages: Up/Down flip pages at the matrix edge.
                        when {
                            nextLocalRow >= rows -> {
                                nextPage += 1
                                nextLocalRow = 0
                            }

                            nextLocalRow < 0 -> {
                                nextPage -= 1
                                nextLocalRow = rows - 1
                            }
                        }
                        if (nextLocalCol !in 0 until columns) return true
                    }
                    if (nextPage < 0) return true
                    nextPage * size + nextLocalRow * columns + nextLocalCol
                }

                horizontalScroll -> {
                    // Continuous horizontal: column-major LazyHorizontalGrid order.
                    val rows = navRows.coerceAtLeast(1)
                    val col = currentIndex / rows
                    val row = currentIndex % rows
                    val nextRow = row + deltaRow
                    if (nextRow !in 0 until rows) return true
                    val nextCol = col + deltaCol
                    if (nextCol < 0) return true
                    nextCol * rows + nextRow
                }

                else -> {
                    val columns = navColumns.coerceAtLeast(1)
                    val row = currentIndex / columns
                    val col = currentIndex % columns
                    val nextCol = col + deltaCol
                    if (nextCol !in 0 until columns) return true
                    val nextRow = row + deltaRow
                    if (nextRow < 0) return true
                    nextRow * columns + nextCol
                }
            }
        if (nextIndex !in apps.indices) return true
        val next = apps[nextIndex]
        selectedPackage = next.packageName
        onFocusChange(next)
        return true
    }

    LaunchedEffect(gridState, continuousScroll) {
        if (!continuousScroll) return@LaunchedEffect
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.drop(1)
            .collect {
                if (touchPointerDown.get() && !selectionScrollActive) {
                    pendingViewportSnap = true
                }
            }
    }

    LaunchedEffect(pagerState, pagesScroll) {
        if (!pagesScroll) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage to pagerState.currentPageOffsetFraction }
            .drop(1)
            .collect {
                if (touchPointerDown.get() && !selectionScrollActive) {
                    pendingViewportSnap = true
                }
            }
    }

    /** Sees the finger even when the grid consumes the drag. */
    fun Modifier.armTouchScrollSnap(): Modifier =
        pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startIndex = gridState.firstVisibleItemIndex
                val startOffset = gridState.firstVisibleItemScrollOffset
                val startPage = pagerState.currentPage
                touchPointerDown.set(true)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) break
                    }
                } finally {
                    touchPointerDown.set(false)
                    // A fling can update the offset after the finger is up, so arm
                    // here too — the scroll collector often misses that window.
                    val moved =
                        if (pagesScroll) {
                            pagerState.currentPage != startPage || pagerState.isScrollInProgress
                        } else {
                            gridState.firstVisibleItemIndex != startIndex ||
                                gridState.firstVisibleItemScrollOffset != startOffset ||
                                gridState.isScrollInProgress
                        }
                    if (moved && !selectionScrollActive) {
                        pendingViewportSnap = true
                    }
                }
            }
        }

    fun selectedAppOnScreen(): Boolean {
        val pkg = selectedPackage ?: return false
        if (pagesScroll) {
            val index = apps.indexOfFirst { it.packageName == pkg }
            if (index < 0) return false
            return index / pageSize == pagerState.settledPage
        }
        return gridState.layoutInfo.visibleItemsInfo.any { info ->
            info.key == pkg &&
                info.hasCenterInViewport(gridState.layoutInfo, horizontalScroll = horizontalScroll)
        }
    }

    fun snapFocusToLeadingVisible(): Boolean {
        if (pagesScroll) {
            val start = pagerState.settledPage * pageSize
            if (start !in apps.indices) return false
            val app = apps[start]
            selectedPackage = app.packageName
            onFocusChange(app)
            try {
                tileFocusRequesters[app.packageName]?.requestFocus()
            } catch (_: Exception) {
            }
            return true
        }
        val layoutInfo = gridState.layoutInfo
        val firstVisible =
            layoutInfo.visibleItemsInfo
                .filter { it.hasCenterInViewport(layoutInfo, horizontalScroll = horizontalScroll) }
                .minWithOrNull(
                    if (horizontalScroll) {
                        compareBy({ it.column }, { it.row })
                    } else {
                        compareBy({ it.row }, { it.column })
                    },
                )
                ?: return false
        val pkg = firstVisible.key as? String ?: return false
        val app = apps.firstOrNull { it.packageName == pkg } ?: return false
        // The tile is already on screen. Edge-pad scrolling would pull the
        // previous row in and the highlight would no longer be the first item.
        viewportSnapKeepsScroll = true
        selectedPackage = pkg
        onFocusChange(app)
        try {
            tileFocusRequesters[pkg]?.requestFocus()
        } catch (_: Exception) {
        }
        return true
    }

    fun openContextMenu(packageName: String) {
        val app = apps.firstOrNull { it.packageName == packageName } ?: return
        WajihaLog.i(WajihaTags.LAUNCH, "appContextMenu: open pkg=$packageName label=${app.label}")
        selectedPackage = packageName
        onFocusChange(app)
        contextMenuAnchorBounds = tileBoundsByPackage[packageName]
        restoringGridFocus = false
        restoreFocusPackage = null
        contextMenuTarget = AppContextTarget(packageName, app.label)
    }

    fun dismissContextMenu() {
        val menuPackage = contextMenuTarget?.packageName
        contextMenuTarget = null
        contextMenuAnchorBounds = null
        if (menuPackage != null) {
            selectedPackage = menuPackage
            onFocusChange(apps.firstOrNull { it.packageName == menuPackage })
            restoreFocusPackage = menuPackage
        }
        restoringGridFocus = true
    }

    val gamepadHints =
        remember(menuOpen, optionsOpen, dualDisplay) {
            buildList {
                when {
                    menuOpen -> {
                        add(GamepadHint(GamepadHintButton.B, "Back"))
                    }

                    optionsOpen -> {
                        add(GamepadHint(GamepadHintButton.A, "Adjust"))
                        add(GamepadHint(GamepadHintButton.B, "Close"))
                        // Start stays on the Options corner glyph — omit from the bar.
                    }

                    else -> {
                        add(GamepadHint(GamepadHintButton.A, "Open app"))
                        if (dualDisplay) {
                            add(GamepadHint(GamepadHintButton.Y, "Bottom screen"))
                        }
                        add(GamepadHint(GamepadHintButton.X, "Menu"))
                        add(GamepadHint(GamepadHintButton.B, "Back"))
                        if (dualDisplay) {
                            add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
                        }
                        // Start is on the Options button glyph — omit from the bar.
                    }
                }
            }
        }

    WajihaScreen(
        layerId = "app_drawer",
        modifier = modifier,
        showActionBar = true,
        gamepadHints = gamepadHints,
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = {
            if (!menuOpen && !optionsOpen) {
                val pkg = selectedPackage ?: apps.firstOrNull()?.packageName
                if (pkg != null) {
                    val appIndex = apps.indexOfFirst { it.packageName == pkg }
                    if (pagesScroll) {
                        if (appIndex >= 0) {
                            val page = appIndex / pageSize
                            if (pagerState.currentPage != page) {
                                pagerState.scrollToPage(page)
                                withFrameNanos { }
                            }
                        }
                    } else {
                        val appIsVisible =
                            gridState.layoutInfo.visibleItemsInfo.any { info ->
                                info.key == pkg &&
                                    info.hasCenterInViewport(
                                        gridState.layoutInfo,
                                        horizontalScroll = horizontalScroll,
                                    )
                            }
                        if (appIndex >= 0 && !appIsVisible) {
                            // Fallback for list mutations or an already-mounted grid.
                            gridState.scrollToItem(appIndex)
                            withFrameNanos { }
                        }
                    }
                    tileFocusRequesters[pkg]?.requestContentFocus()
                }
            }
        },
        onPreviewKey = { event ->
            if (menuOpen) return@WajihaScreen false
            if (optionsOpen) {
                // Overlay owns focus; only dismiss keys are handled at screen level.
                return@WajihaScreen when {
                    GamepadKeys.isBack(event.type, event.key) ||
                        GamepadKeys.isStart(event.type, event.key) -> {
                        closeOptions()
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
            val pkg = selectedPackage

            fun consumeDirection(
                deltaRow: Int,
                deltaCol: Int,
            ): Boolean {
                // After a finger scroll that left the highlight off screen, the
                // first D-pad lands on the first visible app. A later press steps
                // from there. If the highlight is still on screen, step normally.
                if (pendingViewportSnap) {
                    pendingViewportSnap = false
                    if (!selectedAppOnScreen() && snapFocusToLeadingVisible()) {
                        return true
                    }
                }
                return moveAppGridFocus(deltaRow = deltaRow, deltaCol = deltaCol)
            }

            // Android consumes the first directional DOWN after touch to leave
            // touch mode. The matching UP still arrives — use it if that DOWN
            // never ran, so one press focuses the first app still on screen.
            fun recoverEatenDirection(
                deltaRow: Int,
                deltaCol: Int,
            ): Boolean {
                if (!pendingViewportSnap) return false
                return consumeDirection(deltaRow = deltaRow, deltaCol = deltaCol)
            }
            when {
                GamepadKeys.isUp(event.type, event.key) -> {
                    consumeDirection(deltaRow = -1, deltaCol = 0)
                }

                GamepadKeys.isDown(event.type, event.key) -> {
                    consumeDirection(deltaRow = 1, deltaCol = 0)
                }

                GamepadKeys.isLeft(event.type, event.key) -> {
                    consumeDirection(deltaRow = 0, deltaCol = -1)
                }

                GamepadKeys.isRight(event.type, event.key) -> {
                    consumeDirection(deltaRow = 0, deltaCol = 1)
                }

                event.type == KeyEventType.KeyUp && event.key == Key.DirectionUp -> {
                    recoverEatenDirection(deltaRow = -1, deltaCol = 0)
                }

                event.type == KeyEventType.KeyUp && event.key == Key.DirectionDown -> {
                    recoverEatenDirection(deltaRow = 1, deltaCol = 0)
                }

                event.type == KeyEventType.KeyUp && event.key == Key.DirectionLeft -> {
                    recoverEatenDirection(deltaRow = 0, deltaCol = -1)
                }

                event.type == KeyEventType.KeyUp && event.key == Key.DirectionRight -> {
                    recoverEatenDirection(deltaRow = 0, deltaCol = 1)
                }

                GamepadKeys.isX(event.type, event.key) && pkg != null -> {
                    openContextMenu(pkg)
                    true
                }

                GamepadKeys.isY(event.type, event.key) && dualDisplay && pkg != null -> {
                    WajihaLog.i(
                        WajihaTags.LAUNCH,
                        "appDrawer: Y launches pkg=$pkg on displayId=$bottomDisplayId",
                    )
                    onLaunchOnDisplay(pkg, bottomDisplayId)
                    true
                }

                pkg != null &&
                    GamepadKeys.isConfirm(event.type, event.key) -> {
                    WajihaLog.i(WajihaTags.LAUNCH, "appDrawer: A launches pkg=$pkg")
                    onLaunch(pkg)
                    true
                }

                else -> {
                    false
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalGamepadFocusChromeScope provides GamepadFocusChromeScope.GameGrid,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = WajihaFolderChromeMetrics.horizontalPadding,
                                    vertical = WajihaFolderChromeMetrics.topPadding,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GamepadButton(
                            text = "Back",
                            onClick = {
                                if (optionsOpen) closeOptions() else onBack()
                            },
                            outlined = true,
                            // Chrome is touch-only; D-pad stays on the app grid.
                            gamepadFocusable = false,
                            sound = null,
                        )
                        Text(
                            text = "Apps",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = WajihaSpacing.sm),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        GamepadHintCornerButton(
                            text = "Options",
                            onClick = {
                                if (optionsOpen) closeOptions() else openOptions()
                            },
                            hint = GamepadHintButton.Start,
                            // Start / touch open options; never steal grid focus.
                            gamepadFocusable = false,
                            focusId = "apps_options",
                        )
                    }
                    LaunchedEffect(restoringGridFocus, restoreFocusPackage) {
                        if (restoringGridFocus) {
                            val pkg = restoreFocusPackage ?: selectedPackage
                            if (pkg != null) {
                                withFrameNanos { }
                                try {
                                    tileFocusRequesters[pkg]?.requestFocus()
                                } catch (_: Exception) {
                                }
                            }
                            restoringGridFocus = false
                            restoreFocusPackage = null
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val layout =
                            remember(
                                maxWidth,
                                maxHeight,
                                gridColumns,
                                gridRows,
                                iconSizePreference,
                                showAppLabels,
                            ) {
                                AppDrawerGridPreferences.resolve(
                                    availableWidth = maxWidth,
                                    availableHeight = maxHeight,
                                    preferredColumns = gridColumns,
                                    preferredRows = gridRows,
                                    preferredIconSize = iconSizePreference,
                                    showLabels = showAppLabels,
                                )
                            }
                        LaunchedEffect(layout.columns, layout.rows, layout.maxColumns, layout.maxRows) {
                            navColumns = layout.columns
                            navRows = layout.rows
                            layoutMaxColumns = layout.maxColumns
                            layoutMaxRows = layout.maxRows
                        }
                        val showStartFade by remember(continuousScroll, gridState) {
                            derivedStateOf { continuousScroll && gridState.canScrollBackward }
                        }
                        val showEndFade by remember(continuousScroll, gridState) {
                            derivedStateOf { continuousScroll && gridState.canScrollForward }
                        }
                        val gridContentPadding =
                            PaddingValues(
                                start = layout.contentPadHorizontal,
                                end =
                                    if (horizontalScroll && continuousScroll) {
                                        layout.contentPadHorizontal + WajihaSpacing.md
                                    } else {
                                        layout.contentPadHorizontal
                                    },
                                top = layout.contentPadVertical,
                                // Extra bottom so the last row clears the action bar.
                                bottom = layout.contentPadVertical + WajihaSpacing.md,
                            )

                        if (pagesScroll) {
                            val pageContent: @Composable (page: Int) -> Unit = { page ->
                                val start = page * pageSize
                                val end = min(start + pageSize, apps.size)
                                val pageApps =
                                    if (start in apps.indices) {
                                        apps.subList(start, end)
                                    } else {
                                        emptyList()
                                    }
                                AppDrawerPagedGrid(
                                    pageApps = pageApps,
                                    columns = layout.columns,
                                    rows = layout.rows,
                                    selectedPackage = selectedPackage,
                                    menuOpen = menuOpen,
                                    optionsOpen = optionsOpen,
                                    contextMenuPackage = contextMenuTarget?.packageName,
                                    restoringGridFocus = restoringGridFocus,
                                    restoreFocusPackage = restoreFocusPackage,
                                    tileFocusRequesters = tileFocusRequesters,
                                    iconShape = iconShape,
                                    layout = layout,
                                    favoritePackages = favoritePackageSet,
                                    contentPadding = gridContentPadding,
                                    gamepadOwner = gamepadOwner,
                                    onClaimGamepad = onClaimGamepad,
                                    onSelectPackage = ::selectPackage,
                                    onLaunch = onLaunch,
                                    onOpenContextMenu = ::openContextMenu,
                                    onTileFocusChanged = { _, _ -> },
                                    onTileBounds = { pkg, bounds ->
                                        tileBoundsByPackage[pkg] = bounds
                                    },
                                )
                            }
                            if (horizontalScroll) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize().armTouchScrollSnap(),
                                    beyondViewportPageCount = 1,
                                ) { page ->
                                    pageContent(page)
                                }
                            } else {
                                VerticalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize().armTouchScrollSnap(),
                                    beyondViewportPageCount = 1,
                                ) { page ->
                                    pageContent(page)
                                }
                            }
                        } else {
                            ScrollEdgeFadeBox(
                                showStart = showStartFade,
                                showEnd = showEndFade,
                                horizontal = horizontalScroll,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (horizontalScroll) {
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(layout.rows),
                                        state = gridState,
                                        modifier = Modifier.fillMaxSize().armTouchScrollSnap(),
                                        contentPadding = gridContentPadding,
                                        horizontalArrangement = Arrangement.spacedBy(layout.hSpacing),
                                        verticalArrangement = Arrangement.spacedBy(layout.vSpacing),
                                    ) {
                                        appDrawerGridItems(
                                            apps = apps,
                                            selectedPackage = selectedPackage,
                                            menuOpen = menuOpen,
                                            optionsOpen = optionsOpen,
                                            contextMenuPackage = contextMenuTarget?.packageName,
                                            restoringGridFocus = restoringGridFocus,
                                            restoreFocusPackage = restoreFocusPackage,
                                            tileFocusRequesters = tileFocusRequesters,
                                            iconShape = iconShape,
                                            layout = layout,
                                            favoritePackages = favoritePackageSet,
                                            // Horizontal scroll axis sizes to content unless fixed —
                                            // long labels were stretching columns unevenly.
                                            fixedItemWidth = layout.cellWidth,
                                            gamepadOwner = gamepadOwner,
                                            onClaimGamepad = onClaimGamepad,
                                            onSelectPackage = ::selectPackage,
                                            onLaunch = onLaunch,
                                            onOpenContextMenu = ::openContextMenu,
                                            onTileFocusChanged = { _, _ -> },
                                            onTileBounds = { pkg, bounds ->
                                                tileBoundsByPackage[pkg] = bounds
                                            },
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(layout.columns),
                                        state = gridState,
                                        modifier = Modifier.fillMaxSize().armTouchScrollSnap(),
                                        contentPadding = gridContentPadding,
                                        horizontalArrangement = Arrangement.spacedBy(layout.hSpacing),
                                        verticalArrangement = Arrangement.spacedBy(layout.vSpacing),
                                    ) {
                                        appDrawerGridItems(
                                            apps = apps,
                                            selectedPackage = selectedPackage,
                                            menuOpen = menuOpen,
                                            optionsOpen = optionsOpen,
                                            contextMenuPackage = contextMenuTarget?.packageName,
                                            restoringGridFocus = restoringGridFocus,
                                            restoreFocusPackage = restoreFocusPackage,
                                            tileFocusRequesters = tileFocusRequesters,
                                            iconShape = iconShape,
                                            layout = layout,
                                            favoritePackages = favoritePackageSet,
                                            gamepadOwner = gamepadOwner,
                                            onClaimGamepad = onClaimGamepad,
                                            onSelectPackage = ::selectPackage,
                                            onLaunch = onLaunch,
                                            onOpenContextMenu = ::openContextMenu,
                                            onTileFocusChanged = { _, _ -> },
                                            onTileBounds = { pkg, bounds ->
                                                tileBoundsByPackage[pkg] = bounds
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (optionsOpen) {
                AppDrawerOptionsPanel(
                    columns = gridColumns.coerceAtMost(layoutMaxColumns),
                    rows = gridRows.coerceAtMost(layoutMaxRows),
                    iconSize = iconSizePreference,
                    orientation = gridOrientation,
                    scrollMode = gridScrollMode,
                    showLabels = showAppLabels,
                    maxColumns = layoutMaxColumns,
                    maxRows = layoutMaxRows,
                    onColumnsChange = onGridColumnsChange,
                    onRowsChange = onGridRowsChange,
                    onIconSizeChange = onIconSizeChange,
                    onOrientationChange = onGridOrientationChange,
                    onScrollModeChange = onGridScrollModeChange,
                    onShowLabelsChange = onShowAppLabelsChange,
                    onDismiss = ::closeOptions,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 52.dp, end = WajihaSpacing.sm)
                            .zIndex(3f),
                )
            }

            val menuPackage = contextMenuTarget?.packageName
            val menuIsFavorite =
                menuPackage != null && menuPackage in favoritePackageSet
            AppContextMenu(
                target = contextMenuTarget,
                anchorBounds = contextMenuAnchorBounds,
                onDismiss = ::dismissContextMenu,
                onOpenAppInfo = onOpenAppInfo,
                onLaunchOnDisplay = onLaunchOnDisplay,
                dualDisplay = dualDisplay,
                topDisplayId = 0,
                bottomDisplayId = bottomDisplayId,
                isFavorite = menuIsFavorite,
                canMoveFavoriteUp =
                    menuPackage != null &&
                        AppDrawerGridPreferences.canMoveFavoriteUp(
                            packageName = menuPackage,
                            favoritePackages = favoritePackages,
                            installedPackages = installedPackages,
                        ),
                canMoveFavoriteDown =
                    menuPackage != null &&
                        AppDrawerGridPreferences.canMoveFavoriteDown(
                            packageName = menuPackage,
                            favoritePackages = favoritePackages,
                            installedPackages = installedPackages,
                        ),
                onToggleFavorite = { pkg ->
                    if (pkg in favoritePackageSet) {
                        onRemoveFavorite(pkg)
                    } else {
                        onAddFavorite(pkg)
                    }
                },
                onMoveFavoriteUp = { pkg -> onMoveFavorite(pkg, -1) },
                onMoveFavoriteDown = { pkg -> onMoveFavorite(pkg, 1) },
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}

@Composable
private fun AppDrawerPagedGrid(
    pageApps: List<LaunchableApp>,
    columns: Int,
    rows: Int,
    selectedPackage: String?,
    menuOpen: Boolean,
    optionsOpen: Boolean,
    contextMenuPackage: String?,
    restoringGridFocus: Boolean,
    restoreFocusPackage: String?,
    tileFocusRequesters: MutableMap<String, FocusRequester>,
    iconShape: String,
    layout: AppDrawerGridPreferences.Layout,
    favoritePackages: Set<String>,
    contentPadding: PaddingValues,
    gamepadOwner: GamepadOwner?,
    onClaimGamepad: ((GamepadOwner) -> Unit)?,
    onSelectPackage: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onOpenContextMenu: (String) -> Unit,
    onTileFocusChanged: (packageName: String, focused: Boolean) -> Unit,
    onTileBounds: (packageName: String, bounds: Rect) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    val rowCount = rows.coerceAtLeast(1)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(layout.vSpacing),
    ) {
        repeat(rowCount) { row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(layout.hSpacing),
            ) {
                repeat(cols) { col ->
                    val index = row * cols + col
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index < pageApps.size) {
                            val app = pageApps[index]
                            val isSelected = app.packageName == selectedPackage
                            val isMenuTile = menuOpen && contextMenuPackage == app.packageName
                            val isRestoreTile =
                                restoringGridFocus &&
                                    (restoreFocusPackage ?: selectedPackage) == app.packageName
                            val tileFocus =
                                remember(app.packageName) {
                                    tileFocusRequesters.getOrPut(app.packageName) { FocusRequester() }
                                }
                            AppTile(
                                app = app,
                                selected = isSelected && !optionsOpen,
                                iconShape = iconShape,
                                iconSize = layout.iconSize,
                                tilePadHorizontal = layout.tilePadHorizontal,
                                tilePadVertical = layout.tilePadVertical,
                                showLabels = layout.showLabels,
                                isFavorite = app.packageName in favoritePackages,
                                onSelect = {
                                    if (gamepadOwner != null) {
                                        onClaimGamepad?.invoke(gamepadOwner)
                                    }
                                    onSelectPackage(app.packageName)
                                },
                                onLaunch = { onLaunch(app.packageName) },
                                onLongPress = {
                                    if (gamepadOwner != null) {
                                        onClaimGamepad?.invoke(gamepadOwner)
                                    }
                                    onSelectPackage(app.packageName)
                                    onOpenContextMenu(app.packageName)
                                },
                                onTileFocusChanged = { focused ->
                                    onTileFocusChanged(app.packageName, focused)
                                },
                                focusRequester = tileFocus,
                                gamepadFocusable =
                                    when {
                                        menuOpen || optionsOpen -> false

                                        restoringGridFocus -> isRestoreTile

                                        // Only the selected tile is focusable. Touch-scroll
                                        // otherwise parks Compose focus on a newly visible app.
                                        else -> isSelected
                                    },
                                navHighlighted = isSelected && !optionsOpen,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .then(if (isMenuTile) Modifier.zIndex(1f) else Modifier)
                                        .onGloballyPositioned { coords ->
                                            onTileBounds(app.packageName, coords.boundsInRoot())
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.appDrawerGridItems(
    apps: List<LaunchableApp>,
    selectedPackage: String?,
    menuOpen: Boolean,
    optionsOpen: Boolean,
    contextMenuPackage: String?,
    restoringGridFocus: Boolean,
    restoreFocusPackage: String?,
    tileFocusRequesters: MutableMap<String, FocusRequester>,
    iconShape: String,
    layout: AppDrawerGridPreferences.Layout,
    favoritePackages: Set<String>,
    gamepadOwner: GamepadOwner?,
    onClaimGamepad: ((GamepadOwner) -> Unit)?,
    onSelectPackage: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onOpenContextMenu: (String) -> Unit,
    onTileFocusChanged: (packageName: String, focused: Boolean) -> Unit,
    onTileBounds: (packageName: String, bounds: Rect) -> Unit,
    fixedItemWidth: Dp? = null,
) {
    items(apps, key = { it.packageName }) { app ->
        val isSelected = app.packageName == selectedPackage
        val isMenuTile = menuOpen && contextMenuPackage == app.packageName
        val isRestoreTile =
            restoringGridFocus &&
                (restoreFocusPackage ?: selectedPackage) == app.packageName
        val tileFocus =
            remember(app.packageName) {
                tileFocusRequesters.getOrPut(app.packageName) { FocusRequester() }
            }
        AppTile(
            app = app,
            selected = isSelected && !optionsOpen,
            iconShape = iconShape,
            iconSize = layout.iconSize,
            tilePadHorizontal = layout.tilePadHorizontal,
            tilePadVertical = layout.tilePadVertical,
            showLabels = layout.showLabels,
            isFavorite = app.packageName in favoritePackages,
            onSelect = {
                if (gamepadOwner != null) {
                    onClaimGamepad?.invoke(gamepadOwner)
                }
                onSelectPackage(app.packageName)
            },
            onLaunch = { onLaunch(app.packageName) },
            onLongPress = {
                if (gamepadOwner != null) {
                    onClaimGamepad?.invoke(gamepadOwner)
                }
                onSelectPackage(app.packageName)
                onOpenContextMenu(app.packageName)
            },
            onTileFocusChanged = { focused ->
                onTileFocusChanged(app.packageName, focused)
            },
            focusRequester = tileFocus,
            gamepadFocusable =
                when {
                    menuOpen || optionsOpen -> false

                    restoringGridFocus -> isRestoreTile

                    // Only the selected tile is focusable. Touch-scroll
                    // otherwise parks Compose focus on a newly visible app.
                    else -> isSelected
                },
            navHighlighted = isSelected && !optionsOpen,
            modifier =
                Modifier
                    .then(
                        if (fixedItemWidth != null) {
                            Modifier.width(fixedItemWidth)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    ).then(if (isMenuTile) Modifier.zIndex(1f) else Modifier)
                    .onGloballyPositioned { coords ->
                        onTileBounds(app.packageName, coords.boundsInRoot())
                    },
        )
    }
}

@Composable
private fun AppDrawerOptionsPanel(
    columns: Int,
    rows: Int,
    iconSize: String,
    orientation: String,
    scrollMode: String,
    showLabels: Boolean,
    maxColumns: Int,
    maxRows: Int,
    onColumnsChange: (Int) -> Unit,
    onRowsChange: (Int) -> Unit,
    onIconSizeChange: (String) -> Unit,
    onOrientationChange: (String) -> Unit,
    onScrollModeChange: (String) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFocus = remember { FocusRequester() }
    val selectedSizeId = AppDrawerGridPreferences.normalizeIconSize(iconSize)
    val selectedOrientation = AppDrawerGridPreferences.normalizeOrientation(orientation)
    val selectedScrollMode = AppDrawerGridPreferences.normalizeScrollMode(scrollMode)

    GamepadOverlayLayer(
        layerId = "app_drawer_options",
        onDismiss = onDismiss,
        onToggleKey = { event ->
            if (GamepadKeys.isStart(event.type, event.key)) {
                onDismiss()
                true
            } else {
                false
            }
        },
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            try {
                initialFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
        Surface(
            shape = WajihaShapes.tile,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .width(IntrinsicSize.Max)
                        .padding(WajihaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            ) {
                OptionsLabeledRow(label = "Size") {
                    AppDrawerGridPreferences.iconSizes.forEach { id ->
                        GamepadChip(
                            label = AppDrawerGridPreferences.iconSizeLabel(id),
                            selected = selectedSizeId == id,
                            onClick = { onIconSizeChange(id) },
                            focusRequester = if (id == selectedSizeId) initialFocus else null,
                            focusId = "apps_opt_size_$id",
                        )
                    }
                }
                OptionsLabeledRow(label = "Grid") {
                    AppDrawerGridPreferences.orientations.forEach { id ->
                        GamepadChip(
                            label = AppDrawerGridPreferences.orientationLabel(id),
                            selected = selectedOrientation == id,
                            onClick = { onOrientationChange(id) },
                            focusId = "apps_opt_orient_$id",
                        )
                    }
                }
                OptionsLabeledRow(label = "Browse") {
                    AppDrawerGridPreferences.scrollModes.forEach { id ->
                        GamepadChip(
                            label = AppDrawerGridPreferences.scrollModeLabel(id),
                            selected = selectedScrollMode == id,
                            onClick = { onScrollModeChange(id) },
                            focusId = "apps_opt_scroll_$id",
                        )
                    }
                }
                OptionsLabeledRow(label = "Name") {
                    GamepadChip(
                        label = "Show",
                        selected = showLabels,
                        onClick = { onShowLabelsChange(true) },
                        focusId = "apps_opt_name_show",
                    )
                    GamepadChip(
                        label = "Hide",
                        selected = !showLabels,
                        onClick = { onShowLabelsChange(false) },
                        focusId = "apps_opt_name_hide",
                    )
                }
                CompactStepperRow(
                    label = "Cols",
                    valueLabel = "$columns",
                    canDecrement = columns > AppDrawerGridPreferences.ABS_MIN_COLUMNS,
                    canIncrement = columns < maxColumns,
                    onDecrement = {
                        onColumnsChange((columns - 1).coerceAtLeast(AppDrawerGridPreferences.ABS_MIN_COLUMNS))
                    },
                    onIncrement = {
                        onColumnsChange((columns + 1).coerceAtMost(maxColumns))
                    },
                )
                CompactStepperRow(
                    label = "Rows",
                    valueLabel = "$rows",
                    canDecrement = rows > AppDrawerGridPreferences.ABS_MIN_ROWS,
                    canIncrement = rows < maxRows,
                    onDecrement = {
                        onRowsChange((rows - 1).coerceAtLeast(AppDrawerGridPreferences.ABS_MIN_ROWS))
                    },
                    onIncrement = {
                        onRowsChange((rows + 1).coerceAtMost(maxRows))
                    },
                )
            }
        }
    }
}

private val OptionsLabelWidth = 56.dp

@Composable
private fun OptionsLabeledRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(OptionsLabelWidth),
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun CompactStepperRow(
    label: String,
    valueLabel: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    OptionsLabeledRow(label = label) {
        GamepadButton(
            text = "−",
            onClick = onDecrement,
            outlined = true,
            enabled = canDecrement,
            focusId = "apps_opt_${label}_dec",
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        GamepadButton(
            text = "+",
            onClick = onIncrement,
            outlined = true,
            enabled = canIncrement,
            focusId = "apps_opt_${label}_inc",
        )
    }
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    selected: Boolean,
    iconShape: String,
    iconSize: Dp,
    tilePadHorizontal: Dp,
    tilePadVertical: Dp,
    showLabels: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
    onTileFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
    gamepadFocusable: Boolean = true,
    navHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val iconClip = AppIconShapes.forPreference(iconShape)
    GamepadTile(
        selected = selected,
        onSelect = onSelect,
        onLaunch = onLaunch,
        onLongPress = onLongPress,
        onFocusChanged = onTileFocusChanged,
        focusRequester = focusRequester,
        focusId = app.packageName,
        gamepadFocusable = gamepadFocusable,
        navHighlighted = navHighlighted,
        // Drawer D-pad owns selection via selectedPackage — don't let Compose
        // focus restore rewrite it mid-scroll.
        selectOnFocus = false,
        // Outer gutter clears neighbor rings; inner pad gives the ring air around glyph+label.
        // Caller supplies width (fixed cell for horizontal scroll, fillMaxWidth for vertical).
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDrawerGridPreferences.OuterTileGutter / 2,
                    vertical = AppDrawerGridPreferences.OuterTileGutter / 2,
                ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tilePadHorizontal, vertical = tilePadVertical),
        ) {
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(iconClip)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = app.icon
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = app.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = app.label.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (isFavorite) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }
            if (showLabels) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = AppDrawerGridPreferences.LabelGap),
                )
            }
        }
    }
}
