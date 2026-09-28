package com.wajiha.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.GamepadTextEditRegistry
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.ui.components.WajihaContextMenuDivider
import com.wajiha.ui.components.WajihaContextMenuExpansion
import com.wajiha.ui.components.WajihaContextMenuMetrics
import com.wajiha.ui.components.WajihaContextMenuPanel
import com.wajiha.ui.components.WajihaContextMenuRow
import com.wajiha.ui.components.WajihaContextMenuScrim
import com.wajiha.ui.components.WajihaDialog
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.theme.FocusBorderStyle
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.InputMode
import com.wajiha.ui.theme.LocalFocusIndicatorStyle
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.LocalInputMode
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaIconSize
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class PendingConfirm { None, RemoveFromLibrary, DeleteFile }

private enum class MenuSide { Right, Left, Above, Below }

/** Which context-menu row (or nested expansion) currently holds gamepad focus. */
private enum class ContextMenuFocusZone {
    None,
    Open,
    Info,
    Collections,
    Delete,
    OpenNested,
    DeleteNested,
}

/** Logical menu rows for focus-independent (bridge) navigation. */
private enum class MenuNavId {
    Open,
    TopLaunch,
    BottomLaunch,
    Info,
    Collection,
    Delete,
    RemoveLib,
    DeleteFile,
}

private fun menuNavItems(
    openExpanded: Boolean,
    deleteExpanded: Boolean,
    dualDisplay: Boolean,
): List<MenuNavId> =
    buildList {
        add(MenuNavId.Open)
        if (openExpanded) {
            add(MenuNavId.TopLaunch)
            if (dualDisplay) add(MenuNavId.BottomLaunch)
        }
        add(MenuNavId.Info)
        add(MenuNavId.Collection)
        add(MenuNavId.Delete)
        if (deleteExpanded) {
            add(MenuNavId.RemoveLib)
            add(MenuNavId.DeleteFile)
        }
    }

private fun MenuNavId.toFocusZone(): ContextMenuFocusZone =
    when (this) {
        MenuNavId.Open -> ContextMenuFocusZone.Open

        MenuNavId.TopLaunch,
        MenuNavId.BottomLaunch,
        -> ContextMenuFocusZone.OpenNested

        MenuNavId.Info -> ContextMenuFocusZone.Info

        MenuNavId.Collection -> ContextMenuFocusZone.Collections

        MenuNavId.Delete -> ContextMenuFocusZone.Delete

        MenuNavId.RemoveLib,
        MenuNavId.DeleteFile,
        -> ContextMenuFocusZone.DeleteNested
    }

/**
 * Picks a popover side from tile bounds and screen size.
 * Priority: right, left, below, above.
 */
private fun chooseMenuSide(
    anchor: Rect,
    menuW: Float,
    menuH: Float,
    containerW: Float,
    containerH: Float,
    gapPx: Float,
    padPx: Float,
): MenuSide {
    val rightSpace = containerW - padPx - anchor.right
    val leftSpace = anchor.left - padPx
    val belowSpace = containerH - padPx - anchor.bottom
    val aboveSpace = anchor.top - padPx

    return when {
        rightSpace >= menuW + gapPx -> MenuSide.Right
        leftSpace >= menuW + gapPx -> MenuSide.Left
        belowSpace >= menuH + gapPx -> MenuSide.Below
        aboveSpace >= menuH + gapPx -> MenuSide.Above
        rightSpace >= leftSpace && rightSpace >= belowSpace && rightSpace >= aboveSpace -> MenuSide.Right
        leftSpace >= belowSpace && leftSpace >= aboveSpace -> MenuSide.Left
        belowSpace >= aboveSpace -> MenuSide.Below
        else -> MenuSide.Above
    }
}

/**
 * Places the menu on a locked [side], clamping so it stays fully inside [padPx] margins.
 * Growing height after lock only shifts along the free axis — never flips side.
 */
