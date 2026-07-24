package com.wajiha.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadHintButton
import com.wajiha.platform.LaunchableApp
import com.wajiha.ui.components.gamepad.GamepadHintGlyph
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.theme.AppIconShapes
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaSpacing

/** Total chrome height for home dock (icons + vertical padding). */
val WajihaDockHeight: Dp = WajihaSpacing.touchMin + WajihaSpacing.sm * 2

private val WajihaDockIconSize: Dp = 40.dp
private val WajihaDockSlotGap: Dp = WajihaSpacing.sm
private val DockEdgeFadeWidth: Dp = WajihaSpacing.lg
private val DockShellGlyphSize: Dp = 14.dp

enum class DockShellAction {
    Apps,
    Settings,
}

sealed class DockSlot {
    data class Pin(
        val app: LaunchableApp,
    ) : DockSlot()

    data object Add : DockSlot()

    data class Shell(
        val action: DockShellAction,
    ) : DockSlot()
}

/** Leading Apps shell index in [buildDockSlots]. */
const val DockAppsSlotIndex: Int = 0

/**
 * Persistent quick-launch strip: Apps (start) · pins · Settings (end).
 * System lives in the top-right header chrome. Focus selection is owned by
 * the parent GameGrid; this paints chrome and exposes per-slot [FocusRequester]s.
 */
@Composable
fun WajihaDock(
    pins: List<LaunchableApp>,
    selectedIndex: Int,
    dockFocused: Boolean,
    iconShape: String,
    onSelectIndex: (Int) -> Unit,
    onActivateIndex: (Int) -> Unit,
    onLongPressIndex: (Int) -> Unit,
    focusRequesters: Map<Int, FocusRequester>,
    onSlotBoundsChanged: (Int, Rect) -> Unit = { _, _ -> },
    /**
     * When false, keep selection chrome / scroll but do not steal Compose focus
     * (e.g. while a dock pin context menu owns the focus layer).
     */
    claimFocus: Boolean = true,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val slots = remember(pins) { buildDockSlots(pins = pins) }
    val pinSlots = remember(slots) { slots.filter { it is DockSlot.Pin || it is DockSlot.Add } }
    val pinStartIndex = 1
    val iconClip = AppIconShapes.forPreference(iconShape)
    val scheme = MaterialTheme.colorScheme
    val edgeColor = scheme.surfaceContainerHigh
    val mountAlpha = remember { Animatable(0f) }
    val mountOffsetY = remember { Animatable(10f) }

    LaunchedEffect(Unit) {
        mountAlpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        mountOffsetY.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(selectedIndex, dockFocused, claimFocus, pinSlots.size) {
        if (!dockFocused) return@LaunchedEffect
        val pinLocal = selectedIndex - pinStartIndex
        if (pinLocal !in pinSlots.indices) return@LaunchedEffect
        runCatching { listState.animateScrollToItem(pinLocal) }
        if (claimFocus) {
            runCatching { focusRequesters[selectedIndex]?.requestFocus() }
        }
    }

    LaunchedEffect(selectedIndex, dockFocused, claimFocus, slots.size) {
        if (!dockFocused || !claimFocus) return@LaunchedEffect
        val pinLocal = selectedIndex - pinStartIndex
        if (pinLocal in pinSlots.indices) return@LaunchedEffect
        runCatching { focusRequesters[selectedIndex]?.requestFocus() }
    }

    val showStartFade by remember {
        derivedStateOf { listState.canScrollBackward }
    }
    val showEndFade by remember {
        derivedStateOf { listState.canScrollForward }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(WajihaDockHeight)
                .graphicsLayer {
                    alpha = mountAlpha.value
                    translationY = mountOffsetY.value
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    scheme.surface.copy(alpha = 0f),
                                    scheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    scheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(WajihaSpacing.folderEdge)
                    .align(Alignment.TopCenter)
                    .background(scheme.outline.copy(alpha = WajihaAlphas.outlineSubtle)),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = WajihaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockSlotTile(
                slot = DockSlot.Shell(DockShellAction.Apps),
                selected = dockFocused && selectedIndex == DockAppsSlotIndex,
                iconClip = iconClip,
                focusRequester = focusRequesters[DockAppsSlotIndex],
                gamepadFocusable = dockFocused && claimFocus,
                shortcutGlyph = GamepadHintButton.L3,
                onSelect = { onSelectIndex(DockAppsSlotIndex) },
                onActivate = { onActivateIndex(DockAppsSlotIndex) },
                onLongPress = { onLongPressIndex(DockAppsSlotIndex) },
                onBoundsChanged = { onSlotBoundsChanged(DockAppsSlotIndex, it) },
            )
            VerticalDivider(
                modifier =
                    Modifier
                        .fillMaxHeight(0.55f)
                        .padding(horizontal = WajihaSpacing.sm),
                color = scheme.outline.copy(alpha = WajihaAlphas.outlineSubtle),
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawWithContent {
                            drawContent()
                            val fadeWidth = DockEdgeFadeWidth.toPx().coerceAtMost(size.width / 3f)
                            if (showStartFade) {
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors = listOf(edgeColor, Color.Transparent),
                                            startX = 0f,
                                            endX = fadeWidth,
                                        ),
                                    size = Size(fadeWidth, size.height),
                                )
                            }
                            if (showEndFade) {
                                val fadeStart = size.width - fadeWidth
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, edgeColor),
                                            startX = fadeStart,
                                            endX = size.width,
                                        ),
                                    topLeft = Offset(fadeStart, 0f),
                                    size = Size(fadeWidth, size.height),
                                )
                            }
                        },
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WajihaDockSlotGap),
                    contentPadding = PaddingValues(horizontal = WajihaSpacing.xs),
                ) {
                    itemsIndexed(
                        items = pinSlots,
                        key = { _, slot -> dockSlotKey(slot) },
                    ) { pinLocal, slot ->
                        val index = pinStartIndex + pinLocal
                        DockSlotTile(
                            slot = slot,
                            selected = dockFocused && index == selectedIndex,
                            iconClip = iconClip,
                            focusRequester = focusRequesters[index],
                            gamepadFocusable = dockFocused && claimFocus,
                            onSelect = { onSelectIndex(index) },
                            onActivate = { onActivateIndex(index) },
                            onLongPress = { onLongPressIndex(index) },
                            onBoundsChanged = { onSlotBoundsChanged(index, it) },
                        )
                    }
                }
            }
            VerticalDivider(
                modifier =
                    Modifier
                        .fillMaxHeight(0.55f)
                        .padding(horizontal = WajihaSpacing.sm),
                color = scheme.outline.copy(alpha = WajihaAlphas.outlineSubtle),
            )
            val settingsIndex = slots.lastIndex
            DockSlotTile(
                slot = DockSlot.Shell(DockShellAction.Settings),
                selected = dockFocused && selectedIndex == settingsIndex,
                iconClip = iconClip,
                focusRequester = focusRequesters[settingsIndex],
                gamepadFocusable = dockFocused && claimFocus,
                shortcutGlyph = GamepadHintButton.Start,
                onSelect = { onSelectIndex(settingsIndex) },
                onActivate = { onActivateIndex(settingsIndex) },
                onLongPress = { onLongPressIndex(settingsIndex) },
                onBoundsChanged = { onSlotBoundsChanged(settingsIndex, it) },
            )
        }
    }
}

