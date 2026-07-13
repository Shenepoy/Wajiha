package com.wajiha.ui.components.gamepad

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Compact row height for multi-choice list items (denser than [WajihaSpacing.touchMin]). */
private val MultiChoiceItemMinHeight = 34.dp

/** One selectable value in a [SettingType.MultiChoice] row. */
data class MultiChoiceOption(
    val value: String,
    val label: String,
    val description: String? = null,
    val icon: String? = null,
    /** Optional Kenney / drawable leading icon (takes precedence over [icon] text). */
    val iconRes: DrawableResource? = null,
    val enabled: Boolean = true,
)

/** Setting row interaction pattern for gamepad-first settings screens. */
enum class SettingType {
    /** On/off toggle — A or Left/Right flips the switch. */
    Toggle,

    /** Two or three discrete values — inline segmented pills beside the label. */
    BinaryChoice,

    /** Four or more values — collapsed row that expands into a vertical radio list. */
    MultiChoice,

    /** Integer value — Left decreases, Right increases; A also steps up. */
    Number,

    /** Row supports Y → reset when [GamepadSettingRow.onReset] is set. */
    WithReset,
}

/**
 * Unified settings row: whole row receives gamepad focus chrome; A activates;
 * optional yellow reset glyph (Y) when [onReset] is provided and [isAtDefault] is false.
 */
