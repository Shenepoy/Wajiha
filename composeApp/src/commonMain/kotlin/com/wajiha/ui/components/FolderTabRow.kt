package com.wajiha.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wajiha.ui.components.gamepad.wajihaPressedFeedback
import com.wajiha.ui.theme.WajihaFocus
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Horizontal folder-style tabs where the selected tab visually connects to a
 * content panel below (manila-folder metaphor).
 *
 * Tabs are touch-only and switched via L1/R1 at screen level — not gamepad focusable.
 */
@Composable
fun FolderTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in tabs.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
        verticalAlignment = Alignment.Bottom
    ) {
        itemsIndexed(tabs, key = { _, label -> label }) { index, label ->
            FolderTab(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun FolderTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = WajihaShapes.folderTab
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        label = "folder_tab_color"
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        label = "folder_tab_label"
    )
    val topPadding by animateDpAsState(
        targetValue = if (selected) 0.dp else WajihaSpacing.xs,
        label = "folder_tab_elevation"
    )
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Box(
        modifier = modifier.padding(top = topPadding)
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            modifier = Modifier
                .clip(shape)
                .wajihaPressedFeedback(pressed, shape)
                .then(
                    if (!selected) {
                        Modifier.border(
                            width = 1.dp,
                            color = outlineColor,
                            shape = shape
                        )
                    } else {
                        Modifier
                    }
                )
                .pointerInput(onClick) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            val released = tryAwaitRelease()
                            pressed = false
                            if (released) onClick()
                        }
                    )
                }
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = labelColor,
                modifier = Modifier
                    .defaultMinSize(minHeight = WajihaSpacing.touchMin)
                    .padding(
                        horizontal = WajihaSpacing.md,
                        vertical = WajihaSpacing.sm
                    )
                    .then(
                        if (pressed) {
                            Modifier.background(WajihaFocus.pressedOverlay())
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}
