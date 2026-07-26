package com.wajiha.ui.home.hero

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.wajiha.data.db.GameEntity
import com.wajiha.data.prefs.HeroDisplaySlot
import com.wajiha.data.prefs.HeroElementId
import com.wajiha.data.prefs.HeroElementLayout
import com.wajiha.data.prefs.HeroLayout
import com.wajiha.data.prefs.HeroLayoutPresets
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.state.DualScreenStore
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.WajihaActionSetting
import com.wajiha.ui.components.gamepad.WajihaNumberSetting
import com.wajiha.ui.components.gamepad.WajihaSettingDivider
import com.wajiha.ui.components.gamepad.WajihaSettingGroup
import com.wajiha.ui.components.gamepad.WajihaToggleSetting
import com.wajiha.ui.components.softOutlineBorder
import com.wajiha.ui.home.GameTile
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** Hero display aspect — keeps the embedded single-screen preview truthful. */
const val HeroPreviewAspect = 16f / 9f

private const val EditorNudgeStep = 0.02f
private const val EditorScaleMin = 8
private const val EditorScaleMax = 95

private fun sampleTile(): GameTile =
    GameTile(
        game =
            GameEntity(
                id = -1,
                uri = "sample://game",
                platformId = "sample",
                displayName = "Sample Game",
                sortName = "sample game",
                fileName = "sample.rom",
                description = "Customize hero layout. Drag elements or use the gamepad.",
                playCount = 3,
            ),
    )

/** Elements the editor can select / nudge (backdrop is full-bleed). */
internal fun heroLayoutEditableIds(draft: HeroLayout): List<HeroElementId> =
    draft.elements
        .asSequence()
        .map { it.id }
        .filter { it != HeroElementId.Backdrop && !it.detailOnly }
        .toList()

internal fun HeroElementId.editorLabel(): String =
    when (this) {
        HeroElementId.Backdrop -> "Backdrop"
        HeroElementId.Cover -> "Cover"
        HeroElementId.Logo -> "Logo"
        HeroElementId.PlatformIcon -> "Platform icon"
        HeroElementId.Platform -> "Platform name"
        HeroElementId.Title -> "Title"
        HeroElementId.Metadata -> "Metadata"
        HeroElementId.Description -> "Description"
        HeroElementId.PlayStats -> "Play stats"
        HeroElementId.Favorite -> "Favorite"
        HeroElementId.SectionHint -> "Section hint"
    }

/** Hints while the pad drives the hero canvas (dual display, hero owns keys). */
internal fun heroLayoutEditorCanvasHints(): List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.DpadLeftRight, "Move"),
        GamepadHint(GamepadHintButton.L1R1, "Cycle"),
        GamepadHint(GamepadHintButton.X, "Hide"),
        GamepadHint(GamepadHintButton.Y, "Classic"),
        GamepadHint(GamepadHintButton.B, "Done"),
    )

/**
 * Hints while the pad drives the Settings controls (single screen, or dual after
 * L2). Canvas keys are not routed here, so only row semantics are advertised.
 */
internal fun heroLayoutEditorControlsHints(): List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Select"),
        GamepadHint(GamepadHintButton.DpadLeftRight, "Adjust"),
        GamepadHint(GamepadHintButton.Y, "Reset"),
        GamepadHint(GamepadHintButton.B, "Back"),
    )

private fun defaultWidth(id: HeroElementId): Float =
    when (id) {
        HeroElementId.Cover -> 0.28f
        HeroElementId.Logo -> 0.30f
        HeroElementId.PlatformIcon -> 0.06f
        HeroElementId.Title -> 0.50f
        else -> 0.40f
    }

private fun defaultHeight(id: HeroElementId): Float =
    when (id) {
        HeroElementId.Cover -> 0.70f
        HeroElementId.Logo -> 0.16f
        HeroElementId.PlatformIcon -> 0.08f
        HeroElementId.Title -> 0.12f
        HeroElementId.Description -> 0.18f
        else -> 0.08f
    }

private fun resolvedSize(el: HeroElementLayout): Pair<Float, Float> {
    val w = if (el.w > 0f) el.w else defaultWidth(el.id)
    val h = if (el.h > 0f) el.h else defaultHeight(el.id)
    return w to h
}

