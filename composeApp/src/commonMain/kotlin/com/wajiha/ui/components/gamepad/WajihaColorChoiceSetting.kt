package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.theme.WajihaElevation
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

data class WajihaColorChoice(
    val value: String,
    val label: String,
    val color: Color,
)

/**
 * Settings-specific expandable color selector. Rendering and custom-value policy
 * stay caller-controlled while expansion, reset, focus, and swatches are shared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WajihaColorChoiceSetting(
    label: String,
    choices: List<WajihaColorChoice>,
    selectedValue: String,
    selectedLabel: String,
    selectedColor: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    defaultValue: String? = null,
    onReset: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    focusId: Any? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    onHintCapabilitiesChanged: ((SettingHintCapabilities?) -> Unit)? = null,
    customSelected: Boolean = false,
    onCustomChoice: (() -> Unit)? = null,
    customContent: (@Composable (collapse: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val resolvedFocusId = focusId ?: label
    val canReset = onReset != null && defaultValue != null && selectedValue != defaultValue
    val headerFocusRequester = focusRequester ?: remember { FocusRequester() }
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val showDescription =
        !description.isNullOrBlank() && !settings.settingsHeroHelp && !expanded
    val swatchFocusRequesters =
        remember(choices.size, onCustomChoice != null) {
            List(choices.size + if (onCustomChoice != null) 1 else 0) { FocusRequester() }
        }

    fun collapse() {
        expanded = false
    }

    fun collapseToHeader() {
        collapse()
        try {
            headerFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun select(value: String) {
        onSelect(value)
        collapse()
    }

    fun focusSelection() {
        val selectedIndex =
            if (customSelected && onCustomChoice != null) {
                choices.size
            } else {
                choices.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
            }
        try {
            swatchFocusRequesters.getOrNull(selectedIndex)?.requestFocus()
        } catch (_: Exception) {
        }
    }

    WajihaExpandableSection(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
        focusRequester = headerFocusRequester,
        focusId = resolvedFocusId,
        // Expand under the row — pinning the header to the panel top jumps the ring.
        scrollHeaderToTop = false,
        onReset = onReset.takeIf { canReset },
        onFocusedChanged = onFocusedChanged,
        onHintCapabilitiesChanged = onHintCapabilitiesChanged,
        onExpanded = ::focusSelection,
        headerModifier =
            Modifier
                .clip(WajihaShapes.focus)
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
        header = { isExpanded ->
            SettingLabelWithReset(
                label = label,
                canReset = canReset,
                onReset = onReset,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(WajihaShapes.chip)
                        .background(selectedColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), WajihaShapes.chip),
            )
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            SettingExpansionIcon(expanded = isExpanded)
        },
        supportingContent =
            if (showDescription) {
                {
                    Text(
                        text = description.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                start = WajihaSpacing.sm,
                                end = WajihaSpacing.sm,
                                top = WajihaSpacing.xs / 2,
                            ),
                    )
                }
            } else {
                null
            },
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = WajihaSpacing.xs / 2),
            shape = WajihaShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = WajihaElevation.low,
        ) {
            Column(
                modifier = Modifier.padding(WajihaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                ) {
                    choices.forEachIndexed { index, choice ->
                        WajihaColorSwatch(
                            label = choice.label,
                            color = choice.color,
                            selected = !customSelected && choice.value == selectedValue,
                            focusRequester = swatchFocusRequesters[index],
                            focusId = "$resolvedFocusId:color:${choice.value}",
                            onNavigateUp = if (index == 0) ::collapseToHeader else null,
                            onSelect = { select(choice.value) },
                        )
                    }
                    if (onCustomChoice != null) {
                        WajihaColorSwatch(
                            label = "Custom",
                            color =
                                if (customSelected) {
                                    selectedColor
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            selected = customSelected,
                            focusRequester = swatchFocusRequesters[choices.size],
                            focusId = "$resolvedFocusId:color:custom",
                            showPlusGlyph = !customSelected,
                            onNavigateUp = if (choices.isEmpty()) ::collapseToHeader else null,
                            onSelect = onCustomChoice,
                        )
                    }
                }
                customContent?.invoke(::collapse)
            }
        }
    }
}

@Composable
private fun WajihaColorSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    focusRequester: FocusRequester,
    focusId: Any,
    showPlusGlyph: Boolean = false,
    onNavigateUp: (() -> Unit)? = null,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            Modifier
                .wajihaFocusIndicator(
                    highlighted = !useCustomNav && focused,
                    shape = WajihaShapes.chip,
                ).clip(WajihaShapes.chip)
                .focusRequester(focusRequester)
                .reportSectionVisibleFocus(focusRequester, focusId)
                .then(
                    if (!useCustomNav) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .wajihaGamepadFocus()
                            .onPreviewKeyEvent { event ->
                                when {
                                    GamepadKeys.isUp(event.type, event.key) && onNavigateUp != null -> {
                                        onNavigateUp()
                                        true
                                    }

                                    GamepadKeys.isConfirm(event.type, event.key) -> {
                                        onSelect()
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            }
                    } else {
                        Modifier
                    },
                ).pointerInput(label) {
                    detectTapGestures { onSelect() }
                }.padding(WajihaSpacing.xs / 2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(WajihaShapes.chip)
                    .background(color)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            },
                        shape = WajihaShapes.chip,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (showPlusGlyph) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
