package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.ui.theme.InputMode
import com.wajiha.ui.theme.LocalInputMode
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.flow.drop

@Composable
fun <T> GamepadList(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(WajihaSpacing.md),
    emptyContent: @Composable (() -> Unit)? = null,
    row: @Composable (item: T) -> Unit,
) {
    if (items.isEmpty()) {
        emptyContent?.invoke()
        return
    }
    val listState = rememberLazyListState()
    val inputMode = LocalInputMode.current
    val navController = LocalGamepadNavController.current
    var pendingViewportSnap by remember { mutableStateOf(false) }

    LaunchedEffect(listState, inputMode) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.drop(1)
            .collect {
                if (inputMode == InputMode.Touch) {
                    pendingViewportSnap = true
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (!pendingViewportSnap) return@onPreviewKeyEvent false
                    val isDpad =
                        GamepadKeys.isUp(event.type, event.key) ||
                            GamepadKeys.isDown(event.type, event.key) ||
                            GamepadKeys.isLeft(event.type, event.key) ||
                            GamepadKeys.isRight(event.type, event.key)
                    if (!isDpad) return@onPreviewKeyEvent false
                    pendingViewportSnap = false
                    val layoutInfo = listState.layoutInfo
                    val firstVisible =
                        layoutInfo.visibleItemsInfo
                            .firstOrNull { info ->
                                val extent = info.size
                                if (extent <= 0) return@firstOrNull false
                                val center = info.offset + extent / 2
                                center >= layoutInfo.viewportStartOffset &&
                                    center < layoutInfo.viewportEndOffset
                            }?.index
                            ?: return@onPreviewKeyEvent false
                    val nav = navController
                    if (nav != null) {
                        // Land on the top in-view row only — next press moves.
                        nav.focusState.focusedIndex =
                            firstVisible.coerceIn(0, (nav.focusState.itemCount - 1).coerceAtLeast(0))
                        true
                    } else {
                        false
                    }
                },
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        items(items, key = { key(it) }) { item ->
            row(item)
        }
    }
}