/** Average axis as 8..95 percent for the scale stepper. */
private fun scalePercent(el: HeroElementLayout): Int {
    val (w, h) = resolvedSize(el)
    return (((w + h) * 0.5f) * 100f).roundToInt().coerceIn(EditorScaleMin, EditorScaleMax)
}

private fun withScalePercent(
    el: HeroElementLayout,
    percent: Int,
): HeroElementLayout {
    val target = percent.coerceIn(EditorScaleMin, EditorScaleMax) / 100f
    val (w0, h0) = resolvedSize(el)
    val current = ((w0 + h0) * 0.5f).coerceAtLeast(0.01f)
    val factor = target / current
    return el.copy(
        w = (w0 * factor).coerceIn(0.08f, 0.95f),
        h = (h0 * factor).coerceIn(0.06f, 0.95f),
    )
}

private fun nudgeElement(
    el: HeroElementLayout,
    dx: Float = 0f,
    dy: Float = 0f,
): HeroElementLayout {
    if (el.id == HeroElementId.Backdrop) return el
    return el.copy(
        x = (el.x + dx).coerceIn(0f, 0.95f),
        y = (el.y + dy).coerceIn(0f, 0.95f),
    )
}

/**
 * Topmost visible editable element under [point], or null. [rtl] mirrors the
 * start-edge x the same way [HeroCanvas] paints it.
 */
private fun hitTestElement(
    point: Offset,
    canvas: IntSize,
    draft: HeroLayout,
    rtl: Boolean,
): HeroElementId? {
    if (canvas.width <= 0 || canvas.height <= 0) return null
    val ids = heroLayoutEditableIds(draft).asReversed()
    for (id in ids) {
        val el = draft.element(id) ?: continue
        if (!el.visible) continue
        val (wFrac, hFrac) = resolvedSize(el)
        val startFrac = if (rtl) 1f - el.x - wFrac else el.x
        val left = startFrac * canvas.width
        val top = el.y * canvas.height
        val right = left + wFrac * canvas.width
        val bottom = top + hFrac * canvas.height
        if (point.x in left..right && point.y in top..bottom) return id
    }
    return null
}

@Composable
fun HeroLayoutEditorCanvasFromStore(
    tile: GameTile?,
    platformName: String?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
) {
    val dualStore = koinInject<DualScreenStore>()
    val draft by dualStore.heroLayoutEditDraft.collectAsState()
    val selected by dualStore.heroLayoutEditSelected.collectAsState()
    val slot by dualStore.heroLayoutEditSlot.collectAsState()
    val layout = draft ?: return
    HeroLayoutEditorCanvas(
        tile = tile,
        platformName = platformName,
        slot = slot,
        draft = layout,
        selectedId = selected,
        onDraftChange = dualStore::updateHeroLayoutEditDraft,
        onSelect = dualStore::setHeroLayoutEditSelected,
        onExit = onExit,
        modifier = modifier,
        contentFocusRequester = contentFocusRequester,
    )
}