/**
 * Focus order left→right: Apps · pins/Add · Settings.
 * System is not a dock slot — it stays in top-right header chrome.
 */
fun buildDockSlots(pins: List<LaunchableApp>): List<DockSlot> =
    buildList {
        add(DockSlot.Shell(DockShellAction.Apps))
        if (pins.isEmpty()) {
            add(DockSlot.Add)
        } else {
            pins.forEach { add(DockSlot.Pin(it)) }
        }
        add(DockSlot.Shell(DockShellAction.Settings))
    }

/** First pin/Add index in [buildDockSlots]. */
fun dockPinStartIndex(): Int = 1

/** Number of pin/Add slots for a pin list. */
fun dockPinSlotCount(pins: List<LaunchableApp>): Int = if (pins.isEmpty()) 1 else pins.size

private fun dockSlotKey(slot: DockSlot): Any =
    when (slot) {
        is DockSlot.Pin -> "pin:${slot.app.packageName}"
        DockSlot.Add -> "add"
        is DockSlot.Shell -> "shell:${slot.action}"
    }

@Composable
private fun DockSlotTile(
    slot: DockSlot,
    selected: Boolean,
    iconClip: Shape,
    focusRequester: FocusRequester?,
    gamepadFocusable: Boolean,
    onSelect: () -> Unit,
    onActivate: () -> Unit,
    onLongPress: () -> Unit,
    onBoundsChanged: (Rect) -> Unit = {},
    shortcutGlyph: GamepadHintButton? = null,
) {
    val focusId =
        when (slot) {
            is DockSlot.Pin -> "dock:pin:${slot.app.packageName}"
            DockSlot.Add -> "dock:add"
            is DockSlot.Shell -> "dock:shell:${slot.action}"
        }
    GamepadTile(
        selected = selected,
        onSelect = onSelect,
        onLaunch = onActivate,
        onLongPress = onLongPress,
        focusRequester = focusRequester,
        focusId = focusId,
        selectOnFocus = true,
        // Only join Compose focus search while the dock band owns navigation —
        // otherwise Down from the bottom game row can spatially leap into a slot.
        gamepadFocusable = gamepadFocusable,
        modifier =
            Modifier
                .size(WajihaSpacing.touchMin)
                .onGloballyPositioned { coords ->
                    onBoundsChanged(coords.boundsInRoot())
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(WajihaSpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            when (slot) {
                is DockSlot.Pin -> {
                    Box(
                        modifier =
                            Modifier
                                .size(WajihaDockIconSize)
                                .clip(iconClip)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val icon = slot.app.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = slot.app.label,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text =
                                    slot.app.label
                                        .take(1)
                                        .uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                DockSlot.Add -> {
                    Box(
                        modifier =
                            Modifier
                                .size(WajihaDockIconSize)
                                .clip(iconClip)
                                .border(softOutlineBorder(), iconClip)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add apps",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                is DockSlot.Shell -> {
                    val (vector, label) = shellIcon(slot.action)
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier
                                    .size(WajihaDockIconSize)
                                    .align(Alignment.Center)
                                    .clip(iconClip)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (shortcutGlyph != null) {
                            GamepadHintGlyph(
                                button = shortcutGlyph,
                                size = DockShellGlyphSize,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 0.dp, end = 0.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shellIcon(action: DockShellAction): Pair<ImageVector, String> =
    when (action) {
        DockShellAction.Apps -> Icons.Filled.Menu to "Apps"
        DockShellAction.Settings -> Icons.Filled.Settings to "Settings"
    }
