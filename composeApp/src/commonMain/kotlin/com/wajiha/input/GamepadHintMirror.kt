package com.wajiha.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.gamepad.GamepadActionBar
import com.wajiha.ui.navigation.menuOnPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject

/**
 * Process-wide mirror of the active menu screen's [GamepadHint] list so the
 * hero display can show them when Settings → Screens → Swap gamepad hints is on.
 */
object GamepadHintMirror {
    private val _hints = MutableStateFlow<List<GamepadHint>>(emptyList())
    val hints: StateFlow<List<GamepadHint>> = _hints.asStateFlow()

    @Volatile
    private var publisherId: String? = null

    fun publish(
        id: String,
        hints: List<GamepadHint>,
    ) {
        publisherId = id
        _hints.value = hints
    }

    fun clear(id: String) {
        if (publisherId == id) {
            publisherId = null
            _hints.value = emptyList()
        }
    }
}

/** True while dual-browsing and the user wants hints on the hero display. */
@Composable
fun rememberSwapGamepadHintsActive(
    settingsRepository: SettingsRepository = koinInject(),
    dualStore: DualScreenStore = koinInject(),
): Boolean {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val dualState by dualStore.state.collectAsState()
    return settings.swapGamepadHints &&
        !settings.singleScreen &&
        dualState == DualScreenState.DualBrowsing
}

@Composable
private fun rememberIsMenuHost(
    hostOwner: GamepadOwner?,
    settingsRepository: SettingsRepository = koinInject(),
    dualStore: DualScreenStore = koinInject(),
): Boolean {
    if (hostOwner == null) return true
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val dualState by dualStore.state.collectAsState()
    val isDual = dualState != DualScreenState.SingleDisplay
    val primaryIsMenu = menuOnPrimary(isDual, settings.swapScreenRoles)
    return when (hostOwner) {
        GamepadOwner.Primary -> primaryIsMenu
        GamepadOwner.Secondary -> !primaryIsMenu
    }
}

@Composable
fun PublishGamepadHintsEffect(
    publisherId: String,
    hints: List<GamepadHint>,
    enabled: Boolean,
) {
    DisposableEffect(enabled, publisherId, hints) {
        if (enabled) {
            GamepadHintMirror.publish(publisherId, hints)
            onDispose { GamepadHintMirror.clear(publisherId) }
        } else {
            onDispose { }
        }
    }
}

/**
 * Shows [hints] locally, or publishes them to the hero when swap-hints is active
 * and this host is the menu display.
 *
 * [hostOwner] null = treat as menu host (single-display / embedded panels).
 */
@Composable
fun MirroredOrLocalGamepadActionBar(
    publisherId: String,
    hints: List<GamepadHint>,
    hostOwner: GamepadOwner? = null,
) {
    val swapActive = rememberSwapGamepadHintsActive()
    val isMenuHost = rememberIsMenuHost(hostOwner)
    val mirrorAway = swapActive && isMenuHost && hints.isNotEmpty()
    PublishGamepadHintsEffect(publisherId, hints, mirrorAway)
    if (!mirrorAway && hints.isNotEmpty()) {
        GamepadActionBar(hints = hints)
    }
}

/** Hero-side host for mirrored hints (empty when swap is off or nothing published). */
@Composable
fun MirroredGamepadHintsHost() {
    val swapActive = rememberSwapGamepadHintsActive()
    val hints by GamepadHintMirror.hints.collectAsState()
    if (swapActive && hints.isNotEmpty()) {
        GamepadActionBar(hints = hints)
    }
}
