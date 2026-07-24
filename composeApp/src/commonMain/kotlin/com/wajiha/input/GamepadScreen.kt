package com.wajiha.input

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.gamepad.FocusRingOverlayHost
import com.wajiha.ui.components.gamepad.LocalFocusRingOverlay
import com.wajiha.ui.components.gamepad.rememberFocusRingOverlayState
import com.wajiha.ui.theme.LocalInputMode
import com.wajiha.ui.theme.LocalInputModeController
import com.wajiha.ui.theme.rememberInputModeController

/**
 * Registers a gamepad layer and optional screen-level key handler.
 * Touch down claims gamepad for [owner] when [onClaimGamepad] is provided.
 * Tracks touch vs gamepad input mode for chrome visibility app-wide.
 *
 * Text edit defaults: A enters edit on [GamepadSafeTextField], B / system back
 * dismisses the keyboard, tap outside hides the keyboard.
 *
 * [onPreviewKey] is registered on [GamepadLayers] for focus-independent dispatch
 * (Android activity bridge) and mirrored on [onPreviewKeyEvent] when the focus
 * tree tunnels through this root — no root focus target (that would steal indicators).
 * Bridged preview keys also call [InputModeController.onGamepadKey] so Outside
 * focus rings return after a touch-open (D-pad never reached Compose otherwise).
 *
 * The layer [DisposableEffect] keys only on [layerId]. Preview handlers are kept
 * fresh via [rememberUpdatedState] so BottomScreen recompositions (common on the
 * secondary display) do not re-push the layer, re-arm the input grace period, or
 * climb above an open context-menu layer — which previously ate D-pad/A and made
 * the game context menu appear dead after X opened it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadScreen(
    layerId: String,
    modifier: Modifier = Modifier,
    owner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
    onPreviewKey: ((KeyEvent) -> Boolean)? = null,
    restorePolicy: FocusRestorePolicy = FocusRestorePolicy.PreserveAnchor,
    defaultFocusId: Any? = null,
    /** Called when [owner] gains gamepad ownership — restore content focus. */
    onOwnerGainedFocus: (suspend () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val processor = remember { GamepadInputProcessor() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val latestPreviewKey = rememberUpdatedState(onPreviewKey)
    val latestOwner = rememberUpdatedState(owner)
    val latestClaimGamepad = rememberUpdatedState(onClaimGamepad)
    // Created before the layer handler so bridged D-pad (GamepadPreviewKeyBridge)
    // can restore Gamepad chrome — those keys never reach onPreviewKeyEvent.
    val inputModeController = rememberInputModeController()
    val latestInputMode = rememberUpdatedState(inputModeController)
    val focusContinuity =
        remember(layerId, restorePolicy) {
            FocusContinuityController(layerId, restorePolicy)
        }

    LaunchedEffect(focusContinuity, defaultFocusId) {
        withFrameNanos { }
        focusContinuity.finishRestore(defaultFocusId)
    }

    DisposableEffect(layerId) {
        val handler: (KeyEvent) -> Boolean = { event ->
            if (GamepadKeys.switchesToGamepadMode(event)) {
                latestInputMode.value.onGamepadKey()
            }
            latestPreviewKey.value?.invoke(event) == true
        }
        GamepadLayers.stack.push(layerId)
        GamepadLayers.stack.setPreviewHandler(layerId, handler)
        processor.onLayerPushed()
        onDispose {
            GamepadLayers.stack.setPreviewHandler(layerId, null)
            GamepadLayers.stack.pop(layerId)
            processor.reset()
        }
    }

    // Always track ownership when [owner] is set so losing L2 clears focus rings.
    if (owner != null) {
        RememberGamepadOwnerFocus(
            owner = owner,
            onGained = onOwnerGainedFocus ?: {},
        )
    }

    val focusRingOverlay = rememberFocusRingOverlayState(focusContinuity)
    val textFieldEditing = GamepadTextEditRegistry.isEditing

    BackHandler(enabled = textFieldEditing) {
        dismissTextEdit(focusManager, keyboard)
    }

    CompositionLocalProvider(
        LocalInputModeController provides inputModeController,
        LocalInputMode provides inputModeController.mode,
        LocalFocusContinuityController provides focusContinuity,
        LocalFocusLayerId provides layerId,
        LocalGamepadOwner provides owner,
        LocalFocusRingOverlay provides focusRingOverlay,
    ) {
        FocusRingOverlayHost(
            state = focusRingOverlay,
            modifier =
                modifier
                    .fillMaxSize()
                    .pointerInput(inputModeController, focusManager, keyboard) {
                        detectTapGestures(
                            onPress = {
                                inputModeController.onTouch()
                                // Route gamepad keys to this display without a focus
                                // restore — claim is sticky routing only (no epoch yank).
                                val touchOwner = latestOwner.value
                                val claim = latestClaimGamepad.value
                                if (touchOwner != null && claim != null) {
                                    claim(touchOwner)
                                }
                                tryAwaitRelease()
                            },
                            onTap = {
                                if (GamepadTextEditRegistry.isEditing) {
                                    dismissTextEdit(focusManager, keyboard)
                                }
                            },
                        )
                    }.onPreviewKeyEvent { event ->
                        if (GamepadKeys.switchesToGamepadMode(event)) {
                            inputModeController.onGamepadKey()
                        }
                        if (
                            GamepadKeys.isBack(event.type, event.key) &&
                            dismissTextEdit(focusManager, keyboard)
                        ) {
                            return@onPreviewKeyEvent true
                        }
                        val claimOwner = latestOwner.value
                        val claimGamepad = latestClaimGamepad.value
                        if (
                            claimOwner != null &&
                            claimGamepad != null &&
                            event.type == KeyEventType.KeyDown &&
                            isGamepadClaimKey(event.key)
                        ) {
                            claimGamepad(claimOwner)
                        }
                        // Screen handlers (L1/R1 tab cycle, X, etc.) before grace/repeat throttle.
                        if (latestPreviewKey.value?.invoke(event) == true) {
                            return@onPreviewKeyEvent true
                        }
                        // Modal layers (context menu, dialogs) sit above this screen in
                        // [GamepadLayers]; do not grace/throttle-steal keys from them.
                        if (GamepadLayers.stack.topLayer != layerId) {
                            return@onPreviewKeyEvent false
                        }
                        processor.shouldConsume(event.type, event.key)
                    },
        ) {
            content()
        }
    }
}

private fun isGamepadClaimKey(key: androidx.compose.ui.input.key.Key): Boolean =
    key == androidx.compose.ui.input.key.Key.DirectionUp ||
        key == androidx.compose.ui.input.key.Key.DirectionDown ||
        key == androidx.compose.ui.input.key.Key.DirectionLeft ||
        key == androidx.compose.ui.input.key.Key.DirectionRight ||
        key == androidx.compose.ui.input.key.Key.ButtonA ||
        key == androidx.compose.ui.input.key.Key.ButtonB ||
        key == androidx.compose.ui.input.key.Key.ButtonX ||
        key == androidx.compose.ui.input.key.Key.ButtonL1 ||
        key == androidx.compose.ui.input.key.Key.ButtonR1 ||
        // L2/R2 are global (owner toggle / notifications) — never claim via Compose.
        key == androidx.compose.ui.input.key.Key.DirectionCenter ||
        key == androidx.compose.ui.input.key.Key.Enter
