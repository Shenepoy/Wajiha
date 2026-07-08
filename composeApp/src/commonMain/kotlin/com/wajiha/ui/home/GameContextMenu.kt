package com.wajiha.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.ui.components.WajihaDialog
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.theme.GamepadFocusChromeScope
import com.wajiha.ui.theme.LocalGamepadFocusChromeScope
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class MenuLevel { Main, Open, Delete }

private enum class PendingConfirm { None, RemoveFromLibrary, DeleteFile }

data class GameContextTarget(
    val gameId: Long,
    val gameName: String
)

/**
 * Context menu for a game tile: side-attached popover beside the focused tile.
 * No scrim — grid stays fully visible. Gamepad X or long-press opens; B dismisses.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameContextMenu(
    target: GameContextTarget?,
    anchorBounds: Rect?,
    secondaryDisplayId: Int?,
    onDismiss: () -> Unit,
    onOpenOnDisplay: (gameId: Long, displayId: Int) -> Unit,
    onOpenInfo: (gameId: Long) -> Unit,
    onRemoveFromLibrary: (gameId: Long) -> Unit,
    onDeleteFile: (gameId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (target == null) return

    var level by remember(target.gameId) { mutableStateOf(MenuLevel.Main) }
    var pendingConfirm by remember(target.gameId) { mutableStateOf(PendingConfirm.None) }
    val firstButtonFocus = remember(target.gameId) { FocusRequester() }

    val layerId = "game_context_${target.gameId}"
    DisposableEffect(layerId) {
        GamepadLayers.stack.push(layerId)
        onDispose { GamepadLayers.stack.pop(layerId) }
    }

    LaunchedEffect(target.gameId, level) {
        delay(50)
        try {
            firstButtonFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    val topDisplayId = 0
    val bottomDisplayId = secondaryDisplayId ?: 4

    WajihaDialog(
        visible = pendingConfirm == PendingConfirm.RemoveFromLibrary,
        title = "Remove from Wajiha?",
        message = "\"${target.gameName}\" will be removed from your library. " +
            "The ROM file on disk is kept.",
        onDismiss = { pendingConfirm = PendingConfirm.None },
        onConfirm = {
            pendingConfirm = PendingConfirm.None
            onRemoveFromLibrary(target.gameId)
            onDismiss()
        },
        confirmText = "Remove",
        dismissText = "Cancel"
    )

    WajihaDialog(
        visible = pendingConfirm == PendingConfirm.DeleteFile,
        title = "Delete ROM file?",
        message = "\"${target.gameName}\" and its ROM file will be permanently deleted. " +
            "This cannot be undone.",
        onDismiss = { pendingConfirm = PendingConfirm.None },
        onConfirm = {
            pendingConfirm = PendingConfirm.None
            onDeleteFile(target.gameId)
            onDismiss()
        },
        confirmText = "Delete",
        dismissText = "Cancel"
    )

    val density = LocalDensity.current
    val menuWidth = 240.dp
    val gap = WajihaSpacing.sm

    // B is remapped to onBackPressed() at the Activity layer (GamepadKeyMapper),
    // so it never arrives as Key.ButtonB in Compose — handle via BackHandler.
    BackHandler {
        when {
            pendingConfirm != PendingConfirm.None -> pendingConfirm = PendingConfirm.None
            level != MenuLevel.Main -> level = MenuLevel.Main
            else -> onDismiss()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (GamepadKeys.isBack(event.type, event.key)) {
                    when (level) {
                        MenuLevel.Main -> onDismiss()
                        else -> level = MenuLevel.Main
                    }
                    true
                } else {
                    false
                }
            }
    ) {
        val menuOffset = remember(anchorBounds, constraints.maxWidth, constraints.maxHeight) {
            with(density) {
                val menuW = menuWidth.toPx()
                val gapPx = gap.toPx()
                val padPx = WajihaSpacing.md.toPx()
                val cw = constraints.maxWidth.toFloat()
                val ch = constraints.maxHeight.toFloat()

                if (anchorBounds != null) {
                    val rightSpace = cw - anchorBounds.right
                    val leftSpace = anchorBounds.left
                    val xPx = when {
                        rightSpace >= menuW + gapPx -> anchorBounds.right + gapPx
                        leftSpace >= menuW + gapPx -> anchorBounds.left - menuW - gapPx
                        else -> (cw - menuW) / 2f
                    }
                    val yPx = anchorBounds.top.coerceIn(padPx, (ch - padPx).coerceAtLeast(padPx))
                    IntOffset(xPx.roundToInt(), yPx.roundToInt())
                } else {
                    IntOffset(
                        (cw - menuW - padPx).roundToInt(),
                        padPx.roundToInt()
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .offset { menuOffset }
                .widthIn(min = 200.dp, max = 280.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            CompositionLocalProvider(
                LocalGamepadFocusChromeScope provides GamepadFocusChromeScope.Menu
            ) {
            Column(
                modifier = Modifier.padding(WajihaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
            ) {
                Text(
                    text = target.gameName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                when (level) {
                    MenuLevel.Main -> {
                        GamepadButton(
                            text = "Open",
                            onClick = { level = MenuLevel.Open },
                            focusRequester = firstButtonFocus
                        )
                        GamepadButton(
                            text = "Info",
                            onClick = {
                                onOpenInfo(target.gameId)
                                onDismiss()
                            }
                        )
                        GamepadButton(
                            text = "Delete",
                            onClick = { level = MenuLevel.Delete },
                            outlined = true
                        )
                    }
                    MenuLevel.Open -> {
                        Text(
                            text = "Launch on",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GamepadButton(
                            text = "Top screen",
                            onClick = {
                                onOpenOnDisplay(target.gameId, topDisplayId)
                                onDismiss()
                            },
                            focusRequester = firstButtonFocus
                        )
                        GamepadButton(
                            text = "Bottom screen",
                            onClick = {
                                onOpenOnDisplay(target.gameId, bottomDisplayId)
                                onDismiss()
                            }
                        )
                        GamepadButton(
                            text = "Back",
                            onClick = { level = MenuLevel.Main },
                            outlined = true
                        )
                    }
                    MenuLevel.Delete -> {
                        Text(
                            text = "Delete options",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GamepadButton(
                            text = "Remove from Wajiha",
                            onClick = { pendingConfirm = PendingConfirm.RemoveFromLibrary },
                            outlined = true,
                            focusRequester = firstButtonFocus
                        )
                        GamepadButton(
                            text = "Delete file",
                            onClick = { pendingConfirm = PendingConfirm.DeleteFile }
                        )
                        GamepadButton(
                            text = "Back",
                            onClick = { level = MenuLevel.Main },
                            outlined = true
                        )
                    }
                }
            }
            }
        }
    }
}