private fun offsetForLockedSide(
    side: MenuSide,
    anchor: Rect,
    menuW: Float,
    menuH: Float,
    containerW: Float,
    containerH: Float,
    gapPx: Float,
    padPx: Float,
): IntOffset {
    val minX = padPx
    val minY = padPx
    val maxX = (containerW - padPx - menuW).coerceAtLeast(minX)
    val maxY = (containerH - padPx - menuH).coerceAtLeast(minY)

    fun clampX(x: Float) = x.coerceIn(minX, maxX)

    fun clampY(y: Float) = y.coerceIn(minY, maxY)

    return when (side) {
        MenuSide.Right -> {
            IntOffset(
                clampX(anchor.right + gapPx).roundToInt(),
                clampY(anchor.top).roundToInt(),
            )
        }

        MenuSide.Left -> {
            IntOffset(
                clampX(anchor.left - menuW - gapPx).roundToInt(),
                clampY(anchor.top).roundToInt(),
            )
        }

        MenuSide.Below -> {
            IntOffset(
                clampX(anchor.center.x - menuW / 2f).roundToInt(),
                clampY(anchor.bottom + gapPx).roundToInt(),
            )
        }

        MenuSide.Above -> {
            IntOffset(
                clampX(anchor.center.x - menuW / 2f).roundToInt(),
                clampY(anchor.top - menuH - gapPx).roundToInt(),
            )
        }
    }
}

/** Inflate layout bounds to match [GamepadTile] selected scale so the cutout fits the ring. */
private fun inflateForTileScale(
    rect: Rect,
    scale: Float,
    ringPadPx: Float = 0f,
): Rect {
    val cx = rect.center.x
    val cy = rect.center.y
    val hw = rect.width * scale / 2f + ringPadPx
    val hh = rect.height * scale / 2f + ringPadPx
    return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
}

data class GameContextTarget(
    val gameId: Long,
    val gameName: String,
)

private fun collapseSubmenus(
    openExpanded: Boolean,
    deleteExpanded: Boolean,
    onOpenExpandedChange: (Boolean) -> Unit,
    onDeleteExpandedChange: (Boolean) -> Unit,
): Boolean =
    when {
        openExpanded -> {
            onOpenExpandedChange(false)
            true
        }

        deleteExpanded -> {
            onDeleteExpandedChange(false)
            true
        }

        else -> {
            false
        }
    }

