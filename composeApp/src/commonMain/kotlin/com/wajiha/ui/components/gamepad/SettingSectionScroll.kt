package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.wajiha.input.GamepadKeys
import com.wajiha.input.rememberFocusContext
import com.wajiha.input.rememberedFocusContext
import com.wajiha.ui.theme.InputMode
import com.wajiha.ui.theme.LocalInputMode
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/**
 * Scroll container for grouped settings cards. Provided via [LocalSettingSectionScroll]
 * so [MultiChoiceSettingRow] can scroll the row header to the top on expand, and so
 * touch-scroll → D-pad can snap focus to the topmost visible row.
 */
class SettingSectionScroll(
    val scrollState: ScrollState,
) {
    internal var containerCoordinates: LayoutCoordinates? = null
    private val focusEntries = mutableMapOf<Any, FocusEntry>()

    /** Set when the user touch-scrolls; cleared after the next D-pad snap. */
    var pendingViewportSnap: Boolean = false

    fun updateFocusEntry(
        id: Any,
        requester: FocusRequester,
        boundsInRoot: Rect,
    ) {
        focusEntries[id] = FocusEntry(requester, boundsInRoot)
    }

    fun removeFocusEntry(id: Any) {
        focusEntries.remove(id)
    }

    /**
     * Focus the topmost focusable whose vertical center is in the scroll viewport.
     * @return true if focus was requested
     */
    fun focusTopmostVisible(): Boolean {
        val container = containerCoordinates?.boundsInRoot() ?: return false
        val target =
            focusEntries.values
                .asSequence()
                .filter { entry -> hasCenterInViewportVertically(entry.bounds, container) }
                .minByOrNull { it.bounds.top }
                ?: return false
        return try {
            target.requester.requestFocus()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreFocus(id: Any): Boolean {
        val entry = focusEntries[id] ?: return false
        val container = containerCoordinates?.boundsInRoot() ?: return false
        if (!hasCenterInViewportVertically(entry.bounds, container)) {
            val targetY =
                (scrollState.value + entry.bounds.top - container.top)
                    .roundToInt()
                    .coerceAtLeast(0)
            scrollState.scrollTo(targetY)
            withFrameNanos { }
        }
        return try {
            entry.requester.requestFocus()
            true
        } catch (_: Exception) {
            false
        }
    }

    private data class FocusEntry(
        val requester: FocusRequester,
        val bounds: Rect,
    )
}

val LocalSettingSectionScroll = compositionLocalOf<SettingSectionScroll?> { null }

@Stable
class SettingSectionFocusRestorer(
    private val memoryKey: String,
) {
    internal var section: SettingSectionScroll? = null

    internal fun initialScrollOffset(): Int = rememberedFocusContext("$memoryKey:offset") as? Int ?: 0

    internal fun rememberScrollOffset(value: Int) {
        rememberFocusContext("$memoryKey:offset", value)
    }

    suspend fun restore(
        targetId: Any?,
        fallback: FocusRequester,
    ) {
        if (targetId != null && section?.restoreFocus(targetId) == true) return
        try {
            fallback.requestFocus()
        } catch (_: Exception) {
        }
    }
}

/** Scrollable column used inside settings / game-detail section cards. */
@Composable
fun SettingSectionScrollColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    focusRestorer: SettingSectionFocusRestorer? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState =
        rememberScrollState(
            initial = focusRestorer?.initialScrollOffset() ?: 0,
        )
    val sectionScroll = remember(scrollState) { SettingSectionScroll(scrollState) }
    val inputMode = LocalInputMode.current

    DisposableEffect(focusRestorer, sectionScroll) {
        focusRestorer?.section = sectionScroll
        onDispose {
            if (focusRestorer?.section === sectionScroll) {
                focusRestorer.section = null
            }
        }
    }

    LaunchedEffect(scrollState, inputMode) {
        snapshotFlow { scrollState.value }
            .drop(1)
            .collect {
                focusRestorer?.rememberScrollOffset(it)
                if (inputMode == InputMode.Touch) {
                    sectionScroll.pendingViewportSnap = true
                }
            }
    }

    LaunchedEffect(scrollState, inputMode, sectionScroll) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && inputMode == InputMode.Touch && sectionScroll.pendingViewportSnap) {
                    sectionScroll.pendingViewportSnap = false
                    sectionScroll.focusTopmostVisible()
                }
            }
    }

    CompositionLocalProvider(LocalSettingSectionScroll provides sectionScroll) {
        Column(
            modifier =
                modifier
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { sectionScroll.containerCoordinates = it }
                    .onPreviewKeyEvent { event ->
                        if (!sectionScroll.pendingViewportSnap) return@onPreviewKeyEvent false
                        val isDpad =
                            GamepadKeys.isUp(event.type, event.key) ||
                                GamepadKeys.isDown(event.type, event.key) ||
                                GamepadKeys.isLeft(event.type, event.key) ||
                                GamepadKeys.isRight(event.type, event.key)
                        if (!isDpad) return@onPreviewKeyEvent false
                        // Land on the top in-view row only — next press moves.
                        sectionScroll.pendingViewportSnap = false
                        sectionScroll.focusTopmostVisible()
                        true
                    },
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * Registers this focus target with [LocalSettingSectionScroll] so touch-scroll
 * snap can find the topmost visible row. No-op outside a settings section.
 */
@Composable
fun Modifier.reportSectionVisibleFocus(
    requester: FocusRequester,
    id: Any = remember { Any() },
): Modifier {
    val section = LocalSettingSectionScroll.current ?: return this
    DisposableEffect(section, id) {
        onDispose { section.removeFocusEntry(id) }
    }
    return onGloballyPositioned { coords ->
        section.updateFocusEntry(id, requester, coords.boundsInRoot())
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
