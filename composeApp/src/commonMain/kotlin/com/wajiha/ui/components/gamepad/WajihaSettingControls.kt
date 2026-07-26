package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import com.wajiha.ui.components.WajihaFolderChromeMetrics
import com.wajiha.ui.components.folderChromeBorder
import com.wajiha.ui.components.folderChromeOutlineColor
import com.wajiha.ui.components.folderPanelOpenTopBorder
import com.wajiha.ui.components.softOutlineBorder
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun WajihaSettingBlurb(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = WajihaSpacing.micro),
    )
}

@Composable
fun WajihaSettingDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = WajihaAlphas.divider),
        modifier = Modifier.padding(vertical = WajihaSpacing.micro),
    )
}

/**
 * Inset Cocoon-style group card for settings clusters. Headers are not focusable;
 * place [WajihaSettingDivider] only between sibling rows inside [content].
 *
 * Publishes a nested [LocalFocusRingClipViewport] so Outside focus chrome on rows
 * inside the card cannot paint past this section’s edges.
 *
 * Prefer a short [title]. Leave help on focused rows or the dual-display hero —
 * avoid stacked blurbs on tab + group + row.
 */
@Composable
fun WajihaSettingGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = WajihaShapes.card
    val focusClipViewport = remember { FocusRingClipViewport() }
    // Nested clip viewport: Outside focus chrome stays inside this section card.
    // Background + border (no Surface clip) so local draws aren't double-clipped.
    CompositionLocalProvider(LocalFocusRingClipViewport provides focusClipViewport) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        focusClipViewport.boundsInRoot = coords.boundsInRoot()
                    }.background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = shape,
                    ).border(border = softOutlineBorder(), shape = shape)
                    .padding(
                        horizontal = WajihaSpacing.sm,
                        vertical = WajihaSpacing.xs,
                    ),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .focusProperties { canFocus = false }
                            .padding(bottom = WajihaSpacing.micro),
                )
            }
            if (description != null) {
                WajihaSettingBlurb(description)
            }
            content()
        }
    }
}

/** Standard single-action settings row with a touch-friendly trailing button. */
@Composable
fun WajihaActionSetting(
    label: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    labelMeta: String? = null,
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
) {
    GamepadSettingRow(
        label = label,
        description = description,
        labelMeta = labelMeta,
        type = SettingType.Action,
        onActivate = onClick,
        focusRequester = focusRequester,
        onFocusedChanged = onFocusedChanged,
        modifier = modifier,
        content = {
            SettingsTrailingActionButton(
                text = actionLabel,
                onClick = onClick,
                width = SettingsSingleTrailingActionWidth,
            )
        },
    )
}

/** Hub row that opens a denser settings cluster on a drill-in page. */
@Composable
fun WajihaSettingOpenSetting(
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
) {
    WajihaActionSetting(
        label = label,
        actionLabel = "Open",
        onClick = onOpen,
        modifier = modifier,
        description = description,
        focusRequester = focusRequester,
        onFocusedChanged = onFocusedChanged,
    )
}

/**
 * Full-screen Settings drill-in: Back + title, no folder tabs.
 * Canonical host for Scraper / Screens / Appearance / System (and similar)
 * destinations — replace [WajihaFolderSettingChrome], do not nest inside it.
 *
 * Back uses [GamepadButton.gamepadFocusable] = false so focus stays in the
 * content panel (same as scraper Batch / Sources pages).
 */
@Composable
fun WajihaSettingFullscreenPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = WajihaFolderChromeMetrics.horizontalPadding),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = WajihaFolderChromeMetrics.topPadding)
                    .defaultMinSize(minHeight = LocalSettingRowMinHeight.current),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            GamepadButton(
                text = "Back",
                onClick = onBack,
                outlined = true,
                gamepadFocusable = false,
                sound = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
        }
        WajihaSettingPanel(
            folderPanel = false,
            scrollable = scrollable,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = WajihaFolderChromeMetrics.panelBottomPadding),
            sectionContent = content,
        )
    }
}

/**
 * Legacy in-panel Back + title row. Prefer [WajihaSettingFullscreenPage] for
 * Settings-family drill-ins so folder tabs do not remain visible.
 */
