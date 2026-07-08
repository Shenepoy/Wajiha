package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.input.GamepadKeys
import com.wajiha.ui.components.WajihaSectionDivider
import com.wajiha.ui.theme.WajihaSpacing

data class MasterDetailNavItem(
    val id: String,
    val label: String,
    val groupStart: Boolean = false
)

@Composable
fun GamepadMasterDetail(
    navItems: List<MasterDetailNavItem>,
    selectedId: String,
    onSelectNav: (String) -> Unit,
    onCycleSection: ((delta: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** When false, detail pane does not wrap content in [verticalScroll] (e.g. [LazyColumn]). */
    detailScrollable: Boolean = true,
    navContent: @Composable (item: MasterDetailNavItem, selected: Boolean) -> Unit,
    detailContent: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (onCycleSection == null) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isL1(event.type, event.key) -> {
                        onCycleSection(-1)
                        true
                    }
                    GamepadKeys.isR1(event.type, event.key) -> {
                        onCycleSection(1)
                        true
                    }
                    else -> false
                }
            }
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .width(WajihaSpacing.navWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .verticalScroll(rememberScrollState())
        ) {
            navItems.forEach { item ->
                if (item.groupStart && item.id != navItems.first().id) {
                    WajihaSectionDivider()
                }
                Box(
                    modifier = Modifier.pointerInput(item.id) {
                        detectTapGestures { onSelectNav(item.id) }
                    }
                ) {
                    navContent(item, item.id == selectedId)
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (detailScrollable) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                )
        ) {
            detailContent()
        }
    }
}
