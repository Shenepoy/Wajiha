package com.wajiha.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.data.prefs.HeroDisplaySlot
import com.wajiha.input.GamepadScreen
import com.wajiha.input.MirroredGamepadHintsHost
import com.wajiha.input.requestContentFocus
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.state.HeroContext
import com.wajiha.ui.components.gamepad.FocusScreenHintOverlay
import com.wajiha.ui.home.HomeUiState
import com.wajiha.ui.home.TopScreen
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Dual-display layout roles for the SELECT / swap-screen toggle.
 *
 * - Menu display: grid and launcher routes (settings, apps, scraper, …).
 * - Hero display: contextual [TopScreen] artwork driven by [HeroContext].
 *
 * When roles are swapped, the menu moves to the primary (top) activity and the
 * hero moves to the secondary (bottom) activity — on every route, not only Home.
 */
fun menuOnPrimary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = !isDual || swapScreenRoles

fun heroOnPrimary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = isDual && !swapScreenRoles

fun menuOnSecondary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = isDual && !swapScreenRoles

fun heroOnSecondary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = isDual && swapScreenRoles

@Composable
fun LauncherHeroPane(
    state: HomeUiState,
    focusedGameId: Long?,
    heroContext: HeroContext,
    modifier: Modifier = Modifier,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
) {
    val hero = launcherHero(state, focusedGameId)
    val contentFocus = remember { FocusRequester() }
    val dualStore = koinInject<DualScreenStore>()
    val l2HintOwner by dualStore.l2HintOwner.collectAsState()
    // Show on this hero pane only when it is the unfocused L2 target.
    val showFocusHint = gamepadOwner != null && l2HintOwner == gamepadOwner
    GamepadScreen(
        layerId = "launcher_hero",
        modifier = modifier,
        owner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = { contentFocus.requestContentFocus() },
    ) {
        // Full-bleed hero: mirrored hints overlay the artwork instead of
        // reserving a Column slot that crops the image.
        Box(modifier = Modifier.fillMaxSize()) {
            TopScreen(
                focused = hero.focusedTile,
                platformName = hero.platformName,
                heroContext = heroContext,
                contentFocusRequester = contentFocus,
                displaySlot =
                    if (gamepadOwner == GamepadOwner.Secondary) {
                        HeroDisplaySlot.Secondary
                    } else {
                        HeroDisplaySlot.Primary
                    },
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // Lift mirrored hints / L2 chrome slightly off the bottom edge.
                        .padding(bottom = WajihaSpacing.md),
            ) {
                // Floating L2 sits above the mirrored bar when both are present.
                if (showFocusHint) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FocusScreenHintOverlay(
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
                // When Settings → Screens → Swap gamepad hints is on, menu hints
                // publish here so the hero display shows the controller bar over art.
                MirroredGamepadHintsHost()
            }
        }
    }
}