/**
 * Context menu for a game tile: side-attached popover beside the focused tile.
 * Dims everything except the selected tile and gamepad hints. X or long-press opens; B dismisses.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameContextMenu(
    target: GameContextTarget?,
    anchorBounds: Rect?,
    secondaryDisplayId: Int?,
    dualDisplay: Boolean = false,
    onDismiss: () -> Unit,
    onOpenOnDisplay: (gameId: Long, displayId: Int) -> Unit,
    onOpenInfo: (gameId: Long) -> Unit,
    onRemoveFromLibrary: (gameId: Long) -> Unit,
    onDeleteFile: (gameId: Long) -> Unit,
    collections: List<CollectionSummary> = emptyList(),
    memberCollectionIds: Set<Long> = emptySet(),
    onToggleCollection: (collectionId: Long, member: Boolean) -> Unit = { _, _ -> },
    onCreateCollection: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (target == null) return

    var openExpanded by remember(target.gameId) { mutableStateOf(false) }
    var deleteExpanded by remember(target.gameId) { mutableStateOf(false) }
    var pendingConfirm by remember(target.gameId) { mutableStateOf(PendingConfirm.None) }
    var collectionPickerOpen by remember(target.gameId) { mutableStateOf(false) }
    var navIndex by remember(target.gameId) { mutableStateOf(0) }
    val openRowFocus = remember(target.gameId) { FocusRequester() }
    val topScreenFocus = remember(target.gameId) { FocusRequester() }
    val bottomScreenFocus = remember(target.gameId) { FocusRequester() }
    val infoRowFocus = remember(target.gameId) { FocusRequester() }
    val collectionRowFocus = remember(target.gameId) { FocusRequester() }
    val deleteRowFocus = remember(target.gameId) { FocusRequester() }
    val removeFocus = remember(target.gameId) { FocusRequester() }
    val deleteFileFocus = remember(target.gameId) { FocusRequester() }

    val topDisplayId = 0
    val bottomDisplayId = secondaryDisplayId ?: 4
    val navItems =
        menuNavItems(
            openExpanded = openExpanded,
            deleteExpanded = deleteExpanded,
            dualDisplay = dualDisplay,
        )
    val selectedNav = navItems.getOrElse(navIndex.coerceIn(0, navItems.lastIndex)) { MenuNavId.Open }

    var focusedZone by remember(target.gameId) { mutableStateOf(ContextMenuFocusZone.Open) }
    var wasTouchMode by remember(target.gameId) { mutableStateOf(false) }
    val inputMode = LocalInputMode.current

    fun onMenuZoneFocused(zone: ContextMenuFocusZone) {
        focusedZone = zone
    }

    fun selectNav(id: MenuNavId) {
        val idx = navItems.indexOf(id)
        if (idx >= 0) {
            navIndex = idx
        } else {
            // Row list may not have recomposed yet after expand; keep a stable index.
            navIndex =
                when (id) {
                    MenuNavId.TopLaunch -> {
                        1
                    }

                    MenuNavId.RemoveLib -> {
                        menuNavItems(
                            openExpanded = false,
                            deleteExpanded = true,
                            dualDisplay = dualDisplay,
                        ).indexOf(MenuNavId.RemoveLib)
                            .coerceAtLeast(0)
                    }

                    else -> {
                        navIndex
                    }
                }
        }
        focusedZone = id.toFocusZone()
        // Best-effort Compose focus — secondary display often lacks window focus,
        // so [forceHighlight] + navIndex are the source of truth.
        val requester =
            when (id) {
                MenuNavId.Open -> openRowFocus
                MenuNavId.TopLaunch -> topScreenFocus
                MenuNavId.BottomLaunch -> bottomScreenFocus
                MenuNavId.Info -> infoRowFocus
                MenuNavId.Collection -> collectionRowFocus
                MenuNavId.Delete -> deleteRowFocus
                MenuNavId.RemoveLib -> removeFocus
                MenuNavId.DeleteFile -> deleteFileFocus
            }
        try {
            requester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun activateNav(id: MenuNavId): Boolean {
        when (id) {
            MenuNavId.Open -> {
                deleteExpanded = false
                openExpanded = !openExpanded
                if (openExpanded) {
                    // Expansion inserts nested rows; land on the first launch target.
                    navIndex = 1
                    focusedZone = ContextMenuFocusZone.OpenNested
                }
            }

            MenuNavId.TopLaunch -> {
                onOpenOnDisplay(target.gameId, topDisplayId)
                onDismiss()
            }

            MenuNavId.BottomLaunch -> {
                onOpenOnDisplay(target.gameId, bottomDisplayId)
                onDismiss()
            }

            MenuNavId.Info -> {
                onOpenInfo(target.gameId)
                onDismiss()
            }

            MenuNavId.Collection -> {
                collectionPickerOpen = true
            }

            MenuNavId.Delete -> {
                openExpanded = false
                deleteExpanded = !deleteExpanded
                if (deleteExpanded) {
                    navIndex = navItems.indexOf(MenuNavId.Delete).coerceAtLeast(0) + 1
                    focusedZone = ContextMenuFocusZone.DeleteNested
                }
            }

            MenuNavId.RemoveLib -> {
                pendingConfirm = PendingConfirm.RemoveFromLibrary
            }

            MenuNavId.DeleteFile -> {
                pendingConfirm = PendingConfirm.DeleteFile
            }
        }
        return true
    }

    fun handleBack(): Boolean {
        if (GamepadTextEditRegistry.dismissIfEditing()) return true
        when {
            openExpanded -> {
                openExpanded = false
                navIndex = 0
                focusedZone = ContextMenuFocusZone.Open
                return true
            }

            deleteExpanded -> {
                deleteExpanded = false
                focusedZone = ContextMenuFocusZone.Delete
                navIndex =
                    menuNavItems(
                        openExpanded = false,
                        deleteExpanded = false,
                        dualDisplay = dualDisplay,
                    ).indexOf(MenuNavId.Delete)
                        .coerceAtLeast(0)
                return true
            }

            else -> {
                onDismiss()
                return true
            }
        }
    }

    fun onPreviewKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (pendingConfirm != PendingConfirm.None || collectionPickerOpen) return false
        val items =
            menuNavItems(
                openExpanded = openExpanded,
                deleteExpanded = deleteExpanded,
                dualDisplay = dualDisplay,
            )
        val index = navIndex.coerceIn(0, items.lastIndex)
        val current = items[index]
        when {
            GamepadKeys.isBack(event.type, event.key) -> {
                return handleBack()
            }

            GamepadKeys.isUp(event.type, event.key) -> {
                if (index > 0) {
                    selectNav(items[index - 1])
                    WajihaLog.i(
                        WajihaTags.LAUNCH,
                        "contextMenu: nav → ${items[index - 1]}",
                    )
                }
                return true
            }

            GamepadKeys.isDown(event.type, event.key) -> {
                if (index < items.lastIndex) {
                    selectNav(items[index + 1])
                    WajihaLog.i(
                        WajihaTags.LAUNCH,
                        "contextMenu: nav → ${items[index + 1]}",
                    )
                }
                return true
            }

            GamepadKeys.isLeft(event.type, event.key) -> {
                return when (current) {
                    MenuNavId.TopLaunch,
                    MenuNavId.BottomLaunch,
                    -> {
                        openExpanded = false
                        selectNav(MenuNavId.Open)
                        true
                    }

                    MenuNavId.RemoveLib,
                    MenuNavId.DeleteFile,
                    -> {
                        deleteExpanded = false
                        selectNav(MenuNavId.Delete)
                        true
                    }

                    MenuNavId.Open -> {
                        if (openExpanded) {
                            openExpanded = false
                            true
                        } else {
                            true
                        }
                    }

                    MenuNavId.Delete -> {
                        if (deleteExpanded) {
                            deleteExpanded = false
                            true
                        } else {
                            true
                        }
                    }

                    else -> {
                        true
                    }
                }
            }

            GamepadKeys.isRight(event.type, event.key) -> {
                return when (current) {
                    MenuNavId.Open -> {
                        if (!openExpanded) activateNav(MenuNavId.Open) else true
                    }

                    MenuNavId.Delete -> {
                        if (!deleteExpanded) activateNav(MenuNavId.Delete) else true
                    }

                    else -> {
                        activateNav(current)
                    }
                }
            }

            GamepadKeys.isConfirm(event.type, event.key) -> {
                WajihaLog.i(WajihaTags.LAUNCH, "contextMenu: confirm $current")
                return activateNav(current)
            }

            else -> {
                return false
            }
        }
    }

    val latestPreviewKey =
        rememberUpdatedState<(KeyEvent) -> Boolean>(
            newValue = { event -> onPreviewKey(event) },
        )
    val layerId = "game_context_${target.gameId}"
    val focusContinuity = LocalFocusContinuityController.current
    DisposableEffect(layerId, focusContinuity) {
        val handler: (KeyEvent) -> Boolean = { event -> latestPreviewKey.value(event) }
        GamepadLayers.stack.push(layerId)
        focusContinuity?.pushLayer(layerId)
        GamepadLayers.stack.setPreviewHandler(layerId, handler)
        onDispose {
            GamepadLayers.stack.setPreviewHandler(layerId, null)
            GamepadLayers.stack.pop(layerId)
            focusContinuity?.popLayer(layerId)
        }
    }

    LaunchedEffect(target.gameId) {
        navIndex = 0
        focusedZone = ContextMenuFocusZone.Open
        withFrameNanos { }
        selectNav(MenuNavId.Open)
        WajihaLog.i(WajihaTags.LAUNCH, "contextMenu: ready gameId=${target.gameId}")
    }

    LaunchedEffect(inputMode, focusedZone) {
        val touchJustActivated = inputMode == InputMode.Touch && !wasTouchMode
        wasTouchMode = inputMode == InputMode.Touch
        if (
            touchJustActivated &&
            focusedZone !in setOf(ContextMenuFocusZone.OpenNested, ContextMenuFocusZone.DeleteNested) &&
            (openExpanded || deleteExpanded)
        ) {
            openExpanded = false
            deleteExpanded = false
        }
    }

    LaunchedEffect(pendingConfirm) {
        if (pendingConfirm != PendingConfirm.None) {
            openExpanded = false
            deleteExpanded = false
        }
    }

    // Keep navIndex valid when the visible row list shrinks (collapse).
    LaunchedEffect(navItems, navIndex) {
        if (navIndex > navItems.lastIndex) {
            navIndex = navItems.lastIndex.coerceAtLeast(0)
        }
    }

    val density = LocalDensity.current
    val gap = WajihaSpacing.sm
    var menuSize by remember(target.gameId) { mutableStateOf(IntSize.Zero) }
    var lockedSide by remember(target.gameId) { mutableStateOf<MenuSide?>(null) }

    BackHandler(enabled = pendingConfirm == PendingConfirm.None) { handleBack() }

    CompositionLocalProvider(LocalFocusLayerId provides layerId) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (pendingConfirm != PendingConfirm.None) return@onPreviewKeyEvent false
                        onPreviewKey(event)
                    },
        ) {
            WajihaContextMenuScrim(
                tileCutoutRoot = anchorBounds,
                onDismiss = {
                    if (pendingConfirm == PendingConfirm.None) onDismiss()
                },
            )

            val menuOffset =
                remember(
                    anchorBounds,
                    constraints.maxWidth,
                    constraints.maxHeight,
                    menuSize,
                    lockedSide,
                ) {
                    with(density) {
                        val menuW =
                            if (menuSize.width > 0) {
                                menuSize.width.toFloat()
                            } else {
                                WajihaContextMenuMetrics.width.toPx()
                            }
                        val menuH =
                            if (menuSize.height > 0) {
                                menuSize.height.toFloat()
                            } else {
                                196.dp.toPx()
                            }
                        val gapPx = gap.toPx()
                        val padPx = WajihaSpacing.sm.toPx()
                        val cw = constraints.maxWidth.toFloat()
                        val ch = constraints.maxHeight.toFloat()

                        if (anchorBounds != null) {
                            val anchor = inflateForTileScale(anchorBounds, WajihaFocus.selectedScale)
                            val side =
                                lockedSide ?: chooseMenuSide(
                                    anchor = anchor,
                                    menuW = menuW,
                                    menuH = menuH,
                                    containerW = cw,
                                    containerH = ch,
                                    gapPx = gapPx,
                                    padPx = padPx,
                                )
                            offsetForLockedSide(
                                side = side,
                                anchor = anchor,
                                menuW = menuW,
                                menuH = menuH,
                                containerW = cw,
                                containerH = ch,
                                gapPx = gapPx,
                                padPx = padPx,
                            )
                        } else {
                            IntOffset(
                                (cw - menuW - padPx).roundToInt().coerceAtLeast(padPx.roundToInt()),
                                padPx.roundToInt(),
                            )
                        }
                    }
                }

            LaunchedEffect(menuSize, anchorBounds, constraints.maxWidth, constraints.maxHeight) {
                if (lockedSide == null && menuSize != IntSize.Zero && anchorBounds != null) {
                    with(density) {
                        val anchor = inflateForTileScale(anchorBounds, WajihaFocus.selectedScale)
                        lockedSide =
                            chooseMenuSide(
                                anchor = anchor,
                                menuW = menuSize.width.toFloat(),
                                menuH = menuSize.height.toFloat(),
                                containerW = constraints.maxWidth.toFloat(),
                                containerH = constraints.maxHeight.toFloat(),
                                gapPx = gap.toPx(),
                                padPx = WajihaSpacing.sm.toPx(),
                            )
                    }
                }
            }

            val menuPlaced = menuSize != IntSize.Zero

            Box(
                modifier =
                    Modifier
                        .zIndex(1f)
                        .alpha(if (menuPlaced) 1f else 0f)
                        .offset { menuOffset },
            ) {
                WajihaContextMenuPanel(
                    title = target.gameName,
                    modifier =
                        Modifier.onSizeChanged { size ->
                            if (size != IntSize.Zero) menuSize = size
                        },
                ) {
                    WajihaContextMenuRow(
                        label = "Open",
                        icon = Icons.Filled.PlayArrow,
                        expandable = true,
                        expanded = openExpanded,
                        focusRequester = openRowFocus,
                        forceHighlight = selectedNav == MenuNavId.Open,
                        onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.Open) },
                        onClick = {
                            deleteExpanded = false
                            openExpanded = !openExpanded
                        },
                    )

                    WajihaContextMenuExpansion(visible = openExpanded) {
                        WajihaContextMenuRow(
                            label = if (dualDisplay) "Top screen" else "Launch",
                            nested = true,
                            onClick = {
                                onOpenOnDisplay(target.gameId, topDisplayId)
                                onDismiss()
                            },
                            focusRequester = topScreenFocus,
                            forceHighlight = selectedNav == MenuNavId.TopLaunch,
                            onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.OpenNested) },
                        )
                        if (dualDisplay) {
                            WajihaContextMenuRow(
                                label = "Bottom screen",
                                nested = true,
                                onClick = {
                                    onOpenOnDisplay(target.gameId, bottomDisplayId)
                                    onDismiss()
                                },
                                focusRequester = bottomScreenFocus,
                                forceHighlight = selectedNav == MenuNavId.BottomLaunch,
                                onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.OpenNested) },
                            )
                        }
                    }

                    WajihaContextMenuDivider()

                    WajihaContextMenuRow(
                        label = "Info",
                        icon = Icons.Filled.Info,
                        focusRequester = infoRowFocus,
                        forceHighlight = selectedNav == MenuNavId.Info,
                        onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.Info) },
                        onClick = {
                            onOpenInfo(target.gameId)
                            onDismiss()
                        },
                    )

                    WajihaContextMenuRow(
                        label = "Add to collection",
                        icon = Icons.Filled.Star,
                        focusRequester = collectionRowFocus,
                        forceHighlight = selectedNav == MenuNavId.Collection,
                        onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.Collections) },
                        onClick = { collectionPickerOpen = true },
                    )

                    WajihaContextMenuDivider()

                    WajihaContextMenuRow(
                        label = "Delete",
                        icon = Icons.Filled.Delete,
                        expandable = true,
                        expanded = deleteExpanded,
                        focusRequester = deleteRowFocus,
                        forceHighlight = selectedNav == MenuNavId.Delete,
                        onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.Delete) },
                        onClick = {
                            openExpanded = false
                            deleteExpanded = !deleteExpanded
                        },
                    )

                    WajihaContextMenuExpansion(visible = deleteExpanded) {
                        WajihaContextMenuRow(
                            label = "Remove from library",
                            icon = Icons.Filled.Clear,
                            nested = true,
                            onClick = { pendingConfirm = PendingConfirm.RemoveFromLibrary },
                            focusRequester = removeFocus,
                            forceHighlight = selectedNav == MenuNavId.RemoveLib,
                            onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.DeleteNested) },
                        )
                        WajihaContextMenuRow(
                            label = "Delete file",
                            icon = Icons.Filled.Delete,
                            nested = true,
                            destructive = true,
                            onClick = { pendingConfirm = PendingConfirm.DeleteFile },
                            focusRequester = deleteFileFocus,
                            forceHighlight = selectedNav == MenuNavId.DeleteFile,
                            onFocusGained = { onMenuZoneFocused(ContextMenuFocusZone.DeleteNested) },
                        )
                    }
                }
            }
        }
    }

    CollectionMembershipDialog(
        visible = collectionPickerOpen,
        collections = collections,
        memberIds = memberCollectionIds,
        onToggle = onToggleCollection,
        onCreate = onCreateCollection,
        onDismiss = { collectionPickerOpen = false },
    )

    WajihaDialog(
        visible = pendingConfirm == PendingConfirm.RemoveFromLibrary,
        title = "Remove from Wajiha?",
        message =
            "\"${target.gameName}\" will be removed from your library. " +
                "The ROM file on disk is kept.",
        onDismiss = { pendingConfirm = PendingConfirm.None },
        onConfirm = {
            pendingConfirm = PendingConfirm.None
            onRemoveFromLibrary(target.gameId)
            onDismiss()
        },
        confirmText = "Remove",
        dismissText = "Cancel",
    )

    WajihaDialog(
        visible = pendingConfirm == PendingConfirm.DeleteFile,
        title = "Delete ROM file?",
        message =
            "\"${target.gameName}\" and its ROM file will be permanently deleted. " +
                "This cannot be undone.",
        onDismiss = { pendingConfirm = PendingConfirm.None },
        onConfirm = {
            pendingConfirm = PendingConfirm.None
            onDeleteFile(target.gameId)
            onDismiss()
        },
        confirmText = "Delete",
        dismissText = "Cancel",
    )
}
