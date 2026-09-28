package com.wajiha.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.gamepad.FocusScreenGamepadHint
import com.wajiha.ui.components.gamepad.GamepadActionBar
import com.wajiha.ui.components.gamepad.WajihaGlyphAction
import com.wajiha.ui.components.gamepad.withoutFocusScreenHint
import com.wajiha.ui.navigation.menuOnPrimary
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject

/**
 * Process-wide mirror of the active menu screen's [GamepadHint] list so the
 * hero display can show them when Settings → Screens → Swap gamepad hints is on.
 *
 * Never includes L2 Focus — that hint is owned by [DualScreenStore.l2HintOwner]
 * and shown locally on the unfocused display (or as a hero overlay).
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
        _hints.value = hints.withoutFocusScreenHint()
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
 * L2 "Focus" is stripped from [hints], never mirrored, and re-added only when
 * [DualScreenStore.l2HintOwner] matches [hostOwner] (unfocused target).
 * The menu bar always stays. Swap gamepad hints only publishes a copy so the
 * wide hero can show the primary act.
 *
 * [hostOwner] null = treat as menu host (single-display / embedded panels); no L2.
 */
@Composable
fun MirroredOrLocalGamepadActionBar(
    publisherId: String,
    hints: List<GamepadHint>,
    hostOwner: GamepadOwner? = null,
) {
    val dualStore = koinInject<DualScreenStore>()
    val l2HintOwner by dualStore.l2HintOwner.collectAsState()
    val swapActive = rememberSwapGamepadHintsActive()
    val isMenuHost = rememberIsMenuHost(hostOwner)
    val baseHints =
        remember(hints) {
            hints.withoutFocusScreenHint()
        }
    val showL2Here = hostOwner != null && l2HintOwner == hostOwner
    val l2Hints = if (showL2Here) listOf(FocusScreenGamepadHint) else emptyList()
    val publishToHero = swapActive && isMenuHost && baseHints.isNotEmpty()
    PublishGamepadHintsEffect(publisherId, baseHints, publishToHero)
    val localHints = baseHints + l2Hints
    if (localHints.isNotEmpty()) {
        GamepadActionBar(hints = localHints)
    }
}

/** Wide hero glass. Narrow Thor Screen-2 stays under this. */
private val WideHeroGlassMin = 640.dp

/**
 * Hero-side host for Swap gamepad hints.
 * The wide hero shows the primary act (A, or the first hint) at the bottom end.
 * The narrow hero stays clear. The rest of the menu bar stays on the menu glass.
 */
@Composable
fun MirroredGamepadHintsHost(modifier: Modifier = Modifier) {
    val swapActive = rememberSwapGamepadHintsActive()
    val hints by GamepadHintMirror.hints.collectAsState()
    val primary =
        remember(hints) {
            val safe = hints.withoutFocusScreenHint()
            safe.firstOrNull { it.button == GamepadHintButton.A } ?: safe.firstOrNull()
        }
    if (!swapActive || primary == null) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < WideHeroGlassMin) return@BoxWithConstraints
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(WajihaSpacing.md),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Surface(
                shape = WajihaShapes.chip,
                color = WajihaColors.HeroScrim,
            ) {
                WajihaGlyphAction(
                    button = primary.button,
                    label = primary.action,
                    labelColor = WajihaColors.OnDark,
                    modifier =
                        Modifier.padding(
                            horizontal = WajihaSpacing.sm,
                            vertical = WajihaSpacing.xs,
                        ),
                )
            }
        }
    }
}
