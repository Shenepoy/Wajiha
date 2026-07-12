package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wajiha.ui.theme.WajihaSpacing

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        items(items, key = { key(it) }) { item ->
            row(item)
        }
    }
}
