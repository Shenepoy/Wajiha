package com.wajiha.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.requestContentFocus
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.platform.LaunchableApp
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.components.gamepad.hasCenterInViewport
import com.wajiha.ui.theme.InputMode
import com.wajiha.ui.theme.LocalInputMode
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

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
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoad() }

    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var aTileHasFocus by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<AppContextTarget?>(null) }
    var contextMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val tileBoundsByPackage = remember { mutableStateMapOf<String, Rect>() }
    val tileFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var restoringGridFocus by remember { mutableStateOf(false) }
    var restoreFocusPackage by remember { mutableStateOf<String?>(null) }
    val menuOpen = contextMenuTarget != null
    val bottomDisplayId = secondaryDisplayId ?: 4
    val gridState = rememberLazyGridState()
    val inputMode = LocalInputMode.current
    var pendingViewportSnap by remember { mutableStateOf(false) }

    LaunchedEffect(apps.size, selectedPackage) {
        val focused = apps.firstOrNull { it.packageName == selectedPackage }
        onFocusChange(focused)
    }

    LaunchedEffect(gridState, inputMode) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.drop(1)
            .collect {
                if (inputMode == InputMode.Touch) {
                    pendingViewportSnap = true
                }
            }
    }

    fun snapFocusToTopVisible(): Boolean {
        val layoutInfo = gridState.layoutInfo
        val firstVisible =
            layoutInfo.visibleItemsInfo
                .filter { it.hasCenterInViewport(layoutInfo, horizontalScroll = false) }
                .minWithOrNull(compareBy({ it.row }, { it.column }))
                ?: return false
        val pkg = firstVisible.key as? String ?: return false
        val app = apps.firstOrNull { it.packageName == pkg } ?: return false
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
        remember(menuOpen, dualDisplay) {
            buildList {
                if (menuOpen) {
                    add(GamepadHint(GamepadHintButton.B, "Back"))
                } else {
                    add(GamepadHint(GamepadHintButton.A, "Open app"))
                    if (dualDisplay) {
                        add(GamepadHint(GamepadHintButton.Y, "Bottom screen"))
                    }
                    add(GamepadHint(GamepadHintButton.X, "Menu"))
                    add(GamepadHint(GamepadHintButton.B, "Back"))
                    if (dualDisplay) {
                        add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
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
            if (!menuOpen) {
                val pkg = selectedPackage ?: apps.firstOrNull()?.packageName
                if (pkg != null) {
                    tileFocusRequesters[pkg]?.requestContentFocus()
                }
            }
        },
        onPreviewKey = { event ->
            if (menuOpen) return@WajihaScreen false
            val pkg = selectedPackage
            when {
                pendingViewportSnap &&
                    (
                        GamepadKeys.isUp(event.type, event.key) ||
                            GamepadKeys.isDown(event.type, event.key) ||
                            GamepadKeys.isLeft(event.type, event.key) ||
                            GamepadKeys.isRight(event.type, event.key)
                    ) -> {
                    // Land on the top in-view tile only — next press moves.
                    pendingViewportSnap = false
                    snapFocusToTopVisible()
                    true
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

                aTileHasFocus &&
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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = WajihaSpacing.sm + WajihaSpacing.xs, vertical = WajihaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("< Back") }
                    Text(
                        text = "Apps",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = WajihaSpacing.sm),
                    )
                }
                LaunchedEffect(restoringGridFocus, restoreFocusPackage) {
                    if (restoringGridFocus) {
                        val pkg = restoreFocusPackage ?: selectedPackage
                        if (pkg != null) {
                            delay(40)
                            try {
                                tileFocusRequesters[pkg]?.requestFocus()
                            } catch (_: Exception) {
                            }
                        }
                        restoringGridFocus = false
                        restoreFocusPackage = null
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(WajihaSpacing.touchMin + WajihaSpacing.xl + WajihaSpacing.sm),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(WajihaSpacing.sm + WajihaSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName == selectedPackage
                        val isMenuTile = menuOpen && contextMenuTarget?.packageName == app.packageName
                        val isRestoreTile =
                            restoringGridFocus &&
                                (restoreFocusPackage ?: selectedPackage) == app.packageName
                        val tileFocus =
                            remember(app.packageName) {
                                tileFocusRequesters.getOrPut(app.packageName) { FocusRequester() }
                            }
                        AppTile(
                            app = app,
                            selected = isSelected,
                            onSelect = {
                                if (gamepadOwner != null) {
                                    onClaimGamepad?.invoke(gamepadOwner)
                                }
                                selectedPackage = app.packageName
                                onFocusChange(app)
                            },
                            onLaunch = { onLaunch(app.packageName) },
                            onLongPress = {
                                if (gamepadOwner != null) {
                                    onClaimGamepad?.invoke(gamepadOwner)
                                }
                                selectedPackage = app.packageName
                                onFocusChange(app)
                                openContextMenu(app.packageName)
                            },
                            onTileFocusChanged = { focused ->
                                if (focused) {
                                    aTileHasFocus = true
                                    selectedPackage = app.packageName
                                    onFocusChange(app)
                                } else {
                                    aTileHasFocus = false
                                }
                            },
                            focusRequester = tileFocus,
                            gamepadFocusable =
                                when {
                                    menuOpen -> false
                                    restoringGridFocus -> isRestoreTile
                                    else -> true
                                },
                            navHighlighted = (isMenuTile || isRestoreTile) && isSelected,
                            modifier =
                                Modifier
                                    .then(if (isMenuTile) Modifier.zIndex(1f) else Modifier)
                                    .onGloballyPositioned { coords ->
                                        tileBoundsByPackage[app.packageName] = coords.boundsInRoot()
                                    },
                        )
                    }
                }
            }

            AppContextMenu(
                target = contextMenuTarget,
                anchorBounds = contextMenuAnchorBounds,
                onDismiss = ::dismissContextMenu,
                onOpenAppInfo = onOpenAppInfo,
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    selected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
    onTileFocusChanged: (Boolean) -> Unit,
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
        onFocusChanged = onTileFocusChanged,
        focusRequester = focusRequester,
        gamepadFocusable = gamepadFocusable,
        navHighlighted = navHighlighted,
        modifier = modifier.padding(WajihaSpacing.sm),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(WajihaSpacing.touchMin + WajihaSpacing.sm)
                        .clip(WajihaShapes.tile)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val icon = app.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
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
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = WajihaSpacing.xs),
            )
        }
    }
}