@Composable
fun HeroLayoutEditorCanvas(
    tile: GameTile?,
    platformName: String?,
    slot: HeroDisplaySlot,
    draft: HeroLayout,
    selectedId: HeroElementId,
    onDraftChange: (HeroLayout) -> Unit,
    onSelect: (HeroElementId) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
) {
    val previewTile = tile ?: sampleTile()
    val localFocus = remember { FocusRequester() }
    val focusRequester = contentFocusRequester ?: localFocus
    val dualStore = koinInject<DualScreenStore>()
    val gamepadOwner by dualStore.gamepadOwner.collectAsState()
    val focusEpoch by dualStore.gamepadFocusEpoch.collectAsState()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val selected = draft.element(selectedId)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Pointer handlers read the newest draft without restarting: re-keying
    // pointerInput on draft would cancel the in-flight drag on every nudge.
    val latestDraft by rememberUpdatedState(draft)
    val latestSelectedId by rememberUpdatedState(selectedId)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestDraftChange by rememberUpdatedState(onDraftChange)

    LaunchedEffect(focusEpoch, gamepadOwner) {
        if (gamepadOwner != dualStore.heroGamepadOwner()) return@LaunchedEffect
        withFrameNanos { }
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .focusRequester(focusRequester)
                .focusable()
                .onSizeChanged { canvasSize = it }
                .pointerInput(rtl) {
                    detectTapGestures { offset ->
                        val hit =
                            hitTestElement(
                                point = offset,
                                canvas = IntSize(size.width, size.height),
                                draft = latestDraft,
                                rtl = rtl,
                            )
                        if (hit != null) latestSelect(hit)
                    }
                }.pointerInput(rtl) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Dragging an unselected element picks it up first.
                            hitTestElement(
                                point = offset,
                                canvas = IntSize(size.width, size.height),
                                draft = latestDraft,
                                rtl = rtl,
                            )?.let(latestSelect)
                        },
                    ) { change, drag ->
                        change.consume()
                        val current = latestDraft
                        val el = current.element(latestSelectedId) ?: return@detectDragGestures
                        if (el.id == HeroElementId.Backdrop) return@detectDragGestures
                        if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                        // Start-edge x is mirrored under RTL, so drag right moves start back.
                        val dx = (drag.x / size.width).let { if (rtl) -it else it }
                        latestDraftChange(
                            current.withElement(
                                nudgeElement(
                                    el,
                                    dx = dx,
                                    dy = drag.y / size.height,
                                ),
                            ),
                        )
                    }
                }.onPreviewKeyEvent { event ->
                    handleHeroLayoutEditorKey(
                        eventType = event.type,
                        key = event.key,
                        draft = draft,
                        selectedId = selectedId,
                        onSelect = onSelect,
                        onUpdate = onDraftChange,
                        onExit = onExit,
                    )
                },
    ) {
        HeroCanvas(
            tile = previewTile,
            platformName = platformName ?: "Platform",
            layout = draft,
            displaySlot = slot,
            playCoverVideo = false,
            detailContext = false,
            selectedElementId = selectedId,
            dimFreeform = true,
            modifier = Modifier.fillMaxSize(),
        )

        // Ghost frame when the selected element is hidden.
        if (
            selected != null &&
            !selected.visible &&
            selected.id != HeroElementId.Backdrop &&
            canvasSize.width > 0
        ) {
            val (wFrac, hFrac) = resolvedSize(selected)
            val startFrac = if (rtl) 1f - selected.x - wFrac else selected.x
            val leftPx = startFrac * canvasSize.width
            val topPx = selected.y * canvasSize.height
            val widthPx = wFrac * canvasSize.width
            val heightPx = hFrac * canvasSize.height
            Box(
                modifier =
                    Modifier
                        .offset {
                            IntOffset(leftPx.roundToInt(), topPx.roundToInt())
                        }.size(
                            width = with(density) { widthPx.toDp() },
                            height = with(density) { heightPx.toDp() },
                        ).border(
                            width = WajihaSpacing.folderEdge,
                            color = WajihaColors.OverrideAmber.copy(alpha = WajihaAlphas.overrideAmberStrong),
                            shape = WajihaShapes.tile,
                        ),
            )
        }

        EditorCanvasHud(
            presetId = draft.presetId,
            selectedId = selectedId,
            selectedVisible = selected?.visible != false,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(WajihaSpacing.sm),
        )
    }
}

