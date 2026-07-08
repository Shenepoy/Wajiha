package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.wajiha.input.GamepadKeys
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Horizontal game grid with NeoStation confirm model:
 * screen-level A launches selected item; tiles use touch two-tap.
 */
@Composable
fun <T> GamepadGrid(
    items: List<T>,
    key: (T) -> Any,
    rows: Int,
    selectedId: Any?,
    onSelect: (T) -> Unit,
    onLaunch: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(WajihaSpacing.md),
    tile: @Composable (item: T, selected: Boolean, focusRequester: FocusRequester?) -> Unit
) {
    if (items.isEmpty()) return

    val firstFocus = remember { FocusRequester() }
    var gridHasFocus by remember { mutableStateOf(false) }
    var launchTarget: T? by remember { mutableStateOf(null) }

    LaunchedEffect(items.map { key(it) }) {
        if (items.isNotEmpty()) {
            launchTarget = items.first()
            onSelect(items.first())
            try {
                firstFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (
                    gridHasFocus &&
                    GamepadKeys.isConfirm(event.type, event.key) &&
                    launchTarget != null
                ) {
                    WajihaLog.i(WajihaTags.LAUNCH, "GamepadGrid confirm launch")
                    onLaunch(launchTarget!!)
                    true
                } else {
                    false
                }
            }
    ) {
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs)
        ) {
            items(items, key = { key(it) }) { item ->
                val isFirst = key(item) == key(items.first())
                val selected = key(item) == selectedId
                tile(
                    item,
                    selected,
                    if (isFirst) firstFocus else null
                )
            }
        }
    }
}

/** Helper to wire [GamepadTile] focus tracking from grid scope. */
@Composable
fun rememberGridFocusHandler(
    onSelect: () -> Unit,
    onGridFocus: (Boolean) -> Unit
): (Boolean) -> Unit {
    return { focused ->
        onGridFocus(focused)
        if (focused) onSelect()
    }
}
