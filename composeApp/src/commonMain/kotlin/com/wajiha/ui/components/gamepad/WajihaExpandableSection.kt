package com.wajiha.ui.components.gamepad

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadNavItem
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.input.LocalGamepadNavController
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.theme.WajihaShapes

/**
 * Shared expansion behavior for settings headers.
 *
 * Owns A/touch toggling, B collapse, header focus restoration, optional
 * scroll-to-header, section focus registration, and expansion animation.
 * Header and body rendering remain specialized at the call site.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WajihaExpandableSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusId: Any = remember { Any() },
    enabled: Boolean = true,
    scrollHeaderToTop: Boolean = true,
    onReset: (() -> Unit)? = null,
    onFocusedChanged: ((Boolean) -> Unit)? = null,
    onHintCapabilitiesChanged: ((SettingHintCapabilities?) -> Unit)? = null,
    onExpanded: (() -> Unit)? = null,
    headerModifier: Modifier = Modifier,
    headerVerticalAlignment: Alignment.Vertical = Alignment.Top,
    headerHorizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    header: @Composable RowScope.(expanded: Boolean) -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var headerFocused by remember { mutableStateOf(false) }
    val navController = LocalGamepadNavController.current
    val useCustomNav = navController != null
    val resolvedFocusRequester = focusRequester ?: remember { FocusRequester() }
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val anchor =
        remember(continuity, layerId, focusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(focusId, layerId)
        }
    val sectionScroll = LocalSettingSectionScroll.current
    var headerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val feedback = LocalUiFeedback.current
    val hintReporter = LocalSettingHintCapabilitiesReporter.current
    val currentOnExpanded by rememberUpdatedState(onExpanded)

    fun setExpanded(value: Boolean) {
        if (!enabled || value == expanded) return
        feedback.confirm()
        onExpandedChange(value)
        if (!value) {
            try {
                resolvedFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        val coordinates = headerCoordinates
        if (scrollHeaderToTop && sectionScroll != null && coordinates != null) {
            sectionScroll.scrollHeaderToTop(coordinates)
        }
        withFrameNanos { }
        currentOnExpanded?.invoke()
    }

    BackHandler(enabled = expanded) {
        setExpanded(false)
    }

    DisposableEffect(navController, expanded, focusId) {
        if (navController != null && expanded) {
            navController.registerBackHandler(focusId) {
                setExpanded(false)
                true
            }
        }
        onDispose {
            navController?.unregisterBackHandler(focusId)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val headerContent: @Composable (Modifier, Boolean) -> Unit = { baseModifier, highlighted ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { headerCoordinates = it }
                        .wajihaFocusIndicator(
                            highlighted = enabled && highlighted,
                            shape = WajihaShapes.focus,
                            focusAnchor = anchor,
                        ),
            ) {
                Row(
                    modifier =
                        baseModifier
                            .fillMaxWidth()
                            .focusRequester(resolvedFocusRequester)
                            .reportSectionVisibleFocus(resolvedFocusRequester, focusId)
                            .focusProperties { canFocus = enabled }
                            .then(
                                if (!useCustomNav && enabled) {
                                    Modifier
                                        .onFocusChanged {
                                            headerFocused = it.isFocused
                                            if (it.isFocused && anchor != null) {
                                                continuity?.claim(anchor, FocusClaimSource.Compose)
                                            }
                                            onFocusedChanged?.invoke(it.isFocused)
                                            val capabilities =
                                                SettingHintCapabilities(
                                                    primaryAction = if (expanded) "Close" else "Open",
                                                    canReset = onReset != null,
                                                ).takeIf { _ -> it.isFocused }
                                            onHintCapabilitiesChanged?.invoke(capabilities)
                                            hintReporter(capabilities)
                                        }.wajihaGamepadFocus()
                                        .onPreviewKeyEvent { event ->
                                            when {
                                                GamepadKeys.isConfirm(event.type, event.key) -> {
                                                    setExpanded(!expanded)
                                                    true
                                                }

                                                GamepadKeys.isY(event.type, event.key) && onReset != null -> {
                                                    feedback.confirm()
                                                    onReset()
                                                    true
                                                }

                                                else -> {
                                                    false
                                                }
                                            }
                                        }.pointerInput(enabled, expanded) {
                                            detectTapGestures {
                                                if (anchor != null) {
                                                    continuity?.claim(anchor, FocusClaimSource.Touch)
                                                }
                                                try {
                                                    resolvedFocusRequester.requestFocus()
                                                } catch (_: Exception) {
                                                }
                                                setExpanded(!expanded)
                                            }
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    verticalAlignment = headerVerticalAlignment,
                    horizontalArrangement = headerHorizontalArrangement,
                ) {
                    header(expanded)
                }
                supportingContent?.invoke()
            }
        }

        if (useCustomNav) {
            GamepadNavItem(
                onActivate = { setExpanded(!expanded) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                itemId = focusId,
                // Header already paints [wajihaFocusIndicator]; avoid double chrome.
                drawChrome = false,
            ) { highlighted ->
                LaunchedEffect(highlighted, sectionScroll, focusId) {
                    if (highlighted) {
                        sectionScroll?.ensureFocusVisible(focusId)
                    }
                }
                LaunchedEffect(highlighted, expanded) {
                    onFocusedChanged?.invoke(highlighted)
                    val capabilities =
                        SettingHintCapabilities(
                            primaryAction = if (expanded) "Close" else "Open",
                            canReset = onReset != null,
                        ).takeIf { highlighted }
                    onHintCapabilitiesChanged?.invoke(capabilities)
                    hintReporter(capabilities)
                }
                headerContent(headerModifier, highlighted)
            }
        } else {
            LaunchedEffect(expanded) {
                if (headerFocused) {
                    val capabilities =
                        SettingHintCapabilities(
                            primaryAction = if (expanded) "Close" else "Open",
                            canReset = onReset != null,
                        )
                    onHintCapabilitiesChanged?.invoke(capabilities)
                    hintReporter(capabilities)
                }
            }
            headerContent(headerModifier, headerFocused)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            content()
        }
    }
}
