package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.UiSound
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.theme.WajihaShapes

@Composable
fun GamepadChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gamepadFocusable: Boolean = true,
    focusRequester: FocusRequester? = null,
    focusId: Any? = null,
    sound: UiSound? = null,
    soundWhenUnselected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val useCustomNav = LocalGamepadNavController.current != null
    val feedback = LocalUiFeedback.current
    val interactionSource = rememberPressInteractionSource()
    val localFocusRequester = remember { FocusRequester() }
    val resolvedFocusRequester = focusRequester ?: localFocusRequester
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val resolvedFocusId = focusId ?: label
    val anchor =
        remember(continuity, layerId, resolvedFocusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(resolvedFocusId, layerId)
        }

    fun performClick() {
        val shouldPlay =
            when {
                soundWhenUnselected && !selected -> sound
                !soundWhenUnselected -> sound
                else -> null
            }
        when (shouldPlay) {
            UiSound.Navigate -> feedback.navigate()
            UiSound.Open -> feedback.confirm()
            UiSound.Back -> feedback.back()
            UiSound.Launch -> feedback.launch()
            null -> Unit
        }
        onClick()
    }

    FilterChip(
        selected = selected,
        onClick = ::performClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        shape = WajihaShapes.chip,
        interactionSource = interactionSource,
        modifier =
            modifier
                .focusRequester(resolvedFocusRequester)
                .wajihaFocusIndicator(
                    highlighted = !useCustomNav && focused,
                    shape = WajihaShapes.chip,
                    focusAnchor = anchor,
                ).clip(WajihaShapes.chip)
                .wajihaPressedFeedback(interactionSource, WajihaShapes.chip)
                .then(
                    if (!useCustomNav) {
                        Modifier
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused && anchor != null) {
                                    continuity?.claim(anchor, FocusClaimSource.Compose)
                                }
                            }.wajihaGamepadFocus(gamepadFocusable)
                            .onPreviewKeyEvent { event ->
                                if (GamepadKeys.isConfirm(event.type, event.key)) {
                                    performClick()
                                    true
                                } else {
                                    false
                                }
                            }
                    } else {
                        Modifier
                    },
                ).pointerInput(Unit) {
                    detectTapGestures {
                        if (anchor != null) {
                            continuity?.claim(anchor, FocusClaimSource.Touch)
                        }
                        try {
                            resolvedFocusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                        performClick()
                    }
                },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
    )
}