@Composable
private fun EditorCanvasHud(
    presetId: String,
    selectedId: HeroElementId,
    selectedVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .clip(WajihaShapes.chip)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
                .border(softOutlineBorder(), WajihaShapes.chip)
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = HeroLayoutPresets.label(presetId),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            maxLines = 1,
        )
        Text(
            text = "·",
            color = scheme.onSurfaceVariant.copy(alpha = WajihaAlphas.outlineMuted),
        )
        Text(
            text =
                buildString {
                    append(selectedId.editorLabel())
                    if (!selectedVisible) append(" · hidden")
                },
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroLayoutEditorControls(
    draft: HeroLayout,
    selectedId: HeroElementId,
    onSelect: (HeroElementId) -> Unit,
    onUpdate: (HeroLayout) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    dualDisplay: Boolean = true,
    firstFocusRequester: FocusRequester? = null,
) {
    val elements = heroLayoutEditableIds(draft)
    val selected = draft.element(selectedId)
    val presetIds =
        (
            HeroLayoutPresets.namedIds +
                listOfNotNull(
                    HeroLayoutPresets.CUSTOM.takeIf { draft.presetId == HeroLayoutPresets.CUSTOM },
                )
        ).distinct()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = WajihaSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        WajihaSettingGroup(
            title = "Presets",
            description =
                if (dualDisplay) {
                    "Tap a look, or edit live on the hero. L2 Focus flips the gamepad."
                } else {
                    "Tap a look, then refine the selected element below."
                },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = WajihaSpacing.xs),
            ) {
                presetIds.forEachIndexed { index, id ->
                    GamepadChip(
                        label = HeroLayoutPresets.label(id),
                        selected = draft.presetId == id,
                        onClick = {
                            if (id != HeroLayoutPresets.CUSTOM) {
                                onUpdate(HeroLayoutPresets.template(id).copy(configured = true))
                            }
                        },
                        focusRequester = firstFocusRequester.takeIf { index == 0 },
                        gamepadFocusable = id != HeroLayoutPresets.CUSTOM,
                    )
                }
            }
        }

        WajihaSettingGroup(
            title = "Elements",
            description =
                if (dualDisplay) {
                    "Pick what to move. On the hero: tap, drag, D-pad, or L1/R1."
                } else {
                    "Pick what to move, then tap or drag it in the preview."
                },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = WajihaSpacing.xs),
            ) {
                elements.forEach { id ->
                    val el = draft.element(id)
                    val isSelected = id == selectedId
                    val hidden = el?.visible == false
                    GamepadChip(
                        label =
                            buildString {
                                append(id.editorLabel())
                                if (hidden) append(" · off")
                            },
                        selected = isSelected,
                        onClick = { onSelect(id) },
                    )
                }
            }
        }

        if (selected != null && selected.id != HeroElementId.Backdrop) {
            WajihaSettingGroup(
                title = selected.id.editorLabel(),
                description = "Size and nudge work by touch or gamepad.",
            ) {
                WajihaToggleSetting(
                    label = "Visible",
                    description =
                        if (dualDisplay) {
                            "Gamepad X also toggles on the hero."
                        } else {
                            "Hidden elements keep an amber frame in the preview."
                        },
                    checked = selected.visible,
                    onCheckedChange = {
                        onUpdate(draft.withElement(selected.copy(visible = it)))
                    },
                    defaultChecked = true,
                    onReset = {
                        onUpdate(draft.withElement(selected.copy(visible = true)))
                    },
                )
                WajihaSettingDivider()
                val classicElement = remember(selected.id) { HeroLayoutPresets.classic().element(selected.id) }
                WajihaNumberSetting(
                    label = "Size",
                    description = "Scales width and height together.",
                    value = scalePercent(selected),
                    onValueChange = { pct ->
                        onUpdate(draft.withElement(withScalePercent(selected, pct)))
                    },
                    range = EditorScaleMin..EditorScaleMax,
                    step = 2,
                    defaultValue = classicElement?.let { scalePercent(it) } ?: scalePercent(selected),
                    onReset = {
                        classicElement?.let {
                            onUpdate(draft.withElement(selected.copy(w = it.w, h = it.h)))
                        }
                    },
                    valueLabel = { "$it%" },
                )
                WajihaSettingDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = WajihaSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Nudge",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    NudgeButton("←") {
                        onUpdate(draft.withElement(nudgeElement(selected, dx = -EditorNudgeStep)))
                    }
                    NudgeButton("↑") {
                        onUpdate(draft.withElement(nudgeElement(selected, dy = -EditorNudgeStep)))
                    }
                    NudgeButton("↓") {
                        onUpdate(draft.withElement(nudgeElement(selected, dy = EditorNudgeStep)))
                    }
                    NudgeButton("→") {
                        onUpdate(draft.withElement(nudgeElement(selected, dx = EditorNudgeStep)))
                    }
                }
                Text(
                    text =
                        "Position " + (selected.x * 100f).roundToInt() + "% · " +
                            (selected.y * 100f).roundToInt() + "%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = WajihaSpacing.xs),
                )
                if (selectedId == HeroElementId.Cover) {
                    WajihaSettingDivider()
                    WajihaToggleSetting(
                        label = "Cover border",
                        description = "Soft outline around cover art.",
                        checked = draft.coverBorder,
                        onCheckedChange = {
                            onUpdate(
                                draft.copy(
                                    coverBorder = it,
                                    presetId = HeroLayoutPresets.CUSTOM,
                                    configured = true,
                                ),
                            )
                        },
                        defaultChecked = false,
                        onReset = {
                            onUpdate(
                                draft.copy(
                                    coverBorder = false,
                                    presetId = HeroLayoutPresets.CUSTOM,
                                    configured = true,
                                ),
                            )
                        },
                    )
                }
            }
        }

        WajihaSettingGroup(title = "Finish") {
            WajihaActionSetting(
                label = "Reset to Classic",
                actionLabel = "Classic",
                description =
                    if (dualDisplay) {
                        "Gamepad Y on the hero also restores Classic."
                    } else {
                        "Restores every element to the Classic preset."
                    },
                onClick = {
                    onUpdate(HeroLayoutPresets.classic(configured = true))
                },
            )
            WajihaSettingDivider()
            GamepadButton(
                text = "Done",
                onClick = onExit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = WajihaSpacing.xs),
            )
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    onClick: () -> Unit,
) {
    GamepadButton(
        text = label,
        onClick = onClick,
        outlined = true,
        sound = null,
        modifier = Modifier.widthIn(min = WajihaSpacing.touchMin),
    )
}

