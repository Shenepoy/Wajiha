package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.wajiha.input.rememberFocusContext
import com.wajiha.input.rememberedFocusContext
import com.wajiha.ui.theme.FocusBorderStyle
import com.wajiha.ui.theme.LocalFocusIndicatorStyle
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Scroll container for grouped settings cards. Provided via [LocalSettingSectionScroll]
 * so [MultiChoiceSettingRow] can scroll the row header to the top on expand and shared
 * focus chrome can resolve the clipped settings viewport.
 */
class SettingSectionScroll(
    val scrollState: ScrollState,
    private val focusClearancePx: Float,
) {
    private var viewportBoundsInRoot by mutableStateOf<Rect?>(null)
    private val focusEntries = mutableMapOf<Any, FocusEntry>()

    fun updateFocusEntry(
        id: Any,
        requester: FocusRequester,
        boundsInRoot: Rect,
        currentBoundsInRoot: () -> Rect?,
    ) {
        focusEntries[id] = FocusEntry(requester, boundsInRoot, currentBoundsInRoot)
    }

    fun removeFocusEntry(id: Any) {
        focusEntries.remove(id)
    }

    internal fun updateViewportBounds(boundsInRoot: Rect) {
        viewportBoundsInRoot = boundsInRoot
    }

    fun viewportBoundsInRoot(): Rect? = viewportBoundsInRoot

    suspend fun restoreFocus(id: Any): Boolean {
        val entry = focusEntries[id] ?: return false
        val container = viewportBoundsInRoot ?: return false
        val bounds = entry.currentBounds()
        if (!hasCenterInViewportVertically(bounds, container)) {
            val targetY =
                (scrollState.value + bounds.top - container.top)
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

    suspend fun ensureFocusVisible(id: Any) {
        withFrameNanos { }
        withFrameNanos { }

        val entry = focusEntries[id] ?: return
        val container = viewportBoundsInRoot ?: return
        val bounds = entry.currentBounds()
        val bottomOverflow = bounds.bottom + focusClearancePx - container.bottom
        val topOverflow = bounds.top - focusClearancePx - container.top
        val delta =
            when {
                bottomOverflow > 0f -> bottomOverflow
                topOverflow < 0f -> topOverflow
                else -> return
            }
        scrollState.scrollBy(delta)
        withFrameNanos { }
    }

    private data class FocusEntry(
        val requester: FocusRequester,
        val bounds: Rect,
        val currentBoundsInRoot: () -> Rect?,
    ) {
        fun currentBounds(): Rect = currentBoundsInRoot() ?: bounds
    }
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
    val focusStyle = LocalFocusIndicatorStyle.current
    val focusClearancePx =
        with(androidx.compose.ui.platform.LocalDensity.current) {
            val thickness = focusStyle.thickness.toPx()
            val stylePad =
                when (focusStyle.borderStyle) {
                    FocusBorderStyle.Glow,
                    FocusBorderStyle.Neon,
                    FocusBorderStyle.Aura,
                    FocusBorderStyle.SoftPulse,
                    FocusBorderStyle.GradientPulse,
                    -> thickness * 6f

                    FocusBorderStyle.Double -> thickness * 3f

                    else -> thickness
                }
            // Clearance for focus chrome + a bit of breathing room at the viewport edge.
            stylePad + WajihaSpacing.md.toPx()
        }
    val sectionScroll =
        remember(scrollState, focusClearancePx) {
            SettingSectionScroll(scrollState, focusClearancePx)
        }
    DisposableEffect(focusRestorer, sectionScroll) {
        focusRestorer?.section = sectionScroll
        onDispose {
            if (focusRestorer?.section === sectionScroll) {
                focusRestorer.section = null
            }
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .drop(1)
            .collect { focusRestorer?.rememberScrollOffset(it) }
    }

    CompositionLocalProvider(LocalSettingSectionScroll provides sectionScroll) {
        Box(
            modifier =
                modifier
                    .onGloballyPositioned {
                        sectionScroll.updateViewportBounds(it.boundsInRoot())
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        // Extra bottom so D-pad can bring the last row fully into view.
                        .padding(bottom = WajihaSpacing.xl + WajihaSpacing.md),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

/**
 * Registers this focus target with [LocalSettingSectionScroll] for deliberate focus-gain
 * visibility and focus restoration. No-op outside a settings section.
 */
@Composable
fun Modifier.reportSectionVisibleFocus(
    requester: FocusRequester,
    id: Any = remember { Any() },
    currentBoundsInRoot: (() -> Rect?)? = null,
): Modifier {
    val section = LocalSettingSectionScroll.current ?: return this
    val scope = rememberCoroutineScope()
    var focused by remember(section, id) { mutableStateOf(false) }
    var visibilityJob by remember(section, id) { mutableStateOf<Job?>(null) }
    DisposableEffect(section, id) {
        onDispose {
            visibilityJob?.cancel()
            section.removeFocusEntry(id)
        }
    }
    return onGloballyPositioned { coords ->
        section.updateFocusEntry(
            id = id,
            requester = requester,
            boundsInRoot = currentBoundsInRoot?.invoke() ?: coords.unclippedBoundsInRoot(),
            currentBoundsInRoot = currentBoundsInRoot ?: { coords.unclippedBoundsInRoot() },
        )
    }.onFocusChanged { state ->
        val gainedFocus = state.isFocused && !focused
        focused = state.isFocused
        if (gainedFocus) {
            visibilityJob?.cancel()
            visibilityJob = scope.launch { section.ensureFocusVisible(id) }
        }
    }
}

suspend fun SettingSectionScroll.scrollHeaderToTop(headerCoordinates: LayoutCoordinates) {
    withFrameNanos { }
    withFrameNanos { }
    val viewport = viewportBoundsInRoot() ?: return
    val headerOffsetFromViewportTop =
        headerCoordinates.unclippedBoundsInRoot().top - viewport.top
    val targetY = (scrollState.value + headerOffsetFromViewportTop).roundToInt().coerceAtLeast(0)
    scrollState.scrollTo(targetY)
}
