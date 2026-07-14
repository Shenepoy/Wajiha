package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import com.wajiha.ui.components.folderChromeBorder
import com.wajiha.ui.components.folderChromeOutlineColor
import com.wajiha.ui.components.folderPanelOpenTopBorder
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun WajihaSettingBlurb(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
fun WajihaSettingDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
fun WajihaSettingPanel(
    modifier: Modifier = Modifier,
    folderPanel: Boolean = false,
    sectionContent: @Composable () -> Unit,
) {
    val shape = if (folderPanel) WajihaShapes.folderPanel else WajihaShapes.card
    val chromeBorder = folderChromeBorder()
    val outlineColor = folderChromeOutlineColor()

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
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
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                sectionContent()
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
        onFocusedChanged = onFocusedChanged,
    )
}