@Composable
fun HeroLayoutEditorControlsFromStore(
    onSaveAndExit: (HeroLayout) -> Unit,
    modifier: Modifier = Modifier,
    dualDisplay: Boolean = true,
    firstFocusRequester: FocusRequester? = null,
) {
    val dualStore = koinInject<DualScreenStore>()
    val draft by dualStore.heroLayoutEditDraft.collectAsState()
    val selected by dualStore.heroLayoutEditSelected.collectAsState()
    val layout = draft ?: return
    val latestLayout = rememberUpdatedState(layout)
    val latestSave = rememberUpdatedState(onSaveAndExit)
    DisposableEffect(Unit) {
        onDispose {
            latestLayout.value.let { latestSave.value(it) }
        }
    }
    HeroLayoutEditorControls(
        draft = layout,
        selectedId = selected,
        onSelect = dualStore::setHeroLayoutEditSelected,
        onUpdate = dualStore::updateHeroLayoutEditDraft,
        onExit = {
            onSaveAndExit(layout)
            dualStore.setHeroLayoutEditing(false)
            dualStore.clearHeroLayoutEditDraft()
        },
        modifier = modifier,
        dualDisplay = dualDisplay,
        firstFocusRequester = firstFocusRequester,
    )
}

/**
 * Shared gamepad handling for the hero layout editor (canvas and Settings host).
 * Returns true when the event was consumed.
 *
 * L2/R2 stay global (focus switch / notifications) and are never claimed here.
 */
fun handleHeroLayoutEditorKey(
    eventType: KeyEventType,
    key: Key,
    draft: HeroLayout,
    selectedId: HeroElementId,
    onSelect: (HeroElementId) -> Unit,
    onUpdate: (HeroLayout) -> Unit,
    onExit: () -> Unit,
): Boolean {
    val cycle = heroLayoutEditableIds(draft)
    if (cycle.isEmpty()) return false
    val idx = cycle.indexOf(selectedId).let { if (it < 0) 0 else it }
    val el = draft.element(selectedId) ?: draft.element(cycle[idx]) ?: return false
    return when {
        GamepadKeys.isBack(eventType, key) -> {
            onExit()
            true
        }

        GamepadKeys.isX(eventType, key) -> {
            onUpdate(draft.withElement(el.copy(visible = !el.visible)))
            true
        }

        GamepadKeys.isY(eventType, key) -> {
            onUpdate(HeroLayoutPresets.classic(configured = true))
            true
        }

        GamepadKeys.isL1(eventType, key) -> {
            onSelect(cycle[(idx - 1 + cycle.size) % cycle.size])
            true
        }

        GamepadKeys.isR1(eventType, key) -> {
            onSelect(cycle[(idx + 1) % cycle.size])
            true
        }

        GamepadKeys.isLeft(eventType, key) -> {
            if (el.id == HeroElementId.Backdrop) return true
            onUpdate(draft.withElement(nudgeElement(el, dx = -EditorNudgeStep)))
            true
        }

        GamepadKeys.isRight(eventType, key) -> {
            if (el.id == HeroElementId.Backdrop) return true
            onUpdate(draft.withElement(nudgeElement(el, dx = EditorNudgeStep)))
            true
        }

        GamepadKeys.isUp(eventType, key) -> {
            if (el.id == HeroElementId.Backdrop) return true
            onUpdate(draft.withElement(nudgeElement(el, dy = -EditorNudgeStep)))
            true
        }

        GamepadKeys.isDown(eventType, key) -> {
            if (el.id == HeroElementId.Backdrop) return true
            onUpdate(draft.withElement(nudgeElement(el, dy = EditorNudgeStep)))
            true
        }

        else -> {
            false
        }
    }
}
