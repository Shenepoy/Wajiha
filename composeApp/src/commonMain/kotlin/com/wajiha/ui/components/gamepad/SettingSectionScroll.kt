package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt

/**
 * Scroll container for grouped settings cards. Provided via [LocalSettingSectionScroll]
 * so [MultiChoiceSettingRow] can scroll the row header to the top on expand.
 */
class SettingSectionScroll(
    val scrollState: ScrollState,
) {
    internal var containerCoordinates: LayoutCoordinates? = null
}

val LocalSettingSectionScroll = compositionLocalOf<SettingSectionScroll?> { null }

/** Scrollable column used inside settings / game-detail section cards. */
@Composable
fun SettingSectionScrollColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val sectionScroll = remember(scrollState) { SettingSectionScroll(scrollState) }
    CompositionLocalProvider(LocalSettingSectionScroll provides sectionScroll) {
        Column(
            modifier =
                modifier
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { sectionScroll.containerCoordinates = it },
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

suspend fun SettingSectionScroll.scrollHeaderToTop(headerCoordinates: LayoutCoordinates) {
    withFrameNanos { }
    withFrameNanos { }
    val container = containerCoordinates ?: return
    val headerOffsetFromViewportTop =
        headerCoordinates.boundsInRoot().top - container.boundsInRoot().top
    val targetY = (scrollState.value + headerOffsetFromViewportTop).roundToInt().coerceAtLeast(0)
    scrollState.scrollTo(targetY)
}
