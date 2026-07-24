package com.wajiha.ui.home.hero

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.data.db.GameEntity
import com.wajiha.data.prefs.HeroDisplaySlot
import com.wajiha.data.prefs.HeroElementId
import com.wajiha.data.prefs.HeroLayout
import com.wajiha.data.prefs.HeroLayoutPresets
import com.wajiha.input.GamepadKeys
import com.wajiha.state.DualScreenStore
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.components.gamepad.WajihaToggleSetting
import com.wajiha.ui.home.GameTile
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

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

@Composable
fun HeroLayoutEditorCanvasFromStore(
    tile: GameTile?,
    platformName: String?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
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
) {
    val previewTile = tile ?: sampleTile()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(selectedId, draft) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val el = draft.element(selectedId) ?: return@detectDragGestures
                        val nx = (el.x + drag.x / size.width).coerceIn(0f, 0.95f)
                        val ny = (el.y + drag.y / size.height).coerceIn(0f, 0.95f)
                        onDraftChange(draft.withElement(el.copy(x = nx, y = ny)))
                    }
                }.onPreviewKeyEvent { event ->
                    handleEditorKey(
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
            modifier = Modifier.fillMaxSize(),
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
) {
    val elements = draft.elements.filter { !it.id.detailOnly }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WajihaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Text(
            text = "Customize hero layout",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "L1/R1 cycle · D-pad nudge · X hide · Y classic · LT/RT size · B done",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HeroLayoutPresets.namedIds.forEach { id ->
                GamepadChip(
                    label = HeroLayoutPresets.label(id),
                    selected = draft.presetId == id,
                    onClick = {
                        onUpdate(HeroLayoutPresets.template(id).copy(configured = true))
                    },
                )
            }
        }
        elements.forEach { el ->
            val selected = el.id == selectedId
            GamepadButton(
                text = "${el.id.name}${if (el.visible) "" else " (hidden)"}",
                onClick = { onSelect(el.id) },
                outlined = !selected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val selected = draft.element(selectedId)
        if (selected != null) {
            WajihaToggleSetting(
                label = "Visible",
                checked = selected.visible,
                onCheckedChange = {
                    onUpdate(draft.withElement(selected.copy(visible = it)))
                },
                defaultChecked = true,
                onReset = {
                    onUpdate(draft.withElement(selected.copy(visible = true)))
                },
            )
            if (selectedId == HeroElementId.Cover) {
                WajihaToggleSetting(
                    label = "Cover border",
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
        GamepadButton(
            text = "Done",
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun HeroLayoutEditorControlsFromStore(
    onSaveAndExit: (HeroLayout) -> Unit,
    modifier: Modifier = Modifier,
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
    )
}

private fun handleEditorKey(
    eventType: androidx.compose.ui.input.key.KeyEventType,
    key: androidx.compose.ui.input.key.Key,
    draft: HeroLayout,
    selectedId: HeroElementId,
    onSelect: (HeroElementId) -> Unit,
    onUpdate: (HeroLayout) -> Unit,
    onExit: () -> Unit,
): Boolean {
    val cycle =
        draft.elements
            .filter { it.id != HeroElementId.Backdrop && !it.id.detailOnly }
            .map { it.id }
    if (cycle.isEmpty()) return false
    val idx = cycle.indexOf(selectedId).coerceAtLeast(0)
    val el = draft.element(selectedId) ?: return false
    val step = 0.02f
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
            onUpdate(draft.withElement(el.copy(x = (el.x - step).coerceIn(0f, 0.95f))))
            true
        }

        GamepadKeys.isRight(eventType, key) -> {
            onUpdate(draft.withElement(el.copy(x = (el.x + step).coerceIn(0f, 0.95f))))
            true
        }

        GamepadKeys.isUp(eventType, key) -> {
            onUpdate(draft.withElement(el.copy(y = (el.y - step).coerceIn(0f, 0.95f))))
            true
        }

        GamepadKeys.isDown(eventType, key) -> {
            onUpdate(draft.withElement(el.copy(y = (el.y + step).coerceIn(0f, 0.95f))))
            true
        }

        GamepadKeys.isL2(eventType, key) -> {
            val w = (if (el.w > 0f) el.w else 0.3f) - step
            val h = (if (el.h > 0f) el.h else 0.2f) - step
            onUpdate(draft.withElement(el.copy(w = w.coerceAtLeast(0.08f), h = h.coerceAtLeast(0.06f))))
            true
        }

        GamepadKeys.isR2(eventType, key) -> {
            val w = (if (el.w > 0f) el.w else 0.3f) + step
            val h = (if (el.h > 0f) el.h else 0.2f) + step
            onUpdate(draft.withElement(el.copy(w = w.coerceAtMost(0.95f), h = h.coerceAtMost(0.95f))))
            true
        }

        else -> {
            false
        }
    }
}