@Composable
fun WajihaSettingSubpage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backFocusRequester: FocusRequester? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            GamepadButton(
                text = "Back",
                onClick = onBack,
                outlined = true,
                focusRequester = backFocusRequester,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun WajihaSettingPanel(
    modifier: Modifier = Modifier,
    folderPanel: Boolean = false,
    scrollable: Boolean = true,
    focusRestorer: SettingSectionFocusRestorer? = null,
    sectionContent: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (folderPanel) WajihaShapes.folderPanel else WajihaShapes.card
    val chromeBorder = folderChromeBorder()
    val outlineColor = folderChromeOutlineColor()
    val focusClipViewport = remember { FocusRingClipViewport() }

    CompositionLocalProvider(LocalFocusRingClipViewport provides focusClipViewport) {
        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        focusClipViewport.boundsInRoot = coords.boundsInRoot()
                    }.then(
                        if (folderPanel) {
                            // Top open so the selected folder tab can join the card.
                            Modifier.folderPanelOpenTopBorder(outlineColor, chromeBorder.width)
                        } else {
                            Modifier.border(border = chromeBorder, shape = shape)
                        },
                    ),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = WajihaSpacing.sm,
                            end = WajihaSpacing.sm,
                            bottom = WajihaSpacing.sm,
                            // Extra air under the folder tab join.
                            top = if (folderPanel) WajihaSpacing.md else WajihaSpacing.sm,
                        ),
            ) {
                SettingSectionScrollColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                    focusRestorer = focusRestorer,
                    scrollEnabled = scrollable,
                ) {
                    sectionContent()
                }
            }
        }
    }
}

@Composable
fun WajihaToggleSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    defaultChecked: Boolean,
    onReset: () -> Unit,
    description: String? = null,
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.Toggle,
        checked = checked,
        onCheckedChange = if (enabled) onCheckedChange else null,
        onReset = if (enabled) onReset else null,
        focusRequester = focusRequester,
        isAtDefault = checked == defaultChecked,
        overridden = overridden,
        overrideHint = overrideHint,
        onFocusedChanged = onFocusedChanged,
        modifier =
            if (enabled) {
                Modifier
            } else {
                Modifier
                    .alpha(0.45f)
                    .focusProperties { canFocus = true }
            },
    )
}

@Composable
fun WajihaNumberSetting(
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    defaultValue: Int,
    onReset: () -> Unit,
    step: Int = 1,
    valueLabel: (Int) -> String = { it.toString() },
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.Number,
        numberValue = value,
        onNumberChange = onValueChange,
        numberRange = range,
        numberStep = step,
        numberLabel = valueLabel,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = value == defaultValue,
        overridden = overridden,
        overrideHint = overrideHint,
        onFocusedChanged = onFocusedChanged,
    )
}

@Composable
fun WajihaChoiceSetting(
    label: String,
    description: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.BinaryChoice,
        options = options,
        selected = selected,
        onSelect = onSelect,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = selected == defaultValue,
        overridden = overridden,
        overrideHint = overrideHint,
        onFocusedChanged = onFocusedChanged,
    )
}

@Composable
fun WajihaMultiChoiceSetting(
    label: String,
    description: String,
    choiceOptions: List<MultiChoiceOption>,
    selected: String,
    onSelect: (String) -> Unit,
    defaultValue: String,
    onReset: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
) {
    GamepadSettingRow(
        label = label,
        description = description,
        type = SettingType.MultiChoice,
        multiChoiceOptions = choiceOptions,
        selected = selected,
        onSelect = onSelect,
        focusRequester = focusRequester,
        onReset = onReset,
        isAtDefault = selected == defaultValue,
        overridden = overridden,
        overrideHint = overrideHint,
        onFocusedChanged = onFocusedChanged,
    )
}

/**
 * Continuous 0..1 (or custom range) slider row for System / Quick Settings.
 * Left/Right nudge; no Y-reset (hardware values have no launcher default).
 */
@Composable
fun WajihaSliderSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String? = null,
    focusId: Any? = null,
    focusRequester: FocusRequester? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
) {
    GamepadSlider(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        valueLabel = valueLabel,
        description = description,
        focusId = focusId,
        focusRequester = focusRequester,
        onFocusedChanged = onFocusedChanged,
    )
}