@Composable
fun GamepadSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    type: SettingType = SettingType.Toggle,
    focusRequester: FocusRequester? = null,
    onReset: (() -> Unit)? = null,
    isAtDefault: Boolean = false,
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    options: List<Pair<String, String>> = emptyList(),
    multiChoiceOptions: List<MultiChoiceOption> = emptyList(),
    selected: String = "",
    onSelect: ((String) -> Unit)? = null,
    onActivate: (() -> Unit)? = null,
    numberValue: Int = 0,
    onNumberChange: ((Int) -> Unit)? = null,
    numberRange: IntRange = 0..100,
    numberStep: Int = 1,
    numberLabel: ((Int) -> String)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    if (type == SettingType.MultiChoice && (multiChoiceOptions.isNotEmpty() || options.isNotEmpty())) {
        val resolved =
            if (multiChoiceOptions.isNotEmpty()) {
                multiChoiceOptions
            } else {
                options.map { (value, display) -> MultiChoiceOption(value, display) }
            }
        MultiChoiceSettingRow(
            label = label,
            description = description,
            options = resolved,
            selected = selected,
            onSelect = onSelect,
            focusRequester = focusRequester,
            onReset = onReset,
            isAtDefault = isAtDefault,
            overridden = overridden,
            overrideHint = overrideHint,
            modifier = modifier,
        )
        return
    }

    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val highlight = !useCustomNav && focused
    val feedback = LocalUiFeedback.current
    val canReset = onReset != null && !isAtDefault
    val stackedChoices = type == SettingType.BinaryChoice && options.size > 3

    fun adjustNumber(delta: Int) {
        val changer = onNumberChange ?: return
        val next = (numberValue + delta).coerceIn(numberRange)
        if (next != numberValue) {
            feedback.navigate()
            changer(next)
        }
    }

    fun activate() {
        when {
            onActivate != null -> {
                feedback.confirm()
                onActivate()
            }

            type == SettingType.Number && onNumberChange != null -> {
                adjustNumber(+numberStep)
            }

            onCheckedChange != null -> {
                feedback.confirm()
                onCheckedChange(!checked)
            }

            options.isNotEmpty() && onSelect != null -> {
                feedback.navigate()
                val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
                val next = options[(idx + 1) % options.size].first
                onSelect(next)
            }
        }
    }

    fun handleHorizontalKey(key: Key): Boolean {
        if (key != Key.DirectionLeft && key != Key.DirectionRight) return false
        return when (type) {
            SettingType.Toggle -> {
                feedback.navigate()
                onCheckedChange?.invoke(!checked)
                true
            }

            SettingType.Number -> {
                adjustNumber(if (key == Key.DirectionLeft) -numberStep else numberStep)
                true
            }

            else -> {
                false
            }
        }
    }

    val interactionModifier =
        Modifier
            .clip(WajihaShapes.focus)
            .wajihaFocusIndicator(highlighted = highlight)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (!useCustomNav) {
                    Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .wajihaGamepadFocus()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                GamepadKeys.isConfirm(event.type, event.key) -> {
                                    activate()
                                    true
                                }

                                handleHorizontalKey(event.key) -> {
                                    true
                                }

                                GamepadKeys.isY(event.type, event.key) && canReset -> {
                                    feedback.confirm()
                                    onReset?.invoke()
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
            ).pointerInput(
                type,
                checked,
                options,
                selected,
                numberValue,
                onCheckedChange,
                onSelect,
                onActivate,
                onNumberChange,
            ) {
                if (type != SettingType.Number) {
                    detectTapGestures { activate() }
                }
            }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WajihaSpacing.touchMin),
    ) {
        if (stackedChoices && options.isNotEmpty()) {
            Column(
                modifier =
                    interactionModifier
                        .fillMaxWidth()
                        .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            ) {
                SettingLabelWithReset(
                    label = label,
                    canReset = canReset,
                    onReset = onReset,
                    overridden = overridden,
                    overrideHint = overrideHint,
                    modifier = Modifier.fillMaxWidth(),
                )
                SegmentedChoice(
                    options = options,
                    selected = selected,
                    onSelect = onSelect,
                    wrap = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier =
                    interactionModifier
                        .fillMaxWidth()
                        .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
            ) {
                SettingLabelWithReset(
                    label = label,
                    canReset = canReset,
                    onReset = onReset,
                    overridden = overridden,
                    overrideHint = overrideHint,
                    modifier = Modifier.weight(1f),
                )

                when {
                    content != null -> {
                        content()
                    }

                    type == SettingType.Toggle -> {
                        Switch(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = false,
                            colors =
                                SwitchDefaults.colors(
                                    disabledCheckedThumbColor = MaterialTheme.colorScheme.primary,
                                    disabledCheckedTrackColor =
                                        MaterialTheme.colorScheme.primaryContainer,
                                    disabledUncheckedThumbColor =
                                        MaterialTheme.colorScheme.outline,
                                    disabledUncheckedTrackColor =
                                        MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        )
                    }

                    type == SettingType.Number && onNumberChange != null -> {
                        GamepadNumberStepper(
                            value = numberValue,
                            onValueChange = onNumberChange,
                            range = numberRange,
                            step = numberStep,
                            valueLabel = numberLabel ?: { it.toString() },
                        )
                    }

                    options.isNotEmpty() -> {
                        SegmentedChoice(
                            options = options,
                            selected = selected,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }

        if (!description.isNullOrBlank()) {
            Text(
                text = description,
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
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MultiChoiceSettingRow(
    label: String,
    description: String?,
    options: List<MultiChoiceOption>,
    selected: String,
    onSelect: ((String) -> Unit)?,
    focusRequester: FocusRequester?,
    onReset: (() -> Unit)?,
    isAtDefault: Boolean,
    overridden: Boolean,
    overrideHint: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var headerFocused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val headerHighlight = !useCustomNav && headerFocused && !expanded
    val canReset = onReset != null && !isAtDefault
    val feedback = LocalUiFeedback.current
    val selectedOption = options.firstOrNull { it.value == selected }
    val selectedLabel = selectedOption?.label.orEmpty()

    val headerFocusRequester = focusRequester ?: remember { FocusRequester() }
    val sectionScroll = LocalSettingSectionScroll.current
    var headerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val listFocusRequesters =
        remember(options.size) {
            List(options.size) { FocusRequester() }
        }

    fun collapse() {
        expanded = false
        try {
            headerFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun expand() {
        expanded = true
    }

    fun selectOption(value: String) {
        feedback.confirm()
        onSelect?.invoke(value)
        collapse()
    }

    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        val header = headerCoordinates
        if (sectionScroll != null && header != null) {
            sectionScroll.scrollHeaderToTop(header)
        }
        delay(50)
        val selectedIndex =
            options
                .indexOfFirst { it.value == selected && it.enabled }
                .takeIf { it >= 0 }
                ?: options.indexOfFirst { it.enabled }.coerceAtLeast(0)
        try {
            listFocusRequesters[selectedIndex].requestFocus()
        } catch (_: Exception) {
        }
    }

    BackHandler(enabled = expanded) {
        collapse()
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WajihaSpacing.touchMin),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerCoordinates = it }
                    .clip(WajihaShapes.focus)
                    .wajihaFocusIndicator(highlighted = headerHighlight)
                    .focusRequester(headerFocusRequester)
                    .then(
                        if (!useCustomNav) {
                            Modifier
                                .onFocusChanged { headerFocused = it.isFocused }
                                .wajihaGamepadFocus()
                                .onPreviewKeyEvent { event ->
                                    when {
                                        GamepadKeys.isConfirm(event.type, event.key) -> {
                                            feedback.confirm()
                                            if (expanded) collapse() else expand()
                                            true
                                        }

                                        GamepadKeys.isY(event.type, event.key) && canReset -> {
                                            feedback.confirm()
                                            onReset?.invoke()
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
                    ).pointerInput(expanded) {
                        detectTapGestures {
                            feedback.confirm()
                            if (expanded) collapse() else expand()
                        }
                    }.padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            SettingLabelWithReset(
                label = label,
                canReset = canReset,
                onReset = onReset,
                overridden = overridden,
                overrideHint = overrideHint,
                modifier = Modifier.weight(1f),
            )
            selectedOption?.iconRes?.let { res ->
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
            Text(
                text = if (expanded) "˅" else "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!description.isNullOrBlank()) {
            Text(
                text = description,
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

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            MultiChoicePickerPanel(
                options = options,
                selected = selected,
                canReset = canReset,
                onReset = onReset,
                useCustomNav = useCustomNav,
                listFocusRequesters = listFocusRequesters,
                onSelect = ::selectOption,
            )
        }
    }
}

@Composable
private fun MultiChoicePickerPanel(
    options: List<MultiChoiceOption>,
    selected: String,
    canReset: Boolean,
    onReset: (() -> Unit)?,
    useCustomNav: Boolean,
    listFocusRequesters: List<FocusRequester>,
    onSelect: (String) -> Unit,
) {
    val feedback = LocalUiFeedback.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = WajihaSpacing.xs / 2),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(vertical = 2.dp)
                    .onPreviewKeyEvent { event ->
                        if (GamepadKeys.isY(event.type, event.key) && canReset) {
                            feedback.confirm()
                            onReset?.invoke()
                            true
                        } else {
                            false
                        }
                    },
        ) {
            options.forEachIndexed { index, option ->
                MultiChoiceListItem(
                    option = option,
                    selected = option.value == selected,
                    focusRequester = listFocusRequesters[index],
                    useCustomNav = useCustomNav,
                    onSelect = { onSelect(option.value) },
                )
            }
        }
    }
}

@Composable
private fun MultiChoiceListItem(
    option: MultiChoiceOption,
    selected: Boolean,
    focusRequester: FocusRequester,
    useCustomNav: Boolean,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val highlight = !useCustomNav && focused && option.enabled
    val disabledAlpha = 0.45f
    val labelColor =
        when {
            !option.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
    val detailColor =
        if (option.enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MultiChoiceItemMinHeight)
                .focusProperties { canFocus = option.enabled }
                .background(
                    when {
                        highlight -> {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        }

                        selected && option.enabled -> {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        }

                        selected && !option.enabled -> {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        }

                        else -> {
                            Color.Transparent
                        }
                    },
                ).wajihaFocusIndicator(highlighted = highlight, shape = RectangleShape)
                .focusRequester(focusRequester)
                .then(
                    if (!useCustomNav && option.enabled) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .wajihaGamepadFocus()
                            .onPreviewKeyEvent { event ->
                                if (GamepadKeys.isConfirm(event.type, event.key)) {
                                    onSelect()
                                    true
                                } else {
                                    false
                                }
                            }
                    } else {
                        Modifier
                    },
                ).then(
                    if (option.enabled) {
                        Modifier.pointerInput(option.value) {
                            detectTapGestures { onSelect() }
                        }
                    } else {
                        Modifier
                    },
                ).padding(horizontal = WajihaSpacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        val iconRes = option.iconRes
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(28.dp)
                        .then(if (option.enabled) Modifier else Modifier.alpha(disabledAlpha)),
                contentScale = ContentScale.Fit,
            )
        } else {
            val leading = option.icon ?: if (selected) "●" else "○"
            Text(
                text = leading,
                style = MaterialTheme.typography.labelMedium,
                color =
                    when {
                        !option.enabled -> detailColor
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(20.dp),
                textAlign = TextAlign.Center,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor,
                maxLines = 1,
            )
            if (!option.description.isNullOrBlank()) {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = detailColor,
                    maxLines = 1,
                )
            }
        }
        if (selected && option.enabled) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: ((String) -> Unit)?,
    wrap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalUiFeedback.current
    val containerModifier =
        modifier
            .clip(WajihaShapes.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))

    val segment: @Composable (Pair<String, String>) -> Unit = { (value, display) ->
        val isSelected = value == selected
        Box(
            modifier =
                Modifier
                    .clip(WajihaShapes.chip)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ).pointerInput(value, onSelect) {
                        detectTapGestures {
                            if (value != selected) {
                                feedback.navigate()
                                onSelect?.invoke(value)
                            }
                        }
                    }.padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs / 2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                textAlign = TextAlign.Center,
            )
        }
    }

    if (wrap) {
        FlowRow(
            modifier = containerModifier.padding(WajihaSpacing.xs / 2),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            options.forEach { segment(it) }
        }
    } else {
        Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            options.forEach { segment(it) }
        }
    }
}

@Composable
private fun SettingLabelWithReset(
    label: String,
    canReset: Boolean,
    onReset: (() -> Unit)?,
    overridden: Boolean = false,
    overrideHint: String = "Changed from global default",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (overridden) {
            PlatformOverrideIndicator(hint = overrideHint)
        }
        if (canReset) {
            ResetGlyph(onClick = onReset)
        }
    }
}

@Composable
private fun ResetGlyph(onClick: (() -> Unit)?) {
    val transition = rememberInfiniteTransition(label = "reset_spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "reset_rotation",
    )
    val tint = Color(0xFFFFC107).copy(alpha = 0.35f)
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .rotate(rotation)
                .pointerInput(onClick) {
                    detectTapGestures { onClick?.invoke() }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "↺",
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}
